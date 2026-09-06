package com.okulyonetim.optikokuyucu.student

import java.util.Locale

data class StudentResultMessageValues(
    val studentName: String,
    val studentNumber: String,
    val className: String,
    val guardianName: String,
    val examName: String,
    val correct: Int,
    val wrong: Int,
    val blank: Int,
    val doubleMark: Int,
    val suspicious: Int,
    val net: Double
)

object StudentResultMessageTemplate {
    const val DEFAULT = "Sayın {veliAdi}, {ogrenciAdi} öğrencimizin {sinavAdi} sonucu: " +
        "Net {net}, Doğru {dogru}, Yanlış {yanlis}, Boş {bos}."

    val placeholders: List<String> = listOf(
        "{veliAdi}",
        "{ogrenciAdi}",
        "{ogrenciNo}",
        "{sinif}",
        "{sinavAdi}",
        "{dogru}",
        "{yanlis}",
        "{bos}",
        "{cift}",
        "{supheli}",
        "{net}"
    )

    fun render(template: String, values: StudentResultMessageValues): String {
        val replacements = linkedMapOf(
            "{veliAdi}" to values.guardianName,
            "{ogrenciAdi}" to values.studentName,
            "{ogrenciNo}" to values.studentNumber,
            "{sinif}" to values.className,
            "{sinavAdi}" to values.examName,
            "{dogru}" to values.correct.toString(),
            "{yanlis}" to values.wrong.toString(),
            "{bos}" to values.blank.toString(),
            "{cift}" to values.doubleMark.toString(),
            "{supheli}" to values.suspicious.toString(),
            "{net}" to formatNet(values.net)
        )
        return replacements.entries.fold(template) { current, (placeholder, value) ->
            current.replace(placeholder, value)
        }
    }

    private fun formatNet(value: Double): String {
        val rounded = String.format(Locale.US, "%.2f", value)
            .trimEnd('0')
            .trimEnd('.')
        return rounded.replace('.', ',')
    }
}
