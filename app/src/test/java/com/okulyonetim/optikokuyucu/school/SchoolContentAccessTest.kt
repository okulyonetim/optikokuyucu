package com.okulyonetim.optikokuyucu.school

import com.okulyonetim.optikokuyucu.exam.ExamFactory
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSelection
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SchoolContentAccessTest {
    private val template = ActiveTemplateSelection(
        source = ActiveTemplateSource.STANDARD,
        templateId = "test-template",
        templateVersion = 1
    )

    @Test
    fun `owner can see and modify private exam but another user cannot`() {
        val owner = profile("owner")
        val other = profile("other")
        val exam = exam(ownerUid = owner.uid, public = false)

        assertTrue(SchoolContentAccess.canViewExam(exam, owner))
        assertTrue(SchoolContentAccess.canModifyExam(exam, owner))
        assertFalse(SchoolContentAccess.canViewExam(exam, other))
        assertFalse(SchoolContentAccess.canModifyExam(exam, other))
    }

    @Test
    fun `public exam is visible but remains read only for non owner`() {
        val owner = profile("owner")
        val other = profile("other")
        val exam = exam(ownerUid = owner.uid, public = true)

        assertTrue(SchoolContentAccess.canViewExam(exam, other))
        assertFalse(SchoolContentAccess.canModifyExam(exam, other))
    }

    @Test
    fun `admin can see modify and publish every exam`() {
        val admin = profile("admin", admin = true)
        val exam = exam(ownerUid = "someone-else", public = false)

        assertTrue(SchoolContentAccess.canViewExam(exam, admin))
        assertTrue(SchoolContentAccess.canModifyExam(exam, admin))
        assertTrue(SchoolContentAccess.canPublishExam(admin))
    }

    @Test
    fun `public admin template cannot be edited or deleted by normal user`() {
        val admin = profile("admin", admin = true)
        val user = profile("user")
        val ownership = SchoolFormOwnership(
            ownerUid = admin.uid,
            ownerName = admin.displayName,
            isPublic = true
        )

        assertTrue(SchoolContentAccess.canViewForm(ownership, user))
        assertFalse(SchoolContentAccess.canModifyForm(ownership, user))
        assertFalse(SchoolContentAccess.canDeleteForm(ownership, user))
        assertTrue(SchoolContentAccess.canModifyForm(ownership, admin))
        assertTrue(SchoolContentAccess.canDeleteForm(ownership, admin))
    }

    @Test
    fun `normal user can edit and delete own form`() {
        val user = profile("user")
        val ownership = SchoolFormOwnership(
            ownerUid = user.uid,
            ownerName = user.displayName,
            isPublic = false
        )

        assertTrue(SchoolContentAccess.canViewForm(ownership, user))
        assertTrue(SchoolContentAccess.canModifyForm(ownership, user))
        assertTrue(SchoolContentAccess.canDeleteForm(ownership, user))
        assertFalse(SchoolContentAccess.canPublishForm(user))
    }

    @Test
    fun `exam catalog merges local and cloud records without duplicates`() {
        val user = profile("user")
        val own = exam(id = "own", ownerUid = user.uid, public = false)
        val publicRemote = SchoolExamSummary(
            id = "public",
            name = "Paylaşılan Sınav",
            schoolName = "Okul",
            examDateEpochDay = 2,
            ownerUid = "admin",
            ownerName = "Admin",
            isPublic = true
        )
        val duplicateOwn = SchoolContentAccess.run { own.toSummary() }
        val privateOther = publicRemote.copy(id = "hidden", isPublic = false)

        val merged = SchoolContentAccess.mergeExamItems(
            localExams = listOf(own),
            cloud = listOf(duplicateOwn, publicRemote, privateOther),
            profile = user
        )

        assertEquals(setOf("own", "public"), merged.map { it.summary.id }.toSet())
        assertEquals(2, merged.size)
    }

    @Test
    fun `shared form document id is stable per owner form and version`() {
        val first = SchoolSharedDocumentId.forForm("uid-1", "form-a", 2)
        val second = SchoolSharedDocumentId.forForm("uid-1", "form-a", 2)
        val other = SchoolSharedDocumentId.forForm("uid-2", "form-a", 2)

        assertEquals(first, second)
        assertTrue(first != other)
    }

    private fun exam(
        id: String = "exam-1",
        ownerUid: String,
        public: Boolean
    ) = ExamFactory.create(
        id = id,
        name = "Sınav",
        schoolName = "Okul",
        templateSelection = template,
        examDateEpochDay = 1,
        createdAtEpochMs = 1,
        ownerUid = ownerUid,
        ownerDisplayName = ownerUid,
        isPublic = public
    )

    private fun profile(uid: String, admin: Boolean = false) = SchoolUserProfile(
        uid = uid,
        username = uid,
        displayName = uid.replaceFirstChar(Char::uppercase),
        admin = admin,
        active = true,
        roleId = if (admin) "admin" else "teacher",
        linkedTeacherId = "",
        permissions = emptyMap()
    )
}
