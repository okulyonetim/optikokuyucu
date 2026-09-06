package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint
import com.okulyonetim.optikokuyucu.omr.template.TemplateRect
import com.okulyonetim.optikokuyucu.omr.template.TemplateSize
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignerDocumentCodecTest {
    @Test
    fun `document round trip preserves OMR visual image and form source data`() {
        val image = DesignerImageData(
            mimeType = "image/jpeg",
            pixelWidth = 320,
            pixelHeight = 180,
            bytes = byteArrayOf(1, 2, 3, 4, 5, 6)
        )
        val document = DesignerDocument(
            id = "my-form",
            version = 3,
            name = "Deneme Formu",
            components = listOf(
                QuestionGroupComponent(
                    id = "questions",
                    startQuestion = 5,
                    questionCount = 40,
                    choices = listOf("A", "B", "C", "D"),
                    columns = 2,
                    firstChoiceX = 140.0,
                    topY = 240.0,
                    bubbleRadius = 11.0,
                    choiceGap = 45.0,
                    rowGap = 46.0,
                    columnGap = 480.0,
                    questionIdPrefix = "turkce",
                    orientation = QuestionGroupOrientation.HORIZONTAL,
                    label = "Türkçe",
                    showLabel = false
                ),
                NumericGridComponent(
                    id = "studentNumber",
                    digits = 6,
                    startX = 120.0,
                    topY = 850.0,
                    bubbleRadius = 10.0,
                    columnGap = 44.0,
                    rowGap = 34.0,
                    label = "Öğrenci No",
                    showLabel = false
                ),
                SingleChoiceComponent(
                    id = "booklet",
                    choices = listOf("A", "B"),
                    start = TemplatePoint(150.0, 1220.0),
                    bubbleRadius = 12.0,
                    gap = 60.0
                )
            ),
            visualElements = listOf(
                DesignerTextElement(
                    id = "title",
                    bounds = TemplateRect(160.0, 120.0, 680.0, 90.0),
                    text = "SINAV OPTİK FORMU\nAçıklama",
                    fontSize = 28.0,
                    alignment = DesignerTextAlignment.CENTER
                ),
                DesignerImageElement(
                    id = "image-1",
                    bounds = TemplateRect(650.0, 220.0, 200.0, 112.5),
                    image = image
                ),
                DesignerBoxElement(
                    id = "name-box",
                    bounds = TemplateRect(120.0, 190.0, 500.0, 60.0),
                    strokeWidth = 2.0
                ),
                DesignerLineElement(
                    id = "separator",
                    start = TemplatePoint(120.0, 280.0),
                    end = TemplatePoint(880.0, 280.0),
                    strokeWidth = 1.5,
                    locked = true
                )
            ),
            formSpec = DesignerFormSpec(
                paperSize = DesignerPaperSize.A3,
                orientation = DesignerPageOrientation.LANDSCAPE,
                examMode = DesignerExamMode.MULTI_LESSON,
                examPreset = DesignerExamPreset.LGS,
                answerAppearance = DesignerAnswerAppearance(
                    bubbleOutlineWidth = 1.35,
                    choiceLabelScale = 0.84,
                    questionNumberScale = 0.96,
                    questionNumberDistanceInRadii = 2.15
                )
            )
        )

        val decoded = DesignerDocumentCodec.decode(DesignerDocumentCodec.encode(document))

        assertEquals(document, decoded)
        val decodedImage = decoded.visualElements.filterIsInstance<DesignerImageElement>().single()
        assertTrue(decodedImage.image.copyBytes().contentEquals(image.copyBytes()))
    }

    @Test
    fun `schema four question groups gain backward compatible stage six defaults`() {
        val bytes = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { out ->
                out.writeInt(0x4F4D5244)
                out.writeInt(4)
                out.writeUTF("legacy-answer-form")
                out.writeInt(2)
                out.writeUTF("Legacy")
                out.writeDouble(1000.0)
                out.writeDouble(1414.0)
                out.writeInt(0)
                out.writeInt(1)
                out.writeByte(1)
                out.writeUTF("questions")
                out.writeInt(1)
                out.writeInt(20)
                out.writeInt(4)
                listOf("A", "B", "C", "D").forEach(out::writeUTF)
                out.writeInt(2)
                out.writeDouble(150.0)
                out.writeDouble(250.0)
                out.writeDouble(10.0)
                out.writeDouble(32.0)
                out.writeDouble(36.0)
                out.writeDouble(330.0)
                out.writeUTF("legacy")
                out.writeInt(0)
                writeLegacyFormSpec(out)
            }
        }.toByteArray()

        val decoded = DesignerDocumentCodec.decode(bytes)
        val answer = decoded.components.single() as QuestionGroupComponent
        assertEquals(QuestionGroupOrientation.VERTICAL, answer.orientation)
        assertEquals("Ders", answer.label)
        assertTrue(answer.showLabel)
        assertEquals("legacy", answer.questionIdPrefix)
    }

    @Test
    fun `schema five document without images remains readable`() {
        val bytes = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { out ->
                out.writeInt(0x4F4D5244)
                out.writeInt(5)
                out.writeUTF("schema-five")
                out.writeInt(1)
                out.writeUTF("Schema Five")
                out.writeDouble(1000.0)
                out.writeDouble(1414.0)
                out.writeInt(0)
                out.writeInt(0)
                out.writeInt(1)
                out.writeByte(11)
                out.writeUTF("text")
                out.writeDouble(120.0)
                out.writeDouble(180.0)
                out.writeDouble(300.0)
                out.writeDouble(80.0)
                out.writeUTF("Açıklama")
                out.writeDouble(20.0)
                out.writeUTF(DesignerTextAlignment.START.name)
                out.writeBoolean(false)
                out.writeBoolean(false)
                writeLegacyFormSpec(out)
            }
        }.toByteArray()

        val decoded = DesignerDocumentCodec.decode(bytes)

        assertEquals("Açıklama", (decoded.visualElements.single() as DesignerTextElement).text)
    }

    @Test
    fun `schema three numeric grids gain backward compatible label defaults`() {
        val bytes = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { out ->
                out.writeInt(0x4F4D5244)
                out.writeInt(3)
                out.writeUTF("legacy-form")
                out.writeInt(2)
                out.writeUTF("Legacy")
                out.writeDouble(1000.0)
                out.writeDouble(1414.0)
                out.writeInt(0)
                out.writeInt(1)
                out.writeByte(2)
                out.writeUTF("studentNumber")
                out.writeInt(6)
                out.writeDouble(120.0)
                out.writeDouble(300.0)
                out.writeDouble(10.0)
                out.writeDouble(44.0)
                out.writeDouble(34.0)
                out.writeInt(10)
                (0..9).forEach { out.writeUTF(it.toString()) }
                out.writeUTF(NumericGridOrientation.DIGITS_HORIZONTAL.name)
                out.writeInt(0)
                writeLegacyFormSpec(out)
            }
        }.toByteArray()

        val decoded = DesignerDocumentCodec.decode(bytes)
        val number = decoded.components.single() as NumericGridComponent
        assertEquals("Numara", number.label)
        assertTrue(number.showLabel)
        assertEquals(DesignerPageOrientation.PORTRAIT, decoded.formSpec.orientation)
    }

    @Test
    fun `default form spec follows page orientation and compact numbered bubble contract`() {
        val document = DesignerDocument(
            id = "reference-style",
            version = 1,
            name = "Reference",
            space = TemplateSize(width = 1414.0, height = 1000.0)
        )
        assertEquals(DesignerPageOrientation.LANDSCAPE, document.formSpec.orientation)
        assertEquals(DesignerPaperSize.A4, document.formSpec.paperSize)
        assertEquals(1.2, document.formSpec.answerAppearance.bubbleOutlineWidth, 0.0001)
        assertEquals(0.82, document.formSpec.answerAppearance.choiceLabelScale, 0.0001)
        assertEquals(0.92, document.formSpec.answerAppearance.questionNumberScale, 0.0001)
        assertEquals(2.0, document.formSpec.answerAppearance.questionNumberDistanceInRadii, 0.0001)
    }

    private fun writeLegacyFormSpec(out: DataOutputStream) {
        out.writeUTF(DesignerPaperSize.A4.name)
        out.writeUTF(DesignerPageOrientation.PORTRAIT.name)
        out.writeUTF(DesignerExamMode.UNSPECIFIED.name)
        out.writeUTF(DesignerExamPreset.CUSTOM.name)
        out.writeDouble(1.2)
        out.writeDouble(0.82)
        out.writeDouble(0.92)
        out.writeDouble(2.0)
    }
}
