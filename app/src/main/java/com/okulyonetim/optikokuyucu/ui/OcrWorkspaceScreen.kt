package com.okulyonetim.optikokuyucu.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.okulyonetim.optikokuyucu.exam.Exam
import com.okulyonetim.optikokuyucu.exam.FileExamRepository
import com.okulyonetim.optikokuyucu.ocr.OcrAnswerKeyExtraction
import com.okulyonetim.optikokuyucu.ocr.OcrAnswerKeyParser
import com.okulyonetim.optikokuyucu.ocr.OcrRecognitionResult
import com.okulyonetim.optikokuyucu.ocr.OcrTextRecognizer
import com.okulyonetim.optikokuyucu.omr.designer.DesignerStarterTemplates
import com.okulyonetim.optikokuyucu.omr.designer.FileDesignerDocumentRepository
import com.okulyonetim.optikokuyucu.omr.scoring.AnswerKey
import com.okulyonetim.optikokuyucu.omr.scoring.AnswerKeySource
import com.okulyonetim.optikokuyucu.omr.scoring.FileAnswerKeyRepository
import com.okulyonetim.optikokuyucu.omr.scoring.ManualAnswerKeyBuilder
import com.okulyonetim.optikokuyucu.omr.scoring.ManualAnswerSection
import com.okulyonetim.optikokuyucu.omr.scoring.StoredAnswerKey
import com.okulyonetim.optikokuyucu.omr.template.ActiveOmrTemplateResolver
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSource
import com.okulyonetim.optikokuyucu.omr.template.OmrRecognitionBindingsResolver
import com.okulyonetim.optikokuyucu.omr.template.OmrTemplate

private enum class OcrWorkspaceMode { DOCUMENT_LAYOUT, ANSWER_KEY }
private enum class OcrWritingMode { PRINTED, HANDWRITING }

private data class OcrExamTarget(
    val exam: Exam,
    val template: OmrTemplate,
    val sections: List<ManualAnswerSection>,
    val bookletGridId: String?,
    val bookletChoices: List<String>
)

@Composable
fun OcrWorkspaceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val exams = remember(context) { FileExamRepository(appContext).list() }
    val keyRepository = remember(context) { FileAnswerKeyRepository(appContext) }

    var mode by remember { mutableStateOf(OcrWorkspaceMode.DOCUMENT_LAYOUT) }
    var writingMode by remember { mutableStateOf(OcrWritingMode.PRINTED) }
    var selectedExamId by remember { mutableStateOf<String?>(null) }
    var examMenuOpen by remember { mutableStateOf(false) }
    var recognition by remember { mutableStateOf<OcrRecognitionResult?>(null) }
    var extraction by remember { mutableStateOf<OcrAnswerKeyExtraction?>(null) }
    var editedAnswers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var selectedBooklet by remember { mutableStateOf<String?>(null) }
    var bookletMenuOpen by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    val selectedExam = remember(selectedExamId, exams) { exams.firstOrNull { it.id == selectedExamId } }
    val target = remember(selectedExam?.id, selectedExam?.templateSelection) {
        selectedExam?.let { loadOcrExamTarget(appContext, it) }
    }

    fun processPages(pageUris: List<android.net.Uri>) {
        if (busy || pageUris.isEmpty()) return
        busy = true
        status = when {
            writingMode == OcrWritingMode.HANDWRITING -> "Belge düzeltildi · el yazısı iyileştirilerek yerinde tanınıyor…"
            pageUris.size > 1 -> "Belge düzeltildi · ${pageUris.size} sayfa yerleşimi korunarak tanınıyor…"
            else -> "Belge düzeltildi · metinler kendi konumlarında tanınıyor…"
        }
        OcrTextRecognizer.recognizePages(
            context = appContext,
            uris = pageUris,
            handwritingMode = writingMode == OcrWritingMode.HANDWRITING,
            answerKeyMode = mode == OcrWorkspaceMode.ANSWER_KEY
        ) { result ->
            busy = false
            result.onSuccess { value ->
                recognition = value
                status = buildString {
                    append("OCR tamamlandı · ${value.tokens.size} konumlu öğe · belge düzeni korundu")
                    if (value.enhancedForHandwriting) append(" · el yazısı iyileştirmesi")
                }
            }.onFailure { error ->
                recognition = null
                extraction = null
                status = "OCR başarısız: ${error.message ?: error.javaClass.simpleName}"
            }
        }
    }

    LaunchedEffect(mode, recognition, target?.exam?.id) {
        if (mode != OcrWorkspaceMode.ANSWER_KEY) {
            extraction = null
            editedAnswers = emptyMap()
            selectedBooklet = null
            return@LaunchedEffect
        }
        val currentRecognition = recognition
        val currentTarget = target
        if (currentRecognition == null || currentTarget == null) {
            extraction = null
            editedAnswers = emptyMap()
            selectedBooklet = null
            return@LaunchedEffect
        }
        val parsed = OcrAnswerKeyParser.parse(
            recognition = currentRecognition,
            sections = currentTarget.sections,
            knownBooklets = currentTarget.bookletChoices
        )
        extraction = parsed
        editedAnswers = parsed.answers
        selectedBooklet = parsed.detectedBooklet ?: currentTarget.bookletChoices.singleOrNull()
        status = "${parsed.answers.size}/${currentTarget.sections.sumOf { it.questionIds.size }} cevap konumlarından algılandı"
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ProductTopBar(title = "Belge / OCR", leadingText = "‹", onLeadingClick = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Spacer(Modifier.height(1.dp)) }
            item {
                OcrSectionCard(
                    title = "OCR Modu",
                    subtitle = "Belge görüntüsü korunur; OCR metni sayfadan koparıp düz listeye dönüştürmez."
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OcrChoiceButton(
                            Modifier.weight(1f),
                            "Belge Düzeni",
                            mode == OcrWorkspaceMode.DOCUMENT_LAYOUT
                        ) { mode = OcrWorkspaceMode.DOCUMENT_LAYOUT }
                        OcrChoiceButton(
                            Modifier.weight(1f),
                            "Cevap Anahtarı",
                            mode == OcrWorkspaceMode.ANSWER_KEY
                        ) { mode = OcrWorkspaceMode.ANSWER_KEY }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OcrChoiceButton(
                            Modifier.weight(1f),
                            "Basılı Metin",
                            writingMode == OcrWritingMode.PRINTED
                        ) { writingMode = OcrWritingMode.PRINTED }
                        OcrChoiceButton(
                            Modifier.weight(1f),
                            "El Yazısı",
                            writingMode == OcrWritingMode.HANDWRITING
                        ) { writingMode = OcrWritingMode.HANDWRITING }
                    }
                    Text(
                        "İşlem sırası: kenar/perspektif düzeltme → kırpma/filtre → aynı görüntü üzerinde konumlu OCR. Türkçe karakterler ve sayfa geometrisi korunur.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (mode == OcrWorkspaceMode.ANSWER_KEY) {
                item {
                    OcrSectionCard(
                        title = "Hedef Sınav",
                        subtitle = "Ders ve soru yapısı seçilen sınavın optik formundan alınır."
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { examMenuOpen = true },
                                shape = RoundedCornerShape(13.dp)
                            ) {
                                Text(
                                    selectedExam?.name ?: "Sınav Seç",
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text("⌄")
                            }
                            DropdownMenu(expanded = examMenuOpen, onDismissRequest = { examMenuOpen = false }) {
                                exams.forEach { exam ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(exam.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(exam.schoolName, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        },
                                        onClick = {
                                            selectedExamId = exam.id
                                            examMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                        when {
                            exams.isEmpty() -> Text("Önce bir sınav oluşturun.", color = MaterialTheme.colorScheme.error)
                            selectedExam != null && target == null -> Text(
                                "Seçili sınavın optik formu çözümlenemedi.",
                                color = MaterialTheme.colorScheme.error
                            )
                            target != null -> Text(
                                "${target.sections.size} ders/cevap bloğu · ${target.sections.sumOf { it.questionIds.size }} soru",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            item {
                val canRead = !busy && (mode == OcrWorkspaceMode.DOCUMENT_LAYOUT || target != null)
                OcrSectionCard(
                    title = "Belgeyi Düzelt ve Tanı",
                    subtitle = "Kamera veya galeriden alınan belge önce Google Document Scanner ile düzeltilir."
                ) {
                    OcrDocumentScannerButton(
                        enabled = canRead,
                        pageLimit = if (mode == OcrWorkspaceMode.ANSWER_KEY) 1 else 10,
                        onPagesReady = ::processPages,
                        onStatus = { status = it }
                    )
                    Text(
                        "Tarayıcı içinden galeriyi de seçebilirsiniz. Ham görsel doğrudan OCR'a gönderilmez; düzeltilmiş sayfa üzerinde tanıma yapılır.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (status.isNotBlank()) {
                        Text(
                            status,
                            fontSize = 10.sp,
                            color = if (status.startsWith("OCR başarısız") || status.startsWith("Akıllı belge tarayıcı açılamadı")) {
                                MaterialTheme.colorScheme.error
                            } else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            recognition?.let { currentRecognition ->
                item {
                    OcrSectionCard(
                        title = "Düzeni Korunan OCR",
                        subtitle = "Tablo tablo olarak, metin kendi sayfa konumunda kalır. Yeşil çerçeveler tanınan alanları gösterir."
                    ) {
                        OcrLayoutPreview(currentRecognition)
                    }
                }
            }

            val parsed = extraction
            val currentTarget = target
            if (mode == OcrWorkspaceMode.ANSWER_KEY && parsed != null && currentTarget != null) {
                if (parsed.warnings.isNotEmpty()) {
                    item {
                        OcrSectionCard("Kontrol Gerekenler", "Belirsiz hücreler otomatik olarak doğru kabul edilmez.") {
                            parsed.warnings.forEach { warning ->
                                Text("• $warning", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                if (currentTarget.bookletChoices.isNotEmpty()) {
                    item {
                        OcrSectionCard("Kitapçık", "Görseldeki kitapçık bilgisini doğrulayın.") {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { bookletMenuOpen = true },
                                    shape = RoundedCornerShape(13.dp)
                                ) {
                                    Text("Kitapçık ${selectedBooklet ?: "Seç"}", modifier = Modifier.weight(1f))
                                    Text("⌄")
                                }
                                DropdownMenu(expanded = bookletMenuOpen, onDismissRequest = { bookletMenuOpen = false }) {
                                    currentTarget.bookletChoices.forEach { value ->
                                        DropdownMenuItem(
                                            text = { Text("Kitapçık $value") },
                                            onClick = {
                                                selectedBooklet = value
                                                bookletMenuOpen = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                currentTarget.sections.forEach { section ->
                    item(key = "ocr-${section.id}") {
                        OcrAnswerSection(section, editedAnswers) { questionId, choice ->
                            editedAnswers = editedAnswers.toMutableMap().apply { put(questionId, choice) }
                        }
                    }
                }

                item {
                    val expected = currentTarget.sections.flatMap { it.questionIds }
                    val sectionByQuestion = buildMap<String, ManualAnswerSection> {
                        currentTarget.sections.forEach { section ->
                            section.questionIds.forEach { questionId -> put(questionId, section) }
                        }
                    }
                    val missing = expected.count { questionId ->
                        val answer = editedAnswers[questionId]
                        answer == null || answer !in sectionByQuestion.getValue(questionId).allowedChoices
                    }
                    val bookletReady = currentTarget.bookletGridId == null || !selectedBooklet.isNullOrBlank()
                    OcrSectionCard(
                        "Cevap Anahtarına Aktar",
                        if (missing == 0) "Tüm cevaplar kontrol edildi." else "$missing soru henüz boş veya geçersiz."
                    ) {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = missing == 0 && bookletReady,
                            onClick = {
                                runCatching {
                                    val variant = selectedBooklet?.takeIf(String::isNotBlank)
                                    StoredAnswerKey(
                                        answerKey = AnswerKey(
                                            currentTarget.template.id,
                                            currentTarget.template.version,
                                            expected.associateWith { editedAnswers.getValue(it) }
                                        ),
                                        variantGridId = if (variant == null) null else currentTarget.bookletGridId,
                                        variantValue = variant,
                                        source = AnswerKeySource.CAMERA,
                                        examId = currentTarget.exam.id
                                    ).also(keyRepository::save)
                                }.onSuccess {
                                    status = "${expected.size} soruluk cevap anahtarı sınava kaydedildi"
                                }.onFailure { error ->
                                    status = "Cevap anahtarı kaydedilemedi: ${error.message ?: error.javaClass.simpleName}"
                                }
                            },
                            shape = RoundedCornerShape(13.dp)
                        ) { Text("Cevap Anahtarına Kaydet") }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun OcrSectionCard(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            subtitle?.let { Text(it, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            content()
        }
    }
}

@Composable
private fun OcrChoiceButton(modifier: Modifier, label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        FilledTonalButton(modifier = modifier, onClick = onClick, shape = RoundedCornerShape(12.dp)) {
            Text("$label ✓", fontSize = 11.sp)
        }
    } else {
        OutlinedButton(modifier = modifier, onClick = onClick, shape = RoundedCornerShape(12.dp)) {
            Text(label, fontSize = 11.sp)
        }
    }
}

@Composable
private fun OcrAnswerSection(
    section: ManualAnswerSection,
    answers: Map<String, String>,
    onAnswerChange: (String, String) -> Unit
) {
    OcrSectionCard(section.label, "${section.questionIds.size} soru · görsel konumlarından okundu") {
        section.questionIds.forEachIndexed { index, questionId ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text("${index + 1}", modifier = Modifier.size(32.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                section.allowedChoices.sorted().forEach { choice ->
                    val selected = answers[questionId] == choice
                    Surface(
                        modifier = Modifier.size(38.dp).clickable { onAnswerChange(questionId, choice) },
                        shape = CircleShape,
                        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        border = BorderStroke(
                            if (selected) 1.6.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(choice, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                if (answers[questionId] == null) {
                    Text("?", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun loadOcrExamTarget(context: Context, exam: Exam): OcrExamTarget? = runCatching {
    val documentRepository = FileDesignerDocumentRepository(context.applicationContext)
    val savedDocuments = documentRepository.list()
    val starterDocuments = DesignerStarterTemplates.all()
    val resolved = requireNotNull(
        ActiveOmrTemplateResolver.resolve(
            selection = exam.templateSelection,
            savedDocuments = savedDocuments,
            starterDocuments = starterDocuments
        )
    ) { "Sınavın optik formu bulunamadı." }
    val document = if (exam.templateSelection.source == ActiveTemplateSource.DESIGNER_DOCUMENT) {
        savedDocuments.firstOrNull {
            it.id == exam.templateSelection.templateId && it.version == exam.templateSelection.templateVersion
        } ?: starterDocuments.firstOrNull {
            it.id == exam.templateSelection.templateId && it.version == exam.templateSelection.templateVersion
        }
    } else null
    val sections = ManualAnswerKeyBuilder.sections(document, resolved.template)
    val bindings = OmrRecognitionBindingsResolver.fromTemplate(resolved.template)
    val bookletGridId = bindings.bookletGridId
    val bookletChoices = bookletGridId
        ?.let { gridId -> resolved.template.markGrids.firstOrNull { it.id == gridId } }
        ?.columns
        ?.flatMap { column -> column.marks.map { it.id } }
        ?.distinct()
        .orEmpty()

    OcrExamTarget(exam, resolved.template, sections, bookletGridId, bookletChoices)
}.getOrNull()
