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

/**
 * Koruk'ta öğrenci numarası kurum içinde benzersizdir; İlkokul ve Ortaokul aynı numarayı
 * kullanabilir. Bu nedenle kalıcı öğrenci kimliği numara + kurum grubundan oluşur.
 */
object StudentSchoolIdentity {
    const val PRIMARY_SCHOOL_NAME = "Koruk İlkokulu"
    const val MIDDLE_SCHOOL_NAME = "Koruk Ortaokulu"

    fun institutionKeyForGrade(gradeLevel: Int): String = when (gradeLevel) {
        in 1..4 -> "koruk-ilkokulu"
        in 5..8 -> "koruk-ortaokulu"
        else -> "sinif-$gradeLevel"
    }

    fun schoolNameForGrade(gradeLevel: Int): String = when (gradeLevel) {
        in 1..4 -> PRIMARY_SCHOOL_NAME
        in 5..8 -> MIDDLE_SCHOOL_NAME
        else -> ""
    }

    fun identityKey(studentNumber: String, gradeLevel: Int): String {
        val normalized = StudentNumber.normalize(studentNumber)
        if (normalized.isBlank()) return ""
        return "${institutionKeyForGrade(gradeLevel)}:$normalized"
    }

    fun gradeLevelFromClassName(className: String): Int? =
        Regex("^\\s*(\\d{1,2})").find(className)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?.takeIf { it in 1..12 }

    fun identityKeyFromClassName(studentNumber: String, className: String): String? =
        gradeLevelFromClassName(className)?.let { identityKey(studentNumber, it) }?.takeIf(String::isNotBlank)

    fun sameInstitution(firstGradeLevel: Int, secondGradeLevel: Int): Boolean =
        institutionKeyForGrade(firstGradeLevel) == institutionKeyForGrade(secondGradeLevel)
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

    val schoolName: String
        get() = StudentSchoolIdentity.schoolNameForGrade(gradeLevel)

    val identityKey: String
        get() = StudentSchoolIdentity.identityKey(studentNumber, gradeLevel)

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
