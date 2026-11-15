package com.firkat.fillin.feature.auth.data

import com.firkat.fillin.feature.auth.domain.*
import java.util.Base64
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EmailAuthRepositoryTest {
    private class Gateway : FirebaseAuthGateway {
        override var user: FirebaseUserSnapshot? = null
        var callback: () -> Unit = {}
        val calls = mutableListOf<String>()
        var sendingFails = false
        var refreshingFails = false
        override fun observe(
            listener: () -> Unit
        ): AutoCloseable {
            callback = listener
            listener()
            return AutoCloseable {}
        }
        private fun created() {
            user =
                FirebaseUserSnapshot("uid", null, "a@example.com", false, setOf("password"), false)
            callback()
        }
        override suspend fun create(
            email: String,
            password: String
        ) {
            calls += "create"
            created()
        }
        override suspend fun signIn(
            email: String,
            password: String
        ) {
            calls += "signIn"
            created()
        }
        override suspend fun google(
            token: String
        ) {
            calls += "google"
            user =
                FirebaseUserSnapshot("uid", null, "a@example.com", true, setOf("google.com"), false)
            callback()
        }
        override suspend fun verification() {
            calls += "send"
            if (sendingFails) throw AuthFailure(AuthError.Network)
        }
        override suspend fun reset(
            email: String
        ) {
            calls += "reset"
        }
        override suspend fun reload() {
            calls += "reload"
            user = user?.copy(verified = true)
            callback()
        }
        override suspend fun refresh() {
            calls += "refresh"
            if (refreshingFails) throw AuthFailure(AuthError.Network)
        }
        override suspend fun linkPassword(
            password: String
        ) {
            calls += "linkPassword"
            user =
                user?.copy(providers = user!!.providers + "password")
        }
        override suspend fun linkGoogle(
            token: String
        ) {
            calls += "linkGoogle"
            user =
                user?.copy(providers = user!!.providers + "google.com")
        }
        override suspend fun reauthenticatePassword(
            password: String
        ) {
            calls += "reauthPassword"
        }
        override suspend fun reauthenticateGoogle(
            token: String
        ) {
            calls += "reauthGoogle"
        }
        override fun signOut() {
            user = null
            callback()
        }
    }
    private fun repository(
        sdk: Gateway
    ) = FirebaseAuthRepository(sdk) {}

    @Test fun `registration and failed email keep pending session without creating twice`() = runTest {
        val sdk = Gateway().apply { sendingFails = true }
        val repo = repository(sdk)
        repo.register("a@example.com", "long password")
        failure(AuthError.Network) { repo.sendVerification() }
        assertTrue(repo.session.value is AuthSession.EmailVerificationRequired)
        sdk.sendingFails = false
        repo.sendVerification()
        assertEquals(listOf("create", "send", "send"), sdk.calls)
    }

    @Test fun `verified user is published only after token refresh`() = runTest {
        val sdk = Gateway()
        val repo = repository(sdk)
        repo.register("a@example.com", "long password")
        sdk.refreshingFails = true
        failure(AuthError.Network) { repo.reloadUser() }
        assertTrue(repo.session.value is AuthSession.EmailVerificationRequired)
        sdk.refreshingFails = false
        repo.reloadUser()
        assertTrue(repo.session.value is AuthSession.Authenticated)
        assertEquals(listOf("create", "reload", "refresh", "reload", "refresh"), sdk.calls)
    }

    @Test fun `restored password user is pending but google is not blocked by linked password`() = runTest {
        val sdk = Gateway()
        sdk.signIn("a", "b")
        assertTrue(repository(sdk).session.value is AuthSession.EmailVerificationRequired)
        sdk.user = sdk.user!!.copy(providers = setOf("google.com", "password"), signInProvider = "google.com")
        assertTrue(repository(sdk).session.value is AuthSession.Authenticated)
    }

    @Test fun `short existing password can sign in but cannot register`() = runTest {
        val sdk = Gateway()
        val repo = repository(sdk)
        repo.signInWithEmail("a@example.com", "old")
        failure(AuthError.WeakPassword) { repo.register("a@example.com", "old") }
        assertEquals(listOf("signIn"), sdk.calls)
    }

    @Test fun `password linking preserves uid and never creates another user`() = runTest {
        val sdk = Gateway()
        val repo = repository(sdk)
        repo.signInWithGoogle("token")
        repo.linkPassword("long password")
        assertEquals("uid", repo.session.value.userOrNull!!.uid)
        assertEquals(listOf("google", "linkPassword", "reload", "refresh"), sdk.calls)
    }

    @Test fun `google linking refuses a different email before SDK mutation`() = runTest {
        val sdk = Gateway()
        val repo = repository(sdk)
        repo.signInWithEmail("a", "b")
        val token =
            "header." +
                Base64.getUrlEncoder().withoutPadding().encodeToString(
                    """{"email":"other@example.com"}""".toByteArray()
                ) +
                ".signature"
        failure(AuthError.EmailMismatch) { repo.linkGoogle(token) }
        assertFalse(sdk.calls.contains("linkGoogle"))
    }

    @Test fun `google linking with same email preserves uid`() = runTest {
        val sdk = Gateway()
        val repo = repository(sdk)
        repo.signInWithEmail("a", "b")
        val token =
            "header." +
                Base64.getUrlEncoder().withoutPadding().encodeToString("""{"email":"a@example.com"}""".toByteArray()) +
                ".signature"
        repo.linkGoogle(token)
        assertEquals("uid", repo.session.value.userOrNull!!.uid)
        assertEquals(listOf("signIn", "linkGoogle", "reload", "refresh"), sdk.calls)
    }

    @Test fun `server verification rejection preserves user and signout removes session`() = runTest {
        val sdk = Gateway()
        val repo = repository(sdk)
        repo.signInWithGoogle("token")
        repo.requireEmailVerification()
        assertTrue(repo.session.value is AuthSession.EmailVerificationRequired)
        repo.signOut()
        assertEquals(AuthSession.Anonymous, repo.session.value)
    }

    @Test fun `SDK codes map by code rather than exception text`() {
        assertEquals(AuthError.InvalidCredential, firebaseError("ERROR_USER_NOT_FOUND"))
        assertEquals(AuthError.InvalidCredential, firebaseError("ERROR_WRONG_PASSWORD"))
        assertEquals(AuthError.InvalidCredential, firebaseError("ERROR_INVALID_CREDENTIAL"))
        assertEquals(AuthError.Disabled, firebaseError("ERROR_USER_DISABLED"))
        assertEquals(AuthError.SignInUnavailable, firebaseError("ERROR_OPERATION_NOT_ALLOWED"))
        assertEquals(AuthError.EmailInUse, firebaseError("ERROR_EMAIL_ALREADY_IN_USE"))
        assertEquals(AuthError.CredentialInUse, firebaseError("ERROR_CREDENTIAL_ALREADY_IN_USE"))
        assertEquals(AuthError.RecentLoginRequired, firebaseError("ERROR_REQUIRES_RECENT_LOGIN"))
    }
    private suspend fun failure(
        error: AuthError,
        block: suspend () -> Unit
    ) {
        try {
            block()
            fail("Expected $error")
        } catch (e: AuthFailure) {
            assertEquals(error, e.error)
        }
    }
}
