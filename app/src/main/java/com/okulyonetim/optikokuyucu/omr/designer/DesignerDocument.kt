package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.FiducialSpec
import com.okulyonetim.optikokuyucu.omr.template.StandardOmrTemplate
import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint
import com.okulyonetim.optikokuyucu.omr.template.TemplateRect
import com.okulyonetim.optikokuyucu.omr.template.TemplateSize

enum class DesignerPaperSize(val displayName: String) {
    A3("A3"), A4("A4"), A5("A5"), A6("A6"), A7("A7"), LETTER("Letter"), CUSTOM("Özel")
}

enum class DesignerPageOrientation(val displayName: String) {
    PORTRAIT("Dikey"), LANDSCAPE("Yatay")
}

enum class DesignerExamMode(val displayName: String) {
    UNSPECIFIED("Belirtilmedi"), SINGLE_LESSON("Tek Ders Sınavı"), MULTI_LESSON("Çoklu Ders Sınavı")
}

enum class DesignerExamPreset(val displayName: String) {
    CUSTOM("Özel"), LGS("LGS"), TYT("TYT"), AYT("AYT"), YDT("YDT"), ALES("ALES"),
    DGS("DGS"), KPSS("KPSS"), TUS("TUS"), SCHOLARSHIP("Bursluluk")
}

data class DesignerAnswerAppearance(
    val bubbleOutlineWidth: Double = 1.2,
    val choiceLabelScale: Double = 0.82,
    val questionNumberScale: Double = 0.92,
    val questionNumberDistanceInRadii: Double = 2.0
) {
    init {
        require(bubbleOutlineWidth in 0.5..4.0)
        require(choiceLabelScale in 0.45..1.4)
        require(questionNumberScale in 0.45..1.6)
        require(questionNumberDistanceInRadii in 1.2..4.0)
    }
}

data class DesignerFormSpec(
    val paperSize: DesignerPaperSize = DesignerPaperSize.A4,
    val orientation: DesignerPageOrientation = DesignerPageOrientation.PORTRAIT,
    val examMode: DesignerExamMode = DesignerExamMode.UNSPECIFIED,
    val examPreset: DesignerExamPreset = DesignerExamPreset.CUSTOM,
    val answerAppearance: DesignerAnswerAppearance = DesignerAnswerAppearance()
) {
    companion object {
        fun forSpace(space: TemplateSize): DesignerFormSpec = DesignerFormSpec(
            orientation = if (space.width <= space.height) DesignerPageOrientation.PORTRAIT else DesignerPageOrientation.LANDSCAPE
        )
    }
}

data class DesignerDocument(
    val id: String,
    val version: Int,
    val name: String,
    val space: TemplateSize = StandardOmrTemplate.DEFAULT_SPACE,
    val fiducials: List<FiducialSpec> = StandardOmrTemplate.DEFAULT.fiducials,
    val components: List<DesignerOmrComponent> = emptyList(),
    val visualElements: List<DesignerVisualElement> = emptyList(),
    val formSpec: DesignerFormSpec = DesignerFormSpec.forSpace(space)
) {
    init {
        require(id.isNotBlank())
        require(version > 0)
        require(name.isNotBlank())
        require(space.width > 0.0 && space.height > 0.0)
        require(components.map { it.id }.toSet().size == components.size) { "Designer component ids must be unique." }
        require(visualElements.map { it.id }.toSet().size == visualElements.size) { "Designer visual element ids must be unique." }
    }
}

sealed interface DesignerOmrComponent { val id: String }

enum class QuestionGroupOrientation(val displayName: String) { VERTICAL("Dikey"), HORIZONTAL("Yatay") }

enum class DesignerTextAlignment { START, CENTER, END }

enum class DesignerTextBinding(val displayName: String) {
    STATIC("Serbest Metin"),
    STUDENT_NAME("Öğrenci Adı Soyadı"),
    CLASS_NAME("Sınıfı"),
    STUDENT_NUMBER("Öğrenci Numarası"),
    EXAM_NAME("Sınav Adı"),
    SCHOOL_NAME("Okul Adı")
}

data class QuestionGroupComponent(
    override val id: String,
    val startQuestion: Int,
    val questionCount: Int,
    val choices: List<String>,
    val columns: Int,
    val firstChoiceX: Double,
    val topY: Double,
    val bubbleRadius: Double,
    val choiceGap: Double,
    val rowGap: Double,
    val columnGap: Double,
    val questionIdPrefix: String = "",
    val orientation: QuestionGroupOrientation = QuestionGroupOrientation.VERTICAL,
    val label: String = "Ders",
    val showLabel: Boolean = true,
    val labelAlignment: DesignerTextAlignment = DesignerTextAlignment.START,
    val labelFontSize: Double = bubbleRadius * 1.15,
    val labelBold: Boolean = true
) : DesignerOmrComponent {
    init {
        require(id.isNotBlank())
        require(startQuestion > 0)
        require(questionCount > 0)
        require(choices.size >= 2)
        require(choices.all { it.isNotBlank() })
        require(choices.toSet().size == choices.size)
        require(columns > 0 && columns <= questionCount)
        require(bubbleRadius > 0.0)
        require(choiceGap > 0.0 && rowGap > 0.0 && columnGap > 0.0)
        require('\n' !in questionIdPrefix && '\r' !in questionIdPrefix)
        require('\n' !in label && '\r' !in label)
        require(labelFontSize in 4.0..72.0)
    }
}

enum class NumericGridOrientation { DIGITS_HORIZONTAL, DIGITS_VERTICAL }

data class NumericGridComponent(
    override val id: String,
    val digits: Int,
    val startX: Double,
    val topY: Double,
    val bubbleRadius: Double,
    val columnGap: Double,
    val rowGap: Double,
    val values: List<String> = (0..9).map { it.toString() },
    val orientation: NumericGridOrientation = NumericGridOrientation.DIGITS_HORIZONTAL,
    val label: String = "Numara",
    val showLabel: Boolean = true,
    val labelAlignment: DesignerTextAlignment = DesignerTextAlignment.START,
    val labelFontSize: Double = bubbleRadius * 1.15,
    val labelBold: Boolean = true
) : DesignerOmrComponent {
    init {
        require(id.isNotBlank())
        require(digits > 0)
        require(values.size >= 2)
        require(values.all { it.isNotBlank() })
        require(values.toSet().size == values.size)
        require(bubbleRadius > 0.0)
        require(columnGap > 0.0 && rowGap > 0.0)
        require('\n' !in label && '\r' !in label)
        require(labelFontSize in 4.0..72.0)
    }
}

enum class ChoiceAxis { HORIZONTAL, VERTICAL }

data class SingleChoiceComponent(
    override val id: String,
    val choices: List<String>,
    val start: TemplatePoint,
    val bubbleRadius: Double,
    val gap: Double,
    val axis: ChoiceAxis = ChoiceAxis.HORIZONTAL,
    val label: String = "Kitapçık Türü",
    val showLabel: Boolean = true,
    val labelAlignment: DesignerTextAlignment = DesignerTextAlignment.START,
    val labelFontSize: Double = bubbleRadius * 1.15,
    val labelBold: Boolean = true
) : DesignerOmrComponent {
    init {
        require(id.isNotBlank())
        require(choices.size >= 2)
        require(choices.all { it.isNotBlank() })
        require(choices.toSet().size == choices.size)
        require(bubbleRadius > 0.0)
        require(gap > 0.0)
        require('\n' !in label && '\r' !in label)
        require(labelFontSize in 4.0..72.0)
    }
}

sealed interface DesignerVisualElement { val id: String; val locked: Boolean }

data class DesignerTextElement(
    override val id: String,
    val bounds: TemplateRect,
    val text: String,
    val fontSize: Double,
    val alignment: DesignerTextAlignment = DesignerTextAlignment.START,
    val bold: Boolean = false,
    override val locked: Boolean = false,
    val binding: DesignerTextBinding = DesignerTextBinding.STATIC
) : DesignerVisualElement {
    init {
        require(id.isNotBlank())
        require(text.isNotEmpty())
        require(fontSize > 0.0)
    }
}

class DesignerImageData(
    val mimeType: String,
    val pixelWidth: Int,
    val pixelHeight: Int,
    bytes: ByteArray
) {
    private val payload: ByteArray = bytes.copyOf()
    init {
        require(mimeType.startsWith("image/") && mimeType.length <= 64)
        require(pixelWidth in 1..10_000 && pixelHeight in 1..10_000)
        require(payload.isNotEmpty())
        require(payload.size <= MAX_BYTES) { "Embedded designer image is too large." }
    }
    val byteSize: Int get() = payload.size
    fun copyBytes(): ByteArray = payload.copyOf()
    override fun equals(other: Any?): Boolean = other is DesignerImageData && mimeType == other.mimeType && pixelWidth == other.pixelWidth && pixelHeight == other.pixelHeight && payload.contentEquals(other.payload)
    override fun hashCode(): Int {
        var result = mimeType.hashCode(); result = 31 * result + pixelWidth; result = 31 * result + pixelHeight; result = 31 * result + payload.contentHashCode(); return result
    }
    override fun toString(): String = "DesignerImageData(mimeType=$mimeType, pixelWidth=$pixelWidth, pixelHeight=$pixelHeight, byteSize=$byteSize)"
    companion object { const val MAX_BYTES: Int = 3_000_000 }
}

data class DesignerImageElement(
    override val id: String,
    val bounds: TemplateRect,
    val image: DesignerImageData,
    override val locked: Boolean = false
) : DesignerVisualElement {
    init { require(id.isNotBlank()); require(bounds.width > 0.0 && bounds.height > 0.0) }
}

data class DesignerBoxElement(
    override val id: String,
    val bounds: TemplateRect,
    val strokeWidth: Double,
    override val locked: Boolean = false
) : DesignerVisualElement {
    init { require(id.isNotBlank()); require(strokeWidth > 0.0) }
}

data class DesignerLineElement(
    override val id: String,
    val start: TemplatePoint,
    val end: TemplatePoint,
    val strokeWidth: Double,
    override val locked: Boolean = false
) : DesignerVisualElement {
    init { require(id.isNotBlank()); require(strokeWidth > 0.0) }
}
