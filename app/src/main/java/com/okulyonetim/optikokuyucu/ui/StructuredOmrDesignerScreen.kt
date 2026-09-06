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
import androidx.compose.material3.Card
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
import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocument
import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocumentEditor
import com.okulyonetim.optikokuyucu.omr.designer.DesignerEditorLayout
import com.okulyonetim.optikokuyucu.omr.designer.DesignerExamMode
import com.okulyonetim.optikokuyucu.omr.designer.DesignerExamPreset
import com.okulyonetim.optikokuyucu.omr.designer.DesignerFormSpec
import com.okulyonetim.optikokuyucu.omr.designer.DesignerImageElement
import com.okulyonetim.optikokuyucu.omr.designer.DesignerPageGeometry
import com.okulyonetim.optikokuyucu.omr.designer.DesignerPageOrientation
import com.okulyonetim.optikokuyucu.omr.designer.DesignerPaperSize
import com.okulyonetim.optikokuyucu.omr.designer.DesignerTextElement
import com.okulyonetim.optikokuyucu.omr.designer.FileDesignerDocumentRepository
import com.okulyonetim.optikokuyucu.omr.designer.NumericGridComponent
import com.okulyonetim.optikokuyucu.omr.designer.QuestionGroupComponent
import com.okulyonetim.optikokuyucu.omr.designer.SingleChoiceComponent

@Suppress("UNUSED_PARAMETER")
@Composable
fun StructuredOmrDesignerScreen(openCvReady: Boolean, onBack: () -> Unit, onOpenAdvanced: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
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

    fun saveDocument() {
        val name = formName.trim()
        if (name.isBlank()) {
            status = "Form adı zorunludur."
            return
        }
        status = runCatching {
            val stored = repository.save(document.copy(name = name))
            document = stored
            formName = stored.name
            "Kaydedildi · v${stored.version}"
        }.getOrElse { "Kaydetme hatası: ${it.message ?: it.javaClass.simpleName}" }
    }

    fun storeComponent(component: com.okulyonetim.optikokuyucu.omr.designer.DesignerOmrComponent) {
        val existing = editingExistingId
        document = if (existing == null) {
            document.copy(components = document.components + component)
        } else {
            DesignerDocumentEditor.replaceComponent(document, component)
        }
        selection = StructuredPaperSelection(StructuredSelectionKind.COMPONENT, component.id)
        status = if (existing == null) "Alan eklendi." else "Alan güncellendi."
        clearEditing()
    }

    fun storeVisual(element: com.okulyonetim.optikokuyucu.omr.designer.DesignerVisualElement) {
        val existing = editingExistingId
        document = if (existing == null) {
            document.copy(visualElements = document.visualElements + element)
        } else {
            document.copy(visualElements = document.visualElements.map { if (it.id == element.id) element else it })
        }
        selection = StructuredPaperSelection(StructuredSelectionKind.VISUAL, element.id)
        status = if (existing == null) "Alan eklendi." else "Alan güncellendi."
        clearEditing()
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
                    answerDraft = component.copy(
                        bubbleRadius = DesignerEditorLayout.STANDARD_BUBBLE_RADIUS,
                        choiceGap = DesignerEditorLayout.ANSWER_CHOICE_GAP,
                        rowGap = DesignerEditorLayout.ANSWER_ROW_GAP,
                        columnGap = DesignerEditorLayout.compactAnswerColumnGap(document)
                    )
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
                }
            }
        }
    }

    fun duplicateSelected() {
        val selected = selection ?: return
        val offset = DesignerEditorLayout.canonicalForMillimeters(document, 4.0)
        when (selected.kind) {
            StructuredSelectionKind.COMPONENT -> {
                val id = nextDuplicateId(selected.id, false)
                document = DesignerDocumentEditor.duplicateComponent(
                    document,
                    selected.id,
                    id,
                    offset,
                    offset,
                    DesignerEditorLayout.canonicalForMillimeters(document, 1.0)
                )
                selection = StructuredPaperSelection(StructuredSelectionKind.COMPONENT, id)
            }

            StructuredSelectionKind.VISUAL -> {
                val id = nextDuplicateId(selected.id, true)
                document = DesignerDocumentEditor.duplicateVisualElement(
                    document,
                    selected.id,
                    id,
                    offset,
                    offset,
                    DesignerEditorLayout.canonicalForMillimeters(document, 1.0)
                )
                selection = StructuredPaperSelection(StructuredSelectionKind.VISUAL, id)
            }
        }
        status = "Öğe kopyalandı."
    }

    fun deleteSelected() {
        val selected = selection ?: return
        document = when (selected.kind) {
            StructuredSelectionKind.COMPONENT -> DesignerDocumentEditor.deleteComponent(document, selected.id)
            StructuredSelectionKind.VISUAL -> DesignerDocumentEditor.deleteVisualElement(document, selected.id)
        }
        selection = null
        status = "Öğe silindi."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f))
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
                .padding(10.dp),
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
                },
                {
                    document = DesignerPageGeometry.apply(document, orientation = it)
                    selection = null
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
                onDocumentChange = { document = it },
                onDirectDragActiveChange = { workspaceDirectDragActive = it }
            )
            selection?.let {
                SelectionActions(it, ::editSelected, ::duplicateSelected, ::deleteSelected)
            }
            if (status.isNotBlank()) {
                Text(
                    status,
                    modifier = Modifier.padding(horizontal = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            DesignerPdfExportCard(
                document = document.copy(name = formName.trim().ifBlank { document.name }),
                openCvReady = openCvReady
            )
            Spacer(Modifier.size(8.dp))
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
}

@Composable
private fun SelectionActions(
    selection: StructuredPaperSelection,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Seçili: ${selection.id}", style = MaterialTheme.typography.labelMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                FilledTonalButton(modifier = Modifier.weight(1f), onClick = onEdit) { Text("Düzenle") }
                OutlinedButton(modifier = Modifier.weight(1f), onClick = onDuplicate) { Text("Kopyala") }
                OutlinedButton(modifier = Modifier.weight(1f), onClick = onDelete) { Text("Sil") }
            }
        }
    }
}

@Composable
private fun EditorTopBar(title: String, onBack: () -> Unit, onSave: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary) {
        Row(modifier = Modifier.fillMaxWidth().padding(2.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = onBack,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
            ) { Text("×", style = MaterialTheme.typography.titleLarge) }
            Text(
                title,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Medium
            )
            TextButton(
                onClick = onSave,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
            ) { Text("Kaydet") }
        }
    }
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
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Optik Form Bilgileri", style = MaterialTheme.typography.labelLarge)
            Text(
                "* Zorunlu Alanlar",
                modifier = Modifier.align(Alignment.CenterHorizontally),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall
            )
            OutlinedTextField(
                value = formName,
                onValueChange = onFormNameChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Ad *") },
                singleLine = true,
                shape = RoundedCornerShape(50.dp)
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
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.labelSmall)
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { expanded = true },
                shape = RoundedCornerShape(50.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(value, modifier = Modifier.weight(1f), maxLines = 1)
                    Text("⌄")
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
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Optik Form Alanı", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
        Surface(
            modifier = Modifier.size(28.dp).clickable(onClick = onAdd),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) { Text("+", fontWeight = FontWeight.Bold) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpticalFormAreaPicker(onDismiss: () -> Unit, onSelected: (DesignerAreaKind) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Optik Form Alanı", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            DesignerAreaCatalog.sections.forEach { section ->
                Text(
                    section.title,
                    modifier = Modifier.padding(horizontal = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                section.kinds.forEach { kind ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onSelected(kind) },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                modifier = Modifier.size(38.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(areaKindSymbol(kind), fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(kind.displayName, modifier = Modifier.weight(1f))
                            Text("›")
                        }
                    }
                }
            }
            Spacer(Modifier.size(18.dp))
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
