package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.FiducialCorner
import com.okulyonetim.optikokuyucu.omr.template.FiducialSpec
import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint
import com.okulyonetim.optikokuyucu.omr.template.TemplateRect
import com.okulyonetim.optikokuyucu.omr.template.TemplateSize
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Small dependency-free, versioned binary codec for offline designer documents.
 * Recognition templates remain compiled from this editable source model.
 */
object DesignerDocumentCodec {
    fun encode(document: DesignerDocument): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(SCHEMA_VERSION)
            out.writeUTF(document.id)
            out.writeInt(document.version)
            out.writeUTF(document.name)
            writeSize(out, document.space)

            out.writeInt(document.fiducials.size)
            document.fiducials.forEach { fiducial ->
                out.writeUTF(fiducial.corner.name)
                out.writeInt(fiducial.markerId)
                writeRect(out, fiducial.bounds)
            }

            out.writeInt(document.components.size)
            document.components.forEach { component -> writeComponent(out, component) }

            out.writeInt(document.visualElements.size)
            document.visualElements.forEach { element -> writeVisual(out, element) }

            writeFormSpec(out, document.formSpec)
        }
        return bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): DesignerDocument {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == MAGIC) { "Geçersiz optik tasarım dosyası." }
            val schema = input.readInt()
            require(schema in MIN_SUPPORTED_SCHEMA..SCHEMA_VERSION) {
                "Desteklenmeyen tasarım dosyası sürümü: $schema"
            }

            val id = input.readUTF()
            val version = input.readInt()
            val name = input.readUTF()
            val space = readSize(input)

            val fiducials = List(readSafeCount(input, MAX_FIDUCIALS)) {
                FiducialSpec(
                    corner = FiducialCorner.valueOf(input.readUTF()),
                    markerId = input.readInt(),
                    bounds = readRect(input)
                )
            }

            val components = List(readSafeCount(input, MAX_COMPONENTS)) {
                readComponent(input, schema)
            }
            val visuals = List(readSafeCount(input, MAX_VISUALS)) {
                readVisual(input, schema)
            }
            val formSpec = if (schema >= 3) {
                readFormSpec(input)
            } else {
                DesignerFormSpec.forSpace(space)
            }

            require(input.available() == 0) { "Tasarım dosyasında beklenmeyen ek veri var." }
            return DesignerDocument(
                id = id,
                version = version,
                name = name,
                space = space,
                fiducials = fiducials,
                components = components,
                visualElements = visuals,
                formSpec = formSpec
            )
        }
    }

    private fun writeComponent(out: DataOutputStream, component: DesignerOmrComponent) {
        when (component) {
            is QuestionGroupComponent -> {
                out.writeByte(TYPE_QUESTION_GROUP)
                out.writeUTF(component.id)
                out.writeInt(component.startQuestion)
                out.writeInt(component.questionCount)
                writeStrings(out, component.choices)
                out.writeInt(component.columns)
                out.writeDouble(component.firstChoiceX)
                out.writeDouble(component.topY)
                out.writeDouble(component.bubbleRadius)
                out.writeDouble(component.choiceGap)
                out.writeDouble(component.rowGap)
                out.writeDouble(component.columnGap)
                out.writeUTF(component.questionIdPrefix)
            }
            is NumericGridComponent -> {
                out.writeByte(TYPE_NUMERIC_GRID)
                out.writeUTF(component.id)
                out.writeInt(component.digits)
                out.writeDouble(component.startX)
                out.writeDouble(component.topY)
                out.writeDouble(component.bubbleRadius)
                out.writeDouble(component.columnGap)
                out.writeDouble(component.rowGap)
                writeStrings(out, component.values)
                out.writeUTF(component.orientation.name)
            }
            is SingleChoiceComponent -> {
                out.writeByte(TYPE_SINGLE_CHOICE)
                out.writeUTF(component.id)
                writeStrings(out, component.choices)
                writePoint(out, component.start)
                out.writeDouble(component.bubbleRadius)
                out.writeDouble(component.gap)
                out.writeUTF(component.axis.name)
            }
        }
    }

    private fun readComponent(input: DataInputStream, schema: Int): DesignerOmrComponent =
        when (input.readByte().toInt()) {
            TYPE_QUESTION_GROUP -> QuestionGroupComponent(
                id = input.readUTF(),
                startQuestion = input.readInt(),
                questionCount = input.readInt(),
                choices = readStrings(input),
                columns = input.readInt(),
                firstChoiceX = input.readDouble(),
                topY = input.readDouble(),
                bubbleRadius = input.readDouble(),
                choiceGap = input.readDouble(),
                rowGap = input.readDouble(),
                columnGap = input.readDouble(),
                questionIdPrefix = if (schema >= 2) input.readUTF() else ""
            )
            TYPE_NUMERIC_GRID -> NumericGridComponent(
                id = input.readUTF(),
                digits = input.readInt(),
                startX = input.readDouble(),
                topY = input.readDouble(),
                bubbleRadius = input.readDouble(),
                columnGap = input.readDouble(),
                rowGap = input.readDouble(),
                values = readStrings(input),
                orientation = if (schema >= 2) {
                    NumericGridOrientation.valueOf(input.readUTF())
                } else {
                    NumericGridOrientation.DIGITS_HORIZONTAL
                }
            )
            TYPE_SINGLE_CHOICE -> SingleChoiceComponent(
                id = input.readUTF(),
                choices = readStrings(input),
                start = readPoint(input),
                bubbleRadius = input.readDouble(),
                gap = input.readDouble(),
                axis = ChoiceAxis.valueOf(input.readUTF())
            )
            else -> error("Bilinmeyen OMR tasarım bileşeni.")
        }

    private fun writeVisual(out: DataOutputStream, element: DesignerVisualElement) {
        when (element) {
            is DesignerTextElement -> {
                out.writeByte(TYPE_TEXT)
                out.writeUTF(element.id)
                writeRect(out, element.bounds)
                out.writeUTF(element.text)
                out.writeDouble(element.fontSize)
                out.writeUTF(element.alignment.name)
                out.writeBoolean(element.locked)
                out.writeBoolean(element.bold)
            }
            is DesignerBoxElement -> {
                out.writeByte(TYPE_BOX)
                out.writeUTF(element.id)
                writeRect(out, element.bounds)
                out.writeDouble(element.strokeWidth)
                out.writeBoolean(element.locked)
            }
            is DesignerLineElement -> {
                out.writeByte(TYPE_LINE)
                out.writeUTF(element.id)
                writePoint(out, element.start)
                writePoint(out, element.end)
                out.writeDouble(element.strokeWidth)
                out.writeBoolean(element.locked)
            }
        }
    }

    private fun readVisual(input: DataInputStream, schema: Int): DesignerVisualElement =
        when (input.readByte().toInt()) {
            TYPE_TEXT -> {
                val id = input.readUTF()
                val bounds = readRect(input)
                val text = input.readUTF()
                val fontSize = input.readDouble()
                val alignment = DesignerTextAlignment.valueOf(input.readUTF())
                val locked = input.readBoolean()
                val bold = if (schema >= 2) input.readBoolean() else false
                DesignerTextElement(
                    id = id,
                    bounds = bounds,
                    text = text,
                    fontSize = fontSize,
                    alignment = alignment,
                    bold = bold,
                    locked = locked
                )
            }
            TYPE_BOX -> DesignerBoxElement(
                id = input.readUTF(),
                bounds = readRect(input),
                strokeWidth = input.readDouble(),
                locked = input.readBoolean()
            )
            TYPE_LINE -> DesignerLineElement(
                id = input.readUTF(),
                start = readPoint(input),
                end = readPoint(input),
                strokeWidth = input.readDouble(),
                locked = input.readBoolean()
            )
            else -> error("Bilinmeyen görsel tasarım bileşeni.")
        }

    private fun writeFormSpec(out: DataOutputStream, spec: DesignerFormSpec) {
        out.writeUTF(spec.paperSize.name)
        out.writeUTF(spec.orientation.name)
        out.writeUTF(spec.examMode.name)
        out.writeUTF(spec.examPreset.name)
        out.writeDouble(spec.answerAppearance.bubbleOutlineWidth)
        out.writeDouble(spec.answerAppearance.choiceLabelScale)
        out.writeDouble(spec.answerAppearance.questionNumberScale)
        out.writeDouble(spec.answerAppearance.questionNumberDistanceInRadii)
    }

    private fun readFormSpec(input: DataInputStream): DesignerFormSpec = DesignerFormSpec(
        paperSize = DesignerPaperSize.valueOf(input.readUTF()),
        orientation = DesignerPageOrientation.valueOf(input.readUTF()),
        examMode = DesignerExamMode.valueOf(input.readUTF()),
        examPreset = DesignerExamPreset.valueOf(input.readUTF()),
        answerAppearance = DesignerAnswerAppearance(
            bubbleOutlineWidth = input.readDouble(),
            choiceLabelScale = input.readDouble(),
            questionNumberScale = input.readDouble(),
            questionNumberDistanceInRadii = input.readDouble()
        )
    )

    private fun writeStrings(out: DataOutputStream, values: List<String>) {
        out.writeInt(values.size)
        values.forEach(out::writeUTF)
    }

    private fun readStrings(input: DataInputStream): List<String> =
        List(readSafeCount(input, MAX_STRING_LIST)) { input.readUTF() }

    private fun writePoint(out: DataOutputStream, point: TemplatePoint) {
        out.writeDouble(point.x)
        out.writeDouble(point.y)
    }

    private fun readPoint(input: DataInputStream): TemplatePoint =
        TemplatePoint(input.readDouble(), input.readDouble())

    private fun writeSize(out: DataOutputStream, size: TemplateSize) {
        out.writeDouble(size.width)
        out.writeDouble(size.height)
    }

    private fun readSize(input: DataInputStream): TemplateSize =
        TemplateSize(input.readDouble(), input.readDouble())

    private fun writeRect(out: DataOutputStream, rect: TemplateRect) {
        out.writeDouble(rect.left)
        out.writeDouble(rect.top)
        out.writeDouble(rect.width)
        out.writeDouble(rect.height)
    }

    private fun readRect(input: DataInputStream): TemplateRect = TemplateRect(
        left = input.readDouble(),
        top = input.readDouble(),
        width = input.readDouble(),
        height = input.readDouble()
    )

    private fun readSafeCount(input: DataInputStream, max: Int): Int {
        val count = input.readInt()
        require(count in 0..max) { "Geçersiz tasarım öğesi sayısı: $count" }
        return count
    }

    private const val MAGIC = 0x4F4D5244 // OMRD
    private const val MIN_SUPPORTED_SCHEMA = 1
    private const val SCHEMA_VERSION = 3
    private const val TYPE_QUESTION_GROUP = 1
    private const val TYPE_NUMERIC_GRID = 2
    private const val TYPE_SINGLE_CHOICE = 3
    private const val TYPE_TEXT = 11
    private const val TYPE_BOX = 12
    private const val TYPE_LINE = 13
    private const val MAX_FIDUCIALS = 32
    private const val MAX_COMPONENTS = 10_000
    private const val MAX_VISUALS = 10_000
    private const val MAX_STRING_LIST = 1_000
}
