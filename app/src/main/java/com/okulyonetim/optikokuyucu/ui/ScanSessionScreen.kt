package com.okulyonetim.optikokuyucu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.okulyonetim.optikokuyucu.omr.results.FileScanRecordRepository
import com.okulyonetim.optikokuyucu.omr.results.RecordedAnswer
import com.okulyonetim.optikokuyucu.omr.results.RecordedAnswerState
import com.okulyonetim.optikokuyucu.omr.results.RecordedMarkState
import com.okulyonetim.optikokuyucu.omr.results.ScanRecord
import com.okulyonetim.optikokuyucu.omr.results.ScanSource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ScanSessionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) {
        FileScanRecordRepository(context.applicationContext)
    }
    var records by remember { mutableStateOf(repository.list()) }
    var expandedRecordId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("← Geri") }
            Text("Tarama Oturumu", style = MaterialTheme.typography.titleLarge)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Cihazdaki ham OMR kayıtları", style = MaterialTheme.typography.titleSmall)
                Text(
                    "${records.size} kayıt · internet bağlantısı gerekmez",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Ham okuma sonucu cevap anahtarından bağımsız saklanır; daha sonra yeniden puanlanabilir.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { records = repository.list() }
                ) {
                    Text("Kayıtları yenile")
                }
            }
        }

        if (records.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Henüz tarama kaydı yok", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Normal canlı kamerada kabul edilen ilk form burada otomatik görünecek.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            records.forEachIndexed { index, record ->
                ScanRecordCard(
                    ordinal = records.size - index,
                    record = record,
                    expanded = expandedRecordId == record.id,
                    onToggleExpanded = {
                        expandedRecordId = if (expandedRecordId == record.id) null else record.id
                    }
                )
            }
        }
    }
}

@Composable
private fun ScanRecordCard(
    ordinal: Int,
    record: ScanRecord,
    expanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    val studentNumber = record.grid("studentNumber")?.value
    val booklet = record.grid("booklet")?.value
    val marked = record.answers.count { it.state == RecordedAnswerState.MARKED }
    val blank = record.answers.count { it.state == RecordedAnswerState.BLANK }
    val doubleMark = record.answers.count { it.state == RecordedAnswerState.DOUBLE_MARK }
    val suspicious = record.answers.count { it.state == RecordedAnswerState.SUSPICIOUS }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Okuma #$ordinal", style = MaterialTheme.typography.titleMedium)
                Text(formatCapturedAt(record.capturedAtEpochMs), style = MaterialTheme.typography.labelMedium)
            }

            Text(
                "Öğrenci No: ${studentNumber ?: "—"} · Kitapçık: ${booklet ?: "—"}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "${record.templateId} · v${record.templateVersion} · ${sourceLabel(record.source)}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "İşaretli $marked · Boş $blank · Çift $doubleMark · Şüpheli $suspicious",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "Geometri ${formatConfidence(record.pageConfidence)} · " +
                    "karar ${formatConfidence(record.decisionConfidence)} · " +
                    String.format(Locale.US, "%.1f ms", record.elapsedMs),
                style = MaterialTheme.typography.bodySmall
            )

            record.markGrids
                .filterNot { it.gridId == "studentNumber" || it.gridId == "booklet" }
                .forEach { grid ->
                    Text(
                        "${grid.gridId}: ${grid.value ?: markGridStateSummary(grid.columns.map { it.state })}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onToggleExpanded
            ) {
                Text(if (expanded) "Ham cevapları gizle" else "Ham cevapları göster")
            }

            if (expanded) {
                Text("Ham cevaplar", style = MaterialTheme.typography.titleSmall)
                answerLines(record.answers).forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall)
                }

                if (record.markGrids.isNotEmpty()) {
                    Text("İşaret alanları", style = MaterialTheme.typography.titleSmall)
                    record.markGrids.forEach { grid ->
                        val columns = grid.columns.joinToString(" · ") { column ->
                            val value = column.selectedValue ?: stateShortLabel(column.state)
                            "${column.columnId}:$value"
                        }
                        Text("${grid.gridId} · $columns", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private fun answerLines(answers: List<RecordedAnswer>): List<String> =
    answers.chunked(10).map { chunk ->
        chunk.joinToString("  ") { answer ->
            val value = when (answer.state) {
                RecordedAnswerState.MARKED -> answer.selectedChoice ?: "?"
                RecordedAnswerState.BLANK -> "-"
                RecordedAnswerState.DOUBLE_MARK -> "Ç"
                RecordedAnswerState.SUSPICIOUS -> "?"
            }
            "${answer.questionId}:$value"
        }
    }

private fun sourceLabel(source: ScanSource): String = when (source) {
    ScanSource.LIVE_CAMERA -> "Canlı kamera"
    ScanSource.GALLERY -> "Galeri"
}

private fun formatCapturedAt(epochMs: Long): String =
    SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale("tr", "TR")).format(Date(epochMs))

private fun formatConfidence(value: Double?): String =
    value?.let { String.format(Locale.US, "%.0f%%", it * 100.0) } ?: "—"

private fun stateShortLabel(state: RecordedMarkState): String = when (state) {
    RecordedMarkState.MARKED -> "✓"
    RecordedMarkState.BLANK -> "-"
    RecordedMarkState.DOUBLE_MARK -> "Ç"
    RecordedMarkState.SUSPICIOUS -> "?"
}

private fun markGridStateSummary(states: List<RecordedMarkState>): String {
    val blank = states.count { it == RecordedMarkState.BLANK }
    val doubleMark = states.count { it == RecordedMarkState.DOUBLE_MARK }
    val suspicious = states.count { it == RecordedMarkState.SUSPICIOUS }
    return "belirsiz · boş $blank · çift $doubleMark · şüpheli $suspicious"
}
