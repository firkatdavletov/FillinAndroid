package com.firkat.fillin.feature.budgetinvitation

import com.firkat.fillin.feature.auth.data.FirebaseAuthenticatedRequests
import com.firkat.fillin.feature.auth.domain.FirebaseIdTokenProvider
import com.firkat.fillin.feature.budgetinvitation.data.FirebaseBudgetInvitationApi
import com.firkat.fillin.feature.sync.domain.BudgetConnectionFailure
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class FirebaseInvitationRequestsTest {
    @Test fun `invitation retries one 401 with a refreshed token and the exact original budget path`() = runBlocking {
        val tokens = Tokens()
        val calls = mutableListOf<Pair<String, String?>>()
        val api = api { request ->
            calls += request.url.encodedPath to request.header("Authorization")
            response(
                request,
                if (calls.size == 1) 401 else 200,
                if (calls.size == 1) {
                    "{}"
                } else {
                    """{"id":"invite","budgetId":"budget","expiresAt":"2026-10-24T00:00:00Z","code":"00001-00001-00001-00001"}"""
                }
            )
        }
        val result = FirebaseAuthenticatedRequests(tokens).execute("owner", api) { endpoint, token ->
            endpoint.create(token, "budget")
        }
        assertTrue(result.isSuccessful)
        assertEquals(
            listOf(
                "/api/v4/budgets/budget/invitations" to "Bearer cached",
                "/api/v4/budgets/budget/invitations" to "Bearer refreshed"
            ),
            calls
        )
        assertEquals(listOf(false, true), tokens.refreshes)
    }

    @Test fun `changing account after an unauthorized invitation response prevents retry`() = runBlocking {
        val tokens = Tokens()
        var calls = 0
        val api = api { request ->
            calls++
            tokens.current = "other"
            response(request, 401, "{}")
        }
        val failure = runCatching {
            FirebaseAuthenticatedRequests(tokens).execute("owner", api) { endpoint, token ->
                endpoint.create(token, "original-budget")
            }
        }.exceptionOrNull()
        assertEquals("account_changed", (failure as BudgetConnectionFailure).code)
        assertEquals(1, calls)
        assertEquals(listOf(false), tokens.refreshes)
    }

    private class Tokens : FirebaseIdTokenProvider {
        var current = "owner"
        val refreshes = mutableListOf<Boolean>()
        override fun isCurrentUser(
            uid: String
        ) = uid == current
        override suspend fun idToken(
            uid: String,
            forceRefresh: Boolean
        ): String {
            refreshes += forceRefresh
            return if (forceRefresh) "refreshed" else "cached"
        }
    }
    private fun api(
        handler: (Request) -> Response
    ) = Retrofit.Builder().baseUrl("https://synthetic.invalid/")
        .client(OkHttpClient.Builder().addInterceptor { handler(it.request()) }.build())
        .addConverterFactory(
            Json.asConverterFactory("application/json".toMediaType())
        ).build().create(FirebaseBudgetInvitationApi::class.java)
    private fun response(
        request: Request,
        status: Int,
        body: String
    ) = Response.Builder().request(request)
        .protocol(
            Protocol.HTTP_1_1
        ).message("Synthetic").code(status).body(body.toResponseBody("application/json".toMediaType())).build()
}
