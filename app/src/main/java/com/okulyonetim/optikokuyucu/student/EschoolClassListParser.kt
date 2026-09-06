package com.okulyonetim.optikokuyucu.student

object EschoolClassListParser {
    private val classHeader = Regex(
        pattern = "^(\\d+)\\.\\s*Sınıf\\s*/\\s*(.+?)\\s+Sınıf\\s+Listesi$",
        option = RegexOption.IGNORE_CASE
    )
    private val studentRowWithSerial = Regex(
        pattern = "^(\\d+)\\s+(\\d+)\\s+(.+?)\\s+(Erkek|Kız)$",
        option = RegexOption.IGNORE_CASE
    )
    private val studentRowWithoutSerial = Regex(
        pattern = "^(\\d+)\\s+(.+?)\\s+(Erkek|Kız)$",
        option = RegexOption.IGNORE_CASE
    )

    fun parse(
        text: String,
        importedAtEpochMs: Long = System.currentTimeMillis()
    ): List<StudentRosterEntry> {
        require(importedAtEpochMs >= 0L)
        var gradeLevel: Int? = null
        var branch = ""
        val students = mutableListOf<StudentRosterEntry>()

        text.lineSequence().forEach { rawLine ->
            val line = rawLine
                .replace('\u00A0', ' ')
                .trim()
                .replace(Regex("\\s+"), " ")
            if (line.isBlank()) return@forEach

            classHeader.matchEntire(line)?.let { match ->
                gradeLevel = match.groupValues[1].toIntOrNull()
                branch = StudentClassName.normalizeBranch(match.groupValues[2])
                return@forEach
            }

            val currentGrade = gradeLevel ?: return@forEach
            val parsed = studentRowWithSerial.matchEntire(line)?.let { row ->
                ParsedStudentRow(
                    number = row.groupValues[2],
                    name = row.groupValues[3],
                    gender = row.groupValues[4]
                )
            } ?: studentRowWithoutSerial.matchEntire(line)?.let { row ->
                ParsedStudentRow(
                    number = row.groupValues[1],
                    name = row.groupValues[2],
                    gender = row.groupValues[3]
                )
            } ?: return@forEach

            val number = StudentNumber.normalize(parsed.number)
            val name = parsed.name.trim().replace(Regex("\\s+"), " ")
            if (number.isBlank() || name.isBlank()) return@forEach
            students += StudentRosterEntry(
                studentNumber = number,
                fullName = name,
                gender = StudentGender.fromEschool(parsed.gender),
                gradeLevel = currentGrade,
                branch = branch,
                updatedAtEpochMs = importedAtEpochMs
            ).normalized()
        }

        require(students.isNotEmpty()) {
            "e-Okul sınıf listesinde öğrenci satırı bulunamadı."
        }
        students.groupBy { it.studentNumber }.forEach { (number, duplicates) ->
            val identities = duplicates.map { it.fullName.lowercase() to it.className.lowercase() }.distinct()
            require(identities.size == 1) {
                "Öğrenci no $number birden fazla öğrenciye ait görünüyor."
            }
        }
        return students.distinctBy { it.studentNumber }
            .sortedWith(
                compareBy<StudentRosterEntry> { it.gradeLevel }
                    .thenBy { it.branch }
                    .thenBy { it.studentNumber.toIntOrNull() ?: Int.MAX_VALUE }
                    .thenBy { it.studentNumber }
            )
    }

    private data class ParsedStudentRow(
        val number: String,
        val name: String,
        val gender: String
    )
}
