package com.okulyonetim.optikokuyucu.omr.bubble

import com.okulyonetim.optikokuyucu.omr.template.BubbleRowSpec
import com.okulyonetim.optikokuyucu.omr.template.BubbleSpec
import com.okulyonetim.optikokuyucu.omr.template.OmrTemplate
import com.okulyonetim.optikokuyucu.omr.template.StandardOmrTemplate
import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint
import java.util.Random
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws a rectified (canonical-space) answer sheet into a byte array so the real scoring and
 * classification code can be measured without a camera, OpenCV or a device.
 *
 * Every bubble has a printed outline ring and a dark printed glyph core; filled marks are discs with
 * speckled coverage and positional jitter. Optional illumination falloff, blur and sensor noise
 * approximate a phone photo. Everything is seeded, so results are reproducible bit-for-bit.
 */
internal object SyntheticSheetRenderer {
    data class Look(
        val fillMin: Double,
        val fillMax: Double,
        val noise: Double,
        val blurPasses: Int,
        val jitter: Double
    ) {
        companion object {
            /** Dark pen/marker fill, light blur. */
            val CLEAN = Look(fillMin = 35.0, fillMax = 100.0, noise = 3.0, blurPasses = 1, jitter = 0.8)

            /** Light pencil, heavier blur, off-center fills: deliberately hostile. */
            val PENCIL = Look(fillMin = 90.0, fillMax = 150.0, noise = 6.0, blurPasses = 3, jitter = 1.5)
        }
    }

    enum class Kind { SINGLE, BLANK, DOUBLE }

    class Sheet(
        val image: GrayImage,
        val kinds: Map<String, Kind>,
        val marked: Map<String, Set<String>>
    )

    /** A dense multi-column form (e.g. 100 questions) with the standard fiducials. */
    fun denseTemplate(radius: Double, columns: Int, rowsPerColumn: Int, choices: Int): OmrTemplate {
        val base = StandardOmrTemplate.DEFAULT
        val rows = ArrayList<BubbleRowSpec>()
        var question = 1
        val columnWidth = 800.0 / columns
        for (column in 0 until columns) {
            for (row in 0 until rowsPerColumn) {
                val y = 200.0 + row * (1100.0 / rowsPerColumn)
                val bubbles = (0 until choices).map { index ->
                    BubbleSpec(
                        id = "$question-${'A' + index}",
                        center = TemplatePoint(110.0 + column * columnWidth + index * radius * 3.2, y),
                        radius = radius
                    )
                }
                rows += BubbleRowSpec("$question", bubbles)
                question += 1
            }
        }
        return OmrTemplate("synthetic-dense", 1, base.space, base.fiducials, rows)
    }

    fun choiceOf(bubbleId: String): String = bubbleId.substringAfterLast('-')

    /** 76% single answers, 12% blank, 12% double marks. */
    fun render(template: OmrTemplate, seed: Long, look: Look): Sheet {
        val random = Random(seed)
        val choices = template.bubbleRows.first().bubbles.map { choiceOf(it.id) }
        val kinds = HashMap<String, Kind>()
        val marked = HashMap<String, Set<String>>()
        for (row in template.bubbleRows) {
            val p = random.nextDouble()
            when {
                p < 0.76 -> {
                    kinds[row.id] = Kind.SINGLE
                    marked[row.id] = setOf(choices[random.nextInt(choices.size)])
                }
                p < 0.88 -> {
                    kinds[row.id] = Kind.BLANK
                    marked[row.id] = emptySet()
                }
                else -> {
                    kinds[row.id] = Kind.DOUBLE
                    val first = random.nextInt(choices.size)
                    var second = random.nextInt(choices.size)
                    while (second == first) second = random.nextInt(choices.size)
                    marked[row.id] = setOf(choices[first], choices[second])
                }
            }
        }
        return Sheet(draw(template, marked, random, look), kinds, marked)
    }

    private fun draw(
        template: OmrTemplate,
        marked: Map<String, Set<String>>,
        random: Random,
        look: Look
    ): GrayImage {
        val width = template.space.width.roundToInt()
        val height = template.space.height.roundToInt()
        var pixels = DoubleArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val illumination = 0.86 + 0.14 * (x / width.toDouble()) * (0.9 + 0.1 * (y / height.toDouble()))
                pixels[y * width + x] = 238.0 * illumination
            }
        }

        fun disc(cx: Double, cy: Double, r: Double, value: Double, coverage: Double) {
            for (y in max(0, (cy - r - 1).toInt())..min(height - 1, (cy + r + 1).toInt())) {
                for (x in max(0, (cx - r - 1).toInt())..min(width - 1, (cx + r + 1).toInt())) {
                    if (hypot(x + 0.5 - cx, y + 0.5 - cy) <= r && random.nextDouble() < coverage) {
                        pixels[y * width + x] = value
                    }
                }
            }
        }

        fun ring(cx: Double, cy: Double, r: Double, strokeWidth: Double, value: Double) {
            for (y in max(0, (cy - r - 2).toInt())..min(height - 1, (cy + r + 2).toInt())) {
                for (x in max(0, (cx - r - 2).toInt())..min(width - 1, (cx + r + 2).toInt())) {
                    if (abs(hypot(x + 0.5 - cx, y + 0.5 - cy) - r) <= strokeWidth / 2) {
                        pixels[y * width + x] = value
                    }
                }
            }
        }

        for (row in template.bubbleRows) {
            for (bubble in row.bubbles) {
                ring(bubble.center.x, bubble.center.y, bubble.radius, 1.6, 70.0)
                disc(bubble.center.x, bubble.center.y, bubble.radius * 0.30, 95.0, 1.0) // printed glyph
                if (choiceOf(bubble.id) in (marked[row.id] ?: emptySet())) {
                    val fillRadius = bubble.radius * (0.80 + random.nextDouble() * 0.2)
                    val value = look.fillMin + random.nextDouble() * (look.fillMax - look.fillMin)
                    disc(
                        bubble.center.x + random.nextGaussian() * look.jitter,
                        bubble.center.y + random.nextGaussian() * look.jitter,
                        fillRadius,
                        value,
                        0.88
                    )
                }
            }
        }

        repeat(look.blurPasses) { pixels = boxBlur(pixels, width, height) }
        val bytes = ByteArray(width * height) { index ->
            (pixels[index] + random.nextGaussian() * look.noise).roundToInt().coerceIn(0, 255).toByte()
        }
        return GrayImage(width, height, bytes)
    }

    private fun boxBlur(source: DoubleArray, width: Int, height: Int): DoubleArray {
        val horizontal = DoubleArray(source.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var sum = 0.0
                var count = 0
                for (dx in -1..1) {
                    val xx = x + dx
                    if (xx in 0 until width) {
                        sum += source[y * width + xx]
                        count += 1
                    }
                }
                horizontal[y * width + x] = sum / count
            }
        }
        val result = DoubleArray(source.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var sum = 0.0
                var count = 0
                for (dy in -1..1) {
                    val yy = y + dy
                    if (yy in 0 until height) {
                        sum += horizontal[yy * width + x]
                        count += 1
                    }
                }
                result[y * width + x] = sum / count
            }
        }
        return result
    }
}
