package com.okulyonetim.optikokuyucu.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.okulyonetim.optikokuyucu.omr.designer.DesignerFormTransfer
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
    var pendingExport by remember { mutableStateOf<DesignerDocument?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(DesignerFormTransfer.MIME_TYPE)
    ) { uri ->
        val document = pendingExport
        pendingExport = null
        if (uri == null || document == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri, "w").use { output ->
                requireNotNull(output) { "Form çıktı akışı açılamadı." }
                output.write(DesignerFormTransfer.export(document))
                output.flush()
            }
        }.onSuccess {
            status = "${document.name} düzenlenebilir .omrd formu olarak dışa aktarıldı."
        }.onFailure { error ->
            status = "Form dışa aktarılamadı: ${error.message ?: error.javaClass.simpleName}"
        }
    }

    fun openDocument(document: DesignerDocument, mode: DesignerLibraryOpenMode) {
        DesignerLibraryOpenHandoff.offer(document, mode)
        onCreateForm()
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val imported = context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Form dosyası açılamadı." }
                DesignerFormTransfer.import(input.readBytes())
            }
            documentRepository.save(imported)
        }.onSuccess { stored ->
            savedDocuments = documentRepository.list()
            status = "${stored.name} içe aktarıldı · v${stored.version}. Düzenleme ekranı açılıyor."
            openDocument(stored, DesignerLibraryOpenMode.EDIT)
        }.onFailure { error ->
            status = "Form içe aktarılamadı: ${error.message ?: error.javaClass.simpleName}"
        }
    }

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
            .onFailure { error -> status = "Form seçilemedi: ${error.message}" }
    }

    fun exportDocument(document: DesignerDocument) {
        pendingExport = document
        exportLauncher.launch(DesignerFormTransfer.fileName(document))
    }

    val locale = Locale("tr", "TR")
    val normalizedQuery = query.trim().lowercase(locale)
    val visibleStarters = starters.filter { document ->
        filter != FormLibraryFilter.SAVED &&
            (normalizedQuery.isBlank() || document.name.lowercase(locale).contains(normalizedQuery))
    }
    val defaultVisible = filter != FormLibraryFilter.SAVED &&
        (normalizedQuery.isBlank() || ActiveOmrTemplateDefaults.displayName.lowercase(locale).contains(normalizedQuery))
    val visibleSaved = savedDocuments.filter { document ->
        filter != FormLibraryFilter.READY &&
            (normalizedQuery.isBlank() || document.name.lowercase(locale).contains(normalizedQuery))
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
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            item { Spacer(Modifier.height(2.dp)) }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Aktif Form", style = MaterialTheme.typography.labelMedium)
                        Text(resolved.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${resolved.template.bubbleRows.size} soru · ${resolved.template.markGrids.size} bilgi alanı · v${resolved.template.version}")
                    }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = onCreateForm,
                        shape = RoundedCornerShape(15.dp)
                    ) { Text("＋ Yeni Form", fontSize = 13.sp) }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            importLauncher.launch(arrayOf(DesignerFormTransfer.MIME_TYPE, "application/*"))
                        },
                        shape = RoundedCornerShape(15.dp)
                    ) { Text("⇩ Form İçe Aktar", fontSize = 13.sp) }
                }
            }
            item {
                Text(
                    "Oluşturduğunuz formlar .omrd biçiminde kayıpsız dışa aktarılabilir; başka cihazda içe aktarılıp yeniden düzenlenebilir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                    item { ProductFilterPill("Tümü", 1 + starters.size + savedDocuments.size, filter == FormLibraryFilter.ALL) { filter = FormLibraryFilter.ALL } }
                    item { ProductFilterPill("Hazır", 1 + starters.size, filter == FormLibraryFilter.READY) { filter = FormLibraryFilter.READY } }
                    item { ProductFilterPill("Kurum", savedDocuments.size, filter == FormLibraryFilter.SAVED) { filter = FormLibraryFilter.SAVED } }
                }
            }

            if (defaultVisible || visibleStarters.isNotEmpty()) item {
                Text("Hazır Şablonlar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (defaultVisible) item {
                FormLibraryCard(
                    name = ActiveOmrTemplateDefaults.displayName,
                    subtitle = "Güvenli varsayılan form",
                    selected = resolved.selection == ActiveOmrTemplateDefaults.selection,
                    onSelect = { choose(ActiveOmrTemplateDefaults.selection, ActiveOmrTemplateDefaults.displayName) }
                )
            }
            items(visibleStarters, key = { "starter-${it.id}-${it.version}" }) { document ->
                val selection = designerSelection(document)
                FormLibraryCard(
                    name = document.name,
                    subtitle = "Hazır form · ${document.id} · v${document.version}",
                    selected = resolved.selection == selection,
                    onSelect = { choose(selection, document.name) },
                    onPreview = { openDocument(document, DesignerLibraryOpenMode.PREVIEW) },
                    onEdit = { openDocument(document, DesignerLibraryOpenMode.EDIT) }
                )
            }

            if (filter != FormLibraryFilter.READY) item {
                Text("Kurum Formları", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (filter != FormLibraryFilter.READY && savedDocuments.isEmpty()) item {
                Text("Henüz kurum formu yok.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(visibleSaved, key = { "saved-${it.id}-${it.version}" }) { document ->
                val selection = designerSelection(document)
                FormLibraryCard(
                    name = document.name,
                    subtitle = "Düzenlenebilir kurum formu · v${document.version}",
                    selected = resolved.selection == selection,
                    onSelect = { choose(selection, document.name) },
                    onPreview = { openDocument(document, DesignerLibraryOpenMode.PREVIEW) },
                    onEdit = { openDocument(document, DesignerLibraryOpenMode.EDIT) },
                    onExport = { exportDocument(document) }
                )
            }
            if (status.isNotBlank()) item {
                Text(status, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun FormLibraryCard(
    name: String,
    subtitle: String,
    selected: Boolean,
    onSelect: () -> Unit,
    onPreview: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onExport: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ProductStatusBadge(if (selected) "AKTİF" else "FORM", if (selected) ProductBadgeTone.GREEN else ProductBadgeTone.NEUTRAL)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (onPreview != null) OutlinedButton(Modifier.weight(1f), onClick = onPreview) { Text("Önizle", fontSize = 11.sp) }
                if (onEdit != null) OutlinedButton(Modifier.weight(1f), onClick = onEdit) { Text("Düzenle", fontSize = 11.sp) }
                if (!selected) OutlinedButton(Modifier.weight(1f), onClick = onSelect) { Text("Seç", fontSize = 11.sp) }
            }
            if (onExport != null) {
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onExport) {
                    Text("Düzenlenebilir Formu Dışa Aktar (.omrd)", fontSize = 11.sp)
                }
            }
        }
    }
}

private fun designerSelection(document: DesignerDocument) = ActiveTemplateSelection(
    source = ActiveTemplateSource.DESIGNER_DOCUMENT,
    templateId = document.id,
    templateVersion = document.version
)
