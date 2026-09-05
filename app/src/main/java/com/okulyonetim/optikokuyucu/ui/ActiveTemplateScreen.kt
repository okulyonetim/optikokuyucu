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
import androidx.compose.material3.Button
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
import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocument
import com.okulyonetim.optikokuyucu.omr.designer.DesignerStarterTemplates
import com.okulyonetim.optikokuyucu.omr.designer.FileDesignerDocumentRepository
import com.okulyonetim.optikokuyucu.omr.template.ActiveOmrTemplateDefaults
import com.okulyonetim.optikokuyucu.omr.template.ActiveOmrTemplateResolver
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSelection
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSource
import com.okulyonetim.optikokuyucu.omr.template.FileActiveTemplateSelectionRepository

@Composable
fun ActiveTemplateScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val selectionRepository = remember(context) {
        FileActiveTemplateSelectionRepository(appContext)
    }
    val documentRepository = remember(context) {
        FileDesignerDocumentRepository(appContext)
    }
    val starters = remember { DesignerStarterTemplates.all() }
    var savedDocuments by remember { mutableStateOf(documentRepository.list()) }
    var selected by remember { mutableStateOf(selectionRepository.load()) }
    var status by remember { mutableStateOf("") }

    val resolved = ActiveOmrTemplateResolver.resolveOrDefault(
        selection = selected,
        savedDocuments = savedDocuments,
        starterDocuments = starters
    )

    fun choose(selection: ActiveTemplateSelection, name: String) {
        runCatching { selectionRepository.save(selection) }
            .onSuccess {
                selected = selection
                status = "Aktif form değiştirildi · $name"
            }
            .onFailure { error ->
                status = "Aktif form kaydedilemedi: ${error.message ?: error.javaClass.simpleName}"
            }
    }

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
            Text("Aktif Form / Şablon", style = MaterialTheme.typography.titleLarge)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text("Şu anda kullanılacak form", style = MaterialTheme.typography.titleSmall)
                Text(resolved.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${resolved.template.bubbleRows.size} soru · " +
                        "${resolved.template.markGrids.size} işaret alanı · " +
                        "${resolved.template.id} · v${resolved.template.version}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (resolved.fellBackToDefault) {
                    Text(
                        "Önceki seçim bulunamadığı için güvenli varsayılan form kullanılıyor.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (status.isNotBlank()) {
                    Text(status, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Text(
            "Bu seçim normal kamera, galeri öğrenci okuması ve galeriden cevap anahtarı yakalamada ortak kullanılacaktır.",
            style = MaterialTheme.typography.bodyMedium
        )

        Text("Varsayılan üretim formu", style = MaterialTheme.typography.titleMedium)
        TemplateChoiceButton(
            selected = resolved.selection == ActiveOmrTemplateDefaults.selection,
            title = ActiveOmrTemplateDefaults.displayName,
            subtitle = "20 soru · öğrenci no · A/B kitapçık",
            onClick = {
                choose(
                    ActiveOmrTemplateDefaults.selection,
                    ActiveOmrTemplateDefaults.displayName
                )
            }
        )

        Text("Başlangıç şablonları", style = MaterialTheme.typography.titleMedium)
        starters.forEach { document ->
            val selection = documentSelection(document)
            TemplateChoiceButton(
                selected = resolved.selection == selection,
                title = document.name,
                subtitle = "${document.id} · v${document.version}",
                onClick = { choose(selection, document.name) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Cihazda kaydedilen tasarımlar", style = MaterialTheme.typography.titleMedium)
            TextButton(
                onClick = {
                    savedDocuments = documentRepository.list()
                    status = "Kayıtlı tasarımlar yenilendi"
                }
            ) {
                Text("Yenile")
            }
        }

        if (savedDocuments.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    modifier = Modifier.padding(14.dp),
                    text = "Henüz cihazda kaydedilmiş özel tasarım yok. Optik Form Tasarımcısı'nda bir form kaydedebilirsiniz.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            savedDocuments.forEach { document ->
                val selection = documentSelection(document)
                TemplateChoiceButton(
                    selected = resolved.selection == selection,
                    title = document.name,
                    subtitle = "${document.id} · v${document.version} · cihazda kayıtlı",
                    onClick = { choose(selection, document.name) }
                )
            }
        }
    }
}

@Composable
private fun TemplateChoiceButton(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    if (selected) {
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text("✓ $title")
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    } else {
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(title)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun documentSelection(document: DesignerDocument): ActiveTemplateSelection =
    ActiveTemplateSelection(
        source = ActiveTemplateSource.DESIGNER_DOCUMENT,
        templateId = document.id,
        templateVersion = document.version
    )
