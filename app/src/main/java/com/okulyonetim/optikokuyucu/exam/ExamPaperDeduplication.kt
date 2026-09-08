package com.okulyonetim.optikokuyucu.exam

import com.okulyonetim.optikokuyucu.student.StudentNumber
import com.okulyonetim.optikokuyucu.student.StudentSchoolIdentity
import java.util.Locale

/**
 * Stable exam-paper identity policy.
 *
 * A raw ScanRecord is immutable and always has its own id, but one student must have at most one
 * linked result inside the same exam. Student number is the primary identity. When class data is
 * available, primary/middle-school institution boundaries are respected so equal numbers used by
 * different institutions are not collapsed into one student.
 */
internal object ExamPaperDeduplication {
    fun matchingPapers(
        exam: Exam,
        studentNumber: String,
        className: String,
        studentName: String = ""
    ): List<ExamPaperLink> = exam.papers.filter { paper ->
        sameStudent(
            firstNumber = studentNumber,
            firstClass = className,
            firstName = studentName,
            secondNumber = paper.studentNumber,
            secondClass = paper.className,
            secondName = paper.studentName
        )
    }

    /**
     * Legacy migration view: keep the newest linked result for each confidently identifiable
     * student. Papers without enough identity data are preserved rather than guessed.
     */
    fun collapse(exam: Exam): Exam {
        if (exam.papers.size < 2) return exam

        val newestFirst = exam.papers.withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<ExamPaperLink>> { it.value.linkedAtEpochMs }
                    .thenByDescending { it.index }
            )
        val winners = mutableListOf<ExamPaperLink>()

        newestFirst.forEach { indexed ->
            val candidate = indexed.value
            val hasStableNumber = StudentNumber.normalize(candidate.studentNumber).isNotBlank()
            val duplicate = hasStableNumber && winners.any { kept ->
                sameStudent(
                    firstNumber = candidate.studentNumber,
                    firstClass = candidate.className,
                    firstName = candidate.studentName,
                    secondNumber = kept.studentNumber,
                    secondClass = kept.className,
                    secondName = kept.studentName
                )
            }
            if (!duplicate) winners += candidate
        }

        if (winners.size == exam.papers.size) return exam
        val winnerIds = winners.mapTo(hashSetOf()) { it.scanRecordId }
        return exam.copy(papers = exam.papers.filter { it.scanRecordId in winnerIds })
    }

    private fun sameStudent(
        firstNumber: String,
        firstClass: String,
        firstName: String,
        secondNumber: String,
        secondClass: String,
        secondName: String
    ): Boolean {
        val firstNormalized = StudentNumber.normalize(firstNumber)
        val secondNormalized = StudentNumber.normalize(secondNumber)
        if (firstNormalized.isBlank() || firstNormalized != secondNormalized) return false

        val firstGrade = StudentSchoolIdentity.gradeLevelFromClassName(firstClass)
        val secondGrade = StudentSchoolIdentity.gradeLevelFromClassName(secondClass)
        if (firstGrade != null && secondGrade != null) {
            return StudentSchoolIdentity.sameInstitution(firstGrade, secondGrade)
        }

        val firstNormalizedName = normalizeName(firstName)
        val secondNormalizedName = normalizeName(secondName)
        if (firstNormalizedName.isNotBlank() && secondNormalizedName.isNotBlank()) {
            return firstNormalizedName == secondNormalizedName
        }

        // When neither paper has class/institution metadata, number is the only safe identity that
        // exists. If just one side has institution metadata, do not guess across school boundaries.
        return firstGrade == null && secondGrade == null
    }

    private fun normalizeName(value: String): String = value
        .trim()
        .replace(Regex("\\s+"), " ")
        .lowercase(Locale.ROOT)
}
