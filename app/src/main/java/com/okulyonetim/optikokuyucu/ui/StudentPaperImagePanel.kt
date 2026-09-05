package com.okulyonetim.optikokuyucu.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.okulyonetim.optikokuyucu.omr.designer.FileDesignerDocumentRepository
import com.okulyonetim.optikokuyucu.omr.results.FileScanImageRepository
import com.okulyonetim.optikokuyucu.omr.results.RecordedAnswer
import com.okulyonetim.optikokuyucu.omr.results.RecordedAnswerState
import com.okulyonetim.optikokuyucu.omr.results.RecordedMarkColumn
import com.okulyonetim.optikokuyucu.omr.results.RecordedMarkState
import com.okulyonetim.optikokuyucu.omr.results.ScanRecord
import com.okulyonetim.optikokuyucu.omr.results.StoredScanImage
import com.okulyonetim.optikokuyucu.omr.scoring.QuestionEvaluation
import com.okulyonetim.optikokuyucu.omr.scoring.QuestionEvaluationState
import com.okulyonetim.optikokuyucu.omr.template.ActiveOmrTemplateResolver
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSelection
import com.okulyonetim.optikokuyucu.omr.template.BubbleSpec
import com.okulyonetim.optikokuyucu.omr.template.OmrTemplate
import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint

private val OverlayGreen = Color(0xFF28B84A)
private val OverlayRed = Color(0xFFF0443E)
private val OverlayBlue = Color(0xFF145BFF)
private val OverlayOrange = Color(0xFFF39B25)

/**
 * Reference-style result image: canonical sheet plus recognition/scoring overlay.
 * Because both layers use the same template coordinate space, circles stay aligned after camera
 * perspective correction and regardless of A4/A5 or portrait/landscape printing.
 */
@Composable
fun StudentPaperImagePanel(
    scanRecordId: String,
    record: ScanRecord,
    evaluations: Map<String, QuestionEvaluation>,
    templateSelection: ActiveTemplateSelection
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val storedImage = remember(scanRecordId) {
        FileScanImageRepository(appContext).load(scanRecordId)
    }
    val template = remember(templateSelection) {
        ActiveOmrTemplateResolver.resolve(
            selection = templateSelection,
            savedDocuments = FileDesignerDocumentRepository(appContext).list()
        )?.template
    }

    if (storedImage == null) {
        MissingScanImageState(record.sourceWidth, record.sourceHeight)
        return
    }

    val imageBitmap = remember(storedImage.scanRecordId, storedImage.width, storedImage.height) {
        grayscaleBitmap(storedImage).asImageBitmap()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OverlayLegend()

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(storedImage.width.toFloat() / storedImage.height.toFloat())
                    .background(Color.White)
            ) {
                Image(
                    modifier = Modifier.fillMaxSize(),
                    bitmap = imageBitmap,
                    contentDescription = "Okunan optik kağıt",
                    contentScale = ContentScale.FillBounds
                )
                if (template != null) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRecognitionOverlay(
                            template = template,
                            record = record,
                            evaluations = evaluations
                        )
                    }
                }
            }
        }

        Text(
            text = "Düzeltilmiş kağıt · ${storedImage.width} × ${storedImage.height}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (template == null) {
            Text(
                text = "Optik form sürümü bulunamadığı için görüntü gösteriliyor ancak işaret katmanı çizilemiyor.",
                style = MaterialTheme.typography.bodySmall,
                color = OverlayOrange
            )
        } else {
            Text(
                text = "Yeşil doğru/beklenen, kırmızı yanlış, turuncu belirsiz/çift, mavi öğrenci ve kitapçık alanlarını gösterir.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OverlayLegend() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendItem("Doğru", OverlayGreen)
            LegendItem("Yanlış", OverlayRed)
            LegendItem("Şüpheli", OverlayOrange)
            LegendItem("Bilgi", OverlayBlue)
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(modifier = Modifier.size(10.dp), shape = CircleShape, color = color) {}
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun MissingScanImageState(sourceWidth: Int, sourceHeight: Int) {
    Column(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Resim", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Text("Kaynak ölçüsü: $sourceWidth × $sourceHeight")
                Text(
                    "Bu kağıt görüntü saklama özelliğinden önce okunmuş. Yeni sınav taramalarında düzeltilmiş kağıt ve renkli işaret katmanı burada otomatik gösterilir.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun grayscaleBitmap(image: StoredScanImage): Bitmap {
    val pixels = IntArray(image.luma.size)
    image.luma.forEachIndexed { index, byte ->
        val value = byte.toInt() and 0xFF
        pixels[index] = android.graphics.Color.rgb(value, value, value)
    }
    return Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888).also { bitmap ->
        bitmap.setPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
    }
}

private fun DrawScope.drawRecognitionOverlay(
    template: OmrTemplate,
    record: ScanRecord,
    evaluations: Map<String, QuestionEvaluation>
) {
    val scaleX = size.width / template.space.width.toFloat()
    val scaleY = size.height / template.space.height.toFloat()
    val questionRows = template.bubbleRows.associateBy { it.id }
    val gridSpecs = template.markGrids.associateBy { it.id }

    record.answers.forEach { answer ->
        val row = questionRows[answer.questionId] ?: return@forEach
        val evaluation = evaluations[answer.questionId]
        val selectedChoice = answer.selectedChoice

        if (selectedChoice != null) {
            row.bubbles.firstOrNull { it.id == selectedChoice }?.let { bubble ->
                val color = answerOverlayColor(answer, evaluation)
                drawBubbleMarker(bubble, color, scaleX, scaleY, strong = true)
            }
        } else if (answer.state == RecordedAnswerState.DOUBLE_MARK) {
            answer.choiceScores.entries
                .sortedByDescending { it.value }
                .take(2)
                .filter { it.value >= DOUBLE_OVERLAY_SCORE }
                .forEach { candidate ->
                    row.bubbles.firstOrNull { it.id == candidate.key }?.let { bubble ->
                        drawBubbleMarker(bubble, OverlayOrange, scaleX, scaleY, strong = true)
                    }
                }
        }

        val expectedChoice = evaluation?.expectedChoice
        if (expectedChoice != null && expectedChoice != selectedChoice) {
            row.bubbles.firstOrNull { it.id == expectedChoice }?.let { bubble ->
                drawBubbleMarker(bubble, OverlayGreen, scaleX, scaleY, strong = false)
            }
        }
    }

    record.markGrids.forEach { recordedGrid ->
        val gridSpec = gridSpecs[recordedGrid.gridId] ?: return@forEach
        recordedGrid.columns.forEach { recordedColumn ->
            val columnSpec = gridSpec.columns.firstOrNull { it.id == recordedColumn.columnId }
                ?: return@forEach
            drawMarkColumn(columnSpec.marks, recordedColumn, scaleX, scaleY)
        }
    }
}

private fun DrawScope.drawMarkColumn(
    marks: List<BubbleSpec>,
    recorded: RecordedMarkColumn,
    scaleX: Float,
    scaleY: Float
) {
    val selected = recorded.selectedValue
    if (selected != null) {
        marks.firstOrNull { it.id == selected }?.let { bubble ->
            val color = when (recorded.state) {
                RecordedMarkState.MARKED -> OverlayBlue
                RecordedMarkState.SUSPICIOUS,
                RecordedMarkState.DOUBLE_MARK -> OverlayOrange
                RecordedMarkState.BLANK -> OverlayBlue
            }
            drawBubbleMarker(bubble, color, scaleX, scaleY, strong = true)
        }
    } else if (recorded.state == RecordedMarkState.DOUBLE_MARK) {
        recorded.scores.entries
            .sortedByDescending { it.value }
            .take(2)
            .filter { it.value >= DOUBLE_OVERLAY_SCORE }
            .forEach { candidate ->
                marks.firstOrNull { it.id == candidate.key }?.let { bubble ->
                    drawBubbleMarker(bubble, OverlayOrange, scaleX, scaleY, strong = true)
                }
            }
    }
}

private fun DrawScope.drawBubbleMarker(
    bubble: BubbleSpec,
    color: Color,
    scaleX: Float,
    scaleY: Float,
    strong: Boolean
) {
    val center = mapPoint(bubble.center, scaleX, scaleY)
    val radius = bubble.radius.toFloat() * ((scaleX + scaleY) / 2f) * 1.35f
    if (strong) {
        drawCircle(
            color = color.copy(alpha = 0.12f),
            radius = radius * 0.72f,
            center = center
        )
    }
    drawCircle(
        color = color,
        radius = radius,
        center = center,
        style = Stroke(width = if (strong) 2.6.dp.toPx() else 1.8.dp.toPx())
    )
}

private fun DrawScope.mapPoint(point: TemplatePoint, scaleX: Float, scaleY: Float) =
    androidx.compose.ui.geometry.Offset(
        x = point.x.toFloat() * scaleX,
        y = point.y.toFloat() * scaleY
    )

private fun answerOverlayColor(
    answer: RecordedAnswer,
    evaluation: QuestionEvaluation?
): Color = when (evaluation?.state) {
    QuestionEvaluationState.CORRECT -> OverlayGreen
    QuestionEvaluationState.WRONG -> OverlayRed
    QuestionEvaluationState.DOUBLE_MARK,
    QuestionEvaluationState.SUSPICIOUS -> OverlayOrange
    QuestionEvaluationState.BLANK -> OverlayBlue
    QuestionEvaluationState.NO_KEY,
    null -> when (answer.state) {
        RecordedAnswerState.MARKED -> OverlayBlue
        RecordedAnswerState.BLANK -> OverlayBlue
        RecordedAnswerState.DOUBLE_MARK,
        RecordedAnswerState.SUSPICIOUS -> OverlayOrange
    }
}

private const val DOUBLE_OVERLAY_SCORE = 0.10
