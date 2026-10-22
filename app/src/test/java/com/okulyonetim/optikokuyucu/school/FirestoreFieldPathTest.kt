package com.okulyonetim.optikokuyucu.school

import org.junit.Assert.assertEquals
import org.junit.Test

class FirestoreFieldPathTest {
    @Test
    fun `plain identifiers are used as they are`() {
        assertEquals("kullaniciAdi", FirestoreJson.fieldPath("kullaniciAdi"))
        assertEquals("_x9", FirestoreJson.fieldPath("_x9"))
    }

    @Test
    fun `other names are wrapped in backticks with escapes`() {
        assertEquals("`sinav-adi`", FirestoreJson.fieldPath("sinav-adi"))
        assertEquals("`9abc`", FirestoreJson.fieldPath("9abc"))
        assertEquals("`a b`", FirestoreJson.fieldPath("a b"))
        assertEquals("`a\\`b`", FirestoreJson.fieldPath("a`b"))
        assertEquals("`a\\\\b`", FirestoreJson.fieldPath("a\\b"))
    }

    @Test
    fun `update mask query lists every field once and url encodes paths`() {
        val query = FirestoreJson.updateMaskQuery(listOf("ad", "tarih", "sinav-adi"))
        assertEquals(
            "updateMask.fieldPaths=ad&updateMask.fieldPaths=tarih&updateMask.fieldPaths=%60sinav-adi%60",
            query
        )
    }
}
