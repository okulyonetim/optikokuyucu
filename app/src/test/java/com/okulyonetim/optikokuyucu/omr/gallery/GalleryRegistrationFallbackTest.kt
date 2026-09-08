package com.okulyonetim.optikokuyucu.omr.gallery

import com.okulyonetim.optikokuyucu.omr.geometry.ImagePoint
import com.okulyonetim.optikokuyucu.omr.template.FiducialCorner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class GalleryRegistrationFallbackTest {
    private val anchors = mapOf(
        FiducialCorner.TOP_LEFT to ImagePoint(10.0, 20.0),
        FiducialCorner.TOP_RIGHT to ImagePoint(110.0, 20.0),
        FiducialCorner.BOTTOM_RIGHT to ImagePoint(110.0, 220.0),
        FiducialCorner.BOTTOM_LEFT to ImagePoint(10.0, 220.0)
    )

    @Test
    fun infersEachMissingCornerFromOtherThree() {
        anchors.keys.forEach { missing ->
            val result = GalleryRegistrationFallback.inferAnchorQuadrilateral(anchors - missing)
            assertNotNull("Expected recovery when $missing is missing", result)
            val recovered = when (missing) {
                FiducialCorner.TOP_LEFT -> result!!.topLeft
                FiducialCorner.TOP_RIGHT -> result!!.topRight
                FiducialCorner.BOTTOM_RIGHT -> result!!.bottomRight
                FiducialCorner.BOTTOM_LEFT -> result!!.bottomLeft
            }
            val expected = anchors.getValue(missing)
            assertEquals(expected.x, recovered.x, 1e-9)
            assertEquals(expected.y, recovered.y, 1e-9)
        }
    }

    @Test
    fun rejectsWhenFewerThanThreeExpectedCornersExist() {
        val result = GalleryRegistrationFallback.inferAnchorQuadrilateral(
            mapOf(
                FiducialCorner.TOP_LEFT to anchors.getValue(FiducialCorner.TOP_LEFT),
                FiducialCorner.BOTTOM_RIGHT to anchors.getValue(FiducialCorner.BOTTOM_RIGHT)
            )
        )
        assertNull(result)
    }

    @Test
    fun doesNotReplaceACompleteFourMarkerRegistration() {
        assertNull(GalleryRegistrationFallback.inferAnchorQuadrilateral(anchors))
    }
}
