package com.okulyonetim.optikokuyucu.omr.diagnostics

import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.objdetect.ArucoDetector
import org.opencv.objdetect.DetectorParameters
import org.opencv.objdetect.Objdetect

/**
 * Positive detector test that requires no printer, camera target, or external file.
 * Four canonical markers are generated in memory and detected again by OpenCV.
 */
object OpenCvOmrSelfTest {
    private val expectedIds = setOf(11, 22, 33, 44)

    fun run(): OmrSelfTestResult {
        val startedAt = System.nanoTime()
        val canvas = Mat(FRAME_HEIGHT, FRAME_WIDTH, CvType.CV_8UC1, Scalar(255.0))
        val dictionary = Objdetect.getPredefinedDictionary(Objdetect.DICT_4X4_50)
        val detector = ArucoDetector(dictionary, DetectorParameters())
        val ids = Mat()
        val corners = mutableListOf<Mat>()

        return try {
            val placements = listOf(
                11 to Rect(90, 70, MARKER_SIZE, MARKER_SIZE),
                22 to Rect(FRAME_WIDTH - 90 - MARKER_SIZE, 70, MARKER_SIZE, MARKER_SIZE),
                33 to Rect(
                    FRAME_WIDTH - 90 - MARKER_SIZE,
                    FRAME_HEIGHT - 70 - MARKER_SIZE,
                    MARKER_SIZE,
                    MARKER_SIZE
                ),
                44 to Rect(90, FRAME_HEIGHT - 70 - MARKER_SIZE, MARKER_SIZE, MARKER_SIZE)
            )

            placements.forEach { (markerId, targetRect) ->
                val marker = Mat()
                val target = canvas.submat(targetRect)
                try {
                    dictionary.generateImageMarker(markerId, MARKER_SIZE, marker, 1)
                    marker.copyTo(target)
                } finally {
                    target.release()
                    marker.release()
                }
            }

            detector.detectMarkers(canvas, corners, ids)

            val detected = buildSet {
                for (row in 0 until ids.rows()) {
                    val value = ids.get(row, 0)?.firstOrNull()?.toInt() ?: continue
                    add(value)
                }
            }
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000.0

            OmrSelfTestResult(
                passed = detected.containsAll(expectedIds),
                expectedIds = expectedIds,
                detectedIds = detected,
                elapsedMs = elapsedMs
            )
        } catch (_: Throwable) {
            OmrSelfTestResult(
                passed = false,
                expectedIds = expectedIds,
                detectedIds = emptySet(),
                elapsedMs = (System.nanoTime() - startedAt) / 1_000_000.0
            )
        } finally {
            corners.forEach { it.release() }
            ids.release()
            canvas.release()
        }
    }

    private const val FRAME_WIDTH = 960
    private const val FRAME_HEIGHT = 720
    private const val MARKER_SIZE = 120
}

data class OmrSelfTestResult(
    val passed: Boolean,
    val expectedIds: Set<Int>,
    val detectedIds: Set<Int>,
    val elapsedMs: Double
) {
    val detectedExpectedCount: Int
        get() = expectedIds.count { it in detectedIds }

    companion object {
        val NotRun = OmrSelfTestResult(
            passed = false,
            expectedIds = setOf(11, 22, 33, 44),
            detectedIds = emptySet(),
            elapsedMs = 0.0
        )
    }
}
