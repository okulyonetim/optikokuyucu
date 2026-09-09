package com.okulyonetim.optikokuyucu.ocr

import com.okulyonetim.optikokuyucu.omr.scoring.ManualAnswerSection
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Reconstructs subject/question/answer cells from positioned OCR tokens. */
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
        val headerMatches = sections.mapIndexed { index, section ->
            val match = findHeader(tokens, section.label)
            HeaderMatch(section, index, match?.first, match?.second ?: 0f)
        }

        val confident = headerMatches.filter { it.token != null && it.score >= MIN_HEADER_SCORE }
        if (confident.isEmpty()) {
            warnings += "Ders başlıkları net okunamadı. Hücreleri elle kontrol edin."
        }

        val centers = estimateCenters(headerMatches, recognition.imageWidth)
        val headerBottom = confident.mapNotNull { it.token?.bottom }.maxOrNull()
            ?: (recognition.imageHeight * 0.22f).toInt()
        val ranges = centers.mapIndexed { index, center ->
            val left = if (index == 0) 0f else (centers[index - 1] + center) / 2f
            val right = if (index == centers.lastIndex) recognition.imageWidth.toFloat() else (center + centers[index + 1]) / 2f
            left to right
        }

        val allRows = mutableListOf<OcrAnswerRow>()
        val answers = linkedMapOf<String, String>()

        sections.forEachIndexed { sectionIndex, section ->
            val (left, right) = ranges[sectionIndex]
            val sectionTokens = tokens.filter { token ->
                token.centerX >= left && token.centerX < right && token.centerY > headerBottom
            }
            val detectedByNumber = findQuestionAnswers(sectionTokens, section)
            val missing = mutableListOf<Int>()

            section.questionIds.forEachIndexed { index, questionId ->
                val number = index + 1
                val answer = detectedByNumber[number]
                if (answer == null) missing += number else answers[questionId] = answer
                allRows += OcrAnswerRow(
                    sectionId = section.id,
                    subject = section.label,
                    questionId = questionId,
                    questionNumber = number,
                    answer = answer
                )
            }

            val match = headerMatches[sectionIndex]
            if (match.token == null || match.score < MIN_HEADER_SCORE) {
                warnings += "${section.label}: ders başlığı konuma göre tahmin edildi."
            }
            if (missing.isNotEmpty()) {
                warnings += "${section.label}: okunamayan sorular ${compressNumbers(missing)}."
            }
        }

        val booklet = detectBooklet(recognition.text, knownBooklets)
        if (knownBooklets.isNotEmpty() && booklet == null) {
            warnings += "Kitapçık türü otomatik belirlenemedi. Kaydetmeden önce seçin."
        }

        return OcrAnswerKeyExtraction(
            rows = allRows,
            answers = answers,
            detectedBooklet = booklet,
            warnings = warnings.distinct()
        )
    }

    private data class HeaderMatch(
        val section: ManualAnswerSection,
        val index: Int,
        val token: OcrToken?,
        val score: Float
    )

    private fun findHeader(tokens: List<OcrToken>, label: String): Pair<OcrToken, Float>? {
        val aliases = subjectAliases(label)
        val candidates = phraseCandidates(tokens)
        return candidates
            .map { candidate -> candidate to aliases.maxOf { alias -> similarity(normalize(candidate.text), alias) } }
            .filter { (_, score) -> score >= 0.35f }
            .maxByOrNull { it.second }
    }

    private fun phraseCandidates(tokens: List<OcrToken>): List<OcrToken> {
        val ordered = tokens.sortedWith(compareBy<OcrToken> { it.centerY }.thenBy { it.left })
        val result = ArrayList<OcrToken>(ordered.size * 2)
        result += ordered
        ordered.forEachIndexed { index, first ->
            var text = first.text
            var left = first.left
            var top = first.top
            var right = first.right
            var bottom = first.bottom
            for (offset in 1..3) {
                val next = ordered.getOrNull(index + offset) ?: break
                val sameLine = abs(next.centerY - first.centerY) <= max(first.height, next.height) * 0.85f
                val horizontalGap = next.left - right
                val reasonableGap = horizontalGap in (-max(first.width, next.width))..(max(first.height, next.height) * 5)
                if (!sameLine || !reasonableGap) break
                text += " ${next.text}"
                left = min(left, next.left)
                top = min(top, next.top)
                right = max(right, next.right)
                bottom = max(bottom, next.bottom)
                result += OcrToken(text, left, top, right, bottom)
            }
        }
        return result
    }

    private fun estimateCenters(matches: List<HeaderMatch>, imageWidth: Int): List<Float> {
        val result = MutableList(matches.size) { index -> imageWidth * ((index + 0.5f) / matches.size.toFloat()) }
        matches.forEachIndexed { index, match ->
            if (match.token != null && match.score >= MIN_HEADER_SCORE) result[index] = match.token.centerX
        }

        // Preserve the form's subject order even when OCR shifts or misses one header.
        for (i in 1 until result.size) {
            if (result[i] <= result[i - 1]) {
                result[i] = result[i - 1] + imageWidth.toFloat() / matches.size.coerceAtLeast(1)
            }
        }
        val maxCenter = imageWidth * 0.96f
        for (i in result.indices.reversed()) {
            if (result[i] > maxCenter) result[i] = maxCenter - ((result.lastIndex - i) * imageWidth * 0.02f)
        }
        return result
    }

    private fun findQuestionAnswers(
        tokens: List<OcrToken>,
        section: ManualAnswerSection
    ): Map<Int, String> {
        val allowed = section.allowedChoices.map(::normalizeChoice).toSet()
        val numbers = tokens.mapNotNull { token ->
            cleanNumber(token.text)?.takeIf { it in 1..section.questionIds.size }?.let { it to token }
        }
        val answerTokens = tokens.mapNotNull { token ->
            val choice = normalizeChoice(token.text)
            choice.takeIf { it in allowed }?.let { choice to token }
        }
        val found = linkedMapOf<Int, String>()

        numbers.sortedBy { it.second.centerY }.forEach { (number, numberToken) ->
            val best = answerTokens
                .asSequence()
                .filter { (_, token) -> token.centerX > numberToken.centerX }
                .filter { (_, token) ->
                    abs(token.centerY - numberToken.centerY) <= max(token.height, numberToken.height) * 0.90f
                }
                .minByOrNull { (_, token) ->
                    abs(token.centerX - numberToken.centerX) + abs(token.centerY - numberToken.centerY) * 2f
                }
            if (best != null) found.putIfAbsent(number, best.first)
        }
        return found
    }

    private fun cleanNumber(value: String): Int? {
        val cleaned = value.trim().replace(Regex("[^0-9]"), "")
        return cleaned.takeIf { it.length in 1..3 }?.toIntOrNull()
    }

    private fun normalizeChoice(value: String): String = value
        .trim()
        .replace(Regex("[^\\p{L}0-9]"), "")
        .uppercase(TURKISH)

    private fun detectBooklet(text: String, knownBooklets: List<String>): String? {
        if (knownBooklets.isEmpty()) return null
        val normalized = normalize(text)
        val normalizedKnown = knownBooklets.associateBy(::normalize)
        val patterns = listOf(
            Regex("\\b([A-Z0-9])\\s*(?:GRUBU|KITAPCIK|KITAPCIGI)\\b"),
            Regex("\\b(?:GRUBU|KITAPCIK|KITAPCIGI)\\s*([A-Z0-9])\\b")
        )
        patterns.forEach { pattern ->
            val value = pattern.find(normalized)?.groupValues?.getOrNull(1)
            if (value != null) normalizedKnown[value]?.let { return it }
        }
        return knownBooklets.firstOrNull { known ->
            val key = normalize(known)
            Regex("\\b${Regex.escape(key)}\\b").containsMatchIn(normalized) &&
                (normalized.contains("GRUBU") || normalized.contains("KITAPCIK"))
        }
    }

    private fun subjectAliases(label: String): List<String> {
        val normalized = normalize(label)
        val aliases = linkedSetOf(normalized)
        when {
            normalized.contains("TURKCE") -> aliases += listOf("TURKCE", "TURK")
            normalized.contains("MATEMATIK") -> aliases += listOf("MATEMATIK", "MAT")
            normalized.contains("FEN") -> aliases += listOf("FEN BILIMLERI", "FEN")
            normalized.contains("INGILIZCE") || normalized.contains("ENGLISH") -> aliases += listOf("INGILIZCE", "ENGLISH")
            normalized.contains("DIN") -> aliases += listOf("DIN", "DIN KULTURU", "DIN KULTURU VE AHLAK BILGISI")
            normalized.contains("INKILAP") || normalized.contains("ATATURK") -> aliases += listOf(
                "INKILAP",
                "TC INK TARH",
                "TC INKILAP TARIHI",
                "INKILAP TARIHI"
            )
        }
        val meaningfulWords = normalized.split(' ')
            .filter { it.length >= 3 && it !in STOP_WORDS }
        if (meaningfulWords.isNotEmpty()) aliases += meaningfulWords.joinToString(" ")
        return aliases.filter(String::isNotBlank)
    }

    private fun normalize(value: String): String {
        val upper = value.uppercase(TURKISH)
            .replace('İ', 'I')
            .replace('I', 'I')
            .replace('Ş', 'S')
            .replace('Ğ', 'G')
            .replace('Ü', 'U')
            .replace('Ö', 'O')
            .replace('Ç', 'C')
        return Normalizer.normalize(upper, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^A-Z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    private fun similarity(left: String, right: String): Float {
        if (left.isBlank() || right.isBlank()) return 0f
        if (left == right) return 1f
        if (left.contains(right) || right.contains(left)) {
            return min(left.length, right.length).toFloat() / max(left.length, right.length).toFloat() * 0.92f + 0.08f
        }
        val maxLen = max(left.length, right.length)
        val edit = levenshtein(left, right)
        val editScore = 1f - edit.toFloat() / maxLen.toFloat()
        val lWords = left.split(' ').filter(String::isNotBlank).toSet()
        val rWords = right.split(' ').filter(String::isNotBlank).toSet()
        val wordScore = if ((lWords union rWords).isEmpty()) 0f else (lWords intersect rWords).size.toFloat() / (lWords union rWords).size.toFloat()
        return max(editScore, wordScore * 0.95f)
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val current = IntArray(b.length + 1)
            current[0] = i + 1
            for (j in b.indices) {
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + if (a[i] == b[j]) 0 else 1
                )
            }
            previous = current
        }
        return previous[b.length]
    }

    private fun compressNumbers(numbers: List<Int>): String {
        if (numbers.isEmpty()) return ""
        val sorted = numbers.distinct().sorted()
        val parts = mutableListOf<String>()
        var start = sorted.first()
        var end = start
        sorted.drop(1).forEach { value ->
            if (value == end + 1) end = value
            else {
                parts += if (start == end) "$start" else "$start–$end"
                start = value
                end = value
            }
        }
        parts += if (start == end) "$start" else "$start–$end"
        return parts.joinToString(", ")
    }

    private val TURKISH = Locale("tr", "TR")
    private val STOP_WORDS = setOf("VE", "ILE", "BILGISI", "TARIHI", "EGITIMI")
    private const val MIN_HEADER_SCORE = 0.55f
}
