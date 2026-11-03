package com.firkat.fillin.feature.auth.ui

import com.firkat.fillin.feature.auth.domain.*
import com.firkat.fillin.feature.auth.ui.interactor.*
import com.firkat.fillin.feature.auth.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EmailAuthStateTest {
    private val reducer = AuthReducer()
    @Test fun `validation accepts valid addresses and rejects malformed ones`() {
        val state = AuthState(session = AuthSession.Anonymous, email = "a@example.test", password = "old")
        assertTrue(reducer.reduceAction(state, AuthAction.SubmitEmail(EmailTask.Login)).emailBusy)
        assertEquals(AuthError.InvalidEmail, reducer.reduceAction(state.copy(email = "invalid"), AuthAction.SubmitEmail(EmailTask.Login)).error)
        assertEquals(AuthError.WeakPassword, reducer.reduceAction(state, AuthAction.SubmitEmail(EmailTask.Register)).error)
    }
    @Test fun `failed creation stays on registration but failed email stays on verification`() {
        val state = AuthState(page = EmailPage.Register, emailBusy = true, password = "secret", confirmation = "secret")
        val result = reducer.reduceEvent(state, AuthEvent.EmailFinished(EmailTask.Register, AuthError.EmailInUse, 1))
        assertEquals(EmailPage.Register, result.page)
        assertEquals("", result.password)
        val pending = AuthSession.EmailVerificationRequired(AuthUser("u", null, "a@example.test", setOf("password"), false))
        assertEquals(EmailPage.Verify, reducer.reduceEvent(state.copy(session = pending), AuthEvent.EmailFinished(EmailTask.Register, AuthError.Network, 1)).page)
    }
    @Test fun `initial session observation does not dismiss a form and successful resend sets deadline`() {
        val state = AuthState(page = EmailPage.Login)
        assertEquals(EmailPage.Login, reducer.reduceEvent(state, AuthEvent.SessionChanged(AuthSession.Anonymous, false)).page)
        val sent = reducer.reduceEvent(state, AuthEvent.EmailFinished(EmailTask.Resend, null, 1_000))
        assertEquals(61_000, sent.resendAt)
        assertEquals("sent", sent.notice)
    }
    @Test fun `recent login resumes original linking once with same uid`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var links = 0
            var reauths = 0
            val repo = object : AuthRepository {
                override val session = MutableStateFlow<AuthSession>(AuthSession.Authenticated(AuthUser("u", null, "a@example.test", setOf("google.com"))))
                override suspend fun signInWithGoogle(idToken: String) {}
                override suspend fun signOut() {}
                override suspend fun linkPassword(password: String) { links++; if (links == 1) throw AuthFailure(AuthError.RecentLoginRequired) }
                override suspend fun reauthenticateGoogle(idToken: String) { reauths++ }
            }
            val model = AuthViewModel(AuthState(session = repo.session.value, page = EmailPage.AddPassword), reducer, AuthInteractor(repo, null, StandardTestDispatcher(testScheduler)))
            runCurrent()
            model.onAction(AuthAction.PasswordChanged("long password"))
            model.onAction(AuthAction.ConfirmationChanged("long password"))
            model.onAction(AuthAction.SubmitEmail(EmailTask.LinkPassword))
            runCurrent()
            assertEquals(EmailPage.Reauthenticate, model.state.value.page)
            model.onAction(AuthAction.SubmitEmail(EmailTask.ReauthenticateGoogle, "synthetic"))
            runCurrent()
            assertEquals(1, reauths)
            assertEquals(2, links)
            assertEquals(EmailPage.Methods, model.state.value.page)
            assertEquals("", model.state.value.password)
            androidx.lifecycle.ViewModelStore().apply { put("auth", model); clear() }
        } finally { Dispatchers.resetMain() }
    }
    @Test fun `new process model restores pending user but no password`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val pending = AuthSession.EmailVerificationRequired(AuthUser("u", null, "a@example.test", setOf("password"), false))
            val repo = object : AuthRepository {
                override val session = MutableStateFlow<AuthSession>(pending)
                override suspend fun signInWithGoogle(idToken: String) {}
                override suspend fun signOut() {}
            }
            fun model() = AuthViewModel(AuthState(session = repo.session.value), reducer, AuthInteractor(repo, null, StandardTestDispatcher(testScheduler)))
            val previous = model()
            previous.onAction(AuthAction.PasswordChanged("sensitive value"))
            runCurrent()
            androidx.lifecycle.ViewModelStore().apply { put("auth", previous); clear() }
            val restored = model()
            runCurrent()
            assertEquals(EmailPage.Verify, restored.state.value.page)
            assertEquals("", restored.state.value.password)
            assertEquals(pending, restored.state.value.session)
            androidx.lifecycle.ViewModelStore().apply { put("auth", restored); clear() }
        } finally { Dispatchers.resetMain() }
    }

}
