package com.okulyonetim.optikokuyucu.school

import com.okulyonetim.optikokuyucu.exam.Exam
import com.okulyonetim.optikokuyucu.exam.ExamPaperLink
import com.okulyonetim.optikokuyucu.omr.results.RecordedAnswer
import com.okulyonetim.optikokuyucu.omr.results.RecordedAnswerState
import com.okulyonetim.optikokuyucu.omr.results.ScanRecord
import com.okulyonetim.optikokuyucu.omr.results.ScanSource
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSelection
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSource
import com.okulyonetim.optikokuyucu.student.StudentGender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SchoolPortalMappingTest {
    @Test
    fun usernameConversionMatchesSchoolManagementRules() {
        assertEquals(
            "sedat.karagoz@korukokuluportal.com",
            SchoolUsername.emailFor("  Sedat.KARAGÖZ  ")
        )
        assertEquals(
            "ogretmen-1@korukokuluportal.com",
            SchoolUsername.emailFor("Öğretmen-1")
        )
    }

    @Test
    fun studentNumberNormalizationIsStableCrossAppIdentity() {
        assertEquals("123", SchoolStudentMatch.normalizeStudentNumber("00123"))
        assertEquals("123", SchoolStudentMatch.normalizeStudentNumber(" 12-3 "))
        assertEquals("0", SchoolStudentMatch.normalizeStudentNumber("000"))
        assertEquals("", SchoolStudentMatch.normalizeStudentNumber("öğrenci"))
    }

    @Test
    fun parsesCommonSchoolClassNames() {
        assertEquals(7 to "A", SchoolClassParser.parse("7-A"))
        assertEquals(7 to "A", SchoolClassParser.parse("7/A"))
        assertEquals(7 to "A", SchoolClassParser.parse("7 A"))
        assertEquals(8 to "B", SchoolClassParser.parse("8 / B"))
        assertEquals(5 to "", SchoolClassParser.parse("5"))
    }

    @Test
    fun explicitLevelIsUsedWhenClassNameHasNoNumericPrefix() {
        assertEquals(6 to "", SchoolClassParser.parse("Şube A", "6"))
    }

    @Test
    fun missingLevelStaysUnknown() {
        assertNull(SchoolClassParser.parse("Hazırlık").first)
    }

    @Test
    fun mapsSchoolClassDocument() {
        val mapped = SchoolDirectoryMapper.schoolClass(
            FirestoreDocument(
                id = "class-7a",
                fields = mapOf("ad" to "7/A", "seviye" to 7L)
            )
        )
        assertEquals("class-7a", mapped.id)
        assertEquals(7, mapped.gradeLevel)
        assertEquals("A", mapped.branch)
    }

    @Test
    fun mapsOyVelilerDocumentIntoOfflineRoster() {
        val schoolClass = SchoolClassIdentity("class-7a", "7-A", 7, "A")
        val result = SchoolDirectoryMapper.student(
            doc = FirestoreDocument(
                id = "veli-doc-1",
                fields = mapOf(
                    "ogrenciNo" to "00123",
                    "ogrenciAdi" to "  Ayşe   Yılmaz ",
                    "sinifId" to "class-7a",
                    "cinsiyet" to "Kız",
                    "veliAdi" to "Fatma Yılmaz",
                    "telefon1" to "",
                    "telefon" to "05550000000",
                    "telefon2" to "05551111111"
                )
            ),
            classes = mapOf(schoolClass.id to schoolClass),
            updatedAtEpochMs = 42L
        )
        val entry = requireNotNull(result.entry)
        assertEquals("123", entry.studentNumber)
        assertEquals("Ayşe   Yılmaz", entry.fullName)
        assertEquals(7, entry.gradeLevel)
        assertEquals("A", entry.branch)
        assertEquals(StudentGender.GIRL, entry.gender)
        assertEquals("Fatma Yılmaz", entry.guardianName)
        assertEquals("05550000000", entry.guardianPhone)
        assertEquals(42L, entry.updatedAtEpochMs)
    }

    @Test
    fun mappingRejectsStudentWithoutNumber() {
        val result = SchoolDirectoryMapper.student(
            doc = FirestoreDocument(
                id = "veli-doc-2",
                fields = mapOf("ogrenciAdi" to "Numarasız Öğrenci", "sinifId" to "class-7a")
            ),
            classes = mapOf("class-7a" to SchoolClassIdentity("class-7a", "7-A", 7, "A")),
            updatedAtEpochMs = 1L
        )
        assertNull(result.entry)
        assertEquals(SchoolStudentSkipReason.WITHOUT_NUMBER, result.skipReason)
    }

    @Test
    fun documentIdIsResolvedOnlyFromNormalizedStudentNumber() {
        val identities = mapOf("123" to "firestore-veli-id")
        assertEquals("firestore-veli-id", SchoolStudentMatch.documentIdFor("00123", identities))
        assertNull(SchoolStudentMatch.documentIdFor("999", identities))
        assertNull(SchoolStudentMatch.documentIdFor("", identities))
    }

    @Test
    fun duplicateStudentNumberReplacesExistingCloudRow() {
        val rows = linkedMapOf<String, String>()
        assertTrue(SchoolStudentMatch.upsertByStudentNumber(rows, "00123", "ilk"))
        assertTrue(SchoolStudentMatch.upsertByStudentNumber(rows, "123", "güncel"))
        assertEquals(1, rows.size)
        assertEquals("güncel", rows["123"])
    }

    @Test
    fun blankStudentNumberCannotBecomeCloudIdentity() {
        val rows = linkedMapOf<String, String>()
        assertFalse(SchoolStudentMatch.upsertByStudentNumber(rows, "abc", "sonuç"))
        assertTrue(rows.isEmpty())
    }

    @Test
    fun resultModuleViewPermissionMatchesLiveFirestoreWriteRule() {
        val profile = SchoolUserProfile(
            uid = "u1",
            username = "test",
            displayName = "Test",
            admin = false,
            active = true,
            roleId = "r1",
            linkedTeacherId = "t1",
            permissions = mapOf("denemeSonuclari" to "goruntule", "sinavIslemleri" to "duzenle")
        )
        assertTrue(profile.canView("denemeSonuclari"))
        assertFalse(profile.canEdit("denemeSonuclari"))
        assertTrue(profile.canEdit("sinavIslemleri"))
    }

    @Test
    fun adminProfileCanViewAndEditIntegrationModules() {
        val profile = SchoolUserProfile(
            uid = "admin",
            username = "admin",
            displayName = "Admin",
            admin = true,
            active = true,
            roleId = "",
            linkedTeacherId = "",
            permissions = emptyMap()
        )
        assertTrue(profile.canView("denemeSonuclari"))
        assertTrue(profile.canEdit("sinavIslemleri"))
    }

    @Test
    fun cloudFingerprintChangesWhenRawOmrAnswerChanges() {
        val selection = ActiveTemplateSelection(
            source = ActiveTemplateSource.STANDARD,
            templateId = "template-1",
            templateVersion = 1
        )
        val exam = Exam(
            id = "exam-1",
            name = "Deneme",
            schoolName = "Okul",
            templateSelection = selection,
            examDateEpochDay = 1L,
            createdAtEpochMs = 1L,
            papers = listOf(
                ExamPaperLink(
                    scanRecordId = "scan-1",
                    studentName = "Ali Veli",
                    className = "7-A",
                    studentNumber = "123",
                    bookletCode = "A",
                    linkedAtEpochMs = 1L
                )
            )
        )
        val first = scanWithChoice("A")
        val corrected = scanWithChoice("B")

        val firstFingerprint = SchoolCloudFingerprint.digest(listOf(exam), emptyList(), listOf(first))
        val repeatedFingerprint = SchoolCloudFingerprint.digest(listOf(exam), emptyList(), listOf(first))
        val correctedFingerprint = SchoolCloudFingerprint.digest(listOf(exam), emptyList(), listOf(corrected))

        assertEquals(firstFingerprint, repeatedFingerprint)
        assertNotEquals(firstFingerprint, correctedFingerprint)
    }

    private fun scanWithChoice(choice: String): ScanRecord = ScanRecord(
        id = "scan-1",
        templateId = "template-1",
        templateVersion = 1,
        capturedAtEpochMs = 10L,
        source = ScanSource.LIVE_CAMERA,
        sourceWidth = 100,
        sourceHeight = 200,
        pageConfidence = 0.95,
        decisionConfidence = 0.90,
        elapsedMs = 12.0,
        answers = listOf(
            RecordedAnswer(
                questionId = "q1",
                state = RecordedAnswerState.MARKED,
                selectedChoice = choice,
                confidence = 0.9,
                choiceScores = emptyMap()
            )
        ),
        markGrids = emptyList()
    )
}
