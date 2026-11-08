package com.firkat.fillin.core.network

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InstallationIdStoreTest {
    private lateinit var directory: File
    private val idFile get() = File(directory, "installation-id")

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        directory = Files.createTempDirectory(context.noBackupFilesDir.toPath(), "installation-test").toFile()
    }

    @After
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun persistsAcrossStoreInstancesAndRegeneratesWhenRemoved() {
        val original = InstallationIdStore(directory).get()
        assertEquals(original, InstallationIdStore(directory).get())

        assertTrue(idFile.delete())
        assertNotEquals(original, InstallationIdStore(directory).get())
    }

    @Test
    fun replacesAndPersistsCorruptValue() {
        idFile.writeText("corrupt")
        val replacement = InstallationIdStore(directory).get()

        assertEquals(replacement, UUID.fromString(replacement).toString())
        assertEquals(replacement, InstallationIdStore(directory).get())
    }

    @Test
    fun concurrentFirstReadsReturnTheSamePersistedId() {
        val store = InstallationIdStore(directory)
        val pool = Executors.newFixedThreadPool(4)
        val start = CountDownLatch(1)
        try {
            val reads = List(4) {
                pool.submit<String> {
                    start.await()
                    store.get()
                }
            }
            start.countDown()

            val ids = reads.map { it.get(5, SECONDS) }.toSet()
            assertEquals(setOf(idFile.readText()), ids)
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun writeFailureDoesNotCacheAnId() {
        val tempPath = File(directory, "installation-id.tmp")
        assertTrue(tempPath.mkdir())
        val store = InstallationIdStore(directory)

        assertThrows(IOException::class.java) { store.get() }

        assertTrue(tempPath.delete())
        assertEquals(store.get(), idFile.readText())
    }
}
