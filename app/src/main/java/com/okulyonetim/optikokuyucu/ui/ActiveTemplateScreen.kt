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

    val locale = Locale("tr", "TR")
    val normalizedQuery = query.trim().lowercase(locale)
    val visibleStarters = starters.filter { document ->
        filter != FormLibraryFilter.SAVED && (
            normalizedQuery.isBlank() ||
                document.name.lowercase(locale).contains(normalizedQuery) ||
                document.id.lowercase(locale).contains(normalizedQuery)
            )
    }
    val defaultVisible = filter != FormLibraryFilter.SAVED && (
        normalizedQuery.isBlank() ||
            ActiveOmrTemplateDefaults.displayName.lowercase(locale).contains(normalizedQuery)
        )
    val visibleSaved = savedDocuments.filter { document ->
        filter != FormLibraryFilter.READY && (
            normalizedQuery.isBlank() ||
                document.name.lowercase(locale).contains(normalizedQuery) ||
                document.id.lowercase(locale).contains(normalizedQuery)
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
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            item { Spacer(Modifier.height(1.dp)) }

            item {
                ActiveFormSummary(
                    name = resolved.name,
                    questionCount = resolved.template.bubbleRows.size,
                    markGridCount = resolved.template.markGrids.size,
                    version = resolved.template.version,
                    fellBackToDefault = resolved.fellBackToDefault
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    FormStatCard(
                        modifier = Modifier.weight(1f),
                        value = (1 + starters.size + savedDocuments.size).toString(),
                        label = "Toplam"
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
                    shape = RoundedCornerShape(15.dp)
                ) {
                    Text("＋  Yeni Form Oluştur", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            item {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("Form ara") },
                    leadingIcon = { Text("⌕", fontSize = 20.sp) },
                    shape = RoundedCornerShape(18.dp)
                )
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
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
                            label = "Hazır",
                            count = 1 + starters.size,
                            selected = filter == FormLibraryFilter.READY,
                            onClick = { filter = FormLibraryFilter.READY }
                        )
                    }
                    item {
                        ProductFilterPill(
                            label = "Kurum",
                            count = savedDocuments.size,
                            selected = filter == FormLibraryFilter.SAVED,
                            onClick = { filter = FormLibraryFilter.SAVED }
                        )
                    }
                }
            }

            if (defaultVisible || visibleStarters.isNotEmpty()) {
                item { FormSectionTitle("Hazır Şablonlar") }
            }

            if (defaultVisible) {
                item {
                    TemplateLibraryCard(
                        name = ActiveOmrTemplateDefaults.displayName,
                        subtitle = "20 soru · öğrenci no · A/B kitapçık",
                        detail = "Güvenli varsayılan form",
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
                    detail = "Hazır optik form",
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
                        FormSectionTitle("Kurum Formları")
                        if (savedDocuments.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    savedDocuments = documentRepository.list()
                                    status = "Kayıtlı formlar yenilendi."
                                }
                            ) { Text("Yenile", fontSize = 12.sp) }
                        }
                    }
                }
            }

            if (filter != FormLibraryFilter.READY && savedDocuments.isEmpty() && normalizedQuery.isBlank()) {
                item {
                    CompactInfoCard(
                        title = "Henüz kurum formu yok",
                        description = "Yeni Form Oluştur ile okulunuza özel form hazırlayabilirsiniz."
                    )
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
                    CompactInfoCard(
                        title = "Form bulunamadı",
                        description = "Arama metnini veya form filtresini değiştirin."
                    )
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

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun ActiveFormSummary(
    name: String,
    questionCount: Int,
    markGridCount: Int,
    version: Int,
    fellBackToDefault: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Aktif Form",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                    )
                    Text(
                        name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                ProductStatusBadge(text = "AKTİF", tone = ProductBadgeTone.GREEN)
            }
            Text(
                "$questionCount soru · $markGridCount bilgi alanı · v$version",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
            )
            if (fellBackToDefault) {
                Text(
                    "Önceki seçim bulunamadı; güvenli varsayılan form kullanılıyor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun FormStatCard(modifier: Modifier, value: String, label: String) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("  $label", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FormSectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(13.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                ProductStatusBadge(
                    text = if (selected) "AKTİF" else badge,
                    tone = if (selected) ProductBadgeTone.GREEN else ProductBadgeTone.NEUTRAL
                )
                if (!selected) {
                    OutlinedButton(
                        onClick = onSelect,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Seç", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactInfoCard(title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun documentSelection(document: DesignerDocument): ActiveTemplateSelection =
    ActiveTemplateSelection(
        source = ActiveTemplateSource.DESIGNER_DOCUMENT,
        templateId = document.id,
        templateVersion = document.version
    )
