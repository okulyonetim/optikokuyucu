package com.okulyonetim.optikokuyucu.exam

import com.okulyonetim.optikokuyucu.omr.results.ScanRecord
import java.util.Locale

/**
 * Compatibility layer for exams created before scoring configuration was persisted reliably.
 *
 * A legacy exam may still be stored as NORMAL even though its scanned form is the canonical
 * 90-question LGS layout. We only promote NORMAL exams when one complete scan matches the exact
 * six-test LGS question distribution, keeping ordinary/custom exams untouched.
 */
object ExamScoringInference {
    fun resolve(exam: Exam, records: List<ScanRecord>): Exam {
        if (exam.scoringConfiguration.type != ExamScoringType.NORMAL) return exam
        val looksLikeLgs = records.any { record ->
            looksLikeLgsQuestionIds(record.answers.map { it.questionId })
        }
        if (!looksLikeLgs) return exam

        return exam.copy(
            scoringConfiguration = ExamScoringConfiguration.forType(ExamScoringType.LGS),
            wrongAnswerPolicy = WrongAnswerPolicy.THREE_WRONG_ONE_CORRECT
        )
    }

    internal fun looksLikeLgsQuestionIds(questionIds: Iterable<String>): Boolean {
        val uniqueIds = questionIds.map(String::trim).filter(String::isNotBlank).distinct()
        if (uniqueIds.size != LGS_TOTAL_QUESTION_COUNT) return false

        val counts = linkedMapOf<String, Int>()
        for (questionId in uniqueIds) {
            val lessonId = canonicalLgsLessonId(questionLessonPrefix(questionId)) ?: return false
            counts[lessonId] = (counts[lessonId] ?: 0) + 1
        }
        return counts == LGS_EXPECTED_COUNTS
    }

    private fun canonicalLgsLessonId(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val normalized = raw
            .lowercase(Locale.ROOT)
            .replace('ı', 'i')
            .replace('ğ', 'g')
            .replace('ü', 'u')
            .replace('ş', 's')
            .replace('ö', 'o')
            .replace('ç', 'c')
            .filter { it.isLetterOrDigit() }

        val structuredIndex = Regex("^answers?(\\d+)$")
            .matchEntire(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        val structuredLesson = when (structuredIndex) {
            1 -> "turkce"
            2 -> "inkilap"
            3 -> "din"
            4 -> "yabanci"
            5 -> "matematik"
            6 -> "fen"
            else -> null
        }
        if (structuredLesson != null) return structuredLesson

        return when {
            "turk" in normalized -> "turkce"
            normalized == "mat" || "matematik" in normalized -> "matematik"
            normalized == "fen" || "fenbil" in normalized -> "fen"
            "inkilap" in normalized || "ataturk" in normalized -> "inkilap"
            normalized == "din" || "dinkultur" in normalized -> "din"
            "yabanci" in normalized || "ingiliz" in normalized || "foreign" in normalized -> "yabanci"
            else -> null
        }
    }

    private const val LGS_TOTAL_QUESTION_COUNT = 90
    private val LGS_EXPECTED_COUNTS = linkedMapOf(
        "turkce" to 20,
        "inkilap" to 10,
        "din" to 10,
        "yabanci" to 10,
        "matematik" to 20,
        "fen" to 20
    )
}
