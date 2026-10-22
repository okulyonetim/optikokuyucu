package com.okulyonetim.optikokuyucu.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.okulyonetim.optikokuyucu.omr.bubble.BubbleReadResult
import com.okulyonetim.optikokuyucu.omr.bubble.CanonicalBubbleReader
import com.okulyonetim.optikokuyucu.omr.bubble.OmrSensitivity
import com.okulyonetim.optikokuyucu.omr.bubble.toGrayImageOrNull
import com.okulyonetim.optikokuyucu.omr.fiducial.FiducialDetectionResult
import com.okulyonetim.optikokuyucu.omr.fiducial.OpenCvFiducialDetector
import com.okulyonetim.optikokuyucu.omr.geometry.CanonicalImageRectifier
import com.okulyonetim.optikokuyucu.omr.geometry.ImageQuadrilateral
import com.okulyonetim.optikokuyucu.omr.live.LiveReadConsensus
import com.okulyonetim.optikokuyucu.omr.live.LiveScanFingerprint
import com.okulyonetim.optikokuyucu.omr.live.LiveScanGate
import com.okulyonetim.optikokuyucu.omr.live.LiveSessionDeduplicator
import com.okulyonetim.optikokuyucu.omr.markgrid.CanonicalMarkGridReader
import com.okulyonetim.optikokuyucu.omr.markgrid.MarkGridReadResult
import com.okulyonetim.optikokuyucu.omr.results.MAX_SCAN_IMAGE_PIXELS
import com.okulyonetim.optikokuyucu.omr.template.OmrRecognitionBindingsResolver
import com.okulyonetim.optikokuyucu.omr.template.OmrTemplate
import com.okulyonetim.optikokuyucu.omr.template.StandardOmrTemplate
import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint
import com.okulyonetim.optikokuyucu.omr.tracking.PageLockTracker
import com.okulyonetim.optikokuyucu.omr.tracking.PageTrackingPhase
import com.okulyonetim.optikokuyucu.storage.AppLog
import org.opencv.core.CvType
import org.opencv.core.Mat
import kotlin.math.max

/**
 * Live CameraX analyzer.
 *
 * Frames stay in single-channel luminance form. Marker search, canonical rectification, question
 * reading and generic mark-grid reading all use the Y plane; the live OMR path does not create
 * RGB Bitmaps.
 *
 * Acceptance requires both a stable page lock and temporal agreement between consecutive OMR
 * frames. One physical sheet is then latched until it visibly leaves the camera. When a stable
 * student number exists, a session fingerprint also prevents the same completed sheet from being
 * accepted again after it is removed and reinserted.
 *
 * The analyzer accepts any compiled [OmrTemplate]. The default keeps the existing diagnostic
 * scanner behavior while the form designer can pass its currently edited template to "Test Et".
 */
class CameraFrameAnalyzer(
    openCvReady: Boolean,
    private val onStats: (CameraFrameStats) -> Unit,
    private val onLiveRead: (LiveOmrReadResult) -> Unit = {},
    private val template: OmrTemplate = StandardOmrTemplate.SAMPLE_20_ABCD_STUDENT_6_BOOKLET_AB,
    sensitivity: OmrSensitivity = OmrSensitivity.NORMAL
) : ImageAnalysis.Analyzer {

    private val fiducialDetector = if (openCvReady) OpenCvFiducialDetector(template) else null
    private val bubbleReader = CanonicalBubbleReader(template, sensitivity)
    private val markGridReader = CanonicalMarkGridReader(template, sensitivity)
    private val recognitionBindings = OmrRecognitionBindingsResolver.fromTemplate(template)
    private val pageTracker = PageLockTracker()
    private val scanGate = LiveScanGate()
    private val readConsensus = LiveReadConsensus(requiredConsecutiveMatches = REQUIRED_CONSENSUS_FRAMES)
    private val sessionDeduplicator = LiveSessionDeduplicator()

    private var frameCount = 0
    private var windowStartedAtNs = System.nanoTime()
    private var latestDetection = FiducialDetectionResult.Empty
    private var latestPageBoundary: ImageQuadrilateral? = null
    private var latestTrackingPhase = PageTrackingPhase.SEARCHING
    private var latestTrackingConfidence = 0.0
    private var latestMotionRatio = 1.0
    private var detectorHealthy = openCvReady
    private var liveReadCount = 0
    private var duplicateReadCount = 0
    private var latestRegistrationUnreliable = false
    private var failureCount = 0

    override fun analyze(image: ImageProxy) {
        try {
            frameCount += 1

            val detector = fiducialDetector
            if (detector != null) {
                latestDetection = runCatching {
                    detector.detect(image)
                }.onFailure {
                    detectorHealthy = false
                    logAnalyzerFailure("Marker algılama hatası", it)
                }.getOrDefault(FiducialDetectionResult.Empty)
                latestPageBoundary = estimatePageBoundary(latestDetection)

                val tracking = pageTracker.onCandidate(
                    quad = latestDetection.pageQuadrilateral,
                    quality = latestDetection.quality,
                    frameWidth = image.width,
                    frameHeight = image.height
                )
                latestTrackingPhase = tracking.phase
                latestTrackingConfidence = tracking.confidence
                latestMotionRatio = tracking.motionRatio

                val rearmed = scanGate.onFrame(
                    phase = tracking.phase,
                    markerCount = latestDetection.detectedMarkers.size
                )
                if (rearmed) readConsensus.reset()

                // The 16-corner fit leaves a real residual: a large one means the markers do not sit
                // where this template says (wrong form, bent sheet, a mis-detected marker), so the
                // bubbles would be sampled off-target. Refuse to read rather than risk wrong answers.
                val registration = latestDetection.canonicalRegistration
                latestRegistrationUnreliable = registration != null && !registration.isReliable
                val candidateReady = scanGate.canRead(
                    phase = tracking.phase,
                    markerCount = latestDetection.detectedMarkers.size,
                    registrationReady = registration != null && registration.isReliable,
                    pageConfidence = tracking.confidence
                )

                if (!candidateReady &&
                    (tracking.phase != PageTrackingPhase.LOCKED ||
                        latestDetection.detectedMarkers.size != 4)
                ) {
                    readConsensus.reset()
                }

                if (candidateReady) {
                    val liveResult = runCatching {
                        readLockedFrame(
                            image = image,
                            detection = latestDetection,
                            pageConfidence = tracking.confidence
                        )
                    }.onFailure {
                        logAnalyzerFailure("Kare okuma hatası", it)
                    }.getOrNull()

                    if (liveResult == null) {
                        readConsensus.reset()
                    } else {
                        val confirmed = readConsensus.offer(
                            signature = readSignature(liveResult),
                            parts = readSignatureParts(liveResult)
                        )
                        if (confirmed) {
                            scanGate.onAcceptedRead()
                            readConsensus.reset()

                            val studentNumber = recognitionBindings.studentNumber(liveResult.markGridResult)
                            val fingerprint = LiveScanFingerprint.build(
                                templateId = template.id,
                                templateVersion = template.version,
                                studentNumber = studentNumber,
                                answerSignature = readSignature(liveResult)
                            )
                            val isNewResult = fingerprint?.let {
                                sessionDeduplicator.registerIfNew(it)
                            } ?: true

                            if (isNewResult) {
                                liveReadCount += 1
                                onLiveRead(liveResult.copy(sequence = liveReadCount))
                            } else {
                                duplicateReadCount += 1
                            }
                        }
                    }
                }
            }

            val nowNs = System.nanoTime()
            val elapsedNs = nowNs - windowStartedAtNs

            if (elapsedNs >= STATS_WINDOW_NS) {
                val elapsedSeconds = elapsedNs / 1_000_000_000.0
                val fps = if (elapsedSeconds > 0.0) frameCount / elapsedSeconds else 0.0

                onStats(
                    CameraFrameStats(
                        width = image.width,
                        height = image.height,
                        rotationDegrees = image.imageInfo.rotationDegrees,
                        fps = fps,
                        averageLuma = sampleAverageLuma(image),
                        openCvReady = detector != null && detectorHealthy,
                        markerCount = latestDetection.detectedMarkers.size,
                        pageQuadrilateral = latestPageBoundary,
                        pageConfidence = latestTrackingConfidence,
                        trackingPhase = latestTrackingPhase,
                        motionRatio = latestMotionRatio,
                        readArmed = scanGate.isArmed(),
                        consensusMatches = readConsensus.currentMatches(),
                        unstableItems = readConsensus.unstableKeys(),
                        registrationUnreliable = latestRegistrationUnreliable,
                        liveReadCount = liveReadCount,
                        duplicateReadCount = duplicateReadCount
                    )
                )

                frameCount = 0
                windowStartedAtNs = nowNs
            }
        } finally {
            image.close()
        }
    }

    /**
     * The four markers are inset from the physical page edge. Once their homography is known we
     * project the four canonical page corners back to the camera image, producing the actual page
     * outline instead of connecting marker centers.
     */
    private fun estimatePageBoundary(detection: FiducialDetectionResult): ImageQuadrilateral? {
        val transform = detection.canonicalRegistration?.templateToImage ?: return null
        val width = template.space.width
        val height = template.space.height
        return ImageQuadrilateral(
            topLeft = transform.mapTemplate(TemplatePoint(0.0, 0.0)) ?: return null,
            topRight = transform.mapTemplate(TemplatePoint(width, 0.0)) ?: return null,
            bottomRight = transform.mapTemplate(TemplatePoint(width, height)) ?: return null,
            bottomLeft = transform.mapTemplate(TemplatePoint(0.0, height)) ?: return null
        )
    }

    private fun readLockedFrame(
        image: ImageProxy,
        detection: FiducialDetectionResult,
        pageConfidence: Double
    ): LiveOmrReadResult? {
        val plane = image.planes.firstOrNull() ?: return null
        if (plane.pixelStride != 1 || image.width <= 0 || image.height <= 0) return null

        val startedAt = System.nanoTime()
        val buffer = plane.buffer.duplicate().apply { rewind() }
        val gray = Mat(
            image.height,
            image.width,
            CvType.CV_8UC1,
            buffer,
            plane.rowStride.toLong()
        )
        var canonical: Mat? = null

        return try {
            canonical = CanonicalImageRectifier.rectify(gray, detection, template) ?: return null
            // One native copy of the rectified frame feeds both readers and the stored scan image.
            val canonicalImage = canonical.toGrayImageOrNull() ?: return null
            val bubbles = bubbleReader.readCanonical(canonicalImage)
            if (bubbles.questions.size != template.bubbleRows.size) return null

            val markGrids = markGridReader.readCanonical(canonicalImage)
            if (markGrids.grids.size != template.markGrids.size) return null

            val confidences = buildList {
                addAll(bubbles.questions.map { it.confidence })
                addAll(markGrids.grids.flatMap { grid -> grid.columns.map { it.confidence } })
            }
            val canonicalLuma = canonicalImage.pixels.takeIf { it.size.toLong() in 1..MAX_SCAN_IMAGE_PIXELS.toLong() }

            LiveOmrReadResult(
                sequence = 0,
                bubbleResult = bubbles,
                markGridResult = markGrids,
                pageConfidence = pageConfidence,
                decisionConfidence = if (confidences.isEmpty()) {
                    0.0
                } else {
                    confidences.average().coerceIn(0.0, 1.0)
                },
                elapsedMs = (System.nanoTime() - startedAt) / 1_000_000.0,
                sourceWidth = image.width,
                sourceHeight = image.height,
                canonicalWidth = if (canonicalLuma != null) canonicalImage.width else 0,
                canonicalHeight = if (canonicalLuma != null) canonicalImage.height else 0,
                canonicalLuma = canonicalLuma
            )
        } finally {
            canonical?.release()
            gray.release()
        }
    }

    private fun readSignature(result: LiveOmrReadResult): String = buildString {
        append(answerSignature(result.bubbleResult))
        result.markGridResult.grids.forEach { grid ->
            append("#")
            append(grid.gridId)
            append(":")
            append(
                grid.columns.joinToString(separator = "|") { column ->
                    "${column.columnId}:${column.state}:${column.selectedValue ?: "-"}"
                }
            )
        }
    }

    /** Per-question view of the signature so the consensus gate can report which answers flicker. */
    private fun readSignatureParts(result: LiveOmrReadResult): Map<String, String> = buildMap {
        result.bubbleResult.questions.forEach { question ->
            put(question.questionId, "${question.state}:${question.selectedChoice ?: "-"}")
        }
        result.markGridResult.grids.forEach { grid ->
            grid.columns.forEach { column ->
                put("${grid.gridId}/${column.columnId}", "${column.state}:${column.selectedValue ?: "-"}")
            }
        }
    }

    /** Failures used to vanish inside runCatching; log the first and then every 100th. */
    private fun logAnalyzerFailure(message: String, error: Throwable) {
        failureCount += 1
        if (failureCount == 1 || failureCount % FAILURE_LOG_INTERVAL == 0) {
            AppLog.warn("CameraFrameAnalyzer", "$message (#$failureCount)", error)
        }
    }

    private fun answerSignature(result: BubbleReadResult): String =
        result.questions.joinToString(separator = "|") { question ->
            "${question.questionId}:${question.state}:${question.selectedChoice ?: "-"}"
        }

    private fun sampleAverageLuma(image: ImageProxy): Int {
        val yPlane = image.planes.firstOrNull() ?: return 0
        val buffer = yPlane.buffer
        val rowStride = yPlane.rowStride
        val pixelStride = yPlane.pixelStride

        if (image.width <= 0 || image.height <= 0 || buffer.limit() <= 0) return 0

        val stepX = max(1, image.width / SAMPLE_COLUMNS)
        val stepY = max(1, image.height / SAMPLE_ROWS)

        var sum = 0L
        var samples = 0

        var y = stepY / 2
        while (y < image.height) {
            val rowOffset = y * rowStride
            var x = stepX / 2

            while (x < image.width) {
                val index = rowOffset + x * pixelStride
                if (index in 0 until buffer.limit()) {
                    sum += buffer.get(index).toInt() and 0xFF
                    samples += 1
                }
                x += stepX
            }
            y += stepY
        }

        return if (samples == 0) 0 else (sum / samples).toInt()
    }

    private companion object {
        const val STATS_WINDOW_NS = 500_000_000L
        const val SAMPLE_COLUMNS = 24
        const val SAMPLE_ROWS = 24

        /** Identical complete reads required on consecutive locked frames before one is accepted. */
        const val REQUIRED_CONSENSUS_FRAMES = 3
        const val FAILURE_LOG_INTERVAL = 100
    }
}

data class LiveOmrReadResult(
    val sequence: Int,
    val bubbleResult: BubbleReadResult,
    val markGridResult: MarkGridReadResult,
    val pageConfidence: Double,
    val decisionConfidence: Double,
    val elapsedMs: Double,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val canonicalWidth: Int = 0,
    val canonicalHeight: Int = 0,
    val canonicalLuma: ByteArray? = null
)

data class CameraFrameStats(
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val fps: Double,
    val averageLuma: Int,
    val openCvReady: Boolean,
    val markerCount: Int,
    /** Estimated physical page boundary in analyzer pixel coordinates; null until all 4 markers exist. */
    val pageQuadrilateral: ImageQuadrilateral?,
    val pageConfidence: Double,
    val trackingPhase: PageTrackingPhase,
    val motionRatio: Double,
    val readArmed: Boolean,
    val consensusMatches: Int,
    val liveReadCount: Int,
    val duplicateReadCount: Int,
    /** Question/column ids whose reading changed between the last two frames (flickering marks). */
    val unstableItems: List<String> = emptyList(),
    /** True when all markers are visible but their corners disagree with the template geometry. */
    val registrationUnreliable: Boolean = false
) {
    companion object {
        val Empty = CameraFrameStats(
            width = 0,
            height = 0,
            rotationDegrees = 0,
            fps = 0.0,
            averageLuma = 0,
            openCvReady = false,
            markerCount = 0,
            pageQuadrilateral = null,
            pageConfidence = 0.0,
            trackingPhase = PageTrackingPhase.SEARCHING,
            motionRatio = 1.0,
            readArmed = true,
            consensusMatches = 0,
            liveReadCount = 0,
            duplicateReadCount = 0
        )
    }
}
