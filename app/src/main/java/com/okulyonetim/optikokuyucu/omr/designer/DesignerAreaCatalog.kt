package com.okulyonetim.optikokuyucu.omr.designer

enum class DesignerAreaKind(val displayName: String) {
    NUMBER("Numara"),
    ANSWERS("Cevaplar"),
    DESCRIPTION("Açıklama"),
    IMAGE("Resim")
}

data class DesignerAreaSection(
    val title: String,
    val kinds: List<DesignerAreaKind>
) {
    init {
        require(title.isNotBlank())
        require(kinds.isNotEmpty())
        require(kinds.distinct().size == kinds.size)
    }
}

/**
 * Single catalog used by the unified editor when the user taps + to add a field.
 * Field-specific helpers live here as well so the main editor does not maintain a second list of
 * types, presets or default geometry.
 */
object DesignerAreaCatalog {
    val sections: List<DesignerAreaSection> = listOf(
        DesignerAreaSection(
            title = "İşaretleme Alanı",
            kinds = listOf(
                DesignerAreaKind.NUMBER,
                DesignerAreaKind.ANSWERS
            )
        ),
        DesignerAreaSection(
            title = "Bilgilendirme Alanı",
            kinds = listOf(
                DesignerAreaKind.DESCRIPTION,
                DesignerAreaKind.IMAGE
            )
        )
    )

    val allKinds: List<DesignerAreaKind> = sections.flatMap { it.kinds }

    val numberPatternPresets: List<String> = listOf(
        "0123456789",
        "AB",
        "ABC",
        "ABCD",
        "ABCDE"
    )

    init {
        require(allKinds.distinct().size == allKinds.size) {
            "An optical form area kind may appear only once in the add-area catalog."
        }
    }

    fun createNumberArea(document: DesignerDocument): NumericGridComponent {
        val safe = DesignerPageGeometry.safeArea(document.space)
        val usedIds = document.components.map { it.id }.toSet()
        var suffix = 1
        var id = "number-$suffix"
        while (id in usedIds) {
            suffix += 1
            id = "number-$suffix"
        }
        val stagger = ((suffix - 1) % 5) * 18.0
        return NumericGridComponent(
            id = id,
            digits = 6,
            startX = safe.left + 42.0 + stagger,
            topY = safe.top + 110.0 + stagger,
            bubbleRadius = 10.0,
            columnGap = 44.0,
            rowGap = 34.0,
            values = parseNumberPattern("0123456789")!!,
            orientation = NumericGridOrientation.DIGITS_HORIZONTAL,
            label = "Numara",
            showLabel = true
        )
    }

    /**
     * A plain pattern such as ABCD becomes one mark per Unicode character. Comma-separated input
     * supports multi-character/special labels, e.g. "01,02,03".
     */
    fun parseNumberPattern(text: String): List<String>? {
        val normalized = text.trim()
        if (normalized.isEmpty()) return null
        val values = if (',' in normalized) {
            normalized.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            normalized.codePoints().toArray().map { codePoint ->
                String(Character.toChars(codePoint))
            }
        }
        return values.takeIf {
            it.size in 2..24 &&
                it.all { value -> value.isNotBlank() } &&
                it.distinct().size == it.size
        }
    }

    fun numberPatternText(values: List<String>): String =
        if (values.all { it.codePointCount(0, it.length) == 1 && ',' !in it }) {
            values.joinToString("")
        } else {
            values.joinToString(",")
        }

    /** Returns null when the number field is ready to be committed to DesignerDocument. */
    fun numberAreaIssue(
        document: DesignerDocument,
        component: NumericGridComponent
    ): String? {
        if (component.digits !in 1..16) return "Hane sayısı 1–16 arasında olmalıdır."
        if (component.values.size !in 2..24) return "Desen 2–24 benzersiz değerden oluşmalıdır."
        if (component.showLabel && component.label.isBlank()) return "Etiket görünürken etiket metni boş olamaz."
        if (component.bubbleRadius !in 6.0..25.0) return "Baloncuk boyutu 6–25 arasında olmalıdır."
        val minimumGap = component.bubbleRadius * 2.0 + 4.0
        if (component.columnGap < minimumGap || component.rowGap < minimumGap) {
            return "Baloncukların çakışmaması için aralık en az ${minimumGap.toInt()} olmalıdır."
        }

        val safe = DesignerPageGeometry.safeArea(document.space)
        val bounds = DesignerComponentGeometry.bounds(component)
        if (
            bounds.left < safe.left ||
            bounds.top < safe.top ||
            bounds.right > safe.right ||
            bounds.bottom > safe.bottom
        ) {
            return "Numara alanı güvenli yerleşim alanının içinde kalmalıdır."
        }
        return null
    }
}
