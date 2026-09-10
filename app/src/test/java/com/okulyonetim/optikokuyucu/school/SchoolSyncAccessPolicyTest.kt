package com.okulyonetim.optikokuyucu.school

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SchoolSyncAccessPolicyTest {
    @Test
    fun `teacher can sync results but cannot sync or change institution content`() {
        val teacher = profile(
            uid = "teacher",
            roleId = "ogretmen",
            linkedTeacherId = "teacher-doc",
            permissions = mapOf(
                "sinavIslemleri" to "duzenle",
                "denemeSonuclari" to "goruntule"
            )
        )

        assertTrue(SchoolSyncAccessPolicy.isTeacher(teacher))
        assertFalse(SchoolSyncAccessPolicy.canSyncDirectory(teacher))
        assertFalse(SchoolSyncAccessPolicy.canSyncSubjects(teacher))
        assertFalse(SchoolSyncAccessPolicy.canSyncTemplates(teacher))
        assertFalse(SchoolSyncAccessPolicy.canSyncExamDefinition(teacher))
        assertFalse(SchoolSyncAccessPolicy.canChangeCloudContent(teacher))
        assertTrue(SchoolSyncAccessPolicy.canSyncResults(teacher))
    }

    @Test
    fun `admin keeps existing synchronization capabilities`() {
        val admin = profile(uid = "admin", admin = true)

        assertFalse(SchoolSyncAccessPolicy.isTeacher(admin))
        assertTrue(SchoolSyncAccessPolicy.canSyncDirectory(admin))
        assertTrue(SchoolSyncAccessPolicy.canSyncSubjects(admin))
        assertTrue(SchoolSyncAccessPolicy.canSyncTemplates(admin))
        assertTrue(SchoolSyncAccessPolicy.canSyncExamDefinition(admin))
        assertTrue(SchoolSyncAccessPolicy.canChangeCloudContent(admin))
        assertTrue(SchoolSyncAccessPolicy.canSyncResults(admin))
    }

    @Test
    fun `non teacher user keeps permission based exam and result behavior`() {
        val manager = profile(
            uid = "manager",
            roleId = "mudur-yardimcisi",
            permissions = mapOf(
                "sinavIslemleri" to "duzenle",
                "denemeSonuclari" to "goruntule"
            )
        )

        assertFalse(SchoolSyncAccessPolicy.isTeacher(manager))
        assertTrue(SchoolSyncAccessPolicy.canSyncDirectory(manager))
        assertTrue(SchoolSyncAccessPolicy.canSyncSubjects(manager))
        assertTrue(SchoolSyncAccessPolicy.canSyncTemplates(manager))
        assertTrue(SchoolSyncAccessPolicy.canSyncExamDefinition(manager))
        assertTrue(SchoolSyncAccessPolicy.canChangeCloudContent(manager))
        assertTrue(SchoolSyncAccessPolicy.canSyncResults(manager))
    }

    @Test
    fun `teacher role id is recognized even without linked teacher document`() {
        val teacher = profile(uid = "teacher-role", roleId = "ÖĞRETMEN")
        assertTrue(SchoolSyncAccessPolicy.isTeacher(teacher))
        assertFalse(SchoolSyncAccessPolicy.canSyncDirectory(teacher))
    }

    private fun profile(
        uid: String,
        admin: Boolean = false,
        roleId: String = if (admin) "admin" else "user",
        linkedTeacherId: String = "",
        permissions: Map<String, String> = emptyMap()
    ) = SchoolUserProfile(
        uid = uid,
        username = uid,
        displayName = uid,
        admin = admin,
        active = true,
        roleId = roleId,
        linkedTeacherId = linkedTeacherId,
        permissions = permissions
    )
}
