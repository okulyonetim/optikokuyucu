package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.StandardOmrTemplate
import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignerPdfLayoutTest {
    @Test
    fun a4AndA5KeepCanonicalAspectAndStayInsideMargins() {
        listOf(PdfPageProfile.A4, PdfPageProfile.A5).forEach { profile ->
            val space = StandardOmrTemplate.DEFAULT_SPACE
            val transform = DesignerPdfLayout.fit(space, profile)
            val topLeft = transform.map(TemplatePoint(0.0, 0.0))
            val bottomRight = transform.map(TemplatePoint(space.width, space.height))

            assertTrue(topLeft.x >= profile.marginPoints - 0.01)
            assertTrue(topLeft.y >= profile.marginPoints - 0.01)
            assertTrue(bottomRight.x <= profile.widthPoints - profile.marginPoints + 0.01)
            assertTrue(bottomRight.y <= profile.heightPoints - profile.marginPoints + 0.01)

            val renderedWidth = bottomRight.x - topLeft.x
            val renderedHeight = bottomRight.y - topLeft.y
            assertEquals(space.aspectRatio, renderedWidth / renderedHeight, 0.000001)
        }
    }

    @Test
    fun canonicalCenterMapsToPhysicalPageCenter() {
        val space = StandardOmrTemplate.DEFAULT_SPACE
        PdfPageProfile.entries.forEach { profile ->
            val transform = DesignerPdfLayout.fit(space, profile)
            val mappedCenter = transform.map(
                TemplatePoint(space.width / 2.0, space.height / 2.0)
            )

            assertEquals(profile.widthPoints / 2.0, mappedCenter.x, 0.000001)
            assertEquals(profile.heightPoints / 2.0, mappedCenter.y, 0.000001)
        }
    }
}
