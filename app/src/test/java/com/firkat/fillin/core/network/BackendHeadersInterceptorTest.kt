package com.firkat.fillin.core.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class BackendHeadersInterceptorTest {
    @Test
    fun `derived client sends one copy of each header and preserves the request`() {
        val installationId = "c0ab9e92-d853-41ca-a9c4-ed851b1dd033"
        val baseClient = OkHttpClient.Builder()
            .addInterceptor(BackendHeadersInterceptor({ installationId }, "1.0"))
            .build()
        val client = baseClient.newBuilder().build()

        MockWebServer().use { server ->
            server.enqueue(MockResponse())
            val request = Request.Builder()
                .url(server.url("/api/test"))
                .addHeader("X-Device-Id", "old-id")
                .addHeader("x-device-id", "duplicate-id")
                .addHeader("X-App-Version", "old-version")
                .header("Authorization", "Bearer synthetic")
                .post("payload".toRequestBody())
                .build()

            client.newCall(request).execute().close()

            val received = server.takeRequest()
            assertEquals(listOf(installationId), received.headers.values("X-Device-Id"))
            assertEquals(listOf("1.0"), received.headers.values("X-App-Version"))
            assertEquals("Bearer synthetic", received.getHeader("Authorization"))
            assertEquals("POST", received.method)
            assertEquals("payload", received.body.readUtf8())
        }
    }
}
