package com.okulyonetim.optikokuyucu.omr.designer

/** Ready-made starting points inspired by common school exam forms. Every preset remains editable. */
data class StructuredFormPreset(
    val key: String,
    val displayName: String,
    val description: String,
    val baseConfig: StructuredFormConfig
) {
    fun instantiate(nowMillis: Long = System.currentTimeMillis()): StructuredFormConfig =
        baseConfig.copy(
            id = "$key-$nowMillis",
            version = 1
        )
}

object StructuredFormPresets {
    fun all(): List<StructuredFormPreset> = listOf(
        standard20(),
        standard30(),
        lgsMini(),
        lgs(),
        bursluluk()
    )

    fun standard20(): StructuredFormPreset = StructuredFormPreset(
        key = "standard-20-abcd",
        displayName = "Standart 20 Soru",
        description = "20 soru · A/B/C/D · tek cevap bölümü",
        baseConfig = StructuredFormConfig(
            id = "standard-20-abcd",
            name = "Standart 20 Soru 4 Seçenek",
            title = "Standart 20 Soru 4 Seçenek",
            bookletTypeCount = 4,
            studentNumberDigits = 4,
            lessons = listOf(
                StructuredLesson("cevaplar", "Cevaplar", 20)
            )
        )
    )

    fun standard30(): StructuredFormPreset = StructuredFormPreset(
        key = "standard-30-abc",
        displayName = "Standart 30 Soru",
        description = "30 soru · A/B/C · tek cevap bölümü",
        baseConfig = StructuredFormConfig(
            id = "standard-30-abc",
            name = "Standart 30 Soru 3 Seçenek",
            title = "Standart 30 Soru 3 Seçenek",
            bookletTypeCount = 4,
            studentNumberDigits = 4,
            lessons = listOf(
                StructuredLesson(
                    id = "cevaplar",
                    name = "Cevaplar",
                    questionCount = 30,
                    choices = listOf("A", "B", "C")
                )
            )
        )
    )

    fun lgsMini(): StructuredFormPreset = StructuredFormPreset(
        key = "lgs-mini",
        displayName = "LGS Mini",
        description = "6 ders · yatay A4 · LGS ders dağılımı",
        baseConfig = StructuredFormConfig(
            id = "lgs-mini",
            name = "LGS Mini",
            title = "LGS Mini",
            orientation = StructuredOrientation.LANDSCAPE,
            bookletTypeCount = 4,
            studentNumberDigits = 4,
            lessons = lgsLessons()
        )
    )

    fun lgs(): StructuredFormPreset = StructuredFormPreset(
        key = "lgs",
        displayName = "LGS",
        description = "6 ders · dikey A4 · 90 soru",
        baseConfig = StructuredFormConfig(
            id = "lgs",
            name = "LGS",
            title = "LGS",
            orientation = StructuredOrientation.PORTRAIT,
            bookletTypeCount = 4,
            studentNumberDigits = 6,
            lessons = lgsLessons()
        )
    )

    fun bursluluk(): StructuredFormPreset = StructuredFormPreset(
        key = "bursluluk",
        displayName = "Bursluluk Sınavı",
        description = "Türkçe, Matematik, Fen, Sosyal · 25'er soru",
        baseConfig = StructuredFormConfig(
            id = "bursluluk",
            name = "Bursluluk Sınavı",
            title = "Bursluluk Sınavı",
            bookletTypeCount = 4,
            studentNumberDigits = 4,
            lessons = listOf(
                StructuredLesson("turkce", "Türkçe", 25),
                StructuredLesson("matematik", "Matematik", 25),
                StructuredLesson("fen", "Fen Bilimleri", 25),
                StructuredLesson("sosyal", "Sosyal Bilgiler", 25)
            )
        )
    )

    private fun lgsLessons(): List<StructuredLesson> = listOf(
        StructuredLesson("turkce", "Türkçe", 20),
        StructuredLesson("inkilap", "İnkılap Tarihi", 10),
        StructuredLesson("din", "Din Kültürü", 10),
        StructuredLesson("yabanci", "Yabancı Dil", 10),
        StructuredLesson("matematik", "Matematik", 20),
        StructuredLesson("fen", "Fen Bilimleri", 20)
    )
}
