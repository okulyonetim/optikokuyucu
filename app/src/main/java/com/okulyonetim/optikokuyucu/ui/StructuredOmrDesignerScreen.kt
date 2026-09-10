package com.okulyonetim.optikokuyucu.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.okulyonetim.optikokuyucu.omr.designer.DesignerAreaCatalog
import com.okulyonetim.optikokuyucu.omr.designer.DesignerAreaKind
import com.okulyonetim.optikokuyucu.omr.designer.DesignerComponentGeometry
import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocument
import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocumentEditor
import com.okulyonetim.optikokuyucu.omr.designer.DesignerEditSafety
import com.okulyonetim.optikokuyucu.omr.designer.DesignerEditorLayout
import com.okulyonetim.optikokuyucu.omr.designer.DesignerExamMode
import com.okulyonetim.optikokuyucu.omr.designer.DesignerExamPreset
import com.okulyonetim.optikokuyucu.omr.designer.DesignerFormSpec
import com.okulyonetim.optikokuyucu.omr.designer.DesignerImageElement
import com.okulyonetim.optikokuyucu.omr.designer.DesignerPageGeometry
import com.okulyonetim.optikokuyucu.omr.designer.DesignerPageOrientation
import com.okulyonetim.optikokuyucu.omr.designer.DesignerPaperSize
import com.okulyonetim.optikokuyucu.omr.designer.DesignerTemplateCompiler
import com.okulyonetim.optikokuyucu.omr.designer.DesignerTextElement
import com.okulyonetim.optikokuyucu.omr.designer.DesignerVisualGeometry
import com.okulyonetim.optikokuyucu.omr.designer.FileDesignerDocumentRepository
import com.okulyonetim.optikokuyucu.omr.designer.NumericGridComponent
import com.okulyonetim.optikokuyucu.omr.designer.QuestionGroupComponent
import com.okulyonetim.optikokuyucu.omr.designer.SingleChoiceComponent

@Suppress("UNUSED_PARAMETER")
@Composable
fun StructuredOmrDesignerScreen(openCvReady: Boolean, onBack: () -> Unit, onOpenAdvanced: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val feedback = LocalAppFeedback.current
    val repository = remember(context) { FileDesignerDocumentRepository(context.applicationContext) }
    val openRequest = remember { DesignerLibraryOpenHandoff.consume() }
    val initialDocument = remember(openRequest) {
        openRequest?.document ?: DesignerPageGeometry.apply(
            DesignerDocument(
                id = "form-${System.currentTimeMillis()}",
                version = 1,
                name = "Yeni Optik Form",
                formSpec = DesignerFormSpec()
            )
        )
    }
    var document by remember(initialDocument.id, initialDocument.version) { mutableStateOf(initialDocument) }
    var formName by remember(initialDocument.id, initialDocument.version) {
        mutableStateOf(if (openRequest == null) "" else initialDocument.name)
    }
    var libraryMode by remember(openRequest) {
        mutableStateOf(openRequest?.mode ?: DesignerLibraryOpenMode.EDIT)
    }
    var status by remember { mutableStateOf("") }
    var showAreaPicker by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf<StructuredPaperSelection?>(null) }
    var pendingDeleteSelection by remember { mutableStateOf<StructuredPaperSelection?>(null) }
    var workspaceDirectDragActive by remember { mutableStateOf(false) }
    var editingExistingId by remember { mutableStateOf<String?>(null) }

    var numberDraft by remember { mutableStateOf<NumericGridComponent?>(null) }
    var numberPatternText by remember { mutableStateOf("") }
    var answerDraft by remember { mutableStateOf<QuestionGroupComponent?>(null) }
    var answerPatternText by remember { mutableStateOf("") }
    var bookletDraft by remember { mutableStateOf<SingleChoiceComponent?>(null) }
    var bookletPatternText by remember { mutableStateOf("") }
    var descriptionDraft by remember { mutableStateOf<DesignerTextElement?>(null) }
    var imageEditorOpen by remember { mutableStateOf(false) }
    var imageDraft by remember { mutableStateOf<DesignerImageElement?>(null) }

    if (libraryMode == DesignerLibraryOpenMode.PREVIEW) {
        DesignerDocumentPreviewScreen(
            document = document.copy(name = formName.trim().ifBlank { document.name }),
            openCvReady = openCvReady,
            onBack = onBack,
            onEdit = {
                libraryMode = DesignerLibraryOpenMode.EDIT
                selection = null
                status = "Düzenleme modu açıldı."
                feedback.info(status)
            }
        )
        return
    }

    fun clearEditing() {
        editingExistingId = null
        numberDraft = null
        numberPatternText = ""
        answerDraft = null
        answerPatternText = ""
        bookletDraft = null
        bookletPatternText = ""
        descriptionDraft = null
        imageDraft = null
        imageEditorOpen = false
    }

    fun placementIssue(candidate: DesignerDocument, target: StructuredPaperSelection): String? {
        val bounds = when (target.kind) {
            StructuredSelectionKind.COMPONENT -> candidate.components.firstOrNull { it.id == target.id }
                ?.let(DesignerComponentGeometry::interactionBounds)
            StructuredSelectionKind.VISUAL -> candidate.visualElements.firstOrNull { it.id == target.id }
                ?.let(DesignerVisualGeometry::bounds)
        } ?: return "Öğe form üzerinde bulunamadı. Değişiklik uygulanmadı."
        return DesignerEditSafety.placementIssue(candidate, bounds)
    }

    fun applyCandidate(
        candidate: DesignerDocument,
        target: StructuredPaperSelection,
        successText: String,
        validateCompile: Boolean,
        notifySuccess: Boolean = true
    ): Boolean {
        val issue = placementIssue(candidate, target)
        if (issue != null) {
            status = issue
            feedback.warning(issue)
            return false
        }
        if (validateCompile && target.kind == StructuredSelectionKind.COMPONENT) {
            val compileError = runCatching { DesignerTemplateCompiler.compile(candidate) }.exceptionOrNull()
            if (compileError != null) {
                val message = "Öğe form alanına sığmıyor veya geçerli OMR geometrisi oluşturmuyor. Boyutu/konumu düzeltin."
                status = message
                feedback.warning(message)
                return false
            }
        }
        document = candidate
        status = successText
        if (notifySuccess) feedback.success(successText)
        return true
    }

    fun saveDocument() {
        val name = formName.trim()
        if (name.isBlank()) {
            status = "Form adı zorunludur."
            feedback.warning(status)
            return
        }
        val candidate = document.copy(name = name)
        val compileError = runCatching { DesignerTemplateCompiler.compile(candidate) }.exceptionOrNull()
        if (compileError != null) {
            status = "Form kaydedilemedi: Bir veya daha fazla öğe form alanına sığmıyor."
            feedback.warning(status)
            return
        }
        runCatching {
            repository.save(candidate)
        }.onSuccess { stored ->
            document = stored
            formName = stored.name
            status = "Kaydedildi · v${stored.version}"
            feedback.success(status)
        }.onFailure { error ->
            status = "Kaydetme hatası: ${error.message ?: error.javaClass.simpleName}"
            feedback.error(status)
        }
    }

    fun storeComponent(component: com.okulyonetim.optikokuyucu.omr.designer.DesignerOmrComponent) {
        val existing = editingExistingId
        val candidate = if (existing == null) {
            document.copy(components = document.components + component)
        } else {
            runCatching { DesignerDocumentEditor.replaceComponent(document, component) }
                .getOrElse { error ->
                    status = "Alan güncellenemedi: ${error.message ?: error.javaClass.simpleName}"
                    feedback.error(status)
                    return
                }
        }
        val target = StructuredPaperSelection(StructuredSelectionKind.COMPONENT, component.id)
        if (applyCandidate(candidate, target, if (existing == null) "Alan eklendi." else "Alan güncellendi.", true)) {
            selection = target
            clearEditing()
        }
    }

    fun storeVisual(element: com.okulyonetim.optikokuyucu.omr.designer.DesignerVisualElement) {
        val existing = editingExistingId
        val candidate = if (existing == null) {
            document.copy(visualElements = document.visualElements + element)
        } else {
            document.copy(visualElements = document.visualElements.map { if (it.id == element.id) element else it })
        }
        val target = StructuredPaperSelection(StructuredSelectionKind.VISUAL, element.id)
        if (applyCandidate(candidate, target, if (existing == null) "Alan eklendi." else "Alan güncellendi.", false)) {
            selection = target
            clearEditing()
        }
    }

    numberDraft?.let { draft ->
        NumberAreaEditorScreen(
            document,
            draft,
            numberPatternText,
            { numberDraft = it },
            { text ->
                numberPatternText = text
                DesignerAreaCatalog.parseNumberPattern(text)?.let { numberDraft = numberDraft?.copy(values = it) }
            },
            { clearEditing() },
            { storeComponent(it) }
        )
        return
    }

    answerDraft?.let { draft ->
        AnswerAreaEditorScreen(
            document,
            draft,
            answerPatternText,
            { answerDraft = it },
            { text ->
                answerPatternText = text
                DesignerAreaCatalog.parseAnswerPattern(text)?.let { answerDraft = answerDraft?.copy(choices = it) }
            },
            { clearEditing() },
            { storeComponent(it) }
        )
        return
    }

    bookletDraft?.let { draft ->
        BookletAreaEditorScreen(
            document,
            draft,
            bookletPatternText,
            { bookletDraft = it },
            { text ->
                bookletPatternText = text
                DesignerAreaCatalog.parseBookletPattern(text)?.let { bookletDraft = bookletDraft?.copy(choices = it) }
            },
            { clearEditing() },
            { storeComponent(it) }
        )
        return
    }

    descriptionDraft?.let { draft ->
        DescriptionAreaEditorScreen(
            document,
            draft,
            { descriptionDraft = it },
            { clearEditing() },
            { storeVisual(it) }
        )
        return
    }

    if (imageEditorOpen) {
        ImageAreaEditorScreen(
            document,
            imageDraft,
            { imageDraft = it },
            { clearEditing() },
            { storeVisual(it) }
        )
        return
    }

    fun nextDuplicateId(sourceId: String, visual: Boolean): String {
        var n = 1
        var id = "$sourceId-copy$n"
        while (if (visual) document.visualElements.any { it.id == id } else document.components.any { it.id == id }) {
            n++
            id = "$sourceId-copy$n"
        }
        return id
    }

    fun editSelected() {
        val selected = selection ?: return
        editingExistingId = selected.id
        when (selected.kind) {
            StructuredSelectionKind.COMPONENT -> when (val component = document.components.firstOrNull { it.id == selected.id }) {
                is NumericGridComponent -> {
                    numberDraft = component.copy(bubbleRadius = DesignerEditorLayout.STANDARD_BUBBLE_RADIUS)
                    numberPatternText = DesignerAreaCatalog.numberPatternText(component.values)
                }
                is QuestionGroupComponent -> {
                    answerDraft = component.copy(bubbleRadius = DesignerEditorLayout.STANDARD_BUBBLE_RADIUS)
                    answerPatternText = DesignerAreaCatalog.answerPatternText(component.choices)
                }
                is SingleChoiceComponent -> {
                    bookletDraft = component.copy(bubbleRadius = DesignerEditorLayout.STANDARD_BUBBLE_RADIUS)
                    bookletPatternText = DesignerAreaCatalog.bookletPatternText(component.choices)
                }
                null -> editingExistingId = null
            }

            StructuredSelectionKind.VISUAL -> when (val element = document.visualElements.firstOrNull { it.id == selected.id }) {
                is DesignerTextElement -> descriptionDraft = element
                is DesignerImageElement -> {
                    imageDraft = element
                    imageEditorOpen = true
                }
                else -> {
                    editingExistingId = null
                    status = "Bu görsel öğe gelişmiş düzenleyicide düzenlenebilir."
                    feedback.info(status)
                }
            }
        }
    }

    fun duplicateSelected() {
        val selected = selection ?: return
        val offset = DesignerEditorLayout.canonicalForMillimeters(document, 4.0)
        val id = nextDuplicateId(selected.id, selected.kind == StructuredSelectionKind.VISUAL)
        val candidate = runCatching {
            when (selected.kind) {
                StructuredSelectionKind.COMPONENT -> DesignerDocumentEditor.duplicateComponent(
                    document,
                    selected.id,
                    id,
                    offset,
                    offset,
                    DesignerEditorLayout.canonicalForMillimeters(document, 1.0)
                )
                StructuredSelectionKind.VISUAL -> DesignerDocumentEditor.duplicateVisualElement(
                    document,
                    selected.id,
                    id,
                    offset,
                    offset,
                    DesignerEditorLayout.canonicalForMillimeters(document, 1.0)
                )
            }
        }.getOrElse { error ->
            status = "Öğe kopyalanamadı: ${error.message ?: error.javaClass.simpleName}"
            feedback.error(status)
            return
        }
        val target = StructuredPaperSelection(selected.kind, id)
        if (applyCandidate(candidate, target, "Öğe kopyalandı.", selected.kind == StructuredSelectionKind.COMPONENT)) {
            selection = target
        }
    }

    fun deleteSelected() {
        selection?.let { pendingDeleteSelection = it }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .background(MaterialTheme.colorScheme.background)
    ) {
        EditorTopBar(
            title = if (openRequest == null) "Yeni Optik Form" else "Optik Formu Düzenle",
            onBack = onBack,
            onSave = ::saveDocument
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState(), enabled = !workspaceDirectDragActive)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FormInformationCard(
                formName,
                {
                    formName = it
                    if (status == "Form adı zorunludur.") status = ""
                },
                document.formSpec,
                { document = document.copy(formSpec = document.formSpec.copy(examMode = it)) },
                { document = document.copy(formSpec = document.formSpec.copy(examPreset = it)) },
                {
                    document = DesignerPageGeometry.apply(document, paperSize = it)
                    selection = null
                    feedback.info("Kağıt boyutu güncellendi.")
                },
                {
                    document = DesignerPageGeometry.apply(document, orientation = it)
                    selection = null
                    feedback.info("Sayfa yönü güncellendi.")
                }
            )
            OpticalFormAreaHeader {
                status = ""
                showAreaPicker = true
            }
            InteractivePaperWorkspace(
                document = document,
                selection = selection,
                onSelectionChange = { selection = it },
                onDocumentChange = { candidate ->
                    val selected = selection
                    if (selected == null) {
                        document = candidate
                    } else {
                        applyCandidate(
                            candidate = candidate,
                            target = selected,
                            successText = "Öğe konumu güncellendi.",
                            validateCompile = false,
                            notifySuccess = false
                        )
                    }
                },
                onDirectDragActiveChange = { workspaceDirectDragActive = it }
            )
            selection?.let {
                SelectionActions(it, ::editSelected, ::duplicateSelected, ::deleteSelected)
            }
            if (status.isNotBlank()) {
                ProductCompactCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        status,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            DesignerPdfExportCard(
                document = document.copy(name = formName.trim().ifBlank { document.name }),
                openCvReady = openCvReady
            )
            Spacer(Modifier.size(4.dp))
        }
    }

    if (showAreaPicker) {
        OpticalFormAreaPicker(
            onDismiss = { showAreaPicker = false },
            onSelected = { kind ->
                showAreaPicker = false
                editingExistingId = null
                when (kind) {
                    DesignerAreaKind.NUMBER -> DesignerAreaCatalog.createNumberArea(document).also {
                        numberDraft = it
                        numberPatternText = DesignerAreaCatalog.numberPatternText(it.values)
                    }
                    DesignerAreaKind.ANSWERS -> DesignerAreaCatalog.createAnswerArea(document).also {
                        answerDraft = it
                        answerPatternText = DesignerAreaCatalog.answerPatternText(it.choices)
                    }
                    DesignerAreaKind.BOOKLET -> DesignerAreaCatalog.createBookletArea(document).also {
                        bookletDraft = it
                        bookletPatternText = DesignerAreaCatalog.bookletPatternText(it.choices)
                    }
                    DesignerAreaKind.STUDENT_NAME ->
                        descriptionDraft = DesignerAreaCatalog.createStudentNameArea(document)
                    DesignerAreaKind.STUDENT_CLASS ->
                        descriptionDraft = DesignerAreaCatalog.createStudentClassArea(document)
                    DesignerAreaKind.STUDENT_NUMBER_TEXT ->
                        descriptionDraft = DesignerAreaCatalog.createStudentNumberTextArea(document)
                    DesignerAreaKind.EXAM_NAME ->
                        descriptionDraft = DesignerAreaCatalog.createExamNameArea(document)
                    DesignerAreaKind.SCHOOL_NAME ->
                        descriptionDraft = DesignerAreaCatalog.createSchoolNameArea(document)
                    DesignerAreaKind.DESCRIPTION -> descriptionDraft = DesignerAreaCatalog.createDescriptionArea(document)
                    DesignerAreaKind.IMAGE -> {
                        imageDraft = null
                        imageEditorOpen = true
                    }
                }
            }
        )
    }

    pendingDeleteSelection?.let { selected ->
        AppConfirmationDialog(
            title = "Öğe silinsin mi?",
            message = "Seçili öğe optik formdan kaldırılacak. Bu işlem geri alınamaz.",
            confirmText = "Sil",
            destructive = true,
            onConfirm = {
                val candidate = when (selected.kind) {
                    StructuredSelectionKind.COMPONENT -> DesignerDocumentEditor.deleteComponent(document, selected.id)
                    StructuredSelectionKind.VISUAL -> DesignerDocumentEditor.deleteVisualElement(document, selected.id)
                }
                document = candidate
                selection = null
                pendingDeleteSelection = null
                status = "Öğe silindi."
                feedback.success(status)
            },
            onDismiss = { pendingDeleteSelection = null }
        )
    }
}

@Composable
private fun SelectionActions(
    selection: StructuredPaperSelection,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    ProductCompactCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("Seçili öğe", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        selection.id,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Sil") }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ProductFilterPill(label = "Düzenle", selected = true, onClick = onEdit)
                ProductFilterPill(label = "Kopyala", selected = false, onClick = onDuplicate)
            }
        }
    }
}

@Composable
private fun EditorTopBar(title: String, onBack: () -> Unit, onSave: () -> Unit) {
    ProductTopBar(
        title = title,
        leadingText = "‹",
        onLeadingClick = onBack,
        actionText = "Kaydet",
        onActionClick = onSave,
        showAutomaticBack = false
    )
}

@Composable
private fun FormInformationCard(
    formName: String,
    onFormNameChange: (String) -> Unit,
    formSpec: DesignerFormSpec,
    onExamModeChange: (DesignerExamMode) -> Unit,
    onExamPresetChange: (DesignerExamPreset) -> Unit,
    onPaperSizeChange: (DesignerPaperSize) -> Unit,
    onOrientationChange: (DesignerPageOrientation) -> Unit
) {
    ProductSettingsSection(
        title = "Form Ayarları",
        description = "Form adı, sınav türü ve sayfa düzenini belirleyin.",
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = formName,
            onValueChange = onFormNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Form adı *") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )
        DropdownField(
            "Sınav Türü",
            if (formSpec.examMode == DesignerExamMode.UNSPECIFIED) "Seçiniz" else formSpec.examMode.displayName,
            listOf(DesignerExamMode.SINGLE_LESSON, DesignerExamMode.MULTI_LESSON),
            { it.displayName },
            onExamModeChange
        )
        if (formSpec.examMode != DesignerExamMode.UNSPECIFIED) {
            DropdownField(
                "Deneme Türü",
                formSpec.examPreset.displayName,
                listOf(
                    DesignerExamPreset.CUSTOM,
                    DesignerExamPreset.LGS,
                    DesignerExamPreset.TYT,
                    DesignerExamPreset.AYT,
                    DesignerExamPreset.YDT,
                    DesignerExamPreset.ALES,
                    DesignerExamPreset.DGS,
                    DesignerExamPreset.KPSS,
                    DesignerExamPreset.TUS,
                    DesignerExamPreset.SCHOLARSHIP
                ),
                { it.displayName },
                onExamPresetChange
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DropdownField(
                "Kağıt",
                formSpec.paperSize.displayName,
                listOf(
                    DesignerPaperSize.A3,
                    DesignerPaperSize.A4,
                    DesignerPaperSize.A5,
                    DesignerPaperSize.A6,
                    DesignerPaperSize.A7
                ),
                { it.displayName },
                onPaperSizeChange,
                Modifier.weight(1f)
            )
            DropdownField(
                "Yön",
                formSpec.orientation.displayName,
                listOf(DesignerPageOrientation.PORTRAIT, DesignerPageOrientation.LANDSCAPE),
                { it.displayName },
                onOrientationChange,
                Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun <T> DropdownField(
    label: String,
    value: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                label,
                modifier = Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ProductCompactCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { expanded = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(value, modifier = Modifier.weight(1f), maxLines = 1)
                    Text("⌄", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    }
                )
            }
        }
    }
}

@Composable
private fun OpticalFormAreaHeader(onAdd: () -> Unit) {
    ProductCompactCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("Tasarım Alanı", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Alan ekleyin, seçin ve taşıyın.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ProductFilterPill(label = "Alan Ekle", selected = true, onClick = onAdd)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpticalFormAreaPicker(onDismiss: () -> Unit, onSelected: (DesignerAreaKind) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Alan Ekle", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "Forma eklemek istediğiniz alan türünü seçin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DesignerAreaCatalog.sections.forEach { section ->
                Text(
                    section.title,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                section.kinds.forEach { kind ->
                    ProductCompactCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onSelected(kind) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                modifier = Modifier.size(36.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.primary
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(areaKindSymbol(kind), fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(kind.displayName, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            Text("›", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            Spacer(Modifier.size(14.dp))
        }
    }
}

private fun areaKindSymbol(kind: DesignerAreaKind): String = when (kind) {
    DesignerAreaKind.NUMBER -> "123"
    DesignerAreaKind.ANSWERS -> "AB"
    DesignerAreaKind.BOOKLET -> "A/B"
    DesignerAreaKind.STUDENT_NAME -> "AD"
    DesignerAreaKind.STUDENT_CLASS -> "SNF"
    DesignerAreaKind.STUDENT_NUMBER_TEXT -> "NO"
    DesignerAreaKind.EXAM_NAME -> "S"
    DesignerAreaKind.SCHOOL_NAME -> "O"
    DesignerAreaKind.DESCRIPTION -> "T"
    DesignerAreaKind.IMAGE -> "▧"
}
