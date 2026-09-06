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
    val choices: List<String> = listOf("A", "B", "C", "D"),
    /** 0 = automatic. Positive values force the question group to the requested column count. */
    val questionColumns: Int = 0,
    val titleAlignment: DesignerTextAlignment = DesignerTextAlignment.CENTER
) {
    init {
        require(id.isNotBlank())
        require(name.isNotBlank())
        require(questionCount > 0)
        require(choices.size in 2..6)
        require(choices.all { it.isNotBlank() })
        require(choices.toSet().size == choices.size)
        require(questionColumns in 0..8)
        require(questionColumns == 0 || questionColumns <= questionCount)
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
    /** Compact defaults intentionally use more of the canonical page than the old designer. */
    val pageMargin: Double = 60.0,
    val markerSize: Double = 48.0,
    val markerInset: Double = 24.0,
    val protectedPaddingRatio: Double = 0.012,
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
        require(pageMargin in 44.0..180.0)
        require(markerSize in 44.0..80.0)
        require(markerInset in 16.0..80.0)
        require(protectedPaddingRatio in 0.010..0.040)
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
        val fiducials = fiducialsFor(space, config.markerSize, config.markerInset)
        val margin = config.pageMargin
        val contentLeft = margin
        val contentRight = space.width - margin
        val contentWidth = contentRight - contentLeft
        val visuals = mutableListOf<DesignerVisualElement>()
        val components = mutableListOf<DesignerOmrComponent>()

        val titleTop = max(76.0, config.markerInset + config.markerSize + 8.0)
        val titleWidth = contentWidth
        visuals += DesignerTextElement(
            id = "structured:title",
            bounds = TemplateRect(
                left = contentLeft,
                top = titleTop,
                width = titleWidth,
                height = 40.0
            ),
            text = config.title,
            fontSize = config.titleTextStyle.fontSize,
            alignment = config.titleTextStyle.alignment,
            bold = config.titleTextStyle.bold,
            locked = false
        )
        if (config.subtitle.isNotBlank()) {
            visuals += DesignerTextElement(
                id = "structured:subtitle",
                bounds = TemplateRect(
                    left = contentLeft,
                    top = titleTop + 40.0,
                    width = titleWidth,
                    height = 26.0
                ),
                text = config.subtitle,
                fontSize = max(10.0, config.titleTextStyle.fontSize * 0.56),
                alignment = config.titleTextStyle.alignment,
                bold = false,
                locked = false
            )
        }

        val enabledInfo = config.infoFields.filter { it.enabled }
        val infoTop = titleTop + if (config.subtitle.isBlank()) 54.0 else 78.0
        if (enabledInfo.isNotEmpty()) {
            val gap = 9.0
            val width = (contentWidth - gap * (enabledInfo.size - 1)) / enabledInfo.size
            enabledInfo.forEachIndexed { index, field ->
                val left = contentLeft + index * (width + gap)
                visuals += DesignerTextElement(
                    id = "structured:info-label:${field.id}",
                    bounds = TemplateRect(left, infoTop, width, 22.0),
                    text = field.label,
                    fontSize = config.infoTextStyle.fontSize,
                    alignment = config.infoTextStyle.alignment,
                    bold = config.infoTextStyle.bold,
                    locked = false
                )
                visuals += DesignerBoxElement(
                    id = "structured:info-box:${field.id}",
                    bounds = TemplateRect(left, infoTop + 26.0, width, 40.0),
                    strokeWidth = 1.3,
                    locked = false
                )
            }
        }

        val metaTop = if (enabledInfo.isEmpty()) infoTop + 10.0 else infoTop + 92.0
        val metaMarkY = metaTop + 48.0
        val bubbleRadius = if (config.orientation == StructuredOrientation.PORTRAIT) 10.0 else 9.2
        val bookletChoices = (0 until config.bookletTypeCount).map { ('A'.code + it).toChar().toString() }
        components += SingleChoiceComponent(
            id = "booklet",
            choices = bookletChoices,
            start = TemplatePoint(contentLeft + 26.0, metaMarkY),
            bubbleRadius = bubbleRadius + 0.8,
            gap = 42.0,
            axis = ChoiceAxis.HORIZONTAL
        )
        visuals += DesignerTextElement(
            id = "structured:booklet-title",
            bounds = TemplateRect(contentLeft, metaTop, min(245.0, contentWidth * 0.28), 22.0),
            text = "Kitapçık Türü",
            fontSize = 17.0,
            alignment = DesignerTextAlignment.CENTER,
            bold = true,
            locked = false
        )

        val numberStartX = contentLeft + min(290.0, contentWidth * 0.32)
        val digitGap = if (config.orientation == StructuredOrientation.PORTRAIT) 28.0 else 27.0
        val valueGap = if (config.orientation == StructuredOrientation.PORTRAIT) 28.0 else 27.0
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
            bounds = TemplateRect(numberStartX, metaTop, min(330.0, contentRight - numberStartX), 22.0),
            text = "Numara",
            fontSize = 17.0,
            alignment = DesignerTextAlignment.CENTER,
            bold = true,
            locked = false
        )

        val numberBottom = metaMarkY + (config.studentNumberDigits - 1) * digitGap + bubbleRadius
        val lessonsTop = max(
            if (config.orientation == StructuredOrientation.PORTRAIT) 470.0 else 420.0,
            numberBottom + 52.0
        )
        val bottomReserved = max(config.pageMargin, config.markerInset + config.markerSize + 12.0)
        val lessonsBottom = space.height - bottomReserved
        require(lessonsBottom - lessonsTop >= 240.0) {
            "Seçilen öğrenci numarası hane sayısı bu kağıt yönünde ders alanına yer bırakmıyor."
        }

        val lessonGridColumns = lessonGridColumns(config.lessons.size, config.orientation)
        val lessonGridRows = ceil(config.lessons.size.toDouble() / lessonGridColumns.toDouble()).toInt()
        val lessonGap = 14.0
        val blockWidth = (contentWidth - lessonGap * (lessonGridColumns - 1)) / lessonGridColumns
        val availableHeight = lessonsBottom - lessonsTop
        val blockHeight = (availableHeight - lessonGap * (lessonGridRows - 1)) / lessonGridRows

        config.lessons.forEachIndexed { index, lesson ->
            val gridColumn = index % lessonGridColumns
            val gridRow = index / lessonGridColumns
            val blockLeft = contentLeft + gridColumn * (blockWidth + lessonGap)
            val blockTop = lessonsTop + gridRow * (blockHeight + lessonGap)

            val preferredRowGap = when {
                blockHeight < 300.0 -> 24.0
                blockHeight < 390.0 -> 27.0
                else -> 30.0
            }
            val usableQuestionHeight = blockHeight - 42.0
            val autoMaxRows = max(1, floor(usableQuestionHeight / preferredRowGap).toInt())
            val autoColumns = ceil(lesson.questionCount.toDouble() / autoMaxRows.toDouble())
                .toInt()
                .coerceIn(1, lesson.questionCount)
            val internalColumns = if (lesson.questionColumns == 0) {
                autoColumns
            } else {
                lesson.questionColumns.coerceAtMost(lesson.questionCount)
            }
            val internalColumnGap = blockWidth / internalColumns.toDouble()

            // Compact school sheets can use a manually selected 1..8-column question layout.
            // Bubble size and row spacing adapt while the same readability gate remains authoritative.
            val lessonBubbleRadius = min(
                9.5,
                max(6.2, internalColumnGap / (lesson.choices.size * 3.0))
            )
            val rowsPerColumn = ceil(lesson.questionCount.toDouble() / internalColumns.toDouble()).toInt()
            val minRowGap = lessonBubbleRadius * 2.0 + 3.0
            val maxRowGapByHeight = if (rowsPerColumn <= 1) {
                preferredRowGap
            } else {
                (usableQuestionHeight - lessonBubbleRadius * 2.0) / (rowsPerColumn - 1).toDouble()
            }
            require(maxRowGapByHeight >= minRowGap) {
                "${lesson.name} için ${lesson.questionCount} soru ${internalColumns} sütuna güvenli biçimde sığmıyor."
            }
            val rowGap = min(preferredRowGap, maxRowGapByHeight)

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
                bounds = TemplateRect(blockLeft, blockTop, blockWidth, 26.0),
                text = lesson.name,
                fontSize = min(17.0, max(11.0, blockWidth / 14.0)),
                alignment = lesson.titleAlignment,
                bold = true,
                locked = false
            )
            components += QuestionGroupComponent(
                id = "lesson:${lesson.id}",
                startQuestion = 1,
                questionCount = lesson.questionCount,
                choices = lesson.choices,
                columns = internalColumns,
                firstChoiceX = blockLeft + firstChoiceInset,
                topY = blockTop + 40.0,
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
            protectedZones = buildProtectedZones(document, config.protectedPaddingRatio)
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

    private fun fiducialsFor(
        space: TemplateSize,
        markerSize: Double,
        inset: Double
    ): List<FiducialSpec> = listOf(
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

    private fun buildProtectedZones(
        document: DesignerDocument,
        paddingRatio: Double
    ): List<TemplateRect> {
        val basePadding = min(document.space.width, document.space.height) * paddingRatio
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
