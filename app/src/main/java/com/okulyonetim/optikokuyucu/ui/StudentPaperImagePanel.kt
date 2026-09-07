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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.okulyonetim.optikokuyucu.omr.designer.FileDesignerDocumentRepository
import com.okulyonetim.optikokuyucu.omr.results.FileScanImageRepository
import com.okulyonetim.optikokuyucu.omr.results.RecordedAnswerState
import com.okulyonetim.optikokuyucu.omr.results.RecordedMarkColumn
import com.okulyonetim.optikokuyucu.omr.results.RecordedMarkState
import com.okulyonetim.optikokuyucu.omr.results.ScanRecord
import com.okulyonetim.optikokuyucu.omr.results.StoredScanImage
import com.okulyonetim.optikokuyucu.omr.scoring.AnswerKeyChoiceCodec
import com.okulyonetim.optikokuyucu.omr.scoring.QuestionEvaluation
import com.okulyonetim.optikokuyucu.omr.scoring.QuestionEvaluationState
import com.okulyonetim.optikokuyucu.omr.template.ActiveOmrTemplateResolver
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSelection
import com.okulyonetim.optikokuyucu.omr.template.BubbleSpec
import com.okulyonetim.optikokuyucu.omr.template.OmrTemplate
import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint

private val OverlayKeyGreen = Color(0xFF16A05D)
private val OverlayCorrectGreen = Color(0xFF20A861)
private val OverlayWrongRed = Color(0xFFE2464C)
private val OverlayDoubleYellow = Color(0xFFF4B740)
private val OverlayInfoBlue = Color(0xFF3B82F6)

/**
 * Canonical sheet plus a bubble-sized scoring overlay.
 *
 * Visual contract:
 * - answer-key choices: green outline only
 * - correct student choice: translucent green fill
 * - wrong student choice: translucent red fill
 * - student double/suspicious marks: translucent yellow fill
 * - student/booklet information grids: subtle blue bubble-sized mark
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
                color = OverlayDoubleYellow
            )
        } else {
            Text(
                text = "Yeşil kenarlık cevap anahtarı; yeşil dolgu doğru, kırmızı dolgu yanlış, sarı dolgu çift/şüpheli cevaptır.",
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendItem("Anahtar", OverlayKeyGreen)
            LegendItem("Doğru", OverlayCorrectGreen)
            LegendItem("Yanlış", OverlayWrongRed)
            LegendItem("Çift", OverlayDoubleYellow)
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
                when (evaluation?.state) {
                    QuestionEvaluationState.CORRECT ->
                        drawBubbleFill(bubble, OverlayCorrectGreen.copy(alpha = 0.34f), scaleX, scaleY)
                    QuestionEvaluationState.WRONG ->
                        drawBubbleFill(bubble, OverlayWrongRed.copy(alpha = 0.34f), scaleX, scaleY)
                    QuestionEvaluationState.SUSPICIOUS,
                    QuestionEvaluationState.DOUBLE_MARK ->
                        drawBubbleFill(bubble, OverlayDoubleYellow.copy(alpha = 0.38f), scaleX, scaleY)
                    QuestionEvaluationState.NO_KEY,
                    QuestionEvaluationState.BLANK,
                    null ->
                        drawBubbleFill(bubble, OverlayInfoBlue.copy(alpha = 0.18f), scaleX, scaleY)
                }
            }
        }

        if (answer.state == RecordedAnswerState.DOUBLE_MARK) {
            answer.choiceScores.entries
                .sortedByDescending { it.value }
                .take(2)
                .filter { it.value >= DOUBLE_OVERLAY_SCORE }
                .forEach { candidate ->
                    row.bubbles.firstOrNull { it.id == candidate.key }?.let { bubble ->
                        drawBubbleFill(bubble, OverlayDoubleYellow.copy(alpha = 0.40f), scaleX, scaleY)
                    }
                }
        }

        // Draw the answer key last so its green border stays crisp above all fills. Multi-answer
        // keys draw a green outline around every accepted choice.
        AnswerKeyChoiceCodec.decode(evaluation?.expectedChoice).forEach { expectedChoice ->
            row.bubbles.firstOrNull { it.id == expectedChoice }?.let { bubble ->
                drawBubbleOutline(bubble, OverlayKeyGreen, scaleX, scaleY, 1.5.dp.toPx())
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
                RecordedMarkState.MARKED -> OverlayInfoBlue
                RecordedMarkState.SUSPICIOUS,
                RecordedMarkState.DOUBLE_MARK -> OverlayDoubleYellow
                RecordedMarkState.BLANK -> OverlayInfoBlue
            }
            drawBubbleFill(bubble, color.copy(alpha = 0.24f), scaleX, scaleY)
            drawBubbleOutline(bubble, color, scaleX, scaleY, 1.1.dp.toPx())
        }
    } else if (recorded.state == RecordedMarkState.DOUBLE_MARK) {
        recorded.scores.entries
            .sortedByDescending { it.value }
            .take(2)
            .filter { it.value >= DOUBLE_OVERLAY_SCORE }
            .forEach { candidate ->
                marks.firstOrNull { it.id == candidate.key }?.let { bubble ->
                    drawBubbleFill(bubble, OverlayDoubleYellow.copy(alpha = 0.36f), scaleX, scaleY)
                }
            }
    }
}

private fun DrawScope.drawBubbleFill(
    bubble: BubbleSpec,
    color: Color,
    scaleX: Float,
    scaleY: Float
) {
    val center = mapPoint(bubble.center, scaleX, scaleY)
    val radiusX = bubble.radius.toFloat() * scaleX
    val radiusY = bubble.radius.toFloat() * scaleY
    drawOval(
        color = color,
        topLeft = Offset(center.x - radiusX, center.y - radiusY),
        size = Size(radiusX * 2f, radiusY * 2f)
    )
}

private fun DrawScope.drawBubbleOutline(
    bubble: BubbleSpec,
    color: Color,
    scaleX: Float,
    scaleY: Float,
    strokeWidth: Float
) {
    val center = mapPoint(bubble.center, scaleX, scaleY)
    val radiusX = bubble.radius.toFloat() * scaleX
    val radiusY = bubble.radius.toFloat() * scaleY
    drawOval(
        color = color,
        topLeft = Offset(center.x - radiusX, center.y - radiusY),
        size = Size(radiusX * 2f, radiusY * 2f),
        style = Stroke(width = strokeWidth)
    )
}

private fun DrawScope.mapPoint(point: TemplatePoint, scaleX: Float, scaleY: Float) =
    Offset(
        x = point.x.toFloat() * scaleX,
        y = point.y.toFloat() * scaleY
    )

private const val DOUBLE_OVERLAY_SCORE = 0.10
