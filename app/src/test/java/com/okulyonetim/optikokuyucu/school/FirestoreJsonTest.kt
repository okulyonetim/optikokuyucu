package com.okulyonetim.optikokuyucu.school

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirestoreJsonTest {
    @Test
    fun `null is encoded as an explicit nullValue`() {
        val encoded = FirestoreJson.encodeFields(mapOf("a" to null))
        val value = encoded.getJSONObject("a")
        assertTrue(value.has("nullValue"))
        assertTrue(value.isNull("nullValue"))
        assertEquals("""{"a":{"nullValue":null}}""", encoded.toString())
    }

    @Test
    fun `non finite doubles become null instead of throwing`() {
        val encoded = FirestoreJson.encodeFields(
            mapOf("nan" to Double.NaN, "inf" to Double.POSITIVE_INFINITY, "ok" to 2.5, "f" to Float.NaN)
        )
        assertTrue(encoded.getJSONObject("nan").has("nullValue"))
        assertTrue(encoded.getJSONObject("inf").has("nullValue"))
        assertTrue(encoded.getJSONObject("f").has("nullValue"))
        assertEquals(2.5, encoded.getJSONObject("ok").getDouble("doubleValue"), 0.0)
    }

    @Test
    fun `nested maps lists and numbers round trip`() {
        val source = mapOf(
            "ad" to "Deneme",
            "dogru" to 12,
            "puan" to 87.5,
            "acik" to true,
            "dersler" to listOf(mapOf("ad" to "Mat", "soru" to 20L)),
            "bos" to null
        )
        val document = JSONObject().put("name", "projects/p/databases/(default)/documents/c/x1")
            .put("fields", FirestoreJson.encodeFields(source))
        val decoded = FirestoreJson.decodeDocument(document)
        assertEquals("x1", decoded.id)
        assertEquals("Deneme", decoded.fields["ad"])
        assertEquals(12L, decoded.fields["dogru"])
        assertEquals(87.5, decoded.fields["puan"] as Double, 0.0)
        assertEquals(true, decoded.fields["acik"])
        assertNull(decoded.fields["bos"])
        val lessons = decoded.fields["dersler"] as List<*>
        assertEquals("Mat", (lessons.single() as Map<*, *>)["ad"])
        assertEquals(20L, (lessons.single() as Map<*, *>)["soru"])
    }
}
