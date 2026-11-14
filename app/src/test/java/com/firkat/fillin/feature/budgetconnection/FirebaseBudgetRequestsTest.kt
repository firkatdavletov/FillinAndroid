package com.firkat.fillin.feature.budgetconnection

import com.firkat.fillin.feature.auth.domain.FirebaseIdTokenProvider
import com.firkat.fillin.feature.budgetconnection.data.*
import com.firkat.fillin.feature.sync.data.BudgetSyncCommand
import com.firkat.fillin.feature.sync.domain.BudgetConnectionFailure
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class FirebaseBudgetRequestsTest {
    @Test fun `401 refreshes once and repeats the exact scoped command`() = runBlocking {
        val tokens = Tokens()
        val calls = mutableListOf<Pair<String, String>>()
        val requests = client(tokens) { request ->
            val body = okio.Buffer().also { request.body!!.writeTo(it) }.readUtf8()
            calls += request.url.encodedPath to body
            assertEquals(if (calls.size == 1) "Bearer cached" else "Bearer refreshed", request.header("Authorization"))
            response(
                request,
                if (calls.size ==
                    1
                ) {
                    401
                } else {
                    200
                },
                if (calls.size == 1) "{}" else """{"requestId":"request","revision":1}"""
            )
        }
        val command = BudgetSyncCommand("request", "budget", "member", "CREATE_OPERATION", "operation", "2026-10-21")
        assertTrue(requests.command("owner", "budget", command).isSuccessful)
        assertEquals(listOf(false, true), tokens.refreshes)
        assertEquals(2, calls.size)
        assertEquals(calls[0], calls[1])
        assertEquals("/api/v4/budgets/budget/sync/commands", calls.singleOrNull()?.first ?: calls[0].first)
    }

    @Test fun `creation refresh keeps the original request ID and payload`() = runBlocking {
        val tokens = Tokens()
        val bodies = mutableListOf<String>()
        val requests = client(tokens) { request ->
            assertEquals("/api/v4/budgets", request.url.encodedPath)
            bodies += okio.Buffer().also { request.body!!.writeTo(it) }.readUtf8()
            response(
                request,
                if (bodies.size == 1) 401 else 200,
                if (bodies.size == 1) {
                    "{}"
                } else {
                    """{"id":"budget","name":"Новый","memberId":"member","role":"OWNER"}"""
                }
            )
        }
        assertTrue(
            requests.create(
                "owner",
                CreateFirebaseBudgetDto("immutable-request", "Новый", "12.34", "2026-10-20")
            ).isSuccessful
        )
        assertEquals(2, bodies.size)
        assertEquals(bodies[0], bodies[1])
        assertTrue(bodies[0].contains("immutable-request"))
        assertEquals(listOf(false, true), tokens.refreshes)
    }

    @Test fun `local import refresh preserves source UUIDs and immutable bootstrap`() = runBlocking {
        val tokens = Tokens()
        val bodies = mutableListOf<String>()
        val requests = client(tokens) { request ->
            assertEquals("/api/v4/budgets/import", request.url.encodedPath)
            bodies += okio.Buffer().also { request.body!!.writeTo(it) }.readUtf8()
            response(
                request,
                if (bodies.size == 1) 401 else 200,
                if (bodies.size == 1) {
                    "{}"
                } else {
                    """{"id":"original-budget","name":"Офлайн","memberId":"original-member","role":"OWNER"}"""
                }
            )
        }
        val bootstrap = com.firkat.fillin.feature.sync.data.CreateCloudBudget(
            "original-request",
            "original-budget",
            "original-member",
            "100.00",
            "2026-10-20",
            emptyList()
        )
        assertTrue(requests.importLocal("owner", ImportFirebaseBudgetDto("Офлайн", bootstrap)).isSuccessful)
        assertEquals(2, bodies.size)
        assertEquals(bodies[0], bodies[1])
        assertTrue(bodies[0].contains("original-request"))
        assertTrue(bodies[0].contains("original-budget"))
        assertEquals(listOf(false, true), tokens.refreshes)
    }

    @Test fun `repeated 401 stops after the refreshed token`() = runBlocking {
        val tokens = Tokens()
        var calls = 0
        val requests = client(tokens) {
            calls++
            response(it, 401, "{}")
        }
        assertEquals(401, requests.budgets("owner").code())
        assertEquals(2, calls)
        assertEquals(listOf(false, true), tokens.refreshes)
    }

    @Test fun `account switch discards response before publishing budget data`() = runBlocking {
        val tokens = Tokens()
        val requests = client(tokens) {
            tokens.current = "other"
            response(it, 200, "[]")
        }
        val failure = runCatching { requests.budgets("owner") }.exceptionOrNull() as BudgetConnectionFailure
        assertEquals("account_changed", failure.code)
        assertEquals(listOf(false), tokens.refreshes)
    }

    @Test fun `another account never sends an old profiles request`() = runBlocking {
        val tokens = Tokens().apply { current = "other" }
        var calls = 0
        val requests = client(tokens) {
            calls++
            response(it, 200, "[]")
        }
        assertEquals(
            "account_changed",
            (
                runCatching {
                    requests.budget("owner", "budget")
                }.exceptionOrNull() as BudgetConnectionFailure
                ).code
        )
        assertEquals(0, calls)
    }

    @Test fun `network failure is retryable and does not force refresh`() = runBlocking {
        val tokens = Tokens()
        val requests = client(tokens) { throw IOException("Synthetic interruption") }
        assertEquals(
            "network",
            (
                runCatching {
                    requests.changes("owner", "budget", null)
                }.exceptionOrNull() as BudgetConnectionFailure
                ).code
        )
        assertEquals(listOf(false), tokens.refreshes)
    }

    private class Tokens : FirebaseIdTokenProvider {
        var current = "owner"
        val refreshes = mutableListOf<Boolean>()
        override fun isCurrentUser(
            uid: String
        ) = current == uid
        override suspend fun idToken(
            uid: String,
            forceRefresh: Boolean
        ): String {
            refreshes += forceRefresh
            return if (forceRefresh) "refreshed" else "cached"
        }
    }

    private fun client(
        tokens: Tokens,
        handler: (Request) -> Response
    ): FirebaseBudgetRequests {
        val retrofit = Retrofit.Builder().baseUrl("https://synthetic.invalid/")
            .client(OkHttpClient.Builder().addInterceptor { handler(it.request()) }.build())
            .addConverterFactory(Json.asConverterFactory("application/json".toMediaType())).build()
        return FirebaseBudgetRequests(retrofit.create(FirebaseBudgetApi::class.java), tokens)
    }

    private fun response(
        request: Request,
        status: Int,
        body: String
    ) = Response.Builder().request(request)
        .protocol(
            Protocol.HTTP_1_1
        ).message("Synthetic").code(status).body(body.toResponseBody("application/json".toMediaType())).build()
}
