package com.okulyonetim.optikokuyucu.omr.designer

import kotlin.math.ceil

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

    val answerPatternPresets: List<String> = listOf(
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
        val id = nextComponentId(document, "number")
        val suffix = id.substringAfterLast('-').toIntOrNull() ?: 1
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

    fun createAnswerArea(document: DesignerDocument): QuestionGroupComponent {
        val safe = DesignerPageGeometry.safeArea(document.space)
        val id = nextComponentId(document, "answers")
        val suffix = id.substringAfterLast('-').toIntOrNull() ?: 1
        val stagger = ((suffix - 1) % 4) * 20.0
        return QuestionGroupComponent(
            id = id,
            startQuestion = 1,
            questionCount = 20,
            choices = parseAnswerPattern("ABCD")!!,
            columns = 2,
            firstChoiceX = safe.left + 70.0 + stagger,
            topY = safe.top + 140.0 + stagger,
            bubbleRadius = 10.0,
            choiceGap = 30.0,
            rowGap = 36.0,
            columnGap = 330.0,
            questionIdPrefix = id,
            orientation = QuestionGroupOrientation.VERTICAL,
            label = "Ders",
            showLabel = true
        )
    }

    /**
     * A plain pattern such as ABCD becomes one mark per Unicode character. Comma-separated input
     * supports multi-character/special labels, e.g. "01,02,03".
     */
    fun parseNumberPattern(text: String): List<String>? = parsePattern(text, maxValues = 24)

    fun parseAnswerPattern(text: String): List<String>? = parsePattern(text, maxValues = 8)

    fun numberPatternText(values: List<String>): String = patternText(values)

    fun answerPatternText(values: List<String>): String = patternText(values)

    fun answerQuestionsPerBlock(component: QuestionGroupComponent): Int =
        ceil(component.questionCount.toDouble() / component.columns.toDouble())
            .toInt()
            .coerceAtLeast(1)

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

        return boundsIssue(document, component, "Numara")
    }

    /** Returns null when the answer field is ready to be committed to DesignerDocument. */
    fun answerAreaIssue(
        document: DesignerDocument,
        component: QuestionGroupComponent
    ): String? {
        if (component.startQuestion !in 1..9999) return "İlk soru numarası 1–9999 arasında olmalıdır."
        if (component.questionCount !in 1..250) return "Toplam soru sayısı 1–250 arasında olmalıdır."
        if (component.choices.size !in 2..8) return "Desen 2–8 benzersiz şıktan oluşmalıdır."
        if (component.columns !in 1..minOf(8, component.questionCount)) {
            return "Blok sayısı soru sayısını aşamaz ve en fazla 8 olabilir."
        }
        if (component.showLabel && component.label.isBlank()) return "Ders adı görünürken boş olamaz."
        if (component.bubbleRadius !in 6.0..25.0) return "Baloncuk boyutu 6–25 arasında olmalıdır."

        val minimumBubbleGap = component.bubbleRadius * 2.0 + 4.0
        if (component.choiceGap < minimumBubbleGap || component.rowGap < minimumBubbleGap) {
            return "Şık ve soru aralığı en az ${minimumBubbleGap.toInt()} olmalıdır."
        }
        val choiceSpan = (component.choices.size - 1) * component.choiceGap
        val minimumBlockGap = choiceSpan + component.bubbleRadius * 2.0 + 8.0
        if (component.columnGap < minimumBlockGap) {
            return "Bloklar arası boşluk en az ${minimumBlockGap.toInt()} olmalıdır."
        }

        return boundsIssue(document, component, "Cevap")
    }

    private fun parsePattern(text: String, maxValues: Int): List<String>? {
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
            it.size in 2..maxValues &&
                it.all { value -> value.isNotBlank() } &&
                it.distinct().size == it.size
        }
    }

    private fun patternText(values: List<String>): String =
        if (values.all { it.codePointCount(0, it.length) == 1 && ',' !in it }) {
            values.joinToString("")
        } else {
            values.joinToString(",")
        }

    private fun nextComponentId(document: DesignerDocument, prefix: String): String {
        val usedIds = document.components.map { it.id }.toSet()
        var suffix = 1
        var id = "$prefix-$suffix"
        while (id in usedIds) {
            suffix += 1
            id = "$prefix-$suffix"
        }
        return id
    }

    private fun boundsIssue(
        document: DesignerDocument,
        component: DesignerOmrComponent,
        label: String
    ): String? {
        val safe = DesignerPageGeometry.safeArea(document.space)
        val bounds = DesignerComponentGeometry.bounds(component)
        if (
            bounds.left < safe.left ||
            bounds.top < safe.top ||
            bounds.right > safe.right ||
            bounds.bottom > safe.bottom
        ) {
            return "$label alanı güvenli yerleşim alanının içinde kalmalıdır."
        }
        return null
    }
}
