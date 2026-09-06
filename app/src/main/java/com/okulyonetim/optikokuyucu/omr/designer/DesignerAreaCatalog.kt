package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint
import com.okulyonetim.optikokuyucu.omr.template.TemplateRect
import kotlin.math.ceil
import kotlin.math.min

enum class DesignerAreaKind(val displayName: String) {
    NUMBER("Öğrenci No Kodlama"),
    ANSWERS("Cevaplar"),
    BOOKLET("Kitapçık Türü"),
    STUDENT_NAME("Öğrenci Adı Soyadı"),
    CLASS_NAME("Sınıfı"),
    STUDENT_NUMBER_TEXT("Öğrenci Numarası"),
    EXAM_NAME("Sınav Adı"),
    SCHOOL_NAME("Okul Adı"),
    DESCRIPTION("Açıklama"),
    IMAGE("Resim")
}

data class DesignerAreaSection(val title: String, val kinds: List<DesignerAreaKind>) {
    init { require(title.isNotBlank()); require(kinds.isNotEmpty()); require(kinds.distinct().size == kinds.size) }
}

object DesignerAreaCatalog {
    val sections: List<DesignerAreaSection> = listOf(
        DesignerAreaSection("İşaretleme Alanı", listOf(DesignerAreaKind.NUMBER, DesignerAreaKind.ANSWERS, DesignerAreaKind.BOOKLET)),
        DesignerAreaSection(
            "Otomatik Bilgi Alanı",
            listOf(
                DesignerAreaKind.STUDENT_NAME,
                DesignerAreaKind.CLASS_NAME,
                DesignerAreaKind.STUDENT_NUMBER_TEXT,
                DesignerAreaKind.EXAM_NAME,
                DesignerAreaKind.SCHOOL_NAME
            )
        ),
        DesignerAreaSection("Bilgilendirme Alanı", listOf(DesignerAreaKind.DESCRIPTION, DesignerAreaKind.IMAGE))
    )
    val allKinds: List<DesignerAreaKind> = sections.flatMap { it.kinds }
    val numberPatternPresets = listOf("0123456789", "AB", "ABC", "ABCD", "ABCDE")
    val answerPatternPresets = listOf("AB", "ABC", "ABCD", "ABCDE")
    val bookletPatternPresets = listOf("AB", "ABC", "ABCD")

    init { require(allKinds.distinct().size == allKinds.size) }

    fun bindingFor(kind: DesignerAreaKind): DesignerTextBinding? = when (kind) {
        DesignerAreaKind.STUDENT_NAME -> DesignerTextBinding.STUDENT_NAME
        DesignerAreaKind.CLASS_NAME -> DesignerTextBinding.CLASS_NAME
        DesignerAreaKind.STUDENT_NUMBER_TEXT -> DesignerTextBinding.STUDENT_NUMBER
        DesignerAreaKind.EXAM_NAME -> DesignerTextBinding.EXAM_NAME
        DesignerAreaKind.SCHOOL_NAME -> DesignerTextBinding.SCHOOL_NAME
        else -> null
    }

    fun createBoundTextArea(document: DesignerDocument, binding: DesignerTextBinding): DesignerTextElement {
        require(binding != DesignerTextBinding.STATIC)
        val safe = DesignerPageGeometry.safeArea(document.space)
        val id = nextVisualId(document, "bound-${binding.name.lowercase().replace('_', '-')}")
        val existingBoundCount = document.visualElements.filterIsInstance<DesignerTextElement>().count { it.binding != DesignerTextBinding.STATIC }
        val row = existingBoundCount % 6
        val top = (safe.top + 40.0 + row * 48.0).coerceAtMost(safe.bottom - 48.0)
        val width = min(560.0, safe.width - 160.0).coerceAtLeast(220.0)
        val left = ((document.space.width - width) / 2.0).coerceIn(safe.left, safe.right - width)
        return DesignerTextElement(
            id = id,
            bounds = TemplateRect(left, top, width, 38.0),
            text = binding.displayName,
            fontSize = 18.0,
            alignment = DesignerTextAlignment.CENTER,
            bold = false,
            binding = binding
        )
    }

    fun createNumberArea(document: DesignerDocument): NumericGridComponent {
        val safe = DesignerPageGeometry.safeArea(document.space)
        val id = nextComponentId(document, "number")
        return NumericGridComponent(
            id = id,
            digits = 6,
            startX = safe.left + 120.0,
            topY = safe.top + 180.0,
            bubbleRadius = DesignerEditorLayout.STANDARD_BUBBLE_RADIUS,
            columnGap = DesignerEditorLayout.NUMBER_POSITION_GAP,
            rowGap = DesignerEditorLayout.NUMBER_VALUE_GAP,
            values = parseNumberPattern("0123456789")!!,
            orientation = NumericGridOrientation.DIGITS_HORIZONTAL,
            label = "Öğrenci No",
            showLabel = true,
            labelAlignment = DesignerTextAlignment.CENTER
        )
    }

    fun createAnswerArea(document: DesignerDocument): QuestionGroupComponent {
        val ordinal = document.components.count { it is QuestionGroupComponent }
        val id = nextComponentId(document, "answers")
        val start = DesignerEditorLayout.compactAnswerStart(document, ordinal)
        return QuestionGroupComponent(
            id = id,
            startQuestion = 1,
            questionCount = 20,
            choices = parseAnswerPattern("ABCD")!!,
            columns = 1,
            firstChoiceX = start.x,
            topY = start.y,
            bubbleRadius = DesignerEditorLayout.STANDARD_BUBBLE_RADIUS,
            choiceGap = DesignerEditorLayout.ANSWER_CHOICE_GAP,
            rowGap = DesignerEditorLayout.ANSWER_ROW_GAP,
            columnGap = DesignerEditorLayout.compactAnswerColumnGap(document),
            questionIdPrefix = id,
            orientation = QuestionGroupOrientation.VERTICAL,
            label = "Ders ${ordinal + 1}",
            showLabel = true,
            labelAlignment = DesignerTextAlignment.CENTER
        )
    }

    fun createBookletArea(document: DesignerDocument): SingleChoiceComponent {
        val safe = DesignerPageGeometry.safeArea(document.space)
        return SingleChoiceComponent(
            id = nextComponentId(document, "booklet"),
            choices = listOf("A", "B"),
            start = TemplatePoint(safe.left + 120.0, safe.top + 500.0),
            bubbleRadius = DesignerEditorLayout.STANDARD_BUBBLE_RADIUS,
            gap = DesignerEditorLayout.BOOKLET_GAP,
            axis = ChoiceAxis.HORIZONTAL,
            label = "Kitapçık Türü",
            showLabel = true,
            labelAlignment = DesignerTextAlignment.CENTER
        )
    }

    fun createDescriptionArea(document: DesignerDocument): DesignerTextElement {
        val safe = DesignerPageGeometry.safeArea(document.space)
        val id = nextVisualId(document, "description")
        val suffix = id.substringAfterLast('-').toIntOrNull() ?: 1
        val stagger = ((suffix - 1) % 4) * 18.0
        val width = min(520.0, safe.width - 60.0).coerceAtLeast(160.0)
        val height = min(150.0, safe.height - 60.0).coerceAtLeast(70.0)
        val left = (safe.left + 130.0 + stagger).coerceAtMost(safe.right - width)
        val top = (safe.top + 120.0 + stagger).coerceAtMost(safe.bottom - height)
        return DesignerTextElement(id, TemplateRect(left, top, width, height), "Açıklama", 22.0)
    }

    fun createImageArea(document: DesignerDocument, image: DesignerImageData): DesignerImageElement {
        val safe = DesignerPageGeometry.safeArea(document.space)
        val id = nextVisualId(document, "image")
        val suffix = id.substringAfterLast('-').toIntOrNull() ?: 1
        val stagger = ((suffix - 1) % 4) * 18.0
        val maxWidth = min(320.0, safe.width - 60.0).coerceAtLeast(100.0)
        val maxHeight = min(240.0, safe.height - 60.0).coerceAtLeast(80.0)
        val scale = min(maxWidth / image.pixelWidth, maxHeight / image.pixelHeight)
        val width = image.pixelWidth * scale
        val height = image.pixelHeight * scale
        val left = (safe.left + 130.0 + stagger).coerceAtMost(safe.right - width)
        val top = (safe.top + 300.0 + stagger).coerceAtMost(safe.bottom - height)
        return DesignerImageElement(id, TemplateRect(left, top, width, height), image)
    }

    fun parseNumberPattern(text: String): List<String>? = parsePattern(text, 24)
    fun parseAnswerPattern(text: String): List<String>? = parsePattern(text, 8)
    fun parseBookletPattern(text: String): List<String>? = parsePattern(text, 8)
    fun numberPatternText(values: List<String>) = patternText(values)
    fun answerPatternText(values: List<String>) = patternText(values)
    fun bookletPatternText(values: List<String>) = patternText(values)
    fun answerQuestionsPerBlock(component: QuestionGroupComponent): Int = ceil(component.questionCount.toDouble() / component.columns).toInt().coerceAtLeast(1)

    fun numberAreaIssue(document: DesignerDocument, component: NumericGridComponent): String? {
        if (component.digits !in 1..16) return "Hane sayısı 1–16 arasında olmalıdır."
        if (component.values.size !in 2..24) return "Desen 2–24 benzersiz değerden oluşmalıdır."
        if (component.showLabel && component.label.isBlank()) return "Etiket görünürken etiket metni boş olamaz."
        if (component.labelFontSize !in 4.0..72.0) return "Etiket yazı boyutu 4–72 arasında olmalıdır."
        if (component.bubbleRadius != DesignerEditorLayout.STANDARD_BUBBLE_RADIUS) return "Tüm işaretleme alanları standart baloncuk boyutunu kullanmalıdır."
        val minimumGap = component.bubbleRadius * 2.0 + 3.0
        if (component.columnGap < minimumGap || component.rowGap < minimumGap) return "Baloncuklar çakışıyor."
        return componentBoundsIssue(document, component, "Numara")
    }

    fun answerAreaIssue(document: DesignerDocument, component: QuestionGroupComponent): String? {
        if (component.startQuestion !in 1..9999) return "İlk soru numarası 1–9999 arasında olmalıdır."
        if (component.questionCount !in 1..250) return "Toplam soru sayısı 1–250 arasında olmalıdır."
        if (component.choices.size !in 2..8) return "Desen 2–8 benzersiz şıktan oluşmalıdır."
        if (component.columns !in 1..minOf(8, component.questionCount)) return "Sütun sayısı soru sayısını aşamaz ve en fazla 8 olabilir."
        if (component.showLabel && component.label.isBlank()) return "Ders adı görünürken boş olamaz."
        if (component.labelFontSize !in 4.0..72.0) return "Etiket yazı boyutu 4–72 arasında olmalıdır."
        if (component.bubbleRadius != DesignerEditorLayout.STANDARD_BUBBLE_RADIUS) return "Tüm işaretleme alanları standart baloncuk boyutunu kullanmalıdır."
        val minGap = component.bubbleRadius * 2.0 + 3.0
        if (component.choiceGap < minGap || component.rowGap < minGap) return "Şık veya soru aralığı baloncuklar için çok dar."
        return componentBoundsIssue(document, component, "Cevap")
    }

    fun bookletAreaIssue(document: DesignerDocument, component: SingleChoiceComponent): String? {
        if (component.choices.size !in 2..8) return "Kitapçık deseni 2–8 benzersiz değerden oluşmalıdır."
        if (component.showLabel && component.label.isBlank()) return "Kitapçık etiketi görünürken boş olamaz."
        if (component.labelFontSize !in 4.0..72.0) return "Etiket yazı boyutu 4–72 arasında olmalıdır."
        if (component.bubbleRadius != DesignerEditorLayout.STANDARD_BUBBLE_RADIUS) return "Tüm işaretleme alanları standart baloncuk boyutunu kullanmalıdır."
        if (component.gap < component.bubbleRadius * 2.0 + 3.0) return "Kitapçık baloncukları çakışıyor."
        return componentBoundsIssue(document, component, "Kitapçık")
    }

    fun descriptionAreaIssue(document: DesignerDocument, element: DesignerTextElement): String? {
        if (element.text.isBlank()) return "Metin boş olamaz."
        if (element.text.length > 2_000) return "Metin en fazla 2000 karakter olabilir."
        if (element.fontSize !in 8.0..72.0) return "Yazı boyutu 8–72 arasında olmalıdır."
        return visualBoundsIssue(document, element.bounds, element.binding.displayName)
    }

    fun imageAreaIssue(document: DesignerDocument, element: DesignerImageElement): String? {
        if (element.image.byteSize !in 1..DesignerImageData.MAX_BYTES) return "Resim boyutu desteklenen sınırı aşıyor."
        return visualBoundsIssue(document, element.bounds, "Resim")
    }

    private fun parsePattern(text: String, maxValues: Int): List<String>? {
        val normalized = text.trim(); if (normalized.isEmpty()) return null
        val values = if (',' in normalized) normalized.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        else normalized.codePoints().toArray().map { String(Character.toChars(it)) }
        return values.takeIf { it.size in 2..maxValues && it.all(String::isNotBlank) && it.distinct().size == it.size }
    }

    private fun patternText(values: List<String>): String = if (values.all { it.codePointCount(0, it.length) == 1 && ',' !in it }) values.joinToString("") else values.joinToString(",")

    private fun nextComponentId(document: DesignerDocument, prefix: String): String {
        val used = document.components.map { it.id }.toSet(); var n = 1; var id = "$prefix-$n"
        while (id in used) { n++; id = "$prefix-$n" }; return id
    }
    private fun nextVisualId(document: DesignerDocument, prefix: String): String {
        val used = document.visualElements.map { it.id }.toSet(); var n = 1; var id = "$prefix-$n"
        while (id in used) { n++; id = "$prefix-$n" }; return id
    }

    private fun componentBoundsIssue(document: DesignerDocument, component: DesignerOmrComponent, label: String): String? =
        visualBoundsIssue(document, DesignerComponentGeometry.bounds(component), label)

    private fun visualBoundsIssue(document: DesignerDocument, bounds: TemplateRect, label: String): String? =
        if (!DesignerEditSafety.isPlacementSafe(document, bounds)) "$label alanı sayfa/marker güvenli yerleşim alanında kalmalıdır." else null
}
