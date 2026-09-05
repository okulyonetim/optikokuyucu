package com.okulyonetim.optikokuyucu.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import com.okulyonetim.optikokuyucu.omr.diagnostics.OmrSelfTestResult

private enum class RootDestination {
    EXAMS,
    NEW_EXAM,
    EXAM_DETAIL,
    EXAM_SCANNER,
    STUDENT_PAPER,
    TOOLS,
    SCANNER,
    RESULTS,
    ANSWER_KEYS,
    ACTIVE_TEMPLATE,
    DESIGNER,
    ADVANCED_DESIGNER
}

@Composable
fun OmrRootScreen(
    openCvReady: Boolean,
    selfTest: OmrSelfTestResult
) {
    var destination by remember { mutableStateOf(RootDestination.EXAMS) }
    var selectedExamId by remember { mutableStateOf<String?>(null) }
    var selectedScanRecordId by remember { mutableStateOf<String?>(null) }

    if (destination != RootDestination.EXAMS) {
        BackHandler {
            destination = when (destination) {
                RootDestination.NEW_EXAM,
                RootDestination.EXAM_DETAIL,
                RootDestination.TOOLS -> RootDestination.EXAMS

                RootDestination.EXAM_SCANNER,
                RootDestination.STUDENT_PAPER -> RootDestination.EXAM_DETAIL

                RootDestination.ADVANCED_DESIGNER -> RootDestination.DESIGNER

                RootDestination.SCANNER,
                RootDestination.RESULTS,
                RootDestination.ANSWER_KEYS,
                RootDestination.ACTIVE_TEMPLATE,
                RootDestination.DESIGNER -> RootDestination.TOOLS

                RootDestination.EXAMS -> RootDestination.EXAMS
            }
        }
    }

    OptikProductTheme {
        when (destination) {
            RootDestination.EXAMS -> ExamListScreen(
                onNewExam = { destination = RootDestination.NEW_EXAM },
                onOpenExam = { examId ->
                    selectedExamId = examId
                    selectedScanRecordId = null
                    destination = RootDestination.EXAM_DETAIL
                },
                onOpenTools = { destination = RootDestination.TOOLS }
            )

            RootDestination.NEW_EXAM -> NewExamScreen(
                onBack = { destination = RootDestination.EXAMS },
                onSaved = { examId ->
                    selectedExamId = examId
                    selectedScanRecordId = null
                    destination = RootDestination.EXAM_DETAIL
                }
            )

            RootDestination.EXAM_DETAIL -> {
                val examId = selectedExamId
                if (examId == null) {
                    destination = RootDestination.EXAMS
                } else {
                    ExamDetailScreen(
                        examId = examId,
                        onBack = {
                            selectedScanRecordId = null
                            destination = RootDestination.EXAMS
                        },
                        onScan = { destination = RootDestination.EXAM_SCANNER },
                        onOpenPaper = { scanRecordId ->
                            selectedScanRecordId = scanRecordId
                            destination = RootDestination.STUDENT_PAPER
                        },
                        onOpenAnswerKeys = { destination = RootDestination.ANSWER_KEYS },
                        onOpenReports = { destination = RootDestination.RESULTS }
                    )
                }
            }

            RootDestination.EXAM_SCANNER -> {
                val examId = selectedExamId
                if (examId == null) {
                    destination = RootDestination.EXAMS
                } else {
                    ExamScannerScreen(
                        examId = examId,
                        openCvReady = openCvReady,
                        selfTest = selfTest,
                        onBack = { destination = RootDestination.EXAM_DETAIL }
                    )
                }
            }

            RootDestination.STUDENT_PAPER -> {
                val examId = selectedExamId
                val scanRecordId = selectedScanRecordId
                if (examId == null || scanRecordId == null) {
                    destination = RootDestination.EXAM_DETAIL
                } else {
                    StudentPaperDetailScreen(
                        examId = examId,
                        scanRecordId = scanRecordId,
                        onBack = { destination = RootDestination.EXAM_DETAIL }
                    )
                }
            }

            RootDestination.TOOLS -> RootToolsScreen(
                onBackToExams = { destination = RootDestination.EXAMS },
                onOpenScanner = { destination = RootDestination.SCANNER },
                onOpenResults = { destination = RootDestination.RESULTS },
                onOpenAnswerKeys = { destination = RootDestination.ANSWER_KEYS },
                onOpenActiveTemplate = { destination = RootDestination.ACTIVE_TEMPLATE },
                onOpenDesigner = { destination = RootDestination.DESIGNER }
            )

            RootDestination.SCANNER -> OmrAppScreen(
                openCvReady = openCvReady,
                selfTest = selfTest
            )

            RootDestination.RESULTS -> ScanSessionScreen(
                onBack = {
                    destination = if (selectedExamId != null) RootDestination.EXAM_DETAIL else RootDestination.TOOLS
                }
            )

            RootDestination.ANSWER_KEYS -> AnswerKeyScreen(
                openCvReady = openCvReady,
                onBack = {
                    destination = if (selectedExamId != null) RootDestination.EXAM_DETAIL else RootDestination.TOOLS
                }
            )

            RootDestination.ACTIVE_TEMPLATE -> ActiveTemplateScreen(
                onBack = { destination = RootDestination.TOOLS }
            )

            RootDestination.DESIGNER -> StructuredOmrDesignerScreen(
                openCvReady = openCvReady,
                onBack = { destination = RootDestination.TOOLS },
                onOpenAdvanced = { destination = RootDestination.ADVANCED_DESIGNER }
            )

            RootDestination.ADVANCED_DESIGNER -> OmrDesignerScreen(
                openCvReady = openCvReady,
                selfTest = selfTest,
                onBack = { destination = RootDestination.DESIGNER }
            )
        }
    }
}

@Composable
private fun RootToolsScreen(
    onBackToExams: () -> Unit,
    onOpenScanner: () -> Unit,
    onOpenResults: () -> Unit,
    onOpenAnswerKeys: () -> Unit,
    onOpenActiveTemplate: () -> Unit,
    onOpenDesigner: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TextButton(onClick = onBackToExams) { Text("‹ Sınavlar") }
        Text("Optik Araçları", style = MaterialTheme.typography.headlineMedium)
        Text(
            modifier = Modifier.padding(top = 8.dp, bottom = 28.dp),
            text = "Okuma, anahtar ve form tasarımı",
            style = MaterialTheme.typography.bodyMedium
        )

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onOpenScanner
        ) {
            Text("Tara · Kamera ve OMR")
        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            onClick = onOpenResults
        ) {
            Text("Tarama Oturumu · Sonuçlar")
        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            onClick = onOpenAnswerKeys
        ) {
            Text("Cevap Anahtarları")
        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            onClick = onOpenActiveTemplate
        ) {
            Text("Aktif Form / Şablon")
        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            onClick = onOpenDesigner
        ) {
            Text("Optik Form Tasarımcısı")
        }
    }
}
