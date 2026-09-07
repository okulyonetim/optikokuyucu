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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.okulyonetim.optikokuyucu.exam.FileExamRepository
import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocument
import com.okulyonetim.optikokuyucu.omr.designer.DesignerFormTransfer
import com.okulyonetim.optikokuyucu.omr.designer.DesignerStarterTemplates
import com.okulyonetim.optikokuyucu.omr.designer.FileDesignerDocumentRepository
import com.okulyonetim.optikokuyucu.omr.template.ActiveOmrTemplateDefaults
import com.okulyonetim.optikokuyucu.omr.template.ActiveOmrTemplateResolver
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSelection
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSource
import com.okulyonetim.optikokuyucu.omr.template.FileActiveTemplateSelectionRepository
import com.okulyonetim.optikokuyucu.school.SchoolContentAccess
import com.okulyonetim.optikokuyucu.school.SchoolFormOwnershipStore
import com.okulyonetim.optikokuyucu.school.SchoolPortalManager
import com.okulyonetim.optikokuyucu.settings.ReadyTemplateVisibilityRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private enum class FormLibraryFilter { ALL, READY, SAVED }
private data class PendingReadyDelete(val name: String, val key: String)

@Composable
fun ActiveTemplateScreen(
    onBack: () -> Unit,
    onCreateForm: () -> Unit = {}
) {
    val context = LocalContext.current
    val feedback = LocalAppFeedback.current
    val appContext = context.applicationContext
    val selectionRepository = remember(context) { FileActiveTemplateSelectionRepository(appContext) }
    val documentRepository = remember(context) { FileDesignerDocumentRepository(appContext) }
    val examRepository = remember(context) { FileExamRepository(appContext) }
    val visibilityRepository = remember(context) { ReadyTemplateVisibilityRepository(appContext) }
    val ownershipStore = remember(context) { SchoolFormOwnershipStore(appContext) }
    val manager = remember(context) { SchoolPortalManager.get(appContext) }
    val profile = LocalSchoolAccount.current?.profile
    val scope = rememberCoroutineScope()
    val starters = remember { DesignerStarterTemplates.all() }

    var savedDocuments by remember { mutableStateOf(documentRepository.list()) }
    var selected by remember { mutableStateOf(selectionRepository.load()) }
    var hiddenReadyKeys by remember { mutableStateOf(visibilityRepository.hiddenKeys()) }
    var status by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(FormLibraryFilter.ALL) }
    var pendingExport by remember { mutableStateOf<DesignerDocument?>(null) }
    var pendingDelete by remember { mutableStateOf<DesignerDocument?>(null) }
    var pendingReadyDelete by remember { mutableStateOf<PendingReadyDelete?>(null) }

    fun refreshForms(message: String = "Form listesi yenilendi.") {
        savedDocuments = documentRepository.list()
        hiddenReadyKeys = visibilityRepository.hiddenKeys()
        status = message
    }

    fun openDocument(document: DesignerDocument, mode: DesignerLibraryOpenMode) {
        DesignerLibraryOpenHandoff.offer(document, mode)
        onCreateForm()
    }

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
            feedback.success("Form dışa aktarıldı.")
        }.onFailure { error ->
            status = "Form dışa aktarılamadı: ${error.message ?: error.javaClass.simpleName}"
            feedback.error(status)
        }
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
            feedback.success("Form içe aktarıldı.")
            openDocument(stored, DesignerLibraryOpenMode.EDIT)
        }.onFailure { error ->
            status = "Form içe aktarılamadı: ${error.message ?: error.javaClass.simpleName}"
            feedback.error(status)
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
                feedback.success("Aktif form güncellendi.")
            }
            .onFailure { error ->
                status = "Form seçilemedi: ${error.message ?: error.javaClass.simpleName}"
                feedback.error(status)
            }
    }

    fun exportDocument(document: DesignerDocument) {
        pendingExport = document
        exportLauncher.launch(DesignerFormTransfer.fileName(document))
    }

    fun requestDelete(document: DesignerDocument) {
        val currentProfile = profile
        if (currentProfile != null && !SchoolContentAccess.canDeleteForm(ownershipStore.ownership(document), currentProfile)) {
            status = "Bu form başka bir kullanıcıya ait. Silme yetkiniz yok."
            feedback.warning(status)
            return
        }
        val selection = documentSelection(document)
        val linkedExamCount = examRepository.list().count { it.templateSelection == selection }
        if (linkedExamCount > 0) {
            status = "${document.name} $linkedExamCount sınavda kullanılıyor. Önce sınavın optik formunu değiştirin veya sınavı silin."
            feedback.warning(status)
        } else {
            pendingDelete = document
        }
    }

    fun requestReadyDelete(name: String, key: String, selection: ActiveTemplateSelection) {
        if (resolved.selection == selection) {
            status = "$name aktif form. Silmeden önce başka bir formu aktif seçin."
            feedback.warning(status)
            return
        }
        val linkedExamCount = examRepository.list().count { it.templateSelection == selection }
        if (linkedExamCount > 0) {
            status = "$name $linkedExamCount sınavda kullanılıyor. Bu sınavların formunu değiştirdikten sonra silinebilir."
            feedback.warning(status)
            return
        }
        pendingReadyDelete = PendingReadyDelete(name, key)
    }

    fun togglePublic(document: DesignerDocument) {
        val currentProfile = profile
        if (currentProfile?.admin != true) return
        val ownership = ownershipStore.ownership(document)
        val next = !(ownership?.isPublic ?: false)
        scope.launch {
            val outcome = runCatching {
                withContext(Dispatchers.IO) { manager.setTemplatePublic(document, next) }
            }
            outcome.onSuccess {
                refreshForms(if (next) "${document.name} herkese açıldı." else "${document.name} özel yapıldı.")
                feedback.success(status)
            }.onFailure { error ->
                status = "Form paylaşımı değiştirilemedi: ${error.message ?: error.javaClass.simpleName}"
                feedback.error(status)
            }
        }
    }

    val defaultKey = ReadyTemplateVisibilityRepository.defaultKey(
        ActiveOmrTemplateDefaults.selection.templateId,
        ActiveOmrTemplateDefaults.selection.templateVersion
    )
    val readyStarters = starters.filterNot {
        ReadyTemplateVisibilityRepository.starterKey(it.id, it.version) in hiddenReadyKeys
    }
    val readyDefaultVisible = defaultKey !in hiddenReadyKeys
    val locale = Locale("tr", "TR")
    val normalizedQuery = query.trim().lowercase(locale)
    val visibleStarters = readyStarters.filter { document ->
        filter != FormLibraryFilter.SAVED && (
            normalizedQuery.isBlank() ||
                document.name.lowercase(locale).contains(normalizedQuery) ||
                document.id.lowercase(locale).contains(normalizedQuery)
            )
    }
    val defaultVisible = readyDefaultVisible && filter != FormLibraryFilter.SAVED && (
        normalizedQuery.isBlank() || ActiveOmrTemplateDefaults.displayName.lowercase(locale).contains(normalizedQuery)
        )
    val visibleSaved = savedDocuments.filter { document ->
        filter != FormLibraryFilter.READY && (
            normalizedQuery.isBlank() ||
                document.name.lowercase(locale).contains(normalizedQuery) ||
                document.id.lowercase(locale).contains(normalizedQuery) ||
                ownershipStore.ownership(document)?.ownerName?.lowercase(locale)?.contains(normalizedQuery) == true
            )
    }
    val readyCount = (if (readyDefaultVisible) 1 else 0) + readyStarters.size
    val totalCount = readyCount + savedDocuments.size

    Column(modifier = Modifier.fillMaxSize()) {
        ProductTopBar(
            title = "Optik Formlar",
            leadingText = "‹",
            onLeadingClick = onBack,
            actionText = "↻",
            onActionClick = {
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { manager.syncTemplates() } }
                    refreshForms()
                    feedback.info(status)
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            item { Spacer(Modifier.height(1.dp)) }
            if (profile != null) {
                item {
                    Text(
                        if (profile.admin) "Admin · tüm kurum formları" else "${profile.displayName} · kendi formlarınız ve herkese açık formlar",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
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
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    FormStatCard(Modifier.weight(1f), totalCount.toString(), "Toplam")
                    FormStatCard(Modifier.weight(1f), readyCount.toString(), "Hazır")
                    FormStatCard(Modifier.weight(1f), savedDocuments.size.toString(), "Kayıtlı")
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Button(modifier = Modifier.weight(1f), onClick = onCreateForm, shape = RoundedCornerShape(15.dp)) {
                        Text("＋ Yeni Form", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { importLauncher.launch(arrayOf(DesignerFormTransfer.MIME_TYPE, "application/*")) },
                        shape = RoundedCornerShape(15.dp)
                    ) { Text("⇩ İçe Aktar", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
            item {
                Text(
                    "Kendi formlarınızı düzenleyip silebilirsiniz. Herkese açık başka kullanıcı formları salt okunur ve silinemez.",
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
                    label = { Text("Form veya sahibi ara") },
                    leadingIcon = { Text("⌕", fontSize = 20.sp) },
                    shape = RoundedCornerShape(18.dp)
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    item { ProductFilterPill("Tümü", totalCount, filter == FormLibraryFilter.ALL) { filter = FormLibraryFilter.ALL } }
                    item { ProductFilterPill("Hazır", readyCount, filter == FormLibraryFilter.READY) { filter = FormLibraryFilter.READY } }
                    item { ProductFilterPill("Kurum", savedDocuments.size, filter == FormLibraryFilter.SAVED) { filter = FormLibraryFilter.SAVED } }
                }
            }
            if (hiddenReadyKeys.isNotEmpty() && filter != FormLibraryFilter.SAVED) {
                item {
                    TextButton(onClick = {
                        visibilityRepository.restoreAll()
                        hiddenReadyKeys = emptySet()
                        status = "Silinen hazır formlar geri getirildi."
                    }) { Text("Silinen hazır formları geri getir") }
                }
            }
            if (defaultVisible || visibleStarters.isNotEmpty()) item { FormSectionTitle("Hazır Şablonlar") }
            if (defaultVisible) {
                item {
                    TemplateLibraryCard(
                        name = ActiveOmrTemplateDefaults.displayName,
                        subtitle = "20 soru · öğrenci no · A/B kitapçık",
                        detail = "Güvenli varsayılan form",
                        selected = resolved.selection == ActiveOmrTemplateDefaults.selection,
                        badge = "HAZIR",
                        onSelect = { choose(ActiveOmrTemplateDefaults.selection, ActiveOmrTemplateDefaults.displayName) },
                        onDelete = {
                            requestReadyDelete(ActiveOmrTemplateDefaults.displayName, defaultKey, ActiveOmrTemplateDefaults.selection)
                        }
                    )
                }
            }
            items(visibleStarters, key = { "starter-${it.id}-${it.version}" }) { document ->
                val selection = documentSelection(document)
                val key = ReadyTemplateVisibilityRepository.starterKey(document.id, document.version)
                TemplateLibraryCard(
                    name = document.name,
                    subtitle = "${document.id} · v${document.version}",
                    detail = "Hazır optik form · düzenleme kopya oluşturur",
                    selected = resolved.selection == selection,
                    badge = "HAZIR",
                    onSelect = { choose(selection, document.name) },
                    onPreview = { openDocument(document, DesignerLibraryOpenMode.PREVIEW) },
                    onEdit = { openDocument(document, DesignerLibraryOpenMode.EDIT) },
                    onDelete = { requestReadyDelete(document.name, key, selection) }
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
                            TextButton(onClick = { refreshForms("Kayıtlı formlar yenilendi.") }) { Text("Yenile", fontSize = 12.sp) }
                        }
                    }
                }
            }
            if (filter != FormLibraryFilter.READY && savedDocuments.isEmpty() && normalizedQuery.isBlank()) {
                item { CompactInfoCard("Henüz kurum formu yok", "Yeni Form Oluştur ile kendi optik formunuzu hazırlayabilirsiniz.") }
            }
            items(visibleSaved, key = { "saved-${it.id}-${it.version}" }) { document ->
                val selection = documentSelection(document)
                val ownership = ownershipStore.ownership(document)
                val canModify = profile?.let { SchoolContentAccess.canModifyForm(ownership, it) } ?: true
                val isPublic = ownership?.isPublic == true
                val ownerLabel = when {
                    ownership?.ownerName?.isNotBlank() == true -> ownership.ownerName
                    ownership?.ownerUid?.isNotBlank() == true -> "Başka kullanıcı"
                    else -> "Eski yerel form"
                }
                TemplateLibraryCard(
                    name = document.name,
                    subtitle = "${document.id} · v${document.version}",
                    detail = "$ownerLabel · ${if (isPublic) "Herkese açık" else if (canModify) "Size ait" else "Özel"}",
                    selected = resolved.selection == selection,
                    badge = when {
                        isPublic -> "HERKESE AÇIK"
                        canModify -> "SİZİN"
                        else -> "KORUMALI"
                    },
                    onSelect = { choose(selection, document.name) },
                    onPreview = { openDocument(document, DesignerLibraryOpenMode.PREVIEW) },
                    onEdit = if (canModify) ({ openDocument(document, DesignerLibraryOpenMode.EDIT) }) else null,
                    onExport = if (canModify) ({ exportDocument(document) }) else null,
                    onDelete = if (canModify) ({ requestDelete(document) }) else null,
                    onTogglePublic = if (profile?.admin == true) ({ togglePublic(document) }) else null,
                    public = isPublic
                )
            }
            if (!defaultVisible && visibleStarters.isEmpty() && visibleSaved.isEmpty()) {
                item { CompactInfoCard("Form bulunamadı", "Arama metnini veya form filtresini değiştirin.") }
            }
            if (status.isNotBlank()) {
                item { Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }

    pendingReadyDelete?.let { pending ->
        AppConfirmationDialog(
            title = "Hazır Formu Sil",
            message = "${pending.name} hazır form listesinden kaldırılacak. İsterseniz daha sonra ‘Silinen hazır formları geri getir’ ile geri alabilirsiniz.",
            confirmText = "Sil",
            destructive = true,
            onConfirm = {
                visibilityRepository.hide(pending.key)
                hiddenReadyKeys = visibilityRepository.hiddenKeys()
                status = "${pending.name} hazır form listesinden silindi."
                pendingReadyDelete = null
                feedback.success("Hazır form silindi.")
            },
            onDismiss = { pendingReadyDelete = null }
        )
    }

    pendingDelete?.let { document ->
        AppConfirmationDialog(
            title = "Optik Formu Sil",
            message = "${document.name} yalnız size ait form kütüphanesinden silinecek. Paylaşılmışsa bulut kopyası da kullanımdan kaldırılacak.",
            confirmText = "Sil",
            destructive = true,
            onConfirm = {
                val selection = documentSelection(document)
                val wasSelected = selected == selection
                pendingDelete = null
                scope.launch {
                    val outcome = runCatching {
                        withContext(Dispatchers.IO) {
                            manager.deleteTemplateCloudCopy(document)
                            check(documentRepository.delete(document.id, document.version)) { "Form dosyası silinemedi." }
                        }
                    }
                    outcome.onSuccess {
                        savedDocuments = documentRepository.list()
                        if (wasSelected) {
                            runCatching { selectionRepository.save(ActiveOmrTemplateDefaults.selection) }
                            selected = ActiveOmrTemplateDefaults.selection
                        }
                        status = "${document.name} silindi."
                        feedback.success("Form silindi.")
                    }.onFailure { error ->
                        status = "Form silinemedi: ${error.message ?: error.javaClass.simpleName}"
                        feedback.error(status)
                    }
                }
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun ActiveFormSummary(name: String, questionCount: Int, markGridCount: Int, version: Int, fellBackToDefault: Boolean) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Aktif Form", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f))
                    Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                ProductStatusBadge("AKTİF", ProductBadgeTone.GREEN)
            }
            Text("$questionCount soru · $markGridCount bilgi alanı · v$version", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            if (fellBackToDefault) Text("Önceki seçim bulunamadı; güvenli varsayılan form kullanılıyor.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun FormStatCard(modifier: Modifier, value: String, label: String) {
    Card(modifier = modifier, shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 9.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("  $label", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FormSectionTitle(title: String) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }

@Composable
private fun TemplateLibraryCard(
    name: String,
    subtitle: String,
    detail: String,
    selected: Boolean,
    badge: String,
    onSelect: () -> Unit,
    onPreview: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onExport: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onTogglePublic: (() -> Unit)? = null,
    public: Boolean = false
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ProductStatusBadge(if (selected) "AKTİF" else badge, if (selected) ProductBadgeTone.GREEN else ProductBadgeTone.NEUTRAL)
            }
            if (onPreview != null || onEdit != null || !selected) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (onPreview != null) OutlinedButton(modifier = Modifier.weight(1f), onClick = onPreview, shape = RoundedCornerShape(12.dp)) { Text("Önizle", fontSize = 11.sp) }
                    if (onEdit != null) OutlinedButton(modifier = Modifier.weight(1f), onClick = onEdit, shape = RoundedCornerShape(12.dp)) { Text("Düzenle", fontSize = 11.sp) }
                    if (!selected) OutlinedButton(modifier = Modifier.weight(1f), onClick = onSelect, shape = RoundedCornerShape(12.dp)) { Text("Seç", fontSize = 11.sp) }
                }
            }
            if (onExport != null || onDelete != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (onExport != null) OutlinedButton(modifier = Modifier.weight(1f), onClick = onExport, shape = RoundedCornerShape(12.dp)) { Text("Dışa Aktar (.omrd)", fontSize = 11.sp) }
                    if (onDelete != null) OutlinedButton(modifier = Modifier.weight(1f), onClick = onDelete, shape = RoundedCornerShape(12.dp)) { Text("Sil", color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
                }
            }
            if (onTogglePublic != null) {
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onTogglePublic, shape = RoundedCornerShape(12.dp)) {
                    Text(if (public) "Özel Yap" else "Herkese Aç", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun CompactInfoCard(title: String, description: String) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun documentSelection(document: DesignerDocument): ActiveTemplateSelection = ActiveTemplateSelection(
    source = ActiveTemplateSource.DESIGNER_DOCUMENT,
    templateId = document.id,
    templateVersion = document.version
)
