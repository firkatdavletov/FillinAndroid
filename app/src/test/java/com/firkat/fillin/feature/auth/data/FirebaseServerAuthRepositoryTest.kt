package com.firkat.fillin.feature.auth.data

import com.firkat.fillin.feature.auth.domain.FirebaseIdTokenProvider
import com.firkat.fillin.feature.auth.domain.ServerAuthError
import com.firkat.fillin.feature.auth.domain.ServerAuthFailure
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException

class FirebaseServerAuthRepositoryTest {
    private val tokens = FakeTokens()
    private val requests = mutableListOf<Request>()

    @Test fun `profile request sends Firebase bearer and repeated requests keep server identity`() = runBlocking {
        val repository = repository { 200 to PROFILE }
        val first = repository.authenticate(UID)
        assertEquals(first, repository.authenticate(UID))
        assertEquals(listOf(false, false), tokens.refreshes)
        assertEquals("server-user-id", first.id)
        requests.forEach {
            assertEquals("POST", it.method)
            assertEquals("/api/v4/auth/firebase", it.url.encodedPath)
            assertEquals("Bearer synthetic-current-token", it.header("Authorization"))
        }
    }

    @Test fun `401 refreshes once and repeats with the new Firebase token`() = runBlocking {
        val repository = repository { if (requests.size == 1) 401 to "{}" else 200 to PROFILE }
        assertEquals(UID, repository.authenticate(UID).firebaseUid)
        assertEquals(listOf(false, true), tokens.refreshes)
        assertEquals(listOf("Bearer synthetic-current-token", "Bearer synthetic-refreshed-token"),
            requests.map { it.header("Authorization") })
    }

    @Test fun `second 401 ends the attempt without a loop`() = runBlocking {
        expect(ServerAuthError.Unauthorized) { repository { 401 to "{}" }.authenticate(UID) }
        assertEquals(2, requests.size)
        assertEquals(listOf(false, true), tokens.refreshes)
    }

    @Test fun `403 and outages do not refresh or replay the request`() = runBlocking {
        for ((code, reason) in listOf(403 to ServerAuthError.Unauthorized, 503 to ServerAuthError.Unavailable)) {
            requests.clear(); tokens.refreshes.clear()
            expect(reason) { repository { code to "{}" }.authenticate(UID) }
            assertEquals(1, requests.size)
            assertEquals(listOf(false), tokens.refreshes)
        }
    }

    @Test fun `network and malformed server replies produce safe errors`() = runBlocking {
        expect(ServerAuthError.Network) { repository { throw IOException("synthetic outage") }.authenticate(UID) }
        expect(ServerAuthError.Unavailable) { repository { 200 to "invalid json" }.authenticate(UID) }
    }

    @Test fun `account change after 401 prevents refresh using another account`() = runBlocking {
        expect(ServerAuthError.SessionChanged) {
            repository { tokens.current = false; 401 to "{}" }.authenticate(UID)
        }
        assertEquals(listOf(false), tokens.refreshes)
        assertEquals(1, requests.size)
    }

    @Test fun `account change while receiving a successful profile discards that profile`() = runBlocking {
        expect(ServerAuthError.SessionChanged) {
            repository { tokens.current = false; 200 to PROFILE }.authenticate(UID)
        }
    }

    @Test fun `profile for a different UID is never accepted`() = runBlocking {
        expect(ServerAuthError.Unauthorized) {
            repository { 200 to PROFILE.replace(UID, "another-uid") }.authenticate(UID)
        }
    }

    @Test fun `missing endpoint does not request a token or touch the network`() = runBlocking {
        expect(ServerAuthError.NotConfigured) { FirebaseServerAuthRepository(null, tokens).authenticate(UID) }
        assertTrue(tokens.refreshes.isEmpty())
        assertTrue(requests.isEmpty())
    }

    @Test fun `refresh failure does not retry with a stale token`() = runBlocking {
        tokens.failRefresh = true
        expect(ServerAuthError.Unauthorized) { repository { 401 to "{}" }.authenticate(UID) }
        assertEquals(1, requests.size)
    }

    private fun repository(reply: () -> Pair<Int, String>): FirebaseServerAuthRepository {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request()
            val (code, body) = reply()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("Synthetic response").body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val api = Retrofit.Builder().baseUrl("https://synthetic.invalid/").client(client)
            .addConverterFactory(Json.asConverterFactory("application/json".toMediaType())).build()
            .create(FirebaseAuthApi::class.java)
        return FirebaseServerAuthRepository(api, tokens)
    }

    private suspend fun expect(reason: ServerAuthError, action: suspend () -> Unit) {
        try { action(); fail("Expected $reason") } catch (failure: ServerAuthFailure) {
            assertEquals(reason, failure.reason)
            assertNull(failure.cause)
        }
    }

    private class FakeTokens : FirebaseIdTokenProvider {
        var current = true
        var failRefresh = false
        val refreshes = mutableListOf<Boolean>()
        override fun isCurrentUser(uid: String) = current && uid == UID
        override suspend fun idToken(uid: String, forceRefresh: Boolean): String {
            refreshes += forceRefresh
            if (!isCurrentUser(uid)) throw ServerAuthFailure(ServerAuthError.SessionChanged)
            if (forceRefresh && failRefresh) throw ServerAuthFailure(ServerAuthError.Unauthorized)
            return if (forceRefresh) "synthetic-refreshed-token" else "synthetic-current-token"
        }
    }

    private companion object {
        const val UID = "synthetic-firebase-uid"
        const val PROFILE = """{"id":"server-user-id","firebaseUid":"synthetic-firebase-uid","displayName":"Synthetic User","email":"user@example.test"}"""
    }
}
