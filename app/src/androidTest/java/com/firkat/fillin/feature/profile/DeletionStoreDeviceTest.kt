package com.firkat.fillin.feature.profile

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.firkat.fillin.feature.profile.data.*
import com.firkat.fillin.feature.profile.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class DeletionStoreDeviceTest {
    @Test fun encryptedReceiptSurvivesReopenAndFinishPreservesAccountBlock() {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "deletion-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) { override fun getNoBackupFilesDir() = root }
        try {
            val receipt = DeletionOperation("uid", newDeletionSecret(), accepted = true)
            EncryptedDeletionStore(context).save(receipt)
            assertFalse(File(root, "profile-deletion").readBytes().toString(Charsets.ISO_8859_1).contains(receipt.secret))
            val restored = EncryptedDeletionStore(context)
            assertEquals(listOf(receipt), restored.operations())
            assertTrue(restored.blocked("uid"))
            assertFalse(restored.blocked("another"))
            restored.finish("uid")
            val finished = EncryptedDeletionStore(context)
            assertTrue(finished.operations().isEmpty())
            assertTrue(finished.blocked("uid"))
        } finally { root.deleteRecursively() }
    }
    @Test fun damagedReceiptBlocksCloudAndCannotBeOverwritten() {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "deletion-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) { override fun getNoBackupFilesDir() = root }
        try {
            File(root, "profile-deletion").writeText("corrupt")
            val store = EncryptedDeletionStore(context)
            assertTrue(store.blocked("any-account"))
            assertTrue(runCatching { store.operations() }.isFailure)
            assertTrue(runCatching { store.save(DeletionOperation("uid", newDeletionSecret())) }.isFailure)
            assertEquals("corrupt", File(root, "profile-deletion").readText())
        } finally { root.deleteRecursively() }
    }
}
