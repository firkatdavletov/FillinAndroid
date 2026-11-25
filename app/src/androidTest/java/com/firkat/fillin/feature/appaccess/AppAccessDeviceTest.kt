package com.firkat.fillin.feature.appaccess

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.firkat.fillin.core.network.InstallationIdStore
import com.firkat.fillin.feature.appaccess.data.RemoteConfigAppAccess
import com.firkat.fillin.feature.appaccess.domain.AppMode
import com.firkat.fillin.feature.remoteconfig.data.FirebaseAppRemoteConfig
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppAccessDeviceTest {
    @Test
    fun legacyGrantDoesNotEnableCloudAccess() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = Files.createTempDirectory(context.noBackupFilesDir.toPath(), "access-test-").toFile()
        val installation = InstallationIdStore(directory)
        try {
            File(directory, "app-access").writeText("${installation.get()}\nhttps://backend.test/")
            val access = RemoteConfigAppAccess(
                remoteConfig = FirebaseAppRemoteConfig(),
                configured = true,
                logFailure = { throw it }
            )
            assertFalse(access.initialized.value)
            access.loadAtStartup(fetchEnabled = false)

            assertEquals(AppMode.OfflineFree, access.mode.value)
        } finally {
            directory.deleteRecursively()
        }
    }
}
