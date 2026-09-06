package com.okulyonetim.optikokuyucu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocument
import com.okulyonetim.optikokuyucu.omr.designer.DesignerStarterTemplates
import com.okulyonetim.optikokuyucu.omr.designer.FileDesignerDocumentRepository
import com.okulyonetim.optikokuyucu.omr.template.ActiveOmrTemplateDefaults
import com.okulyonetim.optikokuyucu.omr.template.ActiveOmrTemplateResolver
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSelection
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSource
import com.okulyonetim.optikokuyucu.omr.template.FileActiveTemplateSelectionRepository
import java.util.Locale

private enum class FormLibraryFilter { ALL, READY, SAVED }

@Composable
fun ActiveTemplateScreen(
    onBack: () -> Unit,
    onCreateForm: () -> Unit = {}
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val selectionRepository = remember(context) { FileActiveTemplateSelectionRepository(appContext) }
    val documentRepository = remember(context) { FileDesignerDocumentRepository(appContext) }
    val starters = remember { DesignerStarterTemplates.all() }

    var savedDocuments by remember { mutableStateOf(documentRepository.list()) }
    var selected by remember { mutableStateOf(selectionRepository.load()) }
    var status by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(FormLibraryFilter.ALL) }

    val resolved = ActiveOmrTemplateResolver.resolveOrDefault(
        selection = selected,
        savedDocuments = savedDocuments,
        starterDocuments = starters
    )

    fun choose(selection: ActiveTemplateSelection, name: String) {
        runCatching { selectionRepository.save(selection) }
            .onSuccess {
                selected = selection
                status = "$name aktif form olarak seçildi."
            }
            .onFailure { error ->
                status = "Form seçilemedi: ${error.message ?: error.javaClass.simpleName}"
            }
    }

    val normalizedQuery = query.trim().lowercase(Locale("tr", "TR"))
    val visibleStarters = starters.filter { document ->
        filter != FormLibraryFilter.SAVED && (
            normalizedQuery.isBlank() ||
                document.name.lowercase(Locale("tr", "TR")).contains(normalizedQuery) ||
                document.id.lowercase(Locale("tr", "TR")).contains(normalizedQuery)
            )
    }
    val defaultVisible = filter != FormLibraryFilter.SAVED && (
        normalizedQuery.isBlank() ||
            ActiveOmrTemplateDefaults.displayName.lowercase(Locale("tr", "TR")).contains(normalizedQuery)
        )
    val visibleSaved = savedDocuments.filter { document ->
        filter != FormLibraryFilter.READY && (
            normalizedQuery.isBlank() ||
                document.name.lowercase(Locale("tr", "TR")).contains(normalizedQuery) ||
                document.id.lowercase(Locale("tr", "TR")).contains(normalizedQuery)
            )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ProductTopBar(
            title = "Optik Formlar",
            leadingText = "‹",
            onLeadingClick = onBack,
            actionText = "↻",
            onActionClick = {
                savedDocuments = documentRepository.list()
                status = "Form listesi yenilendi."
            }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(Modifier.height(2.dp)) }

            item {
                ActiveFormSummary(
                    name = resolved.name,
                    questionCount = resolved.template.bubbleRows.size,
                    markGridCount = resolved.template.markGrids.size,
                    templateId = resolved.template.id,
                    version = resolved.template.version,
                    fellBackToDefault = resolved.fellBackToDefault
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FormStatCard(
                        modifier = Modifier.weight(1f),
                        value = (1 + starters.size + savedDocuments.size).toString(),
                        label = "Toplam Form"
                    )
                    FormStatCard(
                        modifier = Modifier.weight(1f),
                        value = (1 + starters.size).toString(),
                        label = "Hazır"
                    )
                    FormStatCard(
                        modifier = Modifier.weight(1f),
                        value = savedDocuments.size.toString(),
                        label = "Kayıtlı"
                    )
                }
            }

            item {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onCreateForm,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("＋  Yeni Form Oluştur", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            item {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("Form ara") },
                    leadingIcon = { Text("⌕", fontSize = 24.sp) },
                    shape = RoundedCornerShape(28.dp)
                )
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        ProductFilterPill(
                            label = "Tümü",
                            count = 1 + starters.size + savedDocuments.size,
                            selected = filter == FormLibraryFilter.ALL,
                            onClick = { filter = FormLibraryFilter.ALL }
                        )
                    }
                    item {
                        ProductFilterPill(
                            label = "Hazır Şablonlar",
                            count = 1 + starters.size,
                            selected = filter == FormLibraryFilter.READY,
                            onClick = { filter = FormLibraryFilter.READY }
                        )
                    }
                    item {
                        ProductFilterPill(
                            label = "Kurum Formları",
                            count = savedDocuments.size,
                            selected = filter == FormLibraryFilter.SAVED,
                            onClick = { filter = FormLibraryFilter.SAVED }
                        )
                    }
                }
            }

            if (defaultVisible || visibleStarters.isNotEmpty()) {
                item {
                    Text(
                        "Hazır Şablonlar",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (defaultVisible) {
                item {
                    TemplateLibraryCard(
                        name = ActiveOmrTemplateDefaults.displayName,
                        subtitle = "20 soru · öğrenci no · A/B kitapçık",
                        detail = "Üretim için güvenli varsayılan form",
                        selected = resolved.selection == ActiveOmrTemplateDefaults.selection,
                        badge = "HAZIR",
                        onSelect = {
                            choose(
                                ActiveOmrTemplateDefaults.selection,
                                ActiveOmrTemplateDefaults.displayName
                            )
                        }
                    )
                }
            }

            items(visibleStarters, key = { "starter-${it.id}-${it.version}" }) { document ->
                val selection = documentSelection(document)
                TemplateLibraryCard(
                    name = document.name,
                    subtitle = "${document.id} · v${document.version}",
                    detail = "Hazır optik form şablonu",
                    selected = resolved.selection == selection,
                    badge = "HAZIR",
                    onSelect = { choose(selection, document.name) }
                )
            }

            if (visibleSaved.isNotEmpty() || (filter != FormLibraryFilter.READY && savedDocuments.isEmpty())) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Kurum Formları",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (savedDocuments.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    savedDocuments = documentRepository.list()
                                    status = "Kayıtlı formlar yenilendi."
                                }
                            ) {
                                Text("Yenile")
                            }
                        }
                    }
                }
            }

            if (filter != FormLibraryFilter.READY && savedDocuments.isEmpty() && normalizedQuery.isBlank()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Text("Henüz kurum formu yok", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Yeni Form Oluştur ile okulunuza özel bir optik form hazırlayabilirsiniz.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            items(visibleSaved, key = { "saved-${it.id}-${it.version}" }) { document ->
                val selection = documentSelection(document)
                TemplateLibraryCard(
                    name = document.name,
                    subtitle = "${document.id} · v${document.version}",
                    detail = "Cihazda kayıtlı kurum formu",
                    selected = resolved.selection == selection,
                    badge = "KURUM",
                    onSelect = { choose(selection, document.name) }
                )
            }

            if (!defaultVisible && visibleStarters.isEmpty() && visibleSaved.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Form bulunamadı", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Arama metnini veya form filtresini değiştirin.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (status.isNotBlank()) {
                item {
                    Text(
                        status,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

@Composable
private fun ActiveFormSummary(
    name: String,
    questionCount: Int,
    markGridCount: Int,
    templateId: String,
    version: Int,
    fellBackToDefault: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Aktif Form", color = MaterialTheme.colorScheme.onPrimaryContainer)
                ProductStatusBadge(text = "AKTİF", tone = ProductBadgeTone.GREEN)
            }
            Text(
                name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "$questionCount soru · $markGridCount bilgi alanı · v$version",
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
            )
            Text(
                templateId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.68f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (fellBackToDefault) {
                Text(
                    "Önceki seçim bulunamadığı için güvenli varsayılan form kullanılıyor.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun FormStatCard(modifier: Modifier, value: String, label: String) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(value, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TemplateLibraryCard(
    name: String,
    subtitle: String,
    detail: String,
    selected: Boolean,
    badge: String,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ProductStatusBadge(
                    text = if (selected) "AKTİF" else badge,
                    tone = if (selected) ProductBadgeTone.GREEN else ProductBadgeTone.NEUTRAL
                )
            }

            if (selected) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onSelect,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("✓ Kullanılıyor")
                }
            } else {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onSelect,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Aktif Form Yap")
                }
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
