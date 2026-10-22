package com.okulyonetim.optikokuyucu.ocr

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** One subject column group of a printed answer-key table, e.g. "Türkçe" with 20 questions. */
data class OcrTableSubject(
    val id: String,
    val label: String,
    val questionCount: Int,
    val allowedChoices: Set<String>
) {
    init {
        require(id.isNotBlank())
        require(questionCount > 0)
        require(allowedChoices.isNotEmpty())
    }
}

enum class OcrCellStatus {
    /** Read cleanly. */
    OK,

    /** Read, but low confidence, repaired from a look-alike character or out of sequence. */
    SUSPICIOUS,

    /** The same question number was read twice with different answers. */
    CONFLICT,

    /** Nothing usable was read for this question. */
    MISSING
}

data class OcrTableCell(
    val number: Int,
    val choice: String?,
    val status: OcrCellStatus,
    val note: String? = null
) {
    val needsReview: Boolean get() = status != OcrCellStatus.OK
}

data class OcrTableSubjectResult(
    val subject: OcrTableSubject,
    val headerFound: Boolean,
    /** Always exactly [OcrTableSubject.questionCount] cells, numbered 1..N. */
    val cells: List<OcrTableCell>
) {
    val readCount: Int get() = cells.count { it.choice != null }
    val reviewCount: Int get() = cells.count { it.needsReview }
}

data class OcrAnswerTable(
    /** Booklet letter taken from the table title ("A Grubu"), or null when it could not be read. */
    val booklet: String?,
    val subjects: List<OcrTableSubjectResult>
) {
    val totalQuestions: Int get() = subjects.sumOf { it.cells.size }
    val readCount: Int get() = subjects.sumOf { it.readCount }
    val missingCount: Int get() = totalQuestions - readCount
    val reviewCount: Int get() = subjects.sumOf { it.reviewCount }
}

data class OcrAnswerTableResult(
    /** Tables in reading order: top-to-bottom, then left-to-right. */
    val tables: List<OcrAnswerTable>,
    val warnings: List<String>
)

/**
 * Reads printed answer-key tables where every cell carries its own question number ("12-B"), such as the
 * published LGS/TYT answer keys: several booklets (A/B/C/D Grubu) laid out in a grid, each with one column
 * group per subject.
 *
 * Because the number is inside the cell, no row model is needed: a cell is identified by (table, subject,
 * number). Geometry is only used to decide which table and which subject a cell belongs to, and to flag
 * cells that look wrong (missing, duplicated, out of sequence, low confidence).
 *
 * Two printed layouts are understood and tried against each other, the one that explains more cells wins:
 *  - [Orientation.COLUMNS]: subject names on top, one column group per subject (published LGS key, MEB key);
 *  - [Orientation.ROWS]: subject names at the left, the answers of one subject running along a row (1-10, then
 *    11-20 on the next line), several booklets stacked below each other.
 *
 * Returns null when the page does not look like such a table or the reading explains too few of the cells, so the
 * caller can fall back to [OcrAnswerKeyParser].
 */
object OcrAnswerTableParser {
    fun parse(
        recognition: OcrRecognitionResult,
        subjects: List<OcrTableSubject>,
        knownBooklets: List<String> = emptyList()
    ): OcrAnswerTableResult? {
        if (subjects.isEmpty()) return null
        val tokens = recognition.tokens.filter { it.text.isNotBlank() }
        val allowed = subjects.flatMap { it.allowedChoices }.map { it.uppercase(Locale.ROOT) }.toSet()
        val extracted = extractCells(tokens, allowed)
        val cells = extracted.cells
        val expectedTotal = subjects.sumOf { it.questionCount }
        if (cells.size < max(MIN_TABLE_CELLS, expectedTotal * 3 / 10)) return null

        val textTokens = tokens.filterIndexed { index, _ -> index !in extracted.consumedTokens }
        val page = Page(
            subjects = subjects,
            knownBooklets = knownBooklets,
            cells = cells,
            medianCellWidth = OcrAnswerKeyParser.median(cells.map { it.width }).coerceAtLeast(6f),
            medianCellHeight = OcrAnswerKeyParser.median(cells.map { it.height }).coerceAtLeast(6f),
            width = recognition.imageWidth.toFloat(),
            height = recognition.imageHeight.toFloat(),
            text = recognition.text,
            titles = findTitles(textTokens, knownBooklets)
        )
        val hits = findHeaderHits(textTokens, subjects)
        if (hits.isEmpty()) return null

        // The page is either laid out with headers on top or with row labels at the left: read it both ways and
        // keep the reading that explains more cells. Ties go to the column layout.
        val best = Orientation.entries.mapNotNull { readTables(it, hits, page) }.maxByOrNull { it.placed } ?: return null
        if (best.placed < cells.size * MIN_PLACED_SHARE) return null
        return OcrAnswerTableResult(best.tables, best.warnings.distinct())
    }

    private enum class Orientation { COLUMNS, ROWS }

    private class Page(
        val subjects: List<OcrTableSubject>,
        val knownBooklets: List<String>,
        val cells: List<Cell>,
        val medianCellWidth: Float,
        val medianCellHeight: Float,
        val width: Float,
        val height: Float,
        val text: String,
        val titles: List<TitleHit>
    )

    private class Reading(val tables: List<OcrAnswerTable>, val warnings: List<String>, val placed: Int)

    private fun readTables(orientation: Orientation, allHits: List<HeaderHit>, page: Page): Reading? {
        val hits = supportedHits(orientation, allHits, page)
        if (hits.isEmpty()) return null
        val headerHeight = OcrAnswerKeyParser.median(hits.map { it.token.height.toFloat() }).coerceAtLeast(8f)
        val groups = when (orientation) {
            Orientation.COLUMNS -> buildColumnGroups(hits, headerHeight, page)
            Orientation.ROWS -> buildRowGroups(hits, page)
        }

        val warnings = mutableListOf<String>()
        val claimedCells = mutableSetOf<Int>()
        val drafts = mutableListOf<TableDraft>()
        groups.forEach { group ->
            val inside = page.cells.withIndex().filter { (_, cell) -> group.contains(cell.centerX, cell.centerY) }
            if (inside.size < MIN_CELLS_PER_TABLE) return@forEach
            inside.forEach { claimedCells += it.index }
            drafts += TableDraft(group, pickTitle(group, page.titles, headerHeight, page.height), inside.map { it.value })
        }
        if (drafts.isEmpty()) return null

        val tables = mutableListOf<OcrAnswerTable>()
        val usedBooklets = mutableSetOf<String>()
        drafts.forEachIndexed { index, draft ->
            var booklet = draft.booklet
            if (booklet == null && drafts.size == 1) {
                booklet = OcrAnswerKeyParser.detectBooklet(page.text, page.knownBooklets)
            }
            if (booklet != null && !usedBooklets.add(booklet)) {
                warnings += "Birden fazla tablo '$booklet' olarak okundu; kitapçık türlerini önizlemede kontrol edin."
                booklet = null
            }
            val label = booklet?.let { "$it Grubu" } ?: "Tablo ${index + 1}"
            val table = buildTable(draft, booklet, orientation, page, label, warnings)
            tables += table
            if (table.missingCount > 0) warnings += "$label: ${table.missingCount} cevap okunamadı."
            val flagged = table.reviewCount - table.missingCount
            if (flagged > 0) warnings += "$label: $flagged cevap kontrol gerektiriyor."
        }

        val stray = page.cells.size - claimedCells.size
        if (stray > STRAY_CELL_WARNING) warnings += "Tablo dışında $stray hücre okundu; yoksayıldı."
        if (page.knownBooklets.isNotEmpty() && tables.any { it.booklet == null }) {
            warnings += "Kitapçık türü belirlenemeyen tablo var. Kaydetmeden önce seçin."
        }
        return Reading(tables, warnings, tables.sumOf { it.readCount })
    }

    // ---------------------------------------------------------------------------------------------
    // Cells
    // ---------------------------------------------------------------------------------------------

    private data class Cell(
        val number: Int,
        val choice: String,
        val centerX: Float,
        val centerY: Float,
        val width: Float,
        val height: Float,
        val confidence: Float,
        val lookalike: Boolean
    )

    private data class Extracted(val cells: List<Cell>, val consumedTokens: Set<Int>)

    private fun extractCells(tokens: List<OcrToken>, allowed: Set<String>): Extracted {
        val cells = mutableListOf<Cell>()
        val consumed = mutableSetOf<Int>()
        tokens.forEachIndexed { index, token ->
            val text = token.text.replace(DASHES, "-")
            var any = false
            CELL.findAll(text).forEach { match ->
                val parsed = parseCell(match.groupValues[1], match.groupValues[2].isNotEmpty(), match.groupValues[3], allowed)
                    ?: return@forEach
                // A token may hold several cells ("1-A 11-B"); position each one by its share of the text.
                val start = match.range.first.toFloat() / text.length
                val end = (match.range.last + 1).toFloat() / text.length
                val left = token.left + token.width * start
                val right = token.left + token.width * end
                cells += Cell(
                    number = parsed.number,
                    choice = parsed.choice,
                    centerX = (left + right) / 2f,
                    centerY = token.centerY,
                    width = (right - left).coerceAtLeast(1f),
                    height = token.height.toFloat(),
                    confidence = (token.confidence ?: DEFAULT_CONFIDENCE).coerceIn(0f, 1f),
                    lookalike = parsed.lookalike
                )
                any = true
            }
            if (any) consumed += index
        }
        pairSplitCells(tokens, consumed, allowed, cells)
        return Extracted(cells, consumed)
    }

    private data class ParsedCell(val number: Int, val choice: String, val lookalike: Boolean)

    private fun parseCell(numberRaw: String, hasSeparator: Boolean, letterRaw: String, allowed: Set<String>): ParsedCell? {
        var lookalike = false
        val numberText = if (numberRaw.all { it.isDigit() }) {
            numberRaw
        } else {
            // "l-A" / "I-A" / "|-A": only trusted next to an explicit dash.
            if (!hasSeparator) return null
            lookalike = true
            numberRaw.map { if (it.isDigit()) it else '1' }.joinToString("")
        }
        val number = numberText.toIntOrNull()?.takeIf { it in 1..MAX_QUESTION_NUMBER } ?: return null
        val first = letterRaw.firstOrNull() ?: return null
        val choice = if (first.isLetter()) {
            first.toString().uppercase(Locale.ROOT)
        } else {
            // "5-8" is a "5-B" with a smudged letter; "10" without a dash is just a number.
            if (!hasSeparator) return null
            lookalike = true
            DIGIT_LOOKALIKES[first] ?: return null
        }
        if (choice !in allowed) return null
        return ParsedCell(number, choice, lookalike)
    }

    /** Joins a bare number token and a bare letter token that OCR split apart ("12" + "B"). */
    private fun pairSplitCells(tokens: List<OcrToken>, consumed: MutableSet<Int>, allowed: Set<String>, out: MutableList<Cell>) {
        val numberTokens = tokens.withIndex().filter { (i, t) -> i !in consumed && BARE_NUMBER.matches(t.text) }
        val letterTokens = tokens.withIndex().filter { (i, t) -> i !in consumed && BARE_LETTER.matches(t.text) }
        if (numberTokens.isEmpty() || letterTokens.isEmpty()) return
        val usedLetters = mutableSetOf<Int>()
        numberTokens.forEach { (numberIndex, number) ->
            val best = letterTokens.asSequence()
                .filter { it.index !in usedLetters }
                .filter { (_, letter) ->
                    val lineTolerance = max(number.height, letter.height) * 0.6f
                    abs(letter.centerY - number.centerY) <= lineTolerance &&
                        letter.left >= number.right - number.width * 0.3f &&
                        letter.left - number.right <= max(number.height, letter.height) * 1.5f
                }
                .minByOrNull { it.value.left - number.right }
                ?: return@forEach
            val letterText = best.value.text.trim().trimStart('-', '.', ':', ',', ';').trim()
            val numberText = number.text.trim().trimEnd('-', '.', ':', ',', ';').trim()
            val parsed = parseCell(numberText, true, letterText, allowed) ?: return@forEach
            val left = min(number.left, best.value.left).toFloat()
            val right = max(number.right, best.value.right).toFloat()
            val confidence = listOfNotNull(number.confidence, best.value.confidence).takeIf { it.isNotEmpty() }
                ?.minOrNull() ?: DEFAULT_CONFIDENCE
            out += Cell(
                number = parsed.number,
                choice = parsed.choice,
                centerX = (left + right) / 2f,
                centerY = (number.centerY + best.value.centerY) / 2f,
                width = right - left,
                height = max(number.height, best.value.height).toFloat(),
                confidence = confidence.coerceIn(0f, 1f),
                lookalike = parsed.lookalike
            )
            consumed += numberIndex
            consumed += best.index
            usedLetters += best.index
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Headers (subject names), groups (one per table) and titles
    // ---------------------------------------------------------------------------------------------

    private data class HeaderHit(val subjectIndex: Int, val token: OcrToken, val score: Float) {
        val centerX: Float get() = token.centerX
        val centerY: Float get() = token.centerY
    }

    private fun findHeaderHits(textTokens: List<OcrToken>, subjects: List<OcrTableSubject>): List<HeaderHit> {
        val candidates = OcrAnswerKeyParser.phraseCandidates(textTokens)
        val scored = mutableListOf<HeaderHit>()
        subjects.forEachIndexed { index, subject ->
            val aliases = OcrAnswerKeyParser.subjectAliases(subject.label)
            candidates.forEach { candidate ->
                val normalized = OcrAnswerKeyParser.normalize(candidate.text)
                if (normalized.length < 2) return@forEach
                val lexical = aliases.maxOf { alias ->
                    when {
                        // Very short aliases ("DIN", "MAT") must match exactly: "DIL" is not "DIN".
                        alias.length <= 3 && alias != normalized -> 0f
                        else -> max(
                            OcrAnswerKeyParser.similarity(normalized, alias),
                            firstWordScore(normalized, alias)
                        )
                    }
                }
                val confidence = candidate.confidence ?: DEFAULT_CONFIDENCE
                val score = lexical * (0.82f + confidence.coerceIn(0f, 1f) * 0.18f)
                if (score >= MIN_HEADER_SCORE) scored += HeaderHit(index, candidate, score)
            }
        }
        // Greedy non-maximum suppression: the best reading of a piece of text wins ("Fen Bilimleri" over "Fen").
        val accepted = mutableListOf<HeaderHit>()
        scored.sortedByDescending { it.score }.forEach { hit ->
            if (accepted.none { overlaps(it.token, hit.token) }) accepted += hit
        }
        // A header printed on two lines ("T.C. İnkılap Tarihi" / "ve Atatürkçülük") can match twice: keep the better.
        return accepted.filter { hit ->
            accepted.none { other ->
                other !== hit && other.subjectIndex == hit.subjectIndex &&
                    (other.score > hit.score || (other.score == hit.score && other.token.top < hit.token.top)) &&
                    stacked(other.token, hit.token)
            }
        }
    }

    private fun stacked(a: OcrToken, b: OcrToken): Boolean =
        abs(a.centerY - b.centerY) <= max(a.height, b.height) * 2.5f && a.left < b.right && b.left < a.right

    /** Keeps the headers that have enough answer cells where this layout expects them. */
    private fun supportedHits(orientation: Orientation, hits: List<HeaderHit>, page: Page): List<HeaderHit> =
        hits.filter { hit ->
            val supported = when (orientation) {
                Orientation.COLUMNS -> {
                    val reach = page.medianCellWidth * COLUMN_REACH_CELL_WIDTHS
                    page.cells.count { it.centerY > hit.token.bottom && abs(it.centerX - hit.centerX) <= reach }
                }
                Orientation.ROWS -> {
                    val reach = page.medianCellHeight * ROW_REACH_CELL_HEIGHTS
                    page.cells.count { it.centerX > hit.token.right && abs(it.centerY - hit.centerY) <= reach }
                }
            }
            supported >= MIN_CELLS_PER_TABLE
        }

    /** "Din Kotoro" is still the Din Kültürü header: a multi-word header whose first word is read exactly. */
    private fun firstWordScore(candidate: String, alias: String): Float {
        val candidateWords = candidate.split(' ').filter(String::isNotBlank)
        val aliasWords = alias.split(' ').filter(String::isNotBlank)
        if (candidateWords.size < 2 || aliasWords.size < 2) return 0f
        return if (candidateWords.first().length >= 3 && candidateWords.first() == aliasWords.first()) FIRST_WORD_SCORE else 0f
    }

    private fun overlaps(a: OcrToken, b: OcrToken): Boolean {
        val intersects = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom
        val centerInside = { x: OcrToken, y: OcrToken ->
            y.centerX in x.left.toFloat()..x.right.toFloat() && y.centerY in x.top.toFloat()..x.bottom.toFloat()
        }
        return intersects || centerInside(a, b) || centerInside(b, a)
    }

    /** One printed table: its subject headers plus the page area that holds its answer cells. */
    private data class Group(
        val headers: List<HeaderHit>,
        val left: Float,
        val right: Float,
        val top: Float,
        val bottom: Float,
        /** Top edge of the first header: where to look upwards for the booklet title. */
        val headerTop: Float,
        val titleLeft: Float,
        val titleRight: Float
    ) {
        fun contains(x: Float, y: Float): Boolean = x >= left && x < right && y > top && y < bottom
    }

    private data class TableDraft(val group: Group, val booklet: String?, val cells: List<Cell>)

    /** Headers on top: header rows give tables printed side by side, further rows give tables below. */
    private fun buildColumnGroups(headers: List<HeaderHit>, headerHeight: Float, page: Page): List<Group> {
        // 1. Header rows: headers that share a baseline belong to tables printed side by side.
        val tolerance = max(headerHeight * 0.8f, 6f)
        val rows = mutableListOf<MutableList<HeaderHit>>()
        headers.sortedBy { it.centerY }.forEach { hit ->
            val row = rows.firstOrNull { abs(it.map { h -> h.centerY }.average().toFloat() - hit.centerY) <= tolerance }
            if (row != null) row += hit else rows += mutableListOf(hit)
        }
        rows.forEach { row -> row.sortBy { it.centerX } }
        rows.sortBy { row -> row.minOf { it.token.top } }

        val result = mutableListOf<Group>()
        rows.forEachIndexed { rowIndex, row ->
            // 2. A table never has the same subject twice, so a repeat starts the next table of the row.
            val parts = splitOnRepeatedSubject(row)
            val headerTop = row.minOf { it.token.top }.toFloat()
            val headerBottom = row.maxOf { it.token.bottom }.toFloat()
            val nextTop = rows.getOrNull(rowIndex + 1)?.minOf { it.token.top }?.toFloat()
            val bottom = nextTop?.let { it - headerHeight * 0.2f }?.coerceAtLeast(headerBottom + headerHeight) ?: page.height
            parts.forEachIndexed { partIndex, part ->
                val previous = parts.getOrNull(partIndex - 1)
                val next = parts.getOrNull(partIndex + 1)
                val left = previous?.let { (it.last().centerX + part.first().centerX) / 2f } ?: 0f
                val right = next?.let { (part.last().centerX + it.first().centerX) / 2f } ?: page.width
                result += Group(part, left, right, headerBottom, bottom, headerTop, left, right)
            }
        }
        return result
    }

    /** Subject names at the left: a list of labels top to bottom; a repeated subject starts the next table below. */
    private fun buildRowGroups(headers: List<HeaderHit>, page: Page): List<Group> {
        val parts = splitOnRepeatedSubject(headers.sortedBy { it.centerY })
        return parts.mapIndexed { index, part ->
            val previous = parts.getOrNull(index - 1)
            val next = parts.getOrNull(index + 1)
            val top = previous?.let { (it.last().centerY + part.first().centerY) / 2f } ?: 0f
            val bottom = next?.let { (part.last().centerY + it.first().centerY) / 2f } ?: page.height
            val left = part.maxOf { it.token.right }.toFloat() - 2f
            Group(part, left, page.width, top, bottom, part.minOf { it.token.top }.toFloat(), 0f, page.width)
        }
    }

    private fun splitOnRepeatedSubject(ordered: List<HeaderHit>): List<List<HeaderHit>> {
        val parts = mutableListOf<MutableList<HeaderHit>>()
        ordered.forEach { hit ->
            val current = parts.lastOrNull()
            if (current == null || current.any { it.subjectIndex == hit.subjectIndex }) parts += mutableListOf(hit) else current += hit
        }
        return parts
    }

    private data class TitleHit(val booklet: String, val centerX: Float, val centerY: Float)

    /** Booklet markers: "A Grubu" / "A Kitapçığı" in a title, or a single large letter printed next to the title. */
    private fun findTitles(textTokens: List<OcrToken>, knownBooklets: List<String>): List<TitleHit> {
        val known = knownBooklets.associateBy { OcrAnswerKeyParser.normalize(it) }
        fun resolve(letter: String): String? =
            if (known.isNotEmpty()) known[letter] else letter.takeIf { it.length == 1 && it[0] in 'A'..'H' }

        val hits = mutableListOf<TitleHit>()
        OcrAnswerKeyParser.phraseCandidates(textTokens).forEach { candidate ->
            val normalized = OcrAnswerKeyParser.normalize(candidate.text)
            val letter = TITLE_AFTER.find(normalized)?.groupValues?.get(1)
                ?: TITLE_BEFORE.find(normalized)?.groupValues?.get(1)
                ?: return@forEach
            val booklet = resolve(letter) ?: return@forEach
            hits += TitleHit(booklet, candidate.centerX, candidate.centerY)
        }
        val textHeight = OcrAnswerKeyParser.median(textTokens.map { it.height.toFloat() })
        textTokens.forEach { token ->
            if (token.height < textHeight * BIG_LETTER_RATIO) return@forEach
            val booklet = resolve(OcrAnswerKeyParser.normalize(token.text)) ?: return@forEach
            hits += TitleHit(booklet, token.centerX, token.centerY)
        }
        return hits
    }

    private fun pickTitle(group: Group, titles: List<TitleHit>, headerHeight: Float, pageHeight: Float): String? {
        val maxDistance = max(headerHeight * 9f, pageHeight * 0.25f)
        val aboveBand = group.headerTop - maxDistance
        return titles.asSequence()
            .filter { it.centerY < group.headerTop && it.centerY >= aboveBand }
            .filter { it.centerX >= group.titleLeft && it.centerX < group.titleRight }
            .maxByOrNull { it.centerY }
            ?.booklet
    }

    // ---------------------------------------------------------------------------------------------
    // One table: clusters -> subject blocks -> numbered cells
    // ---------------------------------------------------------------------------------------------

    private fun buildTable(
        draft: TableDraft,
        booklet: String?,
        orientation: Orientation,
        page: Page,
        label: String,
        warnings: MutableList<String>
    ): OcrAnswerTable {
        val columns = orientation == Orientation.COLUMNS
        fun axis(x: Float, y: Float) = if (columns) x else y
        val headers = draft.group.headers.sortedBy { axis(it.centerX, it.centerY) }
        val clusters = if (columns) {
            clusterAxis(draft.cells, max(page.medianCellWidth * COLUMN_GAP_CELL_WIDTHS, 6f)) { it.centerX }
        } else {
            clusterAxis(draft.cells, max(page.medianCellHeight * LINE_GAP_CELL_HEIGHTS, 3f)) { it.centerY }
        }
        // Question numbers restart at every subject, so they split the clusters into subject blocks without any
        // help from the header text; blocks and headers then line up in reading order.
        val blocks = segmentBlocks(clusters)
        val blockPositions = blocks.map { block ->
            block.flatMap { clusters[it] }.map { axis(it.centerX, it.centerY) }.average().toFloat()
        }
        val skip = if (columns) max(page.medianCellWidth * SKIP_CELL_WIDTHS, 40f) else max(page.medianCellHeight * SKIP_CELL_HEIGHTS, 40f)
        val aligned = alignMonotone(blockPositions, headers.map { axis(it.centerX, it.centerY) }, skip)

        val subjectOfBlock = IntArray(blocks.size) { index -> aligned[index].takeIf { it >= 0 }?.let { headers[it].subjectIndex } ?: -1 }
        val foundSubjects = headers.map { it.subjectIndex }.toSet()
        val missingSubjects = page.subjects.indices.filter { it !in foundSubjects }
        val headerless = blocks.indices.filter { subjectOfBlock[it] < 0 }
        val inferred = mutableSetOf<Int>()
        if (headerless.size == 1 && missingSubjects.size == 1) {
            // One unexplained block and one subject without a readable header: they belong together.
            subjectOfBlock[headerless.first()] = missingSubjects.first()
            inferred += missingSubjects.first()
        } else {
            headerless.forEach { blockIndex ->
                val nearest = blocks.indices.filter { subjectOfBlock[it] >= 0 }
                    .minByOrNull { abs(blockPositions[it] - blockPositions[blockIndex]) }
                if (nearest != null) subjectOfBlock[blockIndex] = subjectOfBlock[nearest]
            }
        }

        val bySubject = mutableMapOf<Int, MutableList<ColumnCell>>()
        blocks.forEachIndexed { blockIndex, block ->
            val subjectIndex = subjectOfBlock[blockIndex]
            if (subjectIndex < 0) return@forEachIndexed
            block.forEach { clusterIndex ->
                bySubject.getOrPut(subjectIndex) { mutableListOf() } += clusters[clusterIndex].map {
                    ColumnCell(it, clusterIndex, if (columns) it.centerY else it.centerX)
                }
            }
        }

        val results = page.subjects.mapIndexed { index, subject ->
            val headerFound = index in foundSubjects
            val isInferred = index in inferred
            when {
                isInferred -> warnings += "$label: ${subject.label} başlığı okunamadı; eleme yoluyla eşleştirildi, kontrol edin."
                !headerFound -> warnings += "$label: ${subject.label} başlığı bulunamadı."
            }
            val note = if (isInferred) "başlık okunamadı, eleme ile eşleşti" else null
            resolveSubject(subject, headerFound, bySubject[index].orEmpty(), page.medianCellHeight, columns, label, warnings, note)
        }
        return OcrAnswerTable(booklet, results)
    }

    /**
     * A cell with its place in a subject's reading order: [major] is the index of its column (column layout) or
     * line (row layout), [minor] its position inside that column/line.
     */
    private data class ColumnCell(val cell: Cell, val major: Int, val minor: Float)

    private fun clusterAxis(cells: List<Cell>, gap: Float, key: (Cell) -> Float): List<List<Cell>> {
        val clusters = mutableListOf<MutableList<Cell>>()
        var lastKey = 0f
        cells.sortedBy(key).forEach { cell ->
            val value = key(cell)
            if (clusters.isNotEmpty() && value - lastKey <= gap) clusters.last() += cell else clusters += mutableListOf(cell)
            lastKey = value
        }
        return clusters
    }

    /**
     * Groups neighbouring columns (or lines) into subject blocks. Every subject numbers its questions from 1, so a column that
     * repeats question numbers the current block already has starts the next subject, while the second half of the
     * same subject (11-20 next to 1-10) brings new numbers.
     */
    private fun segmentBlocks(clusters: List<List<Cell>>): List<List<Int>> {
        val blocks = mutableListOf<MutableList<Int>>()
        val blockNumbers = mutableSetOf<Int>()
        clusters.forEachIndexed { index, column ->
            val numbers = column.map { it.number }.toSet()
            val shared = numbers.count { it in blockNumbers }
            val repeats = shared >= max(1f, min(numbers.size, blockNumbers.size) * RESTART_SHARE)
            if (blocks.isEmpty() || repeats) {
                blocks += mutableListOf(index)
                blockNumbers.clear()
            } else {
                blocks.last() += index
            }
            blockNumbers += numbers
        }
        return blocks
    }

    /**
     * Order-preserving matching of block positions to header positions (both in reading order). A block or header
     * may stay unmatched at [skip] cost. Returns, per block, the matched header index or -1.
     */
    private fun alignMonotone(blockXs: List<Float>, headerXs: List<Float>, skip: Float): IntArray {
        val b = blockXs.size
        val h = headerXs.size
        val cost = Array(b + 1) { FloatArray(h + 1) }
        val move = Array(b + 1) { IntArray(h + 1) }
        for (i in 0..b) for (j in 0..h) {
            if (i == 0 && j == 0) continue
            var best = Float.MAX_VALUE
            var chosen = -1
            if (i > 0 && j > 0) {
                best = cost[i - 1][j - 1] + abs(blockXs[i - 1] - headerXs[j - 1])
                chosen = MOVE_MATCH
            }
            if (i > 0 && cost[i - 1][j] + skip < best) {
                best = cost[i - 1][j] + skip
                chosen = MOVE_SKIP_BLOCK
            }
            if (j > 0 && cost[i][j - 1] + skip < best) {
                best = cost[i][j - 1] + skip
                chosen = MOVE_SKIP_HEADER
            }
            cost[i][j] = best
            move[i][j] = chosen
        }
        val result = IntArray(b) { -1 }
        var i = b
        var j = h
        while (i > 0 || j > 0) {
            when (move[i][j]) {
                MOVE_MATCH -> { result[i - 1] = j - 1; i--; j-- }
                MOVE_SKIP_BLOCK -> i--
                else -> j--
            }
        }
        return result
    }

    private fun resolveSubject(
        subject: OcrTableSubject,
        headerFound: Boolean,
        candidates: List<ColumnCell>,
        medianCellHeight: Float,
        columnLayout: Boolean,
        label: String,
        warnings: MutableList<String>,
        inferredNote: String? = null
    ): OcrTableSubjectResult {
        val count = subject.questionCount
        val inRange = candidates.filter { it.cell.number in 1..count }
        if (inRange.size < candidates.size) {
            warnings += "$label: ${subject.label} için soru aralığı dışında ${candidates.size - inRange.size} hücre yoksayıldı."
        }
        val allowed = subject.allowedChoices.map { it.uppercase(Locale.ROOT) }.toSet()
        val ordered = bestReadingOrder(inRange, medianCellHeight, allowTransposed = columnLayout)
        val inSequence = increasingSubsequence(ordered)

        val slots = arrayOfNulls<OcrTableCell>(count + 2)
        fun place(number: Int, cell: Cell, extraNote: String?, forceSuspicious: Boolean) {
            val reasons = buildList {
                extraNote?.let { add(it) }
                inferredNote?.let { add(it) }
                if (cell.lookalike) add("benzer karakterden düzeltildi")
                if (cell.confidence < LOW_CONFIDENCE) add("düşük güven")
            }
            val choice = cell.choice
            slots[number] = when {
                choice !in allowed -> OcrTableCell(number, null, OcrCellStatus.MISSING, "'$choice' bu ders için geçersiz")
                reasons.isEmpty() && !forceSuspicious -> OcrTableCell(number, choice, OcrCellStatus.OK)
                else -> OcrTableCell(number, choice, OcrCellStatus.SUSPICIOUS, reasons.joinToString(", ").ifEmpty { null })
            }
        }

        // Cells that fit the reading order are trusted first ...
        ordered.forEachIndexed { index, item -> if (index in inSequence) place(item.cell.number, item.cell, null, false) }
        // ... then the rest either repair a uniquely determined gap or are flagged / dropped.
        ordered.forEachIndexed { index, item ->
            if (index in inSequence) return@forEachIndexed
            val before = (index - 1 downTo 0).firstOrNull { it in inSequence }?.let { ordered[it].cell.number } ?: 0
            val after = (index + 1 until ordered.size).firstOrNull { it in inSequence }?.let { ordered[it].cell.number } ?: (count + 1)
            val own = item.cell.number
            val gapSlot = if (after - before == 2) before + 1 else null
            when {
                gapSlot != null && slots[gapSlot] == null ->
                    place(gapSlot, item.cell, "numara $own okundu, sıraya göre $gapSlot yapıldı", true)
                slots[own] == null ->
                    place(own, item.cell, "sıra uyumsuz", true)
                else -> {
                    val existing = slots[own]!!
                    if (existing.choice != item.cell.choice) {
                        slots[own] = existing.copy(
                            status = OcrCellStatus.CONFLICT,
                            note = "iki farklı okuma: ${existing.choice ?: "?"} / ${item.cell.choice}"
                        )
                    }
                }
            }
        }

        val cells = (1..count).map { number -> slots[number] ?: OcrTableCell(number, null, OcrCellStatus.MISSING) }
        return OcrTableSubjectResult(subject, headerFound, cells)
    }

    /**
     * Cells of a subject follow their column (or line) order. In the column layout they may instead run across the
     * rows (1-2 side by side, 3-4 below ...); the order that explains more cells as one increasing sequence wins.
     */
    private fun bestReadingOrder(items: List<ColumnCell>, medianCellHeight: Float, allowTransposed: Boolean): List<ColumnCell> {
        val natural = items.sortedWith(compareBy({ it.major }, { it.minor }))
        if (!allowTransposed || items.size < 3) return natural
        val bands = rowBands(items.map { it.cell.centerY }, max(medianCellHeight * 0.6f, 4f))
        val acrossRows = items.withIndex()
            .sortedWith(compareBy({ bands[it.index] }, { it.value.major }))
            .map { it.value }
        return if (increasingSubsequence(acrossRows).size > increasingSubsequence(natural).size) acrossRows else natural
    }

    /** Gives every y a band index so cells printed on one row compare equal despite a few pixels of jitter. */
    private fun rowBands(ys: List<Float>, gap: Float): List<Int> {
        val order = ys.indices.sortedBy { ys[it] }
        val bands = IntArray(ys.size)
        var band = 0
        order.forEachIndexed { position, index ->
            if (position > 0 && ys[index] - ys[order[position - 1]] > gap) band++
            bands[index] = band
        }
        return bands.toList()
    }

    /** Indices of a maximum-weight strictly increasing run of question numbers; ties prefer confident cells. */
    private fun increasingSubsequence(items: List<ColumnCell>): Set<Int> {
        val n = items.size
        if (n == 0) return emptySet()
        val best = DoubleArray(n)
        val previous = IntArray(n) { -1 }
        for (i in 0 until n) {
            best[i] = weight(items[i])
            for (j in 0 until i) {
                if (items[j].cell.number < items[i].cell.number && best[j] + weight(items[i]) > best[i]) {
                    best[i] = best[j] + weight(items[i])
                    previous[i] = j
                }
            }
        }
        var end = 0
        for (i in 1 until n) if (best[i] > best[end]) end = i
        val result = mutableSetOf<Int>()
        var cursor = end
        while (cursor >= 0) {
            result += cursor
            cursor = previous[cursor]
        }
        return result
    }

    private fun weight(item: ColumnCell): Double = 1.0 + item.cell.confidence * 0.01

    // ---------------------------------------------------------------------------------------------

    private const val DEFAULT_CONFIDENCE = 0.72f
    private const val LOW_CONFIDENCE = 0.5f
    private const val MIN_HEADER_SCORE = 0.55f
    private const val MIN_TABLE_CELLS = 8
    private const val MIN_CELLS_PER_TABLE = 3
    private const val MAX_QUESTION_NUMBER = 99
    private const val STRAY_CELL_WARNING = 3
    private const val COLUMN_REACH_CELL_WIDTHS = 6f
    private const val ROW_REACH_CELL_HEIGHTS = 8f
    private const val LINE_GAP_CELL_HEIGHTS = 0.45f
    private const val SKIP_CELL_HEIGHTS = 6f
    private const val BIG_LETTER_RATIO = 2f
    private const val MIN_PLACED_SHARE = 0.5f
    private const val COLUMN_GAP_CELL_WIDTHS = 0.8f
    private const val SKIP_CELL_WIDTHS = 4f
    private const val FIRST_WORD_SCORE = 0.78f
    private const val RESTART_SHARE = 0.4f
    private const val MOVE_MATCH = 0
    private const val MOVE_SKIP_BLOCK = 1
    private const val MOVE_SKIP_HEADER = 2

    private val DASHES = Regex("[\\u2010-\\u2015\\u2212\\u2013\\u2014]")
    private val CELL = Regex("(?<![\\p{L}\\p{N}])([0-9lI|]{1,2})\\s*([-.:,;])?\\s*([\\p{L}0-9])(?![\\p{L}\\p{N}])")
    private val BARE_NUMBER = Regex("\\s*[0-9lI|]{1,2}\\s*[-.:,;]?\\s*")
    private val BARE_LETTER = Regex("\\s*[-.:,;]?\\s*\\p{L}\\s*")
    private val DIGIT_LOOKALIKES = mapOf('8' to "B", '0' to "D", '4' to "A")
    private val TITLE_AFTER = Regex("(?<![A-Z0-9])([A-Z0-9])\\s*(?:GRUBU|GRUP|KITAPCIGI|KITAPCIK)(?![A-Z])")
    private val TITLE_BEFORE = Regex("(?:GRUBU|GRUP|KITAPCIGI|KITAPCIK)\\s*([A-Z0-9])(?![A-Z0-9])")
}
