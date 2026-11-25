package com.firkat.fillin.feature.appaccess.data

import com.firkat.fillin.feature.appaccess.domain.AppMode
import com.firkat.fillin.feature.remoteconfig.data.FirebaseAppRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteConfigAppAccessTest {
    @Test
    fun `cache restores access while network is pending and after a failed startup fetch`() = runTest {
        val fetch = CompletableDeferred<Unit>()
        val failures = mutableListOf<Exception>()
        val access = RemoteConfigAppAccess(
            FirebaseAppRemoteConfig({ flag("true") }) { fetch.await() },
            configured = true,
            logFailure = { failures.add(it) }
        )
        val startup = launch { access.loadAtStartup(fetchEnabled = true) }
        runCurrent()

        assertTrue(access.initialized.value)
        assertEquals(AppMode.InternalFull, access.mode.value)
        fetch.completeExceptionally(java.io.IOException("Offline"))
        startup.join()

        assertTrue(failures.single() is java.io.IOException)
        assertEquals(AppMode.InternalFull, access.mode.value)
    }

    @Test
    fun `startup enables or disables access using the activated Boolean flag`() = runTest {
        val cases = listOf(
            Triple(null, null, AppMode.OfflineFree),
            Triple(null, "true", AppMode.InternalFull),
            Triple("true", "false", AppMode.OfflineFree),
            Triple("true", null, AppMode.OfflineFree),
            Triple("true", "invalid", AppMode.OfflineFree),
            Triple("true", "yes", AppMode.OfflineFree)
        )
        for ((cached, fetched, expected) in cases) {
            var active = cached
            val access = RemoteConfigAppAccess(
                FirebaseAppRemoteConfig({ flag(active) }) { active = fetched },
                configured = true,
                logFailure = { throw it }
            )
            access.loadAtStartup(fetchEnabled = true)
            assertEquals(expected, access.mode.value)
        }
    }

    @Test
    fun `startup fetch runs once even if called again`() = runTest {
        val fetch = CompletableDeferred<Unit>()
        var calls = 0
        val access = RemoteConfigAppAccess(
            FirebaseAppRemoteConfig({ emptyMap() }) {
                calls++
                fetch.await()
            },
            configured = true,
            logFailure = { throw it }
        )

        val startup = launch { access.loadAtStartup(fetchEnabled = true) }
        val repeated = launch { access.loadAtStartup(fetchEnabled = true) }
        runCurrent()
        assertEquals(1, calls)
        fetch.complete(Unit)
        startup.join()
        repeated.join()
        access.loadAtStartup(fetchEnabled = true)
        assertEquals(1, calls)
    }

    @Test
    fun `missing Firebase setup stays local without touching the SDK`() = runTest {
        val access = RemoteConfigAppAccess(
            FirebaseAppRemoteConfig({ error("Unexpected SDK access") }),
            configured = false,
            logFailure = { throw it }
        )
        access.loadAtStartup(fetchEnabled = true)

        assertTrue(access.initialized.value)
        assertEquals(AppMode.OfflineFree, access.mode.value)
    }

    @Test
    fun `old server denial fails the call without revoking remote access`() = runTest {
        val access = RemoteConfigAppAccess(
            FirebaseAppRemoteConfig({ flag("true") }),
            configured = true,
            logFailure = { throw it }
        )
        access.loadAtStartup(fetchEnabled = false)
        val client = OkHttpClient.Builder()
            .addInterceptor(CloudAccessInterceptor { access })
            .addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(403).message("Forbidden")
                    .body("""{"code":"device_access_denied"}""".toResponseBody())
                    .build()
            }.build()
        val request = Request.Builder().url("https://backend.test/api/v4/budgets").build()
        val failure = runCatching { client.newCall(request).execute().close() }.exceptionOrNull()
        assertTrue(failure is DeviceAccessDeniedIOException)
        assertEquals(AppMode.InternalFull, access.mode.value)
    }

    private fun flag(raw: String?): Map<String, FirebaseRemoteConfigValue> = if (raw == null) {
        emptyMap()
    } else {
        mapOf("remote_access" to FlagValue(raw))
    }

    private class FlagValue(private val raw: String) : FirebaseRemoteConfigValue {
        override fun asBoolean() = raw.toBooleanStrict()
        override fun asLong() = raw.toLong()
        override fun asDouble() = raw.toDouble()
        override fun asString() = raw
        override fun asByteArray() = raw.encodeToByteArray()
        override fun getSource() = FirebaseRemoteConfig.VALUE_SOURCE_REMOTE
    }
}
