package com.firkat.fillin.feature.auth.data

import com.firkat.fillin.feature.auth.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class EmailServerAuthTest {
    private val user = AuthUser("uid", null, "a@example.test")
    private class Tokens : FirebaseIdTokenProvider {
        var verificationRequired = false
        override fun emailVerificationRequired() { verificationRequired = true }
        override fun isCurrentUser(uid: String) = true
        override suspend fun idToken(uid: String, forceRefresh: Boolean) = "synthetic"
    }
    private val auth = object : AuthRepository {
        override val session = MutableStateFlow<AuthSession>(AuthSession.Authenticated(user))
        override suspend fun signInWithGoogle(idToken: String) {}
        override suspend fun signOut() {}
        override fun requireEmailVerification() { session.value = AuthSession.EmailVerificationRequired(user) }
    }
    @Test fun `parallel bootstrap callers share auth request and new session invalidates cache`() = runTest {
        var calls = 0
        val api = object : FirebaseAuthApi {
            override suspend fun authenticate(authorization: String): Response<ServerUserDto> { calls++; delay(1); return Response.success(ServerUserDto("server", "uid")) }
        }
        val repo = FirebaseServerAuthRepository(api, Tokens(), auth)
        awaitAll(async { repo.authenticate("uid") }, async { repo.authenticate("uid") })
        assertEquals(1, calls)
        auth.session.value = AuthSession.Anonymous
        auth.session.value = AuthSession.Authenticated(user.copy(email = "updated@example.test"))
        repo.authenticate("uid")
        assertEquals(2, calls)
    }
    @Test fun `email verification rejection is distinct from access and transitions session`() = runTest {
        val api = object : FirebaseAuthApi {
            override suspend fun authenticate(authorization: String): Response<ServerUserDto> = Response.error(403, """{"code":"email_not_verified"}""".toResponseBody("application/json".toMediaType()))
        }
        try { FirebaseServerAuthRepository(api, Tokens(), auth).authenticate("uid"); fail() }
        catch (e: ServerAuthFailure) { assertEquals(ServerAuthError.EmailNotVerified, e.reason) }
        assertTrue(auth.session.value is AuthSession.EmailVerificationRequired)
    }
    @Test fun `budget verification refusal signals session and never retries`() = runTest {
        val tokens = Tokens()
        var calls = 0
        try {
            FirebaseAuthenticatedRequests(tokens).execute("uid", Unit) { _, _ ->
                calls++
                Response.error<Unit>(403, """{"code":"email_not_verified"}""".toResponseBody())
            }
            fail()
        } catch (e: com.firkat.fillin.feature.sync.domain.BudgetConnectionFailure) {
            assertEquals("email_not_verified", e.code)
        }
        assertEquals(1, calls)
        assertTrue(tokens.verificationRequired)
    }
    @Test fun `bootstrap authenticates before listing budgets and shares successful profile`() = runTest {
        val paths = mutableListOf<String>()
        val client = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            paths += chain.request().method + " " + chain.request().url.encodedPath
            val body = if (chain.request().url.encodedPath.endsWith("firebase")) """{"id":"server","firebaseUid":"uid"}""" else "[]"
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val retrofit = retrofit2.Retrofit.Builder().baseUrl("https://example.test/").client(client)
            .addConverterFactory(kotlinx.serialization.json.Json.asConverterFactory("application/json".toMediaType())).build()
        val tokens = Tokens()
        val server = FirebaseServerAuthRepository(retrofit.create(FirebaseAuthApi::class.java), tokens, auth)
        val budgets = com.firkat.fillin.feature.budgetconnection.data.FirebaseBudgetRequests(retrofit.create(com.firkat.fillin.feature.budgetconnection.data.FirebaseBudgetApi::class.java), tokens, server)
        assertTrue(budgets.budgets("uid").isSuccessful)
        assertTrue(budgets.budgets("uid").isSuccessful)
        assertEquals(listOf("POST /api/v4/auth/firebase", "GET /api/v4/budgets", "GET /api/v4/budgets"), paths)
    }

}
