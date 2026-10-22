package com.okulyonetim.optikokuyucu.storage

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AtomicFilesTest {
    private fun tempDirectory(): File = Files.createTempDirectory("atomic-files-test").toFile()

    @Test
    fun writeCreatesAndReplacesWithoutLeavingTemporaries() {
        val directory = tempDirectory()
        val target = File(directory, "record.bin")

        AtomicFiles.writeBytes(target, byteArrayOf(1, 2, 3))
        assertArrayEquals(byteArrayOf(1, 2, 3), target.readBytes())

        AtomicFiles.writeBytes(target, byteArrayOf(9, 8))
        assertArrayEquals(byteArrayOf(9, 8), target.readBytes())

        assertEquals(listOf("record.bin"), directory.list().orEmpty().toList())
        directory.deleteRecursively()
    }

    @Test
    fun writeTextUsesUtf8() {
        val directory = tempDirectory()
        val target = File(directory, "text.txt")
        AtomicFiles.writeText(target, "Öğrenci şığ")
        assertEquals("Öğrenci şığ", target.readText(Charsets.UTF_8))
        directory.deleteRecursively()
    }

    @Test
    fun failedWriteLeavesExistingFileIntact() {
        val directory = tempDirectory()
        val target = File(directory, "keep.bin")
        AtomicFiles.writeBytes(target, byteArrayOf(5, 5, 5))

        // A directory occupying the destination name makes the final move fail.
        val blocked = File(directory, "blocked")
        blocked.mkdirs()
        File(blocked, "child").writeText("x")
        val failure = runCatching { AtomicFiles.writeBytes(blocked, byteArrayOf(1)) }

        assertTrue(failure.isFailure)
        assertArrayEquals(byteArrayOf(5, 5, 5), target.readBytes())
        assertTrue(directory.list().orEmpty().none { it.endsWith(".tmp") })
        directory.deleteRecursively()
    }

    @Test
    fun readOrNullReportsCorruptionWithoutDeletingTheFile() {
        val directory = tempDirectory()
        val file = File(directory, "broken.bin")
        file.writeBytes(byteArrayOf(0, 0))

        val result = AtomicFiles.readOrNull(file, "Kayıt") { bytes ->
            require(bytes.size > 5) { "too short" }
            bytes
        }

        assertNull(result)
        assertTrue(file.isFile)
        directory.deleteRecursively()
    }

    @Test
    fun cleanStaleTemporariesOnlyRemovesOldTempFiles() {
        val directory = tempDirectory()
        val oldTemp = File(directory, "a.bin.12345678.tmp").apply {
            writeText("x")
            setLastModified(System.currentTimeMillis() - 2 * 60 * 60 * 1000L)
        }
        val freshTemp = File(directory, "b.bin.abcdefgh.tmp").apply { writeText("y") }
        val real = File(directory, "c.bin").apply { writeText("z") }

        AtomicFiles.cleanStaleTemporaries(directory)

        assertTrue(!oldTemp.exists())
        assertTrue(freshTemp.exists())
        assertTrue(real.exists())
        directory.deleteRecursively()
    }

    @Test
    fun lockForReturnsSameObjectForSameDirectory() {
        val directory = tempDirectory()
        assertSame(AtomicFiles.lockFor(directory), AtomicFiles.lockFor(File(directory.absolutePath)))
        directory.deleteRecursively()
    }
}
