package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.FiducialSpec
import com.okulyonetim.optikokuyucu.omr.template.StandardOmrTemplate
import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint
import com.okulyonetim.optikokuyucu.omr.template.TemplateRect
import com.okulyonetim.optikokuyucu.omr.template.TemplateSize

enum class DesignerPaperSize(val displayName: String) {
    A3("A3"),
    A4("A4"),
    A5("A5"),
    A6("A6"),
    A7("A7"),
    LETTER("Letter"),
    CUSTOM("Özel")
}

enum class DesignerPageOrientation(val displayName: String) {
    PORTRAIT("Dikey"),
    LANDSCAPE("Yatay")
}

enum class DesignerExamMode(val displayName: String) {
    UNSPECIFIED("Belirtilmedi"),
    SINGLE_LESSON("Tek Ders Sınavı"),
    MULTI_LESSON("Çoklu Ders Sınavı")
}

enum class DesignerExamPreset(val displayName: String) {
    CUSTOM("Özel"),
    LGS("LGS"),
    TYT("TYT"),
    AYT("AYT"),
    YDT("YDT"),
    ALES("ALES"),
    DGS("DGS"),
    KPSS("KPSS"),
    TUS("TUS"),
    SCHOLARSHIP("Bursluluk")
}

/**
 * Printed answer-mark appearance shared by editor preview and PDF rendering.
 * Choice letters are intentionally centered inside outlined bubbles and question numbers stay
 * immediately before the first bubble, matching the compact reference layout used by the app.
 */
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

/**
 * User-facing form identity stored with the same source document that compiles to the OMR reader.
 * This prevents page/exam metadata from living in a parallel editor-only model.
 */
data class DesignerFormSpec(
    val paperSize: DesignerPaperSize = DesignerPaperSize.A4,
    val orientation: DesignerPageOrientation = DesignerPageOrientation.PORTRAIT,
    val examMode: DesignerExamMode = DesignerExamMode.UNSPECIFIED,
    val examPreset: DesignerExamPreset = DesignerExamPreset.CUSTOM,
    val answerAppearance: DesignerAnswerAppearance = DesignerAnswerAppearance()
) {
    companion object {
        fun forSpace(space: TemplateSize): DesignerFormSpec = DesignerFormSpec(
            orientation = if (space.width <= space.height) {
                DesignerPageOrientation.PORTRAIT
            } else {
                DesignerPageOrientation.LANDSCAPE
            }
        )
    }
}

/**
 * Editable source document for the form designer.
 *
 * The editor stores compact parametric OMR components. Recognition still consumes a compiled
 * [com.okulyonetim.optikokuyucu.omr.template.OmrTemplate], so editor and reader share the exact
 * same canonical coordinate space without storing hundreds of hand-authored bubble coordinates.
 */
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
        require(components.map { it.id }.toSet().size == components.size) {
            "Designer component ids must be unique."
        }
        require(visualElements.map { it.id }.toSet().size == visualElements.size) {
            "Designer visual element ids must be unique."
        }
    }
}

sealed interface DesignerOmrComponent {
    val id: String
}

enum class QuestionGroupOrientation(val displayName: String) {
    /** Questions run top-to-bottom in each block; choices run left-to-right. */
    VERTICAL("Dikey"),

    /** Questions run left-to-right in each block; choices run top-to-bottom. */
    HORIZONTAL("Yatay")
}

data class QuestionGroupComponent(
    override val id: String,
    val startQuestion: Int,
    val questionCount: Int,
    val choices: List<String>,
    /** Canonical block/column count. Questions-per-block is derived from this and questionCount. */
    val columns: Int,
    val firstChoiceX: Double,
    val topY: Double,
    val bubbleRadius: Double,
    val choiceGap: Double,
    /** Gap between questions inside one block, on the axis selected by [orientation]. */
    val rowGap: Double,
    /** Gap between blocks, perpendicular to the question-flow axis. */
    val columnGap: Double,
    /**
     * Optional stable internal prefix. Structured multi-course forms use it so every lesson can
     * display question numbers starting from 1 while recognition/scoring ids stay globally unique.
     */
    val questionIdPrefix: String = "",
    val orientation: QuestionGroupOrientation = QuestionGroupOrientation.VERTICAL,
    /** User-visible course/title printed with this answer field. Recognition ignores this text. */
    val label: String = "Ders",
    val showLabel: Boolean = true
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
    }
}

enum class NumericGridOrientation {
    /** Digit positions run left-to-right; selectable values run top-to-bottom. */
    DIGITS_HORIZONTAL,

    /** Digit positions run top-to-bottom; selectable values run left-to-right. */
    DIGITS_VERTICAL
}

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
    /** User-visible title printed with this number field. Recognition ignores this text. */
    val label: String = "Numara",
    val showLabel: Boolean = true
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
    }
}

enum class ChoiceAxis {
    HORIZONTAL,
    VERTICAL
}

data class SingleChoiceComponent(
    override val id: String,
    val choices: List<String>,
    val start: TemplatePoint,
    val bubbleRadius: Double,
    val gap: Double,
    val axis: ChoiceAxis = ChoiceAxis.HORIZONTAL
) : DesignerOmrComponent {
    init {
        require(id.isNotBlank())
        require(choices.size >= 2)
        require(choices.all { it.isNotBlank() })
        require(choices.toSet().size == choices.size)
        require(bubbleRadius > 0.0)
        require(gap > 0.0)
    }
}

sealed interface DesignerVisualElement {
    val id: String
    val locked: Boolean
}

enum class DesignerTextAlignment {
    START,
    CENTER,
    END
}

data class DesignerTextElement(
    override val id: String,
    val bounds: TemplateRect,
    val text: String,
    val fontSize: Double,
    val alignment: DesignerTextAlignment = DesignerTextAlignment.START,
    val bold: Boolean = false,
    override val locked: Boolean = false
) : DesignerVisualElement {
    init {
        require(id.isNotBlank())
        require(text.isNotEmpty())
        require(fontSize > 0.0)
    }
}

data class DesignerBoxElement(
    override val id: String,
    val bounds: TemplateRect,
    val strokeWidth: Double,
    override val locked: Boolean = false
) : DesignerVisualElement {
    init {
        require(id.isNotBlank())
        require(strokeWidth > 0.0)
    }
}

data class DesignerLineElement(
    override val id: String,
    val start: TemplatePoint,
    val end: TemplatePoint,
    val strokeWidth: Double,
    override val locked: Boolean = false
) : DesignerVisualElement {
    init {
        require(id.isNotBlank())
        require(strokeWidth > 0.0)
    }
}
