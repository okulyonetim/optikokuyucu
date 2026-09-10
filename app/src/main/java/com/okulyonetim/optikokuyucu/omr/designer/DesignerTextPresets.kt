package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.TemplateRect
import kotlin.math.min

/** Ready-to-place text layouts that reuse the ordinary editable text element model. */
object DesignerTextPresets {
    fun studentIdentityLine(document: DesignerDocument, source: DesignerTextElement): DesignerTextElement {
        val safe = DesignerPageGeometry.safeArea(document.space)
        val width = min(680.0, safe.width - 36.0).coerceAtLeast(260.0)
        val height = min(54.0, safe.height - 24.0).coerceAtLeast(38.0)
        val left = source.bounds.left.coerceIn(safe.left, (safe.right - width).coerceAtLeast(safe.left))
        val top = source.bounds.top.coerceIn(safe.top, (safe.bottom - height).coerceAtLeast(safe.top))
        return source.copy(
            id = nextVisualId(document, "student-identity-line"),
            bounds = TemplateRect(left, top, width, height),
            text = "Öğrenci Bilgileri",
            fontSize = source.fontSize.coerceAtLeast(15.0),
            alignment = DesignerTextAlignment.START,
            bold = false,
            showPersonalizedLabel = false,
            rotationDegrees = 0
        )
    }

    fun sideText(
        document: DesignerDocument,
        source: DesignerTextElement,
        rightSide: Boolean
    ): DesignerTextElement {
        val safe = DesignerPageGeometry.safeArea(document.space)
        val stripWidth = DesignerEditorLayout.canonicalForMillimeters(document, 8.0)
            .coerceIn(34.0, 52.0)
        val verticalInset = DesignerEditorLayout.canonicalForMillimeters(document, 13.0)
        val height = (safe.height - verticalInset * 2.0).coerceAtLeast(160.0)
        val left = if (rightSide) safe.right - stripWidth else safe.left
        return source.copy(
            id = nextVisualId(document, "side-text"),
            bounds = TemplateRect(left, safe.top + verticalInset, stripWidth, height),
            text = "Optik formu katlamayınız • İşaret alanlarını karalamayınız",
            fontSize = 15.0,
            alignment = DesignerTextAlignment.CENTER,
            bold = false,
            showPersonalizedLabel = false,
            rotationDegrees = if (rightSide) 90 else 270
        )
    }

    private fun nextVisualId(document: DesignerDocument, prefix: String): String {
        val used = document.visualElements.map { it.id }.toSet()
        var index = 1
        var candidate = "$prefix-$index"
        while (candidate in used) {
            index++
            candidate = "$prefix-$index"
        }
        return candidate
    }
}
