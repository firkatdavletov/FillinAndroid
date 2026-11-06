package com.firkat.fillin.feature.appaccess

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.firkat.fillin.feature.appaccess.data.AccessResponse
import com.firkat.fillin.feature.appaccess.data.AppAccessApi
import com.firkat.fillin.feature.appaccess.data.BackendAppAccess
import com.firkat.fillin.feature.appaccess.data.CloudAccessInterceptor
import com.firkat.fillin.feature.appaccess.data.DeviceAccessDeniedIOException
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import com.firkat.fillin.feature.appaccess.domain.AppMode
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import java.io.File
import java.util.UUID

class AppAccessDeviceTest {
    @Test fun offlineRetainsGrantOnlyForSameInstallationAndBackendUntilExplicitRevocation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cache = File(context.noBackupFilesDir, "access-test-${UUID.randomUUID()}")
        val id = UUID.randomUUID().toString()
        var calls = 0
        var now = 0L
        var reply = Response.success(AccessResponse("INTERNAL_FULL"))
        val api = object : AppAccessApi {
            override suspend fun access() = reply.also { calls++ }
        }
        fun repository(backend: String = "https://backend.test/", installation: String = id) =
            BackendAppAccess(api, backend, { installation }, cache, { now })

        try {
            val access = repository()
            assertEquals(AppMode.OfflineFree, access.mode.value)
            assertTrue(access.refresh())
            assertFalse(access.refresh())
            assertEquals(1, calls)
            assertEquals(AppMode.InternalFull, repository().mode.value)
            assertEquals(AppMode.OfflineFree, repository(installation = UUID.randomUUID().toString()).mode.value)
            assertEquals(AppMode.OfflineFree, repository(backend = "https://other.test/").mode.value)

            now += 15 * 60 * 1000
            reply = Response.error(404, "".toResponseBody())
            assertFalse(access.refresh())
            assertEquals(2, calls)
            assertEquals(AppMode.InternalFull, access.mode.value)

            reply = Response.success(AccessResponse("OFFLINE_FREE"))
            assertTrue(access.refresh(manual = true))
            assertEquals(AppMode.OfflineFree, repository().mode.value)
        } finally {
            cache.delete()
        }
    }
    @Test fun cloudDenialRevokesGrantBeforeCallersCanTreatItAsLostMembership() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cache = File(context.noBackupFilesDir, "access-test-${UUID.randomUUID()}")
        val id = UUID.randomUUID().toString()
        val access = BackendAppAccess(object : AppAccessApi {
            override suspend fun access() = Response.success(AccessResponse("INTERNAL_FULL"))
        }, "https://backend.test/", { id }, cache, { 0L })
        try {
            assertTrue(access.refresh())
            val client = OkHttpClient.Builder()
                .addInterceptor(CloudAccessInterceptor { access })
                .addInterceptor { chain ->
                    okhttp3.Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                        .code(403).message("Forbidden")
                        .body("""{"code":"device_access_denied"}""".toResponseBody())
                        .build()
                }.build()
            val request = Request.Builder().url("https://backend.test/api/v4/budgets").build()
            assertTrue(runCatching { client.newCall(request).execute().close() }.exceptionOrNull() is DeviceAccessDeniedIOException)
            assertEquals(AppMode.OfflineFree, access.mode.value)
            assertFalse(cache.exists())
        } finally {
            cache.delete()
        }
    }

}
