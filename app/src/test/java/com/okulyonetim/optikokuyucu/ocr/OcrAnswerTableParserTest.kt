package com.okulyonetim.optikokuyucu.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrAnswerTableParserTest {
    private val abcd = setOf("A", "B", "C", "D")

    /** Same subject order as the printed LGS form, which differs from the order of the answer-key table. */
    private val subjects = listOf(
        OcrTableSubject("tr", "Türkçe", 20, abcd),
        OcrTableSubject("ink", "T.C. İnkılap Tarihi", 10, abcd),
        OcrTableSubject("din", "Din Kültürü ve Ahlak Bilgisi", 10, abcd),
        OcrTableSubject("eng", "İngilizce", 10, abcd),
        OcrTableSubject("mat", "Matematik", 20, abcd),
        OcrTableSubject("fen", "Fen Bilimleri", 20, abcd)
    )
    private val booklets = listOf("A", "B", "C", "D")

    // Ground truth read by eye from the published 2023 LGS answer key (tables A and B).
    private val truthA = mapOf(
        "tr" to "ACDCBCBADCBBDABBADDC",
        "ink" to "CDCBADCDAB",
        "din" to "BCDCADBADC",
        "eng" to "ADCBADBCAC",
        "mat" to "CABDBCBABCBDCADBADCA",
        "fen" to "ADBCBCADABBCDBACADCD"
    )
    private val truthB = mapOf(
        "tr" to "BCCDCADBCBDABBCDDBAA",
        "ink" to "BADABCDCDC",
        "din" to "DBACBCADCD",
        "eng" to "ABACDCCABD",
        "mat" to "DBACCBABDBCBDCBADAAC",
        "fen" to "BDABCDACCABDCBBACDAD"
    )

    // ---------------------------------------------------------------------------------------------
    // Real OCR output of the published answer-key image
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `published LGS key image is read as four booklets`() {
        val result = parse(loadFixture("lgs2023_answer_key_1x.tsv"))

        assertEquals(listOf("A", "B", "C", "D"), result.tables.map { it.booklet })
        val a = result.tables[0]
        val b = result.tables[1]
        assertEquals(truthA, answersByTableId(a))
        assertEquals(truthB, answersByTableId(b))
        assertTrue(result.tables.all { it.totalQuestions == 90 })
        // The only doubtful cells are the ones OCR read as "7-8" / "5-8" / "1-8" and that were repaired to B.
        assertEquals(listOf("tr 7"), reviewCells(a))
        assertEquals(listOf("din 5", "fen 1"), reviewCells(b))
        assertTrue(result.tables[2].readCount >= 85)
        assertTrue(result.tables[3].readCount >= 85)
    }

    @Test
    fun `a subject whose header OCR cannot read is matched by elimination and flagged`() {
        val result = parse(loadFixture("lgs2023_answer_key_1x.tsv"))
        val c = result.tables[2]
        val din = c.subjects.first { it.subject.id == "din" }

        assertFalse(din.headerFound)
        assertEquals("ACBDCBDCAD", din.cells.joinToString("") { it.choice.orEmpty() })
        assertTrue(din.cells.all { it.status == OcrCellStatus.SUSPICIOUS })
        assertTrue(result.warnings.any { it.contains("Din Kültürü") && it.contains("kontrol edin") })
    }

    @Test
    fun `noisier second OCR pass never reports a wrong answer as clean`() {
        val result = parse(loadFixture("lgs2023_answer_key_2x.tsv"))

        assertEquals(listOf("A", "B", "C", "D"), result.tables.map { it.booklet })
        listOf(result.tables[0] to truthA, result.tables[1] to truthB).forEach { (table, truth) ->
            table.subjects.forEach { subject ->
                subject.cells.filter { it.status == OcrCellStatus.OK }.forEach { cell ->
                    assertEquals(
                        "${table.booklet} ${subject.subject.id} ${cell.number}",
                        truth.getValue(subject.subject.id)[cell.number - 1].toString(),
                        cell.choice
                    )
                }
            }
            assertTrue(table.missingCount > 0)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Synthetic tables
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `clean two by two page is read exactly`() {
        val tokens = page(
            Layout("A", 0, 0, truthA),
            Layout("B", 760, 0, truthB),
            Layout("A", 0, 450, truthB) // same letter again: the second one must not claim 'A'
        )
        val result = parse(recognition(tokens, 1500, 900))

        assertEquals(3, result.tables.size)
        assertEquals(truthA, answersByTableId(result.tables[0]))
        assertEquals(truthB, answersByTableId(result.tables[1]))
        assertTrue(result.tables[0].reviewCount == 0 && result.tables[1].reviewCount == 0)
        assertEquals(listOf("A", "B", null), result.tables.map { it.booklet })
        assertTrue(result.warnings.any { it.contains("kitapçık türlerini önizlemede kontrol edin") })
    }

    @Test
    fun `missing cell is reported as missing and neighbours stay clean`() {
        val tokens = page(Layout("A", 0, 0, truthA, drop = setOf("fen" to 5)))
        val table = parse(recognition(tokens, 800, 450)).tables.single()

        val fen = table.subjects.first { it.subject.id == "fen" }
        assertEquals(OcrCellStatus.MISSING, fen.cells[4].status)
        assertEquals(null, fen.cells[4].choice)
        assertEquals(19, fen.readCount)
        assertEquals(1, table.missingCount)
        assertEquals(1, table.reviewCount)
    }

    @Test
    fun `misread question number is repaired from its place in the sequence`() {
        // "3-C" printed in Türkçe row 3 was read as "8-C": number 3 is the only gap between 2 and 4.
        val tokens = page(Layout("A", 0, 0, truthA, rewrite = mapOf(("tr" to 3) to "8-D")))
        val table = parse(recognition(tokens, 800, 450)).tables.single()

        val tr = table.subjects.first { it.subject.id == "tr" }
        assertEquals("D", tr.cells[2].choice)
        assertEquals(OcrCellStatus.SUSPICIOUS, tr.cells[2].status)
        assertTrue(tr.cells[2].note.orEmpty().contains("numara 8 okundu"))
        assertEquals(truthA.getValue("tr"), tr.cells.joinToString("") { it.choice.orEmpty() })
    }

    @Test
    fun `same number read twice with different answers is a conflict`() {
        val tokens = page(Layout("A", 0, 0, truthA)) +
            token("5-A", 22, 340, 0.9f) // a second "5-A" below the first Türkçe column (truth is 5-B)
        val table = parse(recognition(tokens, 800, 450)).tables.single()

        val cell = table.subjects.first { it.subject.id == "tr" }.cells[4]
        assertEquals(OcrCellStatus.CONFLICT, cell.status)
        assertTrue(cell.note.orEmpty().contains("iki farklı okuma"))
    }

    @Test
    fun `smudged letters and low confidence are flagged for review`() {
        val tokens = page(
            Layout(
                "A", 0, 0, truthA,
                rewrite = mapOf(("ink" to 2) to "2-0", ("ink" to 4) to "4-8"),
                confidence = mapOf(("mat" to 4) to 0.3f)
            )
        )
        val table = parse(recognition(tokens, 800, 450)).tables.single()

        // "2-0" is a smudged D and "4-8" a smudged B: the value is recovered but stays flagged.
        val ink = table.subjects.first { it.subject.id == "ink" }
        assertEquals("D", ink.cells[1].choice)
        assertEquals("B", ink.cells[3].choice)
        assertEquals(OcrCellStatus.SUSPICIOUS, ink.cells[1].status)
        assertEquals(OcrCellStatus.SUSPICIOUS, ink.cells[3].status)
        val mat4 = table.subjects.first { it.subject.id == "mat" }.cells[3]
        assertEquals("D", mat4.choice)
        assertEquals(OcrCellStatus.SUSPICIOUS, mat4.status)
        assertTrue(mat4.note.orEmpty().contains("düşük güven"))
    }

    @Test
    fun `several cells inside one OCR line are separated`() {
        val tokens = page(Layout("A", 0, 0, truthA)).toMutableList()
        // Replace the first two Türkçe cells of row 1 by a single merged token.
        val first = tokens.first { it.text == "1-A" }
        val second = tokens.first { it.text == "11-B" }
        tokens.remove(first)
        tokens.remove(second)
        tokens += OcrToken("1-A 11-B", first.left, first.top, second.right, second.bottom, 0.9f)
        val table = parse(recognition(tokens, 800, 450)).tables.single()

        assertEquals(truthA, answersByTableId(table))
        assertEquals(0, table.reviewCount)
    }

    @Test
    fun `number and letter split into two tokens are joined`() {
        val tokens = page(Layout("A", 0, 0, truthA)).toMutableList()
        val cell = tokens.first { it.text == "7-B" } // Türkçe 7
        tokens.remove(cell)
        val mid = (cell.left + cell.right) / 2
        tokens += OcrToken("7", cell.left, cell.top, mid - 2, cell.bottom, 0.9f)
        tokens += OcrToken("B", mid + 2, cell.top, cell.right, cell.bottom, 0.9f)
        val table = parse(recognition(tokens, 800, 450)).tables.single()

        assertEquals(truthA, answersByTableId(table))
    }

    @Test
    fun `table with a row-major layout is still read`() {
        val tokens = page(Layout("A", 0, 0, truthA, rowMajor = true))
        val table = parse(recognition(tokens, 800, 450)).tables.single()

        assertEquals(truthA, answersByTableId(table))
        assertEquals(0, table.reviewCount)
    }

    @Test
    fun `answer outside the subject's choices is not accepted`() {
        val subjectsWithoutD = subjects.map { if (it.id == "ink") it.copy(allowedChoices = setOf("A", "B", "C")) else it }
        val tokens = page(Layout("A", 0, 0, truthA))
        val table = OcrAnswerTableParser.parse(recognition(tokens, 800, 450), subjectsWithoutD, booklets)!!.tables.single()

        val ink = table.subjects.first { it.subject.id == "ink" }
        // truth "CDCBADCDAB": the three D answers (2, 6, 8) are not valid for this subject.
        assertEquals(listOf(2, 6, 8), ink.cells.filter { it.status == OcrCellStatus.MISSING }.map { it.number })
        assertTrue(ink.cells.none { it.choice == "D" })
    }

    @Test
    fun `plain page without answer cells is not a table`() {
        val tokens = listOf(
            token("Türkçe", 50, 80, 0.9f), token("1", 40, 140, 0.9f), token("A", 70, 140, 0.9f),
            token("2", 40, 180, 0.9f), token("B", 70, 180, 0.9f), token("3", 40, 220, 0.9f), token("C", 70, 220, 0.9f)
        )

        assertNull(OcrAnswerTableParser.parse(recognition(tokens, 600, 400), subjects, booklets))
    }

    @Test
    fun `cells without any readable subject header are not guessed`() {
        val tokens = page(Layout("A", 0, 0, truthA)).filterNot { token ->
            subjects.any { token.text.equals(headerText(it.id), ignoreCase = true) }
        }

        assertNull(OcrAnswerTableParser.parse(recognition(tokens, 800, 450), subjects, booklets))
    }

    @Test
    fun `single table without a title falls back to the booklet in the page text`() {
        val tokens = page(Layout(null, 0, 0, truthB))
        val result = OcrAnswerTableParser.parse(
            OcrRecognitionResult("CEVAP ANAHTARI B GRUBU", tokens, 800, 450),
            subjects,
            booklets
        )!!

        assertEquals("B", result.tables.single().booklet)
        assertNotNull(result.tables.single().subjects.first())
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private fun parse(recognition: OcrRecognitionResult): OcrAnswerTableResult =
        OcrAnswerTableParser.parse(recognition, subjects, booklets)!!

    private fun recognition(tokens: List<OcrToken>, width: Int, height: Int) =
        OcrRecognitionResult(tokens.joinToString("\n") { it.text }, tokens, width, height)

    private fun answersByTableId(table: OcrAnswerTable): Map<String, String> =
        table.subjects.associate { subject -> subject.subject.id to subject.cells.joinToString("") { it.choice ?: "-" } }

    private fun reviewCells(table: OcrAnswerTable): List<String> = table.subjects.flatMap { subject ->
        subject.cells.filter { it.needsReview }.map { "${subject.subject.id} ${it.number}" }
    }

    private fun loadFixture(name: String): OcrRecognitionResult {
        val lines = checkNotNull(javaClass.getResourceAsStream("/ocr/$name")) { "fixture $name missing" }
            .bufferedReader(Charsets.UTF_8).readLines()
        val (width, height) = lines.first().split('\t').map { it.toInt() }
        val tokens = lines.drop(1).filter { it.isNotBlank() }.map { line ->
            val p = line.split('\t')
            OcrToken(p[0], p[1].toInt(), p[2].toInt(), p[3].toInt(), p[4].toInt(), p[5].toFloat())
        }
        return recognition(tokens, width, height)
    }

    private fun token(text: String, left: Int, top: Int, confidence: Float, width: Int = 36, height: Int = 18) =
        OcrToken(text, left, top, left + width, top + height, confidence)

    private fun headerText(id: String) = when (id) {
        "tr" -> "Türkçe"
        "ink" -> "T.C. İnkılap"
        "din" -> "Din Kültürü"
        "eng" -> "Yabancı Dil"
        "mat" -> "Matematik"
        else -> "Fen Bilimleri"
    }

    /**
     * One printed table. Subjects run left to right in the order of the published table (Türkçe, Matematik, Fen,
     * İnkılap, Din, Yabancı Dil); every subject owns two 60 px columns of 10 rows, the second one blank for the
     * 10-question subjects, exactly like the real answer keys.
     */
    private data class Layout(
        val booklet: String?,
        val x: Int,
        val y: Int,
        val answers: Map<String, String>,
        val drop: Set<Pair<String, Int>> = emptySet(),
        val rewrite: Map<Pair<String, Int>, String> = emptyMap(),
        val confidence: Map<Pair<String, Int>, Float> = emptyMap(),
        val rowMajor: Boolean = false
    )

    private val tableOrder = listOf("tr", "mat", "fen", "ink", "din", "eng")

    private fun page(vararg layouts: Layout): List<OcrToken> = layouts.flatMap { layout ->
        buildList {
            val titleY = layout.y + 20
            val headerY = layout.y + 60
            if (layout.booklet != null) add(token("2023 LGS (${layout.booklet} Grubu)", layout.x + 180, titleY, 0.9f, 220, 20))
            tableOrder.forEachIndexed { subjectIndex, id ->
                val left = layout.x + 10 + subjectIndex * 120
                add(token(headerText(id), left + 10, headerY, 0.85f, 100, 20))
                val answers = layout.answers.getValue(id)
                answers.forEachIndexed { index, choice ->
                    val number = index + 1
                    if ((id to number) in layout.drop) return@forEachIndexed
                    val column: Int
                    val row: Int
                    if (layout.rowMajor) {
                        column = index % 2
                        row = index / 2
                    } else {
                        column = index / 10
                        row = index % 10
                    }
                    val text = layout.rewrite[id to number] ?: "$number-$choice"
                    val confidence = layout.confidence[id to number] ?: 0.9f
                    add(token(text, left + column * 60 + 12, layout.y + 100 + row * 24, confidence, 36, 18))
                }
            }
        }
    }
}
