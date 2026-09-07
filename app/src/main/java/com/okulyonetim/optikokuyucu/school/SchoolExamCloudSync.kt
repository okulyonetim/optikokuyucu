package com.okulyonetim.optikokuyucu.school

import android.content.Context
import com.okulyonetim.optikokuyucu.exam.Exam
import com.okulyonetim.optikokuyucu.exam.ExamPaperResolution
import com.okulyonetim.optikokuyucu.exam.ExamScoringPolicyResolver
import com.okulyonetim.optikokuyucu.exam.FileExamRepository
import com.okulyonetim.optikokuyucu.exam.WrongAnswerPolicy
import com.okulyonetim.optikokuyucu.omr.designer.DesignerStarterTemplates
import com.okulyonetim.optikokuyucu.omr.designer.DesignerTemplateCompiler
import com.okulyonetim.optikokuyucu.omr.designer.FileDesignerDocumentRepository
import com.okulyonetim.optikokuyucu.omr.designer.QuestionGroupComponent
import com.okulyonetim.optikokuyucu.omr.results.FileScanRecordRepository
import com.okulyonetim.optikokuyucu.omr.scoring.FileAnswerKeyRepository
import com.okulyonetim.optikokuyucu.omr.scoring.OmrScorer
import com.okulyonetim.optikokuyucu.omr.scoring.QuestionEvaluation
import com.okulyonetim.optikokuyucu.omr.scoring.QuestionEvaluationState
import com.okulyonetim.optikokuyucu.student.StudentNumber
import java.time.LocalDate

data class SchoolExamCloudSyncResult(
    val localExamCount: Int,
    val syncedDefinitions: Int,
    val syncedResultDocuments: Int,
    val syncedStudentResults: Int,
    val skippedWithoutPermission: Int,
    val skippedWithoutStudentIdentity: Int,
    val failures: List<String>
)

private data class SubjectCounters(
    var correct: Int = 0,
    var wrong: Int = 0,
    var blank: Int = 0,
    var doubleMark: Int = 0,
    var suspicious: Int = 0,
    var noKey: Int = 0,
    var net: Double = 0.0
)

private data class ResultSyncStats(
    val uploaded: Int,
    val skippedWithoutStudentIdentity: Int
)

/**
 * Publishes local OMR exams into the same Firestore documents already consumed by Okul Yönetim.
 * The student number is the stable cross-app match key requested for this integration. Firestore
 * document ids are only compatibility pointers resolved from that student number.
 */
class SchoolExamCloudSyncService(
    context: Context,
    private val client: SchoolPortalClient
) {
    private val appContext = context.applicationContext
    private val examRepository = FileExamRepository(appContext)
    private val recordRepository = FileScanRecordRepository(appContext)
    private val keyRepository = FileAnswerKeyRepository(appContext)
    private val documentRepository = FileDesignerDocumentRepository(appContext)
    private val studentIdentityStore = SchoolStudentIdentityStore(appContext)

    fun syncAll(): SchoolExamCloudSyncResult {
        val session = requireNotNull(client.cachedSession()) { "Okul Yönetim oturumu yok." }
        val exams = examRepository.list()
        val failures = mutableListOf<String>()
        var definitions = 0
        var resultDocs = 0
        var studentResults = 0
        var skippedPermission = 0
        var skippedIdentity = 0

        exams.forEach { exam ->
            if (session.profile.canEdit("sinavIslemleri")) {
                runCatching { syncDefinition(exam, session.profile) }
                    .onSuccess { definitions += 1 }
                    .onFailure { failures += "${exam.name} sınavı: ${it.message ?: "tanım gönderilemedi"}" }
            } else {
                skippedPermission += 1
            }

            // Live firestore.rules allows denemeSonuclari writes for users who can view this module.
            if (session.profile.canView("denemeSonuclari")) {
                runCatching { syncResults(exam) }
                    .onSuccess { stats ->
                        resultDocs += 1
                        studentResults += stats.uploaded
                        skippedIdentity += stats.skippedWithoutStudentIdentity
                    }
                    .onFailure { failures += "${exam.name} sonuçları: ${it.message ?: "gönderilemedi"}" }
            } else {
                skippedPermission += 1
            }
        }

        return SchoolExamCloudSyncResult(
            localExamCount = exams.size,
            syncedDefinitions = definitions,
            syncedResultDocuments = resultDocs,
            syncedStudentResults = studentResults,
            skippedWithoutPermission = skippedPermission,
            skippedWithoutStudentIdentity = skippedIdentity,
            failures = failures
        )
    }

    private fun syncDefinition(exam: Exam, profile: SchoolUserProfile) {
        val lessonDefinitions = lessonDefinitions(exam)
        val payload = linkedMapOf<String, Any?>(
            "ad" to exam.name,
            "tarih" to LocalDate.ofEpochDay(exam.examDateEpochDay).toString(),
            "sinifSeviyesi" to commonGradeLevel(exam),
            "yanlisKatsayisi" to wrongCoefficient(exam.wrongAnswerPolicy),
            "dersler" to lessonDefinitions,
            "sahipUid" to profile.uid,
            "kaynak" to "optik-okuyucu",
            "optikSinavId" to exam.id,
            "optikFormId" to exam.templateSelection.templateId,
            "optikFormSurumu" to exam.templateSelection.templateVersion,
            "kitapcikSayisi" to exam.bookletCount,
            "olusturmaTarihi" to java.time.Instant.ofEpochMilli(exam.createdAtEpochMs).toString()
        )
        client.upsertDocument(SchoolPortalConfig.TRIAL_EXAMS, exam.id, payload)
    }

    private fun syncResults(exam: Exam): ResultSyncStats {
        val records = recordRepository.list().associateBy { it.id }
        val keys = keyRepository.list()
        val subjectMap = questionSubjectMap(exam)
        val resultByStudentNumber = linkedMapOf<String, Map<String, Any?>>()
        var skippedWithoutStudentIdentity = 0

        exam.papers.forEach { link ->
            val normalizedNumber = StudentNumber.normalize(link.studentNumber)
            if (normalizedNumber.isBlank()) {
                skippedWithoutStudentIdentity += 1
                return@forEach
            }
            val schoolDocumentId = studentIdentityStore.documentIdFor(normalizedNumber)
            if (schoolDocumentId.isNullOrBlank()) {
                skippedWithoutStudentIdentity += 1
                return@forEach
            }

            val record = records[link.scanRecordId] ?: return@forEach
            val key = ExamPaperResolution.answerKey(link, record, keys) ?: return@forEach
            val score = runCatching {
                OmrScorer.score(
                    record = record,
                    answerKey = key.answerKey,
                    policy = ExamScoringPolicyResolver.resolve(exam.wrongAnswerPolicy)
                )
            }.getOrNull() ?: return@forEach

            val grouped = linkedMapOf<String, SubjectCounters>()
            score.evaluations.forEach { evaluation ->
                val subject = subjectMap[evaluation.questionId]
                    ?: fallbackSubject(evaluation.questionId)
                grouped.getOrPut(subject) { SubjectCounters() }.add(evaluation)
            }
            val lessonResults = grouped.mapValues { (_, counters) -> counters.toFirestoreMap() }
            val result = linkedMapOf<String, Any?>(
                "ogrenciId" to schoolDocumentId,
                "ogrenciAdi" to link.studentName,
                "ogrenciNo" to normalizedNumber,
                "sinif" to link.className,
                "dersSonuclari" to lessonResults,
                "dogru" to score.correctCount,
                "yanlis" to score.wrongCount,
                "bos" to score.blankCount,
                "cift" to score.doubleMarkCount,
                "supheli" to score.suspiciousCount,
                "anahtarsiz" to score.noKeyCount,
                "net" to score.totalPoints,
                "kitapcik" to link.bookletCode,
                "optikTaramaId" to record.id,
                "tarih" to java.time.Instant.ofEpochMilli(record.capturedAtEpochMs).toString(),
                "kaynak" to "optik-okuyucu"
            )

            // A corrected/re-read paper replaces an older cloud row for the same normalized number.
            resultByStudentNumber[normalizedNumber] = result
        }

        val results = resultByStudentNumber.values.toList()
        val payload = linkedMapOf<String, Any?>(
            "sinavId" to exam.id,
            "ad" to exam.name,
            "sinavAdi" to exam.name,
            "tarih" to LocalDate.ofEpochDay(exam.examDateEpochDay).toString(),
            "sinifSeviyesi" to commonGradeLevel(exam),
            "yanlisKatsayisi" to wrongCoefficient(exam.wrongAnswerPolicy),
            "dersler" to lessonDefinitions(exam),
            "sonuclar" to results,
            "kaynak" to "optik-okuyucu",
            "optikSinavId" to exam.id,
            "guncellenmeTarihi" to java.time.Instant.now().toString()
        )
        client.upsertDocument(SchoolPortalConfig.TRIAL_RESULTS, exam.id, payload)
        return ResultSyncStats(
            uploaded = results.size,
            skippedWithoutStudentIdentity = skippedWithoutStudentIdentity
        )
    }

    private fun lessonDefinitions(exam: Exam): List<Map<String, Any?>> {
        val subjectMap = questionSubjectMap(exam)
        val counts = subjectMap.values.groupingBy { it }.eachCount()
        if (counts.isNotEmpty()) {
            return counts.map { (name, count) -> mapOf("ad" to name, "soruSayisi" to count) }
        }
        return listOf(mapOf("ad" to "Genel", "soruSayisi" to 0))
    }

    private fun questionSubjectMap(exam: Exam): Map<String, String> {
        val document = documentRepository.load(
            exam.templateSelection.templateId,
            exam.templateSelection.templateVersion
        ) ?: DesignerStarterTemplates.all().firstOrNull {
            it.id == exam.templateSelection.templateId && it.version == exam.templateSelection.templateVersion
        }
        if (document == null) return emptyMap()

        val mapping = linkedMapOf<String, String>()
        document.components.filterIsInstance<QuestionGroupComponent>().forEach { component ->
            val label = component.label.trim().ifBlank { "Genel" }
            repeat(component.questionCount) { offset ->
                val number = component.startQuestion + offset
                val id = DesignerTemplateCompiler.questionReadId(component, number)
                mapping[id] = label
            }
        }
        return mapping
    }

    private fun commonGradeLevel(exam: Exam): String {
        val grades = (exam.participants.map { it.className } + exam.papers.map { it.className })
            .mapNotNull { Regex("\\d{1,2}").find(it)?.value?.toIntOrNull() }
            .filter { it in 1..12 }
            .distinct()
        return if (grades.size == 1) grades.single().toString() else ""
    }

    private fun wrongCoefficient(policy: WrongAnswerPolicy): Int = when (policy) {
        WrongAnswerPolicy.KEEP_AS_IS -> 0
        WrongAnswerPolicy.FOUR_WRONG_ONE_CORRECT -> 4
        WrongAnswerPolicy.THREE_WRONG_ONE_CORRECT -> 3
    }

    private fun fallbackSubject(questionId: String): String {
        val prefix = questionId.substringBefore(':', missingDelimiterValue = "").trim()
        if (prefix.isBlank() || prefix.startsWith("answers-", ignoreCase = true)) return "Genel"
        return prefix
    }

    private fun SubjectCounters.add(evaluation: QuestionEvaluation) {
        when (evaluation.state) {
            QuestionEvaluationState.CORRECT -> correct += 1
            QuestionEvaluationState.WRONG -> wrong += 1
            QuestionEvaluationState.BLANK -> blank += 1
            QuestionEvaluationState.DOUBLE_MARK -> doubleMark += 1
            QuestionEvaluationState.SUSPICIOUS -> suspicious += 1
            QuestionEvaluationState.NO_KEY -> noKey += 1
        }
        net += evaluation.points
    }

    private fun SubjectCounters.toFirestoreMap(): Map<String, Any?> = linkedMapOf(
        "dogru" to correct,
        "yanlis" to wrong,
        "bos" to blank,
        "cift" to doubleMark,
        "supheli" to suspicious,
        "anahtarsiz" to noKey,
        "net" to net
    )
}
