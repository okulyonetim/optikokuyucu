package com.okulyonetim.optikokuyucu.omr.designer

/** Values injected only while producing a personalized print. They are not recognition geometry. */
data class DesignerPrintContext(
    val studentName: String = "",
    val className: String = "",
    val studentNumber: String = "",
    val examName: String = "",
    val schoolName: String = ""
) {
    val isPersonalized: Boolean
        get() = studentName.isNotBlank() || className.isNotBlank() || studentNumber.isNotBlank() || examName.isNotBlank() || schoolName.isNotBlank()
}

object DesignerPrintPersonalization {
    fun value(binding: DesignerTextBinding, context: DesignerPrintContext): String = when (binding) {
        DesignerTextBinding.STATIC -> ""
        DesignerTextBinding.STUDENT_NAME -> context.studentName.trim()
        DesignerTextBinding.CLASS_NAME -> context.className.trim()
        DesignerTextBinding.STUDENT_NUMBER -> context.studentNumber.trim()
        DesignerTextBinding.EXAM_NAME -> context.examName.trim()
        DesignerTextBinding.SCHOOL_NAME -> context.schoolName.trim()
    }

    /** Replaces only printable text content; bounds/style/OMR geometry remain untouched. */
    fun resolveDocument(document: DesignerDocument, context: DesignerPrintContext): DesignerDocument =
        if (!context.isPersonalized) document else document.copy(
            visualElements = document.visualElements.map { element ->
                if (element !is DesignerTextElement || element.binding == DesignerTextBinding.STATIC) element
                else {
                    val resolved = value(element.binding, context)
                    if (resolved.isBlank()) element else element.copy(text = resolved)
                }
            }
        )

    fun studentNumberIssue(document: DesignerDocument, context: DesignerPrintContext): String? {
        if (!context.isPersonalized) return null
        val number = context.studentNumber.trim()
        val component = studentNumberComponent(document) ?: return null
        if (number.isBlank()) return "Öğrenci numarası kişiye özel baskı için zorunludur."
        if (number.codePointCount(0, number.length) != component.digits) {
            return "Öğrenci numarası ${component.digits} haneli olmalıdır."
        }
        val values = number.codePoints().toArray().map { String(Character.toChars(it)) }
        if (values.any { it !in component.values }) {
            return "Öğrenci numarası bu formun kodlama desenine uygun değil."
        }
        return null
    }

    fun studentNumberValues(document: DesignerDocument, context: DesignerPrintContext): List<String>? {
        if (studentNumberIssue(document, context) != null) return null
        val component = studentNumberComponent(document) ?: return null
        val number = context.studentNumber.trim()
        if (number.isBlank()) return null
        val values = number.codePoints().toArray().map { String(Character.toChars(it)) }
        return values.takeIf { it.size == component.digits }
    }

    fun studentNumberComponent(document: DesignerDocument): NumericGridComponent? =
        document.components.filterIsInstance<NumericGridComponent>().firstOrNull {
            it.id == "studentNumber" || it.id.startsWith("number-")
        }
}
