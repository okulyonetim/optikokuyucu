package com.okulyonetim.optikokuyucu.ocr

import com.okulyonetim.optikokuyucu.omr.scoring.ManualAnswerSection
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Reconstructs answer keys from the OCR geometry. The source is treated as a two-dimensional
 * document, not as a flattened text stream: subject blocks may be side-by-side or on multiple rows.
 */
object OcrAnswerKeyParser {
    fun parse(
        recognition: OcrRecognitionResult,
        sections: List<ManualAnswerSection>,
        knownBooklets: List<String> = emptyList()
    ): OcrAnswerKeyExtraction {
        if (sections.isEmpty()) {
            return OcrAnswerKeyExtraction(emptyList(), emptyMap(), null, listOf("Bu optik formda cevap alanı bulunamadı."))
        }
        val tokens = recognition.tokens.filter { it.text.isNotBlank() }
        val warnings = mutableListOf<String>()
        val headers = sections.mapIndexed { index, section ->
            val found = findHeader(tokens, section.label)
            HeaderMatch(section, index, found?.first, found?.second ?: 0f)
        }
        val matched = headers.filter { it.token != null && it.score >= MIN_HEADER_SCORE }
        if (matched.isEmpty()) {
            warnings += "Ders başlıkları güvenilir biçimde bulunamadı. Sonuçları elle kontrol edin."
        }

        val regions = buildSectionRegions(headers, recognition.imageWidth, recognition.imageHeight)
        val answers = linkedMapOf<String, String>()
        val rows = mutableListOf<OcrAnswerRow>()
        val lowConfidence = mutableListOf<String>()

        sections.forEachIndexed { sectionIndex, section ->
            val region = regions[sectionIndex]
            val blockTokens = tokens.filter { region.contains(it.centerX, it.centerY) }
            val detected = findQuestionAnswers(blockTokens, section, region)
            val missing = mutableListOf<Int>()
            section.questionIds.forEachIndexed { index, questionId ->
                val number = index + 1
                val candidate = detected[number]
                if (candidate == null) {
                    missing += number
                } else {
                    answers[questionId] = candidate.choice
                    if (candidate.confidence < LOW_CONFIDENCE_WARNING) {
                        lowConfidence += "${section.label} $number"
                    }
                }
                rows += OcrAnswerRow(section.id, section.label, questionId, number, candidate?.choice)
            }

            val header = headers[sectionIndex]
            if (header.token == null || header.score < MIN_HEADER_SCORE) {
                warnings += "${section.label}: ders bölgesi konumdan tahmin edildi."
            }
            if (missing.isNotEmpty()) {
                warnings += "${section.label}: okunamayan sorular ${compressNumbers(missing)}."
            }
        }

        if (lowConfidence.isNotEmpty()) {
            warnings += "Düşük güvenle okunan cevapları kontrol edin: ${lowConfidence.take(12).joinToString(", ")}${if (lowConfidence.size > 12) "…" else ""}."
        }
        val booklet = detectBooklet(recognition.text, knownBooklets)
        if (knownBooklets.isNotEmpty() && booklet == null) {
            warnings += "Kitapçık türü otomatik belirlenemedi. Kaydetmeden önce seçin."
        }
        return OcrAnswerKeyExtraction(rows, answers, booklet, warnings.distinct())
    }

    private data class HeaderMatch(
        val section: ManualAnswerSection,
        val index: Int,
        val token: OcrToken?,
        val score: Float
    )

    private data class SectionRegion(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        fun contains(x: Float, y: Float): Boolean = x >= left && x < right && y >= top && y < bottom
    }

    private data class RowModel(val firstY: Float, val step: Float, val observed: Map<Int, Float>) {
        fun center(number: Int): Float {
            val fitted = firstY + (number - 1) * step
            val actual = observed[number] ?: return fitted
            return if (abs(actual - fitted) <= step * 0.45f) actual else fitted
        }
    }

    private data class AnswerCandidate(
        val id: String,
        val choice: String,
        val centerX: Float,
        val centerY: Float,
        val height: Int,
        val confidence: Float
    )

    /** Builds independent rectangles for every subject header, including multi-row page layouts. */
    private fun buildSectionRegions(
        matches: List<HeaderMatch>,
        imageWidth: Int,
        imageHeight: Int
    ): List<SectionRegion> {
        val confident = matches.filter { it.token != null && it.score >= MIN_HEADER_SCORE }
        if (confident.isEmpty()) return fallbackRegions(matches.size, imageWidth, imageHeight)

        val medianHeaderHeight = median(confident.map { it.token!!.height.toFloat() }).coerceAtLeast(12f)
        val rowTolerance = max(medianHeaderHeight * 2.3f, imageHeight * 0.045f)
        val rows = mutableListOf<MutableList<HeaderMatch>>()
        confident.sortedBy { it.token!!.centerY }.forEach { match ->
            val target = rows.minByOrNull { row -> abs(row.map { it.token!!.centerY }.average().toFloat() - match.token!!.centerY) }
            if (target != null && abs(target.map { it.token!!.centerY }.average().toFloat() - match.token!!.centerY) <= rowTolerance) {
                target += match
            } else rows += mutableListOf(match)
        }
        rows.forEach { it.sortBy { match -> match.token!!.centerX } }
        rows.sortBy { row -> row.minOf { it.token!!.centerY } }

        val result = MutableList(matches.size) { SectionRegion(0f, 0f, imageWidth.toFloat(), imageHeight.toFloat()) }
        rows.forEachIndexed { rowIndex, row ->
            val nextRowTop = rows.getOrNull(rowIndex + 1)?.minOf { it.token!!.top }?.toFloat() ?: imageHeight.toFloat()
            val rowBottom = (nextRowTop - medianHeaderHeight * 0.45f).coerceAtLeast(row.maxOf { it.token!!.bottom }.toFloat() + medianHeaderHeight)
            row.forEachIndexed { columnIndex, match ->
                val token = match.token!!
                val previous = row.getOrNull(columnIndex - 1)?.token
                val next = row.getOrNull(columnIndex + 1)?.token
                val left = previous?.let { (it.centerX + token.centerX) / 2f } ?: 0f
                val right = next?.let { (token.centerX + it.centerX) / 2f } ?: imageWidth.toFloat()
                val top = (token.bottom - medianHeaderHeight * 0.15f).coerceAtLeast(0f)
                result[match.index] = SectionRegion(left, top, right, rowBottom.coerceAtMost(imageHeight.toFloat()))
            }
        }

        // Unreadable headers inherit the nearest slot from form order without changing confident regions.
        matches.filter { it.token == null || it.score < MIN_HEADER_SCORE }.forEach { missing ->
            val previous = (missing.index - 1 downTo 0).firstNotNullOfOrNull { i ->
                matches[i].takeIf { it.token != null && it.score >= MIN_HEADER_SCORE }
            }
            val next = (missing.index + 1 until matches.size).firstNotNullOfOrNull { i ->
                matches[i].takeIf { it.token != null && it.score >= MIN_HEADER_SCORE }
            }
            result[missing.index] = when {
                previous != null && next != null -> {
                    val a = result[previous.index]
                    val b = result[next.index]
                    if (abs(a.top - b.top) < rowTolerance) {
                        SectionRegion(a.right, min(a.top, b.top), b.left, max(a.bottom, b.bottom))
                    } else fallbackRegions(matches.size, imageWidth, imageHeight)[missing.index]
                }
                else -> fallbackRegions(matches.size, imageWidth, imageHeight)[missing.index]
            }
        }
        return result
    }

    private fun fallbackRegions(count: Int, width: Int, height: Int): List<SectionRegion> =
        List(count.coerceAtLeast(1)) { index ->
            val left = width * index.toFloat() / count.coerceAtLeast(1)
            val right = width * (index + 1).toFloat() / count.coerceAtLeast(1)
            SectionRegion(left, height * 0.12f, right, height.toFloat())
        }

    private fun findQuestionAnswers(
        tokens: List<OcrToken>,
        section: ManualAnswerSection,
        region: SectionRegion
    ): Map<Int, AnswerCandidate> {
        val maxQuestion = section.questionIds.size
        val numbers = tokens.mapNotNull { token ->
            cleanNumber(token.text)?.takeIf { it in 1..maxQuestion }?.let { it to token }
        }
        val candidates = buildAnswerCandidates(tokens, section.allowedChoices.map(::normalizeChoice).toSet())
            .filter { it.centerY > region.top }
        val rowModel = buildLocalRowModel(numbers, maxQuestion)
            ?: inferRowModelFromAnswers(candidates, maxQuestion)
        val found = linkedMapOf<Int, AnswerCandidate>()
        val used = mutableSetOf<String>()

        numbers.sortedBy { it.second.centerY }.forEach { (number, numberToken) ->
            val best = candidates.asSequence()
                .filter { it.id !in used }
                .filter { it.centerX > numberToken.centerX }
                .filter { abs(it.centerY - numberToken.centerY) <= max(it.height, numberToken.height) * 1.05f }
                .minByOrNull {
                    abs(it.centerX - numberToken.centerX) + abs(it.centerY - numberToken.centerY) * 2f + (1f - it.confidence) * 25f
                }
            if (best != null && number !in found) {
                found[number] = best
                used += best.id
            }
        }

        if (rowModel != null) {
            val tolerance = max(rowModel.step * 0.43f, median(candidates.map { it.height.toFloat() }) * 1.2f)
            for (number in 1..maxQuestion) {
                if (number in found) continue
                val y = rowModel.center(number)
                val best = candidates.asSequence()
                    .filter { it.id !in used }
                    .filter { abs(it.centerY - y) <= tolerance }
                    .minByOrNull { abs(it.centerY - y) + (1f - it.confidence) * tolerance }
                if (best != null) {
                    found[number] = best
                    used += best.id
                }
            }
        }
        return found
    }

    private fun buildLocalRowModel(numbers: List<Pair<Int, OcrToken>>, maxQuestion: Int): RowModel? {
        val observed = numbers.groupBy({ it.first }, { it.second.centerY })
            .mapValues { median(it.value) }
            .filterKeys { it in 1..maxQuestion }
        if (observed.size < 2) return null
        val points = observed.entries.sortedBy { it.key }
        val slopes = buildList {
            for (i in points.indices) for (j in i + 1 until points.size) {
                val dn = points[j].key - points[i].key
                val dy = points[j].value - points[i].value
                if (dn > 0 && dy > 0f) {
                    val slope = dy / dn
                    if (slope in MIN_ROW_STEP..MAX_ROW_STEP) add(slope)
                }
            }
        }
        if (slopes.isEmpty()) return null
        val step = median(slopes)
        val first = median(points.map { it.value - (it.key - 1) * step })
        return RowModel(first, step, observed)
    }

    private fun inferRowModelFromAnswers(candidates: List<AnswerCandidate>, maxQuestion: Int): RowModel? {
        if (candidates.size < 3 || maxQuestion < 2) return null
        val clustered = candidates.sortedBy { it.centerY }.fold(mutableListOf<MutableList<AnswerCandidate>>()) { rows, candidate ->
            val last = rows.lastOrNull()
            if (last != null && abs(last.map { it.centerY }.average().toFloat() - candidate.centerY) <= max(5f, candidate.height * 0.65f)) {
                last += candidate
            } else rows += mutableListOf(candidate)
            rows
        }
        val centers = clustered.map { row -> row.map { it.centerY }.average().toFloat() }.distinct().sorted()
        if (centers.size < 3) return null
        val gaps = centers.zipWithNext { a, b -> b - a }.filter { it in MIN_ROW_STEP..MAX_ROW_STEP }
        if (gaps.isEmpty()) return null
        val step = median(gaps)
        return RowModel(centers.first(), step, emptyMap())
    }

    private fun buildAnswerCandidates(tokens: List<OcrToken>, allowed: Set<String>): List<AnswerCandidate> {
        val result = mutableListOf<AnswerCandidate>()
        tokens.forEachIndexed { tokenIndex, token ->
            val normalized = normalizeChoice(token.text)
            val confidence = token.confidence ?: DEFAULT_ELEMENT_CONFIDENCE
            if (normalized in allowed && confidence >= MIN_ELEMENT_ANSWER_CONFIDENCE && abs(token.angle) <= MAX_ANSWER_ANGLE) {
                result += AnswerCandidate("e:$tokenIndex", normalized, token.centerX, token.centerY, token.height, confidence.coerceIn(0f, 1f))
            }
            // Symbol fallback is only allowed for tiny/noisy cell tokens; never pull A/B/C/D out of normal words.
            if (normalized.length <= 3) {
                token.symbols.forEachIndexed { symbolIndex, symbol ->
                    val choice = normalizeChoice(symbol.text)
                    val symbolConfidence = symbol.confidence ?: return@forEachIndexed
                    if (choice in allowed && symbolConfidence >= MIN_SYMBOL_ANSWER_CONFIDENCE && abs(symbol.angle) <= MAX_ANSWER_ANGLE) {
                        result += AnswerCandidate(
                            "s:$tokenIndex:$symbolIndex",
                            choice,
                            symbol.centerX,
                            symbol.centerY,
                            symbol.height,
                            symbolConfidence.coerceIn(0f, 1f)
                        )
                    }
                }
            }
        }
        return result.groupBy { "${it.choice}:${(it.centerX / 3f).toInt()}:${(it.centerY / 3f).toInt()}" }
            .values.map { group -> group.maxBy { it.confidence } }
    }

    private fun findHeader(tokens: List<OcrToken>, label: String): Pair<OcrToken, Float>? {
        val aliases = subjectAliases(label)
        return phraseCandidates(tokens)
            .map { candidate ->
                val lexical = aliases.maxOf { alias -> similarity(normalize(candidate.text), alias) }
                val confidence = candidate.confidence ?: 0.72f
                candidate to lexical * (0.82f + confidence.coerceIn(0f, 1f) * 0.18f)
            }
            .filter { it.second >= 0.35f }
            .maxByOrNull { it.second }
    }

    private fun phraseCandidates(tokens: List<OcrToken>): List<OcrToken> {
        val result = ArrayList<OcrToken>(tokens.size * 2)
        result += tokens
        val lineTolerance = median(tokens.map { it.height.toFloat() }).coerceAtLeast(10f) * 0.9f
        val ordered = tokens.sortedWith(compareBy<OcrToken> { it.centerY }.thenBy { it.left })
        ordered.forEachIndexed { index, first ->
            var current = first
            for (offset in 1..3) {
                val next = ordered.getOrNull(index + offset) ?: break
                if (abs(next.centerY - first.centerY) > lineTolerance) break
                val gap = next.left - current.right
                if (gap > max(first.height, next.height) * 5 || gap < -max(first.width, next.width)) break
                current = OcrToken(
                    text = "${current.text} ${next.text}",
                    left = min(current.left, next.left),
                    top = min(current.top, next.top),
                    right = max(current.right, next.right),
                    bottom = max(current.bottom, next.bottom),
                    confidence = listOfNotNull(current.confidence, next.confidence).takeIf { it.isNotEmpty() }?.average()?.toFloat()
                )
                result += current
            }
        }
        return result
    }

    private fun cleanNumber(value: String): Int? = value.trim().replace(Regex("[^0-9]"), "")
        .takeIf { it.length in 1..3 }?.toIntOrNull()

    private fun normalizeChoice(value: String): String = value.trim()
        .replace(Regex("[^\\p{L}0-9]"), "")
        .uppercase(TURKISH)

    private fun detectBooklet(text: String, knownBooklets: List<String>): String? {
        if (knownBooklets.isEmpty()) return null
        val normalized = normalize(text)
        val known = knownBooklets.associateBy(::normalize)
        listOf(
            Regex("\\b([A-Z0-9])\\s*(?:GRUBU|KITAPCIK|KITAPCIGI)\\b"),
            Regex("\\b(?:GRUBU|KITAPCIK|KITAPCIGI)\\s*([A-Z0-9])\\b")
        ).forEach { pattern ->
            pattern.find(normalized)?.groupValues?.getOrNull(1)?.let { known[it]?.let { value -> return value } }
        }
        return null
    }

    private fun subjectAliases(label: String): List<String> {
        val n = normalize(label)
        val aliases = linkedSetOf(n)
        when {
            n.contains("TURKCE") -> aliases += listOf("TURKCE", "TURK")
            n.contains("MATEMATIK") -> aliases += listOf("MATEMATIK", "MAT")
            n.contains("FEN") -> aliases += listOf("FEN BILIMLERI", "FEN")
            n.contains("INGILIZCE") || n.contains("ENGLISH") -> aliases += listOf("INGILIZCE", "ENGLISH")
            n.contains("DIN") -> aliases += listOf("DIN", "DIN KULTURU", "DIN KULTURU VE AHLAK BILGISI")
            n.contains("INKILAP") || n.contains("ATATURK") -> aliases += listOf("INKILAP", "TC INK TARH", "TC INKILAP TARIHI", "INKILAP TARIHI")
        }
        return aliases.filter(String::isNotBlank)
    }

    private fun normalize(value: String): String {
        val upper = value.uppercase(TURKISH)
            .replace('İ', 'I').replace('Ş', 'S').replace('Ğ', 'G')
            .replace('Ü', 'U').replace('Ö', 'O').replace('Ç', 'C')
        return Normalizer.normalize(upper, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^A-Z0-9]+"), " ")
            .trim().replace(Regex("\\s+"), " ")
    }

    private fun similarity(a: String, b: String): Float {
        if (a.isBlank() || b.isBlank()) return 0f
        if (a == b) return 1f
        if (a.contains(b) || b.contains(a)) return min(a.length, b.length).toFloat() / max(a.length, b.length) * 0.92f + 0.08f
        val editScore = 1f - levenshtein(a, b).toFloat() / max(a.length, b.length)
        val aw = a.split(' ').filter(String::isNotBlank).toSet()
        val bw = b.split(' ').filter(String::isNotBlank).toSet()
        val wordScore = if ((aw union bw).isEmpty()) 0f else (aw intersect bw).size.toFloat() / (aw union bw).size
        return max(editScore, wordScore * 0.95f)
    }

    private fun levenshtein(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val current = IntArray(b.length + 1)
            current[0] = i + 1
            for (j in b.indices) current[j + 1] = minOf(
                current[j] + 1,
                previous[j + 1] + 1,
                previous[j] + if (a[i] == b[j]) 0 else 1
            )
            previous = current
        }
        return previous[b.length]
    }

    private fun compressNumbers(numbers: List<Int>): String {
        val sorted = numbers.distinct().sorted()
        if (sorted.isEmpty()) return ""
        val parts = mutableListOf<String>()
        var start = sorted.first(); var end = start
        sorted.drop(1).forEach { value ->
            if (value == end + 1) end = value else {
                parts += if (start == end) "$start" else "$start–$end"
                start = value; end = value
            }
        }
        parts += if (start == end) "$start" else "$start–$end"
        return parts.joinToString(", ")
    }

    private fun median(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted(); val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2f
    }

    private val TURKISH = Locale("tr", "TR")
    private const val MIN_HEADER_SCORE = 0.55f
    private const val MIN_ROW_STEP = 6f
    private const val MAX_ROW_STEP = 240f
    private const val DEFAULT_ELEMENT_CONFIDENCE = 0.72f
    private const val MIN_ELEMENT_ANSWER_CONFIDENCE = 0.38f
    private const val MIN_SYMBOL_ANSWER_CONFIDENCE = 0.68f
    private const val LOW_CONFIDENCE_WARNING = 0.62f
    private const val MAX_ANSWER_ANGLE = 20f
}
