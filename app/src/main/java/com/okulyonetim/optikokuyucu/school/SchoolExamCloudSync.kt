package com.okulyonetim.optikokuyucu.school

import android.content.Context
import com.okulyonetim.optikokuyucu.exam.Exam
import com.okulyonetim.optikokuyucu.exam.ExamPaperResolution
import com.okulyonetim.optikokuyucu.exam.ExamReportBuilder
import com.okulyonetim.optikokuyucu.exam.ExamScoringPolicyResolver
import com.okulyonetim.optikokuyucu.exam.ExamScoringType
import com.okulyonetim.optikokuyucu.exam.FileExamRepository
import com.okulyonetim.optikokuyucu.exam.StudentResultPresentationBuilder
import com.okulyonetim.optikokuyucu.exam.WrongAnswerPolicy
import com.okulyonetim.optikokuyucu.omr.designer.DesignerStarterTemplates
import com.okulyonetim.optikokuyucu.omr.designer.DesignerTemplateCompiler
import com.okulyonetim.optikokuyucu.omr.designer.FileDesignerDocumentRepository
import com.okulyonetim.optikokuyucu.omr.designer.QuestionGroupComponent
import com.okulyonetim.optikokuyucu.omr.results.FileScanRecordRepository
import com.okulyonetim.optikokuyucu.omr.scoring.FileAnswerKeyRepository
import com.okulyonetim.optikokuyucu.omr.scoring.OmrScorer
import com.okulyonetim.optikokuyucu.student.StudentNumber
import com.okulyonetim.optikokuyucu.student.StudentSchoolIdentity
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

private data class ResultSyncStats(
    val uploaded: Int,
    val skippedWithoutStudentIdentity: Int
)

/**
 * Publishes local OMR exams into the same Firestore documents already consumed by Okul Yönetim.
 * ownerUid is kept stable across accounts; a normal user never republishes another user's public
 * exam under their own identity.
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
        val profile = session.profile
        val exams = examRepository.list()
        val failures = mutableListOf<String>()
        var definitions = 0
        var resultDocs = 0
        var studentResults = 0
        var skippedPermission = 0
        var skippedIdentity = 0

        exams.forEach { rawExam ->
            val exam = if (rawExam.ownerUid.isBlank() && profile.admin) {
                rawExam.copy(ownerUid = profile.uid, ownerDisplayName = profile.displayName).also(examRepository::save)
            } else rawExam
            if (!SchoolContentAccess.canModifyExam(exam, profile)) return@forEach

            if (SchoolSyncAccessPolicy.canSyncExamDefinition(profile)) {
                runCatching { syncDefinition(exam, profile) }
                    .onSuccess { definitions += 1 }
                    .onFailure { failures += "${exam.name} sınavı: ${it.message ?: "tanım gönderilemedi"}" }
            } else {
                skippedPermission += 1
            }

            // Teachers may send results only; definition and institution data stays read-only.
            if (SchoolSyncAccessPolicy.canSyncResults(profile)) {
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

        runCatching { SchoolExamCatalogSyncService(appContext, client).refresh() }
            .onFailure { failures += "Sınav kataloğu: ${it.message ?: "yenilenemedi"}" }

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
        val ownerUid = exam.ownerUid.ifBlank { profile.uid }
        val ownerName = exam.ownerDisplayName.ifBlank {
            if (ownerUid == profile.uid) profile.displayName else ""
        }
        val lessonDefinitions = lessonDefinitions(exam)
        val payload = linkedMapOf<String, Any?>(
            "ad" to exam.name,
            "okulAdi" to exam.schoolName,
            "tarih" to LocalDate.ofEpochDay(exam.examDateEpochDay).toString(),
            "sinifSeviyesi" to commonGradeLevel(exam),
            "yanlisKatsayisi" to wrongCoefficient(exam.wrongAnswerPolicy),
            "dersler" to lessonDefinitions,
            "sahipUid" to ownerUid,
            "sahipAdi" to ownerName,
            "herkeseAcik" to exam.isPublic,
            "kaynak" to "optik-okuyucu",
            "optikSinavId" to exam.id,
            "optikFormId" to exam.templateSelection.templateId,
            "optikFormSurumu" to exam.templateSelection.templateVersion,
            "optikFormKaynagi" to exam.templateSelection.source.name,
            "kitapcikSayisi" to exam.bookletCount,
            "olusturmaTarihi" to java.time.Instant.ofEpochMilli(exam.createdAtEpochMs).toString(),
            "guncellenmeTarihi" to java.time.Instant.now().toString()
        )
        client.upsertDocument(SchoolPortalConfig.TRIAL_EXAMS, exam.id, payload)
    }

    private fun syncResults(exam: Exam): ResultSyncStats {
        val recordList = recordRepository.list()
        val records = recordList.associateBy { it.id }
        val keys = keyRepository.list()
        val subjectMap = questionSubjectMap(exam)
        val report = ExamReportBuilder.build(exam, recordList, keys)
        val reportRowsByScanId = report.rows.associateBy { it.scanRecordId }
        val isLgs = report.scoringType == ExamScoringType.LGS
        val resultByStudentIdentity = linkedMapOf<String, Map<String, Any?>>()
        var skippedWithoutStudentIdentity = 0

        exam.papers.forEach { link ->
            val normalizedNumber = StudentNumber.normalize(link.studentNumber)
            if (normalizedNumber.isBlank()) {
                skippedWithoutStudentIdentity += 1
                return@forEach
            }

            val linkedGrade = StudentSchoolIdentity.gradeLevelFromClassName(link.className)
            val participantGrade = exam.participants
                .filter { StudentNumber.normalize(it.studentNumber) == normalizedNumber }
                .mapNotNull { StudentSchoolIdentity.gradeLevelFromClassName(it.className) }
                .let { grades ->
                    when {
                        linkedGrade != null -> grades.firstOrNull {
                            StudentSchoolIdentity.sameInstitution(it, linkedGrade)
                        } ?: linkedGrade
                        grades.distinctBy(StudentSchoolIdentity::institutionKeyForGrade).size == 1 -> grades.firstOrNull()
                        else -> null
                    }
                }
            val gradeLevel = linkedGrade ?: participantGrade
            val schoolDocumentId = if (gradeLevel != null) {
                studentIdentityStore.documentIdFor(normalizedNumber, gradeLevel)
            } else {
                studentIdentityStore.documentIdFor(normalizedNumber)
            }
            if (schoolDocumentId.isNullOrBlank()) {
                skippedWithoutStudentIdentity += 1
                return@forEach
            }

            val record = records[link.scanRecordId] ?: return@forEach
            val key = ExamPaperResolution.answerKey(exam.id, link, record, keys) ?: return@forEach
            val score = runCatching {
                OmrScorer.score(
                    record = record,
                    answerKey = key.answerKey,
                    // Use the exact same effective exam policy as the report/student-result screens.
                    policy = ExamScoringPolicyResolver.resolve(exam)
                )
            }.getOrNull() ?: return@forEach
            val reportRow = reportRowsByScanId[record.id]
            val presentation = StudentResultPresentationBuilder.build(report, record.id)
            val calculatedScore = presentation?.score ?: reportRow?.points

            val grouped = score.evaluations.groupBy { evaluation ->
                subjectMap[evaluation.questionId] ?: fallbackSubject(evaluation.questionId)
            }
            val lessonResults = grouped.mapValues { (_, evaluations) ->
                SchoolLessonResultMapper.toFirestoreMap(
                    SchoolLessonResultMapper.summarize(evaluations)
                )
            }
            val result = linkedMapOf<String, Any?>(
                "ogrenciId" to schoolDocumentId,
                "ogrenciAdi" to link.studentName,
                "ogrenciNo" to normalizedNumber,
                "sinif" to link.className,
                "okulAdi" to gradeLevel?.let(StudentSchoolIdentity::schoolNameForGrade).orEmpty(),
                "dersSonuclari" to lessonResults,
                "dogru" to (presentation?.correct ?: score.correctCount),
                "yanlis" to (presentation?.wrong ?: score.wrongCount),
                "bos" to (presentation?.blank ?: score.blankCount),
                "cift" to score.doubleMarkCount,
                "supheli" to score.suspiciousCount,
                "anahtarsiz" to score.noKeyCount,
                "net" to (presentation?.net ?: reportRow?.net ?: score.totalPoints),
                // presentation.score is the value displayed on the Optik Okuyucu student-result screen.
                "puan" to calculatedScore,
                "lgsPuani" to if (isLgs) calculatedScore else null,
                "lgsPuan" to if (isLgs) calculatedScore else null,
                "genelSiralama" to (presentation?.overallRank?.rank ?: reportRow?.overallRank),
                "sinifSiralama" to (presentation?.classRank?.rank ?: reportRow?.classRank),
                "katilimciSayisi" to (presentation?.overallRank?.participantCount ?: report.scoredCount),
                "sinifKatilimciSayisi" to presentation?.classRank?.participantCount,
                "maksimumPuan" to (presentation?.maximumScore ?: reportRow?.maximumPoints),
                "puanTuru" to report.scoringType.name,
                "kitapcik" to link.bookletCode,
                "optikTaramaId" to record.id,
                "tarih" to java.time.Instant.ofEpochMilli(record.capturedAtEpochMs).toString(),
                "kaynak" to "optik-okuyucu"
            )

            val resultIdentity = gradeLevel?.let {
                StudentSchoolIdentity.identityKey(normalizedNumber, it)
            }?.takeIf(String::isNotBlank) ?: "number:$normalizedNumber"
            resultByStudentIdentity[resultIdentity] = result
        }

        val results = resultByStudentIdentity.values.toList()
        val payload = linkedMapOf<String, Any?>(
            "sinavId" to exam.id,
            "ad" to exam.name,
            "sinavAdi" to exam.name,
            "tarih" to LocalDate.ofEpochDay(exam.examDateEpochDay).toString(),
            "sinifSeviyesi" to commonGradeLevel(exam),
            "yanlisKatsayisi" to wrongCoefficient(exam.wrongAnswerPolicy),
            "dersler" to lessonDefinitions(exam),
            "sonuclar" to results,
            "puanTuru" to report.scoringType.name,
            "kaynak" to "optik-okuyucu",
            "optikSinavId" to exam.id,
            "sahipUid" to exam.ownerUid,
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
}
