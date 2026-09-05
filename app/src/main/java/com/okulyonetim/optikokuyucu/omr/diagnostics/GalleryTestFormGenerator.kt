package com.okulyonetim.optikokuyucu.omr.diagnostics

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.okulyonetim.optikokuyucu.omr.template.StandardOmrTemplate
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.objdetect.Objdetect
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Creates the first fully phone-only OMR test asset. */
object GalleryTestFormGenerator {
    private val template = StandardOmrTemplate.SAMPLE_20_ABCD

    fun generateBitmap(): Bitmap {
        val width = template.space.width.roundToInt()
        val height = template.space.height.roundToInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val infoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 19f
        }
        val questionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 23f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val choicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 17f
            textAlign = Paint.Align.CENTER
        }
        val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 2.6f
        }

        canvas.drawText("OPTİK OKUYUCU · GALERİ TEST FORMU", 160f, 155f, titlePaint)
        canvas.drawText(
            "Telefonunuzun fotoğraf düzenleyicisinde baloncukların İÇİNİ siyaha boyayın.",
            160f,
            190f,
            infoPaint
        )
        canvas.drawText(
            "Köşelerdeki kare işaretleri silmeyin veya kırpmayın.",
            160f,
            218f,
            infoPaint
        )

        drawFiducials(canvas)

        template.bubbleRows.forEach { row ->
            val first = row.bubbles.first()
            canvas.drawText("${row.id}.", first.center.x.toFloat() - 68f, first.center.y.toFloat() + 8f, questionPaint)

            row.bubbles.forEach { bubble ->
                val cx = bubble.center.x.toFloat()
                val cy = bubble.center.y.toFloat()
                val radius = bubble.radius.toFloat()
                canvas.drawCircle(cx, cy, radius, bubblePaint)
                canvas.drawText(bubble.id, cx, cy - radius - 8f, choicePaint)
            }
        }

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GRAY
            textSize = 16f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "Bu görsel yalnızca algoritma testi içindir · Fiziksel kağıt boyutundan bağımsızdır",
            width / 2f,
            height - 120f,
            footerPaint
        )

        return bitmap
    }

    fun saveToGallery(context: Context): Uri {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "Galeriye doğrudan kaydetme Android 10 ve üstünde destekleniyor."
        }

        val resolver = context.contentResolver
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "optik-test-formu-$timestamp.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OptikOkuyucu")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = requireNotNull(
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ) { "Galeri dosyası oluşturulamadı." }

        try {
            val bitmap = generateBitmap()
            try {
                resolver.openOutputStream(uri, "w").use { output ->
                    requireNotNull(output) { "Galeri çıktı akışı açılamadı." }
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                        "PNG kaydedilemedi."
                    }
                }
            } finally {
                bitmap.recycle()
            }

            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private fun drawFiducials(canvas: Canvas) {
        val dictionary = Objdetect.getPredefinedDictionary(Objdetect.DICT_4X4_50)

        template.fiducials.forEach { spec ->
            val size = spec.bounds.width.roundToInt()
            val markerMat = Mat()
            try {
                dictionary.generateImageMarker(spec.markerId, size, markerMat, 1)
                val markerBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                try {
                    Utils.matToBitmap(markerMat, markerBitmap)
                    canvas.drawBitmap(
                        markerBitmap,
                        spec.bounds.left.toFloat(),
                        spec.bounds.top.toFloat(),
                        null
                    )
                } finally {
                    markerBitmap.recycle()
                }
            } finally {
                markerMat.release()
            }
        }
    }
}
