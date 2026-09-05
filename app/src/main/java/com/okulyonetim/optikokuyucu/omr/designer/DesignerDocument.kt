package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.FiducialSpec
import com.okulyonetim.optikokuyucu.omr.template.StandardOmrTemplate
import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint
import com.okulyonetim.optikokuyucu.omr.template.TemplateRect
import com.okulyonetim.optikokuyucu.omr.template.TemplateSize

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
    val visualElements: List<DesignerVisualElement> = emptyList()
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
    val columnGap: Double
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
    }
}

data class NumericGridComponent(
    override val id: String,
    val digits: Int,
    val startX: Double,
    val topY: Double,
    val bubbleRadius: Double,
    val columnGap: Double,
    val rowGap: Double,
    val values: List<String> = (0..9).map { it.toString() }
) : DesignerOmrComponent {
    init {
        require(id.isNotBlank())
        require(digits > 0)
        require(values.size >= 2)
        require(values.all { it.isNotBlank() })
        require(values.toSet().size == values.size)
        require(bubbleRadius > 0.0)
        require(columnGap > 0.0 && rowGap > 0.0)
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
