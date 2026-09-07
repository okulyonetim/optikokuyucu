package com.okulyonetim.optikokuyucu.school

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun parsesCommonSchoolClassNames() {
        assertEquals(7 to "A", SchoolClassParser.parse("7-A"))
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
}
