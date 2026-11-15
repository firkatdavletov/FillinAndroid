package com.firkat.fillin.feature.auth.data

import com.firkat.fillin.feature.auth.domain.*
import com.firkat.fillin.feature.sync.domain.BudgetConnectionFailure
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class FirebaseRequestRecoveryTest {
    @Test fun `sign out during token acquisition prevents the original HTTP call`() = runTest {
        val tokens = Tokens().apply { afterToken = { current = false } }
        var calls = 0
        val failure = runCatching {
            FirebaseAuthenticatedRequests(tokens).execute("owner", Unit) { _, _ ->
                calls++
                Response.success(Unit)
            }
        }.exceptionOrNull() as BudgetConnectionFailure
        assertEquals("account_changed", failure.code)
        assertEquals(0, calls)
    }

    @Test fun `account change during refresh prevents the second HTTP call`() = runTest {
        val tokens = Tokens().apply { afterToken = { if (refreshes == 2) current = false } }
        var calls = 0
        val failure = runCatching {
            FirebaseAuthenticatedRequests(tokens).execute("owner", Unit) { _, _ ->
                calls++
                Response.error<Unit>(401, "{}".toResponseBody())
            }
        }.exceptionOrNull() as BudgetConnectionFailure
        assertEquals("account_changed", failure.code)
        assertEquals(1, calls)
        assertEquals(2, tokens.refreshes)
    }

    @Test fun `cancellation during a non cancellable token callback prevents delivery`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val tokens = Tokens().apply {
            afterToken =
                {
                    withContext(NonCancellable) {
                        entered.complete(Unit)
                        release.await()
                    }
                }
        }
        var calls = 0
        val attempt = launch {
            FirebaseAuthenticatedRequests(tokens).execute("owner", Unit) { _, _ ->
                calls++
                Response.success(Unit)
            }
        }
        entered.await()
        attempt.cancel()
        release.complete(Unit)
        attempt.join()
        assertTrue(attempt.isCancelled)
        assertEquals(0, calls)
    }

    @Test fun `server profile also fences the token callback before sending`() = runTest {
        val tokens = Tokens().apply { afterToken = { current = false } }
        var calls = 0
        val api = object : FirebaseAuthApi {
            override suspend fun authenticate(
                authorization: String
            ): Response<ServerUserDto> {
                calls++
                error("Must not reach HTTP")
            }
        }
        val failure = runCatching {
            FirebaseServerAuthRepository(api, tokens).authenticate("owner")
        }.exceptionOrNull() as ServerAuthFailure
        assertEquals(ServerAuthError.SessionChanged, failure.reason)
        assertEquals(0, calls)
    }

    private class Tokens : FirebaseIdTokenProvider {
        var current = true
        var refreshes = 0
        var afterToken: suspend Tokens.() -> Unit = {}
        override fun isCurrentUser(
            uid: String
        ) = current && uid == "owner"
        override suspend fun idToken(
            uid: String,
            forceRefresh: Boolean
        ): String {
            refreshes++
            afterToken()
            return "synthetic-token"
        }
    }
}
