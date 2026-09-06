package com.okulyonetim.optikokuyucu.student

enum class StudentGender {
    GIRL,
    BOY,
    UNKNOWN;

    companion object {
        fun fromEschool(value: String): StudentGender = when (value.trim().lowercase()) {
            "kız", "kiz" -> GIRL
            "erkek" -> BOY
            else -> UNKNOWN
        }
    }
}

object StudentNumber {
    fun normalize(raw: String): String {
        val digits = raw.trim().filter(Char::isDigit)
        if (digits.isEmpty()) return ""
        return digits.trimStart('0').ifEmpty { "0" }
    }
}

object StudentClassName {
    fun normalizeBranch(raw: String): String {
        var value = raw.replace('\u00A0', ' ').trim().replace(Regex("\\s+"), " ")
        value = value.replace(Regex("\\s+Şubesi$", RegexOption.IGNORE_CASE), "")
        value = value.replace(Regex("\\s+Şube$", RegexOption.IGNORE_CASE), "")
        return value.trim()
    }

    fun format(gradeLevel: Int, branch: String): String {
        val normalizedBranch = normalizeBranch(branch)
        return if (normalizedBranch.isBlank()) gradeLevel.toString() else "$gradeLevel-$normalizedBranch"
    }
}

data class StudentRosterEntry(
    val studentNumber: String,
    val fullName: String,
    val gender: StudentGender = StudentGender.UNKNOWN,
    val gradeLevel: Int,
    val branch: String,
    val guardianName: String = "",
    val guardianPhone: String = "",
    val updatedAtEpochMs: Long = System.currentTimeMillis()
) {
    init {
        require(StudentNumber.normalize(studentNumber).isNotBlank()) { "Öğrenci numarası boş olamaz." }
        require(fullName.isNotBlank()) { "Öğrenci adı boş olamaz." }
        require(gradeLevel in 1..12) { "Sınıf seviyesi 1–12 arasında olmalıdır." }
        require(updatedAtEpochMs >= 0L)
    }

    val className: String
        get() = StudentClassName.format(gradeLevel, branch)

    fun normalized(): StudentRosterEntry = copy(
        studentNumber = StudentNumber.normalize(studentNumber),
        fullName = fullName.trim().replace(Regex("\\s+"), " "),
        branch = StudentClassName.normalizeBranch(branch),
        guardianName = guardianName.trim().replace(Regex("\\s+"), " "),
        guardianPhone = guardianPhone.trim()
    )
}

data class StudentImportSummary(
    val inserted: Int,
    val updated: Int,
    val unchanged: Int,
    val total: Int
) {
    init {
        require(inserted >= 0 && updated >= 0 && unchanged >= 0 && total >= 0)
        require(inserted + updated + unchanged == total)
    }
}
