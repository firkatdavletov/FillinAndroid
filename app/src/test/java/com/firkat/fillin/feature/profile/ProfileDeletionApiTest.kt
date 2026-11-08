package com.firkat.fillin.feature.profile

import com.firkat.fillin.feature.profile.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class ProfileDeletionApiTest {
    @Test fun `delete has no body and status request has no Firebase token`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(202).setBody("""{"status":"PENDING"}"""))
            server.enqueue(MockResponse().setBody("""{"status":"COMPLETED"}"""))
            val api = Retrofit.Builder().baseUrl(server.url("/"))
                .addConverterFactory(Json.asConverterFactory("application/json".toMediaType())).build().create(ProfileDeletionApi::class.java)
            val secret = newDeletionSecret()
            assertEquals(DeletionStatus.PENDING, api.delete("Bearer synthetic", secret).body()!!.status)
            assertEquals(DeletionStatus.COMPLETED, api.status(secret).body()!!.status)
            val deletion = server.takeRequest()
            assertEquals("DELETE", deletion.method)
            assertEquals("/api/v4/profile", deletion.path)
            assertEquals(0L, deletion.bodySize)
            assertEquals("Bearer synthetic", deletion.getHeader("Authorization"))
            assertEquals(secret, deletion.getHeader("X-Deletion-Token"))
            val status = server.takeRequest()
            assertEquals("GET", status.method)
            assertEquals("/api/v4/profile/deletion", status.path)
            assertNull(status.getHeader("Authorization"))
            assertEquals(secret, status.getHeader("X-Deletion-Token"))
        }
    }
}
