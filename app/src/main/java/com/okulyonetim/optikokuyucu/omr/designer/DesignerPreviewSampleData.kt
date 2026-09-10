package com.okulyonetim.optikokuyucu.omr.designer

/** Example identity values used only in designer preview so the user sees the actual personalized print result. */
object DesignerPreviewSampleData {
    const val STUDENT_NAME = "ALİ YILMAZ"
    const val STUDENT_CLASS = "8/A"
    const val STUDENT_NUMBER = "123456"
    const val SCHOOL_NAME = "KORUK ORTAOKULU"
    const val EXAM_NAME = "LGS DENEME SINAVI"

    fun document(document: DesignerDocument): DesignerDocument = document.copy(
        visualElements = document.visualElements.map { element ->
            if (element !is DesignerTextElement) return@map element
            val field = DesignerPersonalizedTextBinding.fieldForId(element.id) ?: return@map element
            val value = valueFor(field)
            element.copy(text = DesignerPersonalizedTextBinding.render(element, value))
        }
    )

    fun numericHeaderValues(document: DesignerDocument): Map<String, String> =
        document.components
            .filterIsInstance<NumericGridComponent>()
            .associate { component -> component.id to STUDENT_NUMBER }

    fun valueFor(field: DesignerPersonalizedField): String = when (field) {
        DesignerPersonalizedField.STUDENT_NAME -> STUDENT_NAME
        DesignerPersonalizedField.STUDENT_CLASS -> STUDENT_CLASS
        DesignerPersonalizedField.STUDENT_NUMBER -> STUDENT_NUMBER
        DesignerPersonalizedField.STUDENT_IDENTITY_LINE ->
            "AD SOYAD: $STUDENT_NAME  •  NUMARA: $STUDENT_NUMBER  •  SINIF: $STUDENT_CLASS"
        DesignerPersonalizedField.EXAM_NAME -> EXAM_NAME
        DesignerPersonalizedField.SCHOOL_NAME -> SCHOOL_NAME
    }
}
