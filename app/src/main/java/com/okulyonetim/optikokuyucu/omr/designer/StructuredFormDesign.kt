package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.FiducialCorner
import com.okulyonetim.optikokuyucu.omr.template.FiducialSpec
import com.okulyonetim.optikokuyucu.omr.template.StandardOmrTemplate
import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint
import com.okulyonetim.optikokuyucu.omr.template.TemplateRect
import com.okulyonetim.optikokuyucu.omr.template.TemplateSize
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** User-facing paper choice. Recognition remains canonical; this controls aspect/orientation. */
enum class StructuredPaperSize(val displayName: String) {
    A4("A4"),
    A5("A5"),
    LETTER("Letter")
}

enum class StructuredOrientation(val displayName: String) {
    PORTRAIT("Dikey"),
    LANDSCAPE("Yatay")
}

data class StructuredInfoField(
    val id: String,
    val label: String,
    val enabled: Boolean = true
) {
    init {
        require(id.isNotBlank())
        require(label.isNotBlank())
    }
}

data class StructuredLesson(
    val id: String,
    val name: String,
    val questionCount: Int,
    val choices: List<String> = listOf("A", "B", "C", "D")
) {
    init {
        require(id.isNotBlank())
        require(name.isNotBlank())
        require(questionCount > 0)
        require(choices.size in 2..6)
        require(choices.all { it.isNotBlank() })
        require(choices.toSet().size == choices.size)
    }
}

data class StructuredTextStyle(
    val fontSize: Double = 20.0,
    val bold: Boolean = false,
    val alignment: DesignerTextAlignment = DesignerTextAlignment.START
) {
    init {
        require(fontSize in 8.0..72.0)
    }
}

data class StructuredFormConfig(
    val id: String = "structured-form",
    val version: Int = 1,
    val name: String = "Yeni Optik Form",
    val title: String = "DENEME SINAVI",
    val subtitle: String = "",
    val paperSize: StructuredPaperSize = StructuredPaperSize.A4,
    val orientation: StructuredOrientation = StructuredOrientation.PORTRAIT,
    val bookletTypeCount: Int = 2,
    val studentNumberDigits: Int = 6,
    val infoFields: List<StructuredInfoField> = defaultInfoFields(),
    val infoTextStyle: StructuredTextStyle = StructuredTextStyle(fontSize = 16.0),
    val titleTextStyle: StructuredTextStyle = StructuredTextStyle(
        fontSize = 28.0,
        bold = true,
        alignment = DesignerTextAlignment.CENTER
    ),
    val lessons: List<StructuredLesson> = listOf(
        StructuredLesson("turkce", "Türkçe", 20),
        StructuredLesson("matematik", "Matematik", 20),
        StructuredLesson("fen", "Fen Bilimleri", 20)
    )
) {
    init {
        require(id.isNotBlank())
        require(version > 0)
        require(name.isNotBlank())
        require(title.isNotBlank())
        require(bookletTypeCount in 2..8)
        require(studentNumberDigits in 1..12)
        require(infoFields.map { it.id }.toSet().size == infoFields.size)
        require(lessons.isNotEmpty())
        require(lessons.size <= 12)
        require(lessons.map { it.id }.toSet().size == lessons.size)
        require(lessons.sumOf { it.questionCount } <= 300)
    }

    companion object {
        fun defaultInfoFields(): List<StructuredInfoField> = listOf(
            StructuredInfoField("studentName", "AD SOYAD"),
            StructuredInfoField("studentClass", "SINIF"),
            StructuredInfoField("school", "OKUL"),
            StructuredInfoField("numberText", "NUMARA")
        )
    }
}

data class StructuredFormBuildResult(
    val document: DesignerDocument,
    /** Recognition-critical rectangles shown as pink protected areas in the structured preview. */
    val protectedZones: List<TemplateRect>
)

/**
 * Deterministic auto-layout used by the friendly form designer.
 * It never places user text inside OMR geometry; the ordinary readability analyzer remains the
 * final save gate as a second, independent safety layer.
 */
object StructuredFormDocumentFactory {
    fun build(config: StructuredFormConfig): StructuredFormBuildResult {
        val space = canonicalSpace(config.orientation)
        val fiducials = fiducialsFor(space)
        val margin = if (config.orientation == StructuredOrientation.PORTRAIT) 92.0 else 100.0
        val contentLeft = margin
        val contentRight = space.width - margin
        val contentWidth = contentRight - contentLeft
        val visuals = mutableListOf<DesignerVisualElement>()
        val components = mutableListOf<DesignerOmrComponent>()

        val titleWidth = min(720.0, contentWidth * 0.72)
        visuals += DesignerTextElement(
            id = "structured:title",
            bounds = TemplateRect(
                left = (space.width - titleWidth) / 2.0,
                top = 108.0,
                width = titleWidth,
                height = 44.0
            ),
            text = config.title,
            fontSize = config.titleTextStyle.fontSize,
            alignment = config.titleTextStyle.alignment,
            bold = config.titleTextStyle.bold,
            locked = true
        )
        if (config.subtitle.isNotBlank()) {
            visuals += DesignerTextElement(
                id = "structured:subtitle",
                bounds = TemplateRect(
                    left = (space.width - titleWidth) / 2.0,
                    top = 150.0,
                    width = titleWidth,
                    height = 30.0
                ),
                text = config.subtitle,
                fontSize = max(10.0, config.titleTextStyle.fontSize * 0.56),
                alignment = DesignerTextAlignment.CENTER,
                bold = false,
                locked = true
            )
        }

        val enabledInfo = config.infoFields.filter { it.enabled }
        if (enabledInfo.isNotEmpty()) {
            val gap = 12.0
            val width = (contentWidth - gap * (enabledInfo.size - 1)) / enabledInfo.size
            enabledInfo.forEachIndexed { index, field ->
                val left = contentLeft + index * (width + gap)
                visuals += DesignerTextElement(
                    id = "structured:info-label:${field.id}",
                    bounds = TemplateRect(left, 190.0, width, 24.0),
                    text = field.label,
                    fontSize = config.infoTextStyle.fontSize,
                    alignment = config.infoTextStyle.alignment,
                    bold = config.infoTextStyle.bold,
                    locked = true
                )
                visuals += DesignerBoxElement(
                    id = "structured:info-box:${field.id}",
                    bounds = TemplateRect(left, 218.0, width, 46.0),
                    strokeWidth = 1.4,
                    locked = true
                )
            }
        }

        val metaTop = 315.0
        val metaMarkY = metaTop + 62.0
        val bubbleRadius = if (config.orientation == StructuredOrientation.PORTRAIT) 10.5 else 9.5
        val bookletChoices = (0 until config.bookletTypeCount).map { ('A'.code + it).toChar().toString() }
        components += SingleChoiceComponent(
            id = "booklet",
            choices = bookletChoices,
            start = TemplatePoint(contentLeft + 30.0, metaMarkY),
            bubbleRadius = bubbleRadius + 1.0,
            gap = 46.0,
            axis = ChoiceAxis.HORIZONTAL
        )
        visuals += DesignerTextElement(
            id = "structured:booklet-title",
            bounds = TemplateRect(contentLeft, metaTop, min(260.0, contentWidth * 0.28), 24.0),
            text = "Kitapçık Türü",
            fontSize = 18.0,
            alignment = DesignerTextAlignment.CENTER,
            bold = true,
            locked = true
        )

        val numberStartX = contentLeft + min(320.0, contentWidth * 0.34)
        val digitGap = if (config.orientation == StructuredOrientation.PORTRAIT) 30.0 else 28.0
        val valueGap = if (config.orientation == StructuredOrientation.PORTRAIT) 30.0 else 28.0
        components += NumericGridComponent(
            id = "studentNumber",
            digits = config.studentNumberDigits,
            startX = numberStartX,
            topY = metaMarkY,
            bubbleRadius = bubbleRadius,
            columnGap = digitGap,
            rowGap = valueGap,
            orientation = NumericGridOrientation.DIGITS_VERTICAL
        )
        visuals += DesignerTextElement(
            id = "structured:number-title",
            bounds = TemplateRect(numberStartX, metaTop, min(330.0, contentRight - numberStartX), 24.0),
            text = "Numara",
            fontSize = 18.0,
            alignment = DesignerTextAlignment.CENTER,
            bold = true,
            locked = true
        )

        val numberBottom = metaMarkY + (config.studentNumberDigits - 1) * digitGap + bubbleRadius
        val lessonsTop = max(
            if (config.orientation == StructuredOrientation.PORTRAIT) 560.0 else 500.0,
            numberBottom + 74.0
        )
        val lessonsBottom = space.height - 112.0
        require(lessonsBottom - lessonsTop >= 260.0) {
            "Seçilen öğrenci numarası hane sayısı bu kağıt yönünde ders alanına yer bırakmıyor."
        }

        val lessonGridColumns = lessonGridColumns(config.lessons.size, config.orientation)
        val lessonGridRows = ceil(config.lessons.size.toDouble() / lessonGridColumns.toDouble()).toInt()
        val lessonGap = 24.0
        val blockWidth = (contentWidth - lessonGap * (lessonGridColumns - 1)) / lessonGridColumns
        val availableHeight = lessonsBottom - lessonsTop
        val blockHeight = (availableHeight - lessonGap * (lessonGridRows - 1)) / lessonGridRows

        config.lessons.forEachIndexed { index, lesson ->
            val gridColumn = index % lessonGridColumns
            val gridRow = index / lessonGridColumns
            val blockLeft = contentLeft + gridColumn * (blockWidth + lessonGap)
            val blockTop = lessonsTop + gridRow * (blockHeight + lessonGap)

            val rowGap = when {
                blockHeight < 310.0 -> 26.0
                blockHeight < 390.0 -> 29.0
                else -> 31.0
            }
            val usableQuestionHeight = blockHeight - 52.0
            val maxRows = max(1, floor(usableQuestionHeight / rowGap).toInt())
            val internalColumns = ceil(lesson.questionCount.toDouble() / maxRows.toDouble())
                .toInt()
                .coerceIn(1, lesson.questionCount)
            val internalColumnGap = blockWidth / internalColumns.toDouble()

            // Compact LGS-style sheets can place many lessons side-by-side. Bubble size and choice
            // spacing adapt to the actual per-question column width while remaining non-overlapping.
            val lessonBubbleRadius = min(
                9.5,
                max(6.2, internalColumnGap / (lesson.choices.size * 3.0))
            )
            val questionNumberRoom = max(14.0, lessonBubbleRadius * 2.0)
            val maxChoiceGap = if (lesson.choices.size == 1) {
                0.0
            } else {
                (internalColumnGap - questionNumberRoom - lessonBubbleRadius * 2.0) /
                    (lesson.choices.size - 1).toDouble()
            }
            val minChoiceGap = lessonBubbleRadius * 2.0 + 1.5
            require(maxChoiceGap >= minChoiceGap) {
                "${lesson.name} için ${lesson.questionCount} soru seçilen kağıt/yön yerleşimine sığmıyor."
            }
            val choiceGap = min(30.0, maxChoiceGap)
            val firstChoiceInset = questionNumberRoom + lessonBubbleRadius

            visuals += DesignerTextElement(
                id = "structured:lesson-title:${lesson.id}",
                bounds = TemplateRect(blockLeft, blockTop, blockWidth, 28.0),
                text = lesson.name,
                fontSize = min(17.0, max(11.0, blockWidth / 14.0)),
                alignment = DesignerTextAlignment.CENTER,
                bold = true,
                locked = true
            )
            components += QuestionGroupComponent(
                id = "lesson:${lesson.id}",
                startQuestion = 1,
                questionCount = lesson.questionCount,
                choices = lesson.choices,
                columns = internalColumns,
                firstChoiceX = blockLeft + firstChoiceInset,
                topY = blockTop + 52.0,
                bubbleRadius = lessonBubbleRadius,
                choiceGap = choiceGap,
                rowGap = rowGap,
                columnGap = internalColumnGap,
                questionIdPrefix = lesson.id
            )
        }

        val document = DesignerDocument(
            id = config.id,
            version = config.version,
            name = config.name,
            space = space,
            fiducials = fiducials,
            components = components,
            visualElements = visuals
        )
        val template = DesignerTemplateCompiler.compile(document)
        val readability = TemplateReadabilityAnalyzer.analyze(document, template)
        require(readability.canSave) {
            "Otomatik yerleşim güvenli değil: ${readability.issues.firstOrNull()?.message ?: "bilinmeyen hata"}"
        }

        return StructuredFormBuildResult(
            document = document,
            protectedZones = buildProtectedZones(document)
        )
    }

    fun canonicalSpace(orientation: StructuredOrientation): TemplateSize =
        if (orientation == StructuredOrientation.PORTRAIT) {
            StandardOmrTemplate.DEFAULT_SPACE
        } else {
            TemplateSize(
                width = StandardOmrTemplate.DEFAULT_SPACE.height,
                height = StandardOmrTemplate.DEFAULT_SPACE.width
            )
        }

    private fun lessonGridColumns(count: Int, orientation: StructuredOrientation): Int =
        if (orientation == StructuredOrientation.LANDSCAPE) {
            when {
                count <= 2 -> count
                count <= 4 -> 4
                else -> min(6, count)
            }
        } else {
            when {
                count <= 2 -> count
                count <= 4 -> 2
                else -> 3
            }
        }.coerceAtLeast(1)

    private fun fiducialsFor(space: TemplateSize): List<FiducialSpec> {
        val markerSize = 58.0
        val inset = 38.0
        return listOf(
            FiducialSpec(
                FiducialCorner.TOP_LEFT,
                11,
                TemplateRect(inset, inset, markerSize, markerSize)
            ),
            FiducialSpec(
                FiducialCorner.TOP_RIGHT,
                22,
                TemplateRect(space.width - inset - markerSize, inset, markerSize, markerSize)
            ),
            FiducialSpec(
                FiducialCorner.BOTTOM_RIGHT,
                33,
                TemplateRect(
                    space.width - inset - markerSize,
                    space.height - inset - markerSize,
                    markerSize,
                    markerSize
                )
            ),
            FiducialSpec(
                FiducialCorner.BOTTOM_LEFT,
                44,
                TemplateRect(inset, space.height - inset - markerSize, markerSize, markerSize)
            )
        )
    }

    private fun buildProtectedZones(document: DesignerDocument): List<TemplateRect> {
        val basePadding = min(document.space.width, document.space.height) * 0.018
        val componentZones = document.components.map { component ->
            expand(DesignerComponentGeometry.bounds(component), basePadding, document.space)
        }
        val markerZones = document.fiducials.map { expand(it.bounds, basePadding, document.space) }
        return componentZones + markerZones
    }

    private fun expand(rect: TemplateRect, padding: Double, space: TemplateSize): TemplateRect {
        val left = max(0.0, rect.left - padding)
        val top = max(0.0, rect.top - padding)
        val right = min(space.width, rect.right + padding)
        val bottom = min(space.height, rect.bottom + padding)
        return TemplateRect(left, top, right - left, bottom - top)
    }
}
