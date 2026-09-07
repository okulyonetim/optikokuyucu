package com.okulyonetim.optikokuyucu.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.okulyonetim.optikokuyucu.omr.scoring.AnswerKeyPdfExporter
import com.okulyonetim.optikokuyucu.omr.scoring.ManualAnswerSection
import com.okulyonetim.optikokuyucu.omr.scoring.StoredAnswerKey

@Composable
fun AnswerKeyMultiPdfButton(
    title: String,
    matchingKeys: List<StoredAnswerKey>,
    sections: List<ManualAnswerSection>,
    bookletChoices: List<String>,
    onStatus: (String) -> Unit
) {
    val context = LocalContext.current
    val requiredKeys = remember(matchingKeys, bookletChoices) {
        if (bookletChoices.isEmpty()) {
            matchingKeys.firstOrNull { it.variantValue == null }?.let(::listOf).orEmpty()
        } else {
            bookletChoices.mapNotNull { choice ->
                matchingKeys.firstOrNull { it.variantValue == choice }
            }
        }
    }
    val ready = if (bookletChoices.isEmpty()) {
        requiredKeys.isNotEmpty()
    } else {
        requiredKeys.size == bookletChoices.size
    }
    var pendingEntries by remember {
        mutableStateOf<List<AnswerKeyPdfExporter.SheetEntry>?>(null)
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val entries = pendingEntries
        pendingEntries = null
        if (uri == null || entries.isNullOrEmpty()) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri, "w").use { output ->
                requireNotNull(output) { "PDF çıktı akışı açılamadı." }
                AnswerKeyPdfExporter.exportMulti(entries, output)
            }
        }.onSuccess {
            onStatus(
                if (bookletChoices.isEmpty()) {
                    "A4 çoklu cevap anahtarı PDF oluşturuldu · 6 kopya"
                } else {
                    "A4 çoklu cevap anahtarı PDF oluşturuldu · ${bookletChoices.joinToString("+")} · 3 takım"
                }
            )
        }.onFailure { error ->
            onStatus("Cevap anahtarı PDF oluşturulamadı: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        enabled = ready,
        shape = RoundedCornerShape(16.dp),
        onClick = {
            if (!ready) {
                val missing = bookletChoices.filter { choice -> requiredKeys.none { it.variantValue == choice } }
                onStatus("PDF için önce eksik kitapçık anahtarlarını kaydedin: ${missing.joinToString(", ")}")
                return@OutlinedButton
            }
            pendingEntries = requiredKeys.map { key ->
                AnswerKeyPdfExporter.SheetEntry(
                    key = key,
                    title = title,
                    sections = sections
                )
            }
            launcher.launch(answerKeyPdfFileName(title, bookletChoices))
        }
    ) {
        Text(
            if (bookletChoices.isEmpty()) {
                "A4 Çoklu Cevap Anahtarı PDF · 6 Kopya"
            } else {
                "A4 Çoklu Cevap Anahtarı PDF · ${bookletChoices.joinToString(" + ")}"
            }
        )
    }
}

private fun answerKeyPdfFileName(title: String, bookletChoices: List<String>): String {
    val safe = title
        .replace(Regex("[^\\p{L}\\p{N}._-]+"), "_")
        .trim('_')
        .ifBlank { "cevap-anahtari" }
    val booklet = bookletChoices.takeIf { it.isNotEmpty() }
        ?.joinToString("-")
        ?.let { "-$it" }
        .orEmpty()
    return "$safe-cevap-anahtari$booklet-A4-coklu.pdf"
}
