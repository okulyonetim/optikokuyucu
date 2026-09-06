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
            document.components.forEach { writeComponent(out, it) }
            out.writeInt(document.visualElements.size)
            document.visualElements.forEach { writeVisual(out, it) }
            writeFormSpec(out, document.formSpec)
        }
        return bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): DesignerDocument {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == MAGIC) { "Geçersiz optik tasarım dosyası." }
            val schema = input.readInt()
            require(schema in MIN_SUPPORTED_SCHEMA..SCHEMA_VERSION) { "Desteklenmeyen tasarım dosyası sürümü: $schema" }
            val id = input.readUTF()
            val version = input.readInt()
            val name = input.readUTF()
            val space = readSize(input)
            val fiducials = List(readSafeCount(input, MAX_FIDUCIALS)) {
                FiducialSpec(FiducialCorner.valueOf(input.readUTF()), input.readInt(), readRect(input))
            }
            val components = List(readSafeCount(input, MAX_COMPONENTS)) { readComponent(input, schema) }
            val visuals = List(readSafeCount(input, MAX_VISUALS)) { readVisual(input, schema) }
            val formSpec = if (schema >= 3) readFormSpec(input) else DesignerFormSpec.forSpace(space)
            require(input.available() == 0) { "Tasarım dosyasında beklenmeyen ek veri var." }
            return DesignerDocument(id, version, name, space, fiducials, components, visuals, formSpec)
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
                out.writeUTF(component.orientation.name)
                out.writeUTF(component.label)
                out.writeBoolean(component.showLabel)
                out.writeUTF(component.labelAlignment.name)
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
                out.writeUTF(component.label)
                out.writeBoolean(component.showLabel)
                out.writeUTF(component.labelAlignment.name)
            }
            is SingleChoiceComponent -> {
                out.writeByte(TYPE_SINGLE_CHOICE)
                out.writeUTF(component.id)
                writeStrings(out, component.choices)
                writePoint(out, component.start)
                out.writeDouble(component.bubbleRadius)
                out.writeDouble(component.gap)
                out.writeUTF(component.axis.name)
                out.writeUTF(component.label)
                out.writeBoolean(component.showLabel)
                out.writeUTF(component.labelAlignment.name)
            }
        }
    }

    private fun readComponent(input: DataInputStream, schema: Int): DesignerOmrComponent = when (input.readByte().toInt()) {
        TYPE_QUESTION_GROUP -> {
            val id = input.readUTF()
            val startQuestion = input.readInt()
            val questionCount = input.readInt()
            val choices = readStrings(input)
            val columns = input.readInt()
            val firstChoiceX = input.readDouble()
            val topY = input.readDouble()
            val bubbleRadius = input.readDouble()
            val choiceGap = input.readDouble()
            val rowGap = input.readDouble()
            val columnGap = input.readDouble()
            val questionIdPrefix = if (schema >= 2) input.readUTF() else ""
            val orientation = if (schema >= 5) QuestionGroupOrientation.valueOf(input.readUTF()) else QuestionGroupOrientation.VERTICAL
            val label = if (schema >= 5) input.readUTF() else "Ders"
            val showLabel = if (schema >= 5) input.readBoolean() else true
            val labelAlignment = if (schema >= 7) DesignerTextAlignment.valueOf(input.readUTF()) else DesignerTextAlignment.START
            QuestionGroupComponent(id, startQuestion, questionCount, choices, columns, firstChoiceX, topY, bubbleRadius, choiceGap, rowGap, columnGap, questionIdPrefix, orientation, label, showLabel, labelAlignment)
        }
        TYPE_NUMERIC_GRID -> {
            val id = input.readUTF()
            val digits = input.readInt()
            val startX = input.readDouble()
            val topY = input.readDouble()
            val bubbleRadius = input.readDouble()
            val columnGap = input.readDouble()
            val rowGap = input.readDouble()
            val values = readStrings(input)
            val orientation = if (schema >= 2) NumericGridOrientation.valueOf(input.readUTF()) else NumericGridOrientation.DIGITS_HORIZONTAL
            val label = if (schema >= 4) input.readUTF() else "Numara"
            val showLabel = if (schema >= 4) input.readBoolean() else true
            val labelAlignment = if (schema >= 7) DesignerTextAlignment.valueOf(input.readUTF()) else DesignerTextAlignment.START
            NumericGridComponent(id, digits, startX, topY, bubbleRadius, columnGap, rowGap, values, orientation, label, showLabel, labelAlignment)
        }
        TYPE_SINGLE_CHOICE -> {
            val id = input.readUTF()
            val choices = readStrings(input)
            val start = readPoint(input)
            val radius = input.readDouble()
            val gap = input.readDouble()
            val axis = ChoiceAxis.valueOf(input.readUTF())
            val label = if (schema >= 7) input.readUTF() else "Kitapçık Türü"
            val showLabel = if (schema >= 7) input.readBoolean() else true
            val labelAlignment = if (schema >= 7) DesignerTextAlignment.valueOf(input.readUTF()) else DesignerTextAlignment.START
            SingleChoiceComponent(id, choices, start, radius, gap, axis, label, showLabel, labelAlignment)
        }
        else -> error("Bilinmeyen OMR tasarım bileşeni.")
    }

    private fun writeVisual(out: DataOutputStream, element: DesignerVisualElement) {
        when (element) {
            is DesignerTextElement -> {
                out.writeByte(TYPE_TEXT); out.writeUTF(element.id); writeRect(out, element.bounds); out.writeUTF(element.text)
                out.writeDouble(element.fontSize); out.writeUTF(element.alignment.name); out.writeBoolean(element.locked); out.writeBoolean(element.bold)
            }
            is DesignerImageElement -> {
                out.writeByte(TYPE_IMAGE); out.writeUTF(element.id); writeRect(out, element.bounds); out.writeUTF(element.image.mimeType)
                out.writeInt(element.image.pixelWidth); out.writeInt(element.image.pixelHeight)
                val payload = element.image.copyBytes(); out.writeInt(payload.size); out.write(payload); out.writeBoolean(element.locked)
            }
            is DesignerBoxElement -> {
                out.writeByte(TYPE_BOX); out.writeUTF(element.id); writeRect(out, element.bounds); out.writeDouble(element.strokeWidth); out.writeBoolean(element.locked)
            }
            is DesignerLineElement -> {
                out.writeByte(TYPE_LINE); out.writeUTF(element.id); writePoint(out, element.start); writePoint(out, element.end); out.writeDouble(element.strokeWidth); out.writeBoolean(element.locked)
            }
        }
    }

    private fun readVisual(input: DataInputStream, schema: Int): DesignerVisualElement = when (input.readByte().toInt()) {
        TYPE_TEXT -> {
            val id = input.readUTF(); val bounds = readRect(input); val text = input.readUTF(); val fontSize = input.readDouble()
            val alignment = DesignerTextAlignment.valueOf(input.readUTF()); val locked = input.readBoolean(); val bold = if (schema >= 2) input.readBoolean() else false
            DesignerTextElement(id, bounds, text, fontSize, alignment, bold, locked)
        }
        TYPE_IMAGE -> {
            require(schema >= 6) { "Bu tasarım sürümü resim alanını desteklemiyor." }
            val id = input.readUTF(); val bounds = readRect(input); val mimeType = input.readUTF(); val pixelWidth = input.readInt(); val pixelHeight = input.readInt()
            val byteCount = input.readInt(); require(byteCount in 1..DesignerImageData.MAX_BYTES) { "Geçersiz gömülü resim boyutu." }
            val payload = ByteArray(byteCount); input.readFully(payload); val locked = input.readBoolean()
            DesignerImageElement(id, bounds, DesignerImageData(mimeType, pixelWidth, pixelHeight, payload), locked)
        }
        TYPE_BOX -> DesignerBoxElement(input.readUTF(), readRect(input), input.readDouble(), input.readBoolean())
        TYPE_LINE -> DesignerLineElement(input.readUTF(), readPoint(input), readPoint(input), input.readDouble(), input.readBoolean())
        else -> error("Bilinmeyen görsel tasarım bileşeni.")
    }

    private fun writeFormSpec(out: DataOutputStream, spec: DesignerFormSpec) {
        out.writeUTF(spec.paperSize.name); out.writeUTF(spec.orientation.name); out.writeUTF(spec.examMode.name); out.writeUTF(spec.examPreset.name)
        out.writeDouble(spec.answerAppearance.bubbleOutlineWidth); out.writeDouble(spec.answerAppearance.choiceLabelScale)
        out.writeDouble(spec.answerAppearance.questionNumberScale); out.writeDouble(spec.answerAppearance.questionNumberDistanceInRadii)
    }

    private fun readFormSpec(input: DataInputStream): DesignerFormSpec = DesignerFormSpec(
        paperSize = DesignerPaperSize.valueOf(input.readUTF()),
        orientation = DesignerPageOrientation.valueOf(input.readUTF()),
        examMode = DesignerExamMode.valueOf(input.readUTF()),
        examPreset = DesignerExamPreset.valueOf(input.readUTF()),
        answerAppearance = DesignerAnswerAppearance(input.readDouble(), input.readDouble(), input.readDouble(), input.readDouble())
    )

    private fun writeStrings(out: DataOutputStream, values: List<String>) { out.writeInt(values.size); values.forEach(out::writeUTF) }
    private fun readStrings(input: DataInputStream): List<String> = List(readSafeCount(input, MAX_STRING_LIST)) { input.readUTF() }
    private fun writePoint(out: DataOutputStream, point: TemplatePoint) { out.writeDouble(point.x); out.writeDouble(point.y) }
    private fun readPoint(input: DataInputStream): TemplatePoint = TemplatePoint(input.readDouble(), input.readDouble())
    private fun writeSize(out: DataOutputStream, size: TemplateSize) { out.writeDouble(size.width); out.writeDouble(size.height) }
    private fun readSize(input: DataInputStream): TemplateSize = TemplateSize(input.readDouble(), input.readDouble())
    private fun writeRect(out: DataOutputStream, rect: TemplateRect) { out.writeDouble(rect.left); out.writeDouble(rect.top); out.writeDouble(rect.width); out.writeDouble(rect.height) }
    private fun readRect(input: DataInputStream): TemplateRect = TemplateRect(input.readDouble(), input.readDouble(), input.readDouble(), input.readDouble())
    private fun readSafeCount(input: DataInputStream, max: Int): Int { val count = input.readInt(); require(count in 0..max) { "Geçersiz tasarım öğesi sayısı: $count" }; return count }

    private const val MAGIC = 0x4F4D5244
    private const val MIN_SUPPORTED_SCHEMA = 1
    private const val SCHEMA_VERSION = 7
    private const val TYPE_QUESTION_GROUP = 1
    private const val TYPE_NUMERIC_GRID = 2
    private const val TYPE_SINGLE_CHOICE = 3
    private const val TYPE_TEXT = 11
    private const val TYPE_BOX = 12
    private const val TYPE_LINE = 13
    private const val TYPE_IMAGE = 14
    private const val MAX_FIDUCIALS = 32
    private const val MAX_COMPONENTS = 10_000
    private const val MAX_VISUALS = 10_000
    private const val MAX_STRING_LIST = 1_000
}
