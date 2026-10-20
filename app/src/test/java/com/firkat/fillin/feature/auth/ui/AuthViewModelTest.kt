package com.firkat.fillin.feature.auth.ui

import androidx.lifecycle.ViewModelStore
import com.firkat.fillin.feature.auth.domain.AuthError
import com.firkat.fillin.feature.auth.domain.AuthFailure
import com.firkat.fillin.feature.auth.domain.AuthRepository
import com.firkat.fillin.feature.auth.domain.AuthSession
import com.firkat.fillin.feature.auth.domain.AuthUser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val models = ViewModelStore()

    @Before fun setup() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun cleanup() { models.clear(); Dispatchers.resetMain() }

    private fun model(repository: FakeAuthRepository) = AuthViewModel(repository).also {
        models.put("auth", it)
    }

    @Test fun `restored session shows its profile without signing in again`() = runTest {
        val repository = FakeAuthRepository(AuthSession.Authenticated(USER))
        val model = model(repository)
        runCurrent()
        assertEquals(AuthSession.Authenticated(USER), model.state.value.session)
        assertFalse(model.state.value.busy)
        assertEquals(0, repository.signIns)
    }

    @Test fun `only a received Google credential signs in and repeated taps do not launch another picker`() = runTest {
        val repository = FakeAuthRepository()
        val model = model(repository)
        val requests = mutableListOf<GoogleSignInRequest>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.googleSignInRequests.collect { requests += it } }
        model.signIn()
        model.signIn()
        runCurrent()
        assertEquals(1, requests.size)
        assertEquals(0, repository.signIns)
        assertTrue(model.state.value.busy)
        model.credentialReceived(requests.single().id, "synthetic-google-credential")
        model.credentialReceived(requests.single().id, "duplicate-result")
        runCurrent()
        assertEquals(1, repository.signIns)
        assertEquals(AuthSession.Authenticated(USER), model.state.value.session)
        assertFalse(model.state.value.busy)
    }

    @Test fun `cancelled picker leaves anonymous session with no error and permits retry`() = runTest {
        val repository = FakeAuthRepository()
        val model = model(repository)
        model.signIn()
        val request = model.googleSignInRequests.first()
        model.credentialCancelled(request.id)
        runCurrent()
        assertEquals(AuthSession.Anonymous, model.state.value.session)
        assertFalse(model.state.value.busy)
        assertNull(model.state.value.error)
        assertEquals(0, repository.signIns)
        model.signIn()
        val retry = model.googleSignInRequests.first()
        assertNotEquals(request.id, retry.id)
    }

    @Test fun `stale picker result cannot sign in during a newer request`() = runTest {
        val repository = FakeAuthRepository()
        val model = model(repository)
        model.signIn()
        val first = model.googleSignInRequests.first()
        model.cancelCredentialRequest()
        model.signIn()
        val second = model.googleSignInRequests.first()
        model.credentialReceived(first.id, "stale-result")
        model.credentialCancelled(first.id)
        runCurrent()
        assertEquals(0, repository.signIns)
        assertTrue(model.state.value.busy)
        model.credentialReceived(second.id, "current-result")
        runCurrent()
        assertEquals(1, repository.signIns)
        assertEquals(AuthSession.Authenticated(USER), model.state.value.session)
    }

    @Test fun `network failure is recoverable and cleared for the next attempt`() = runTest {
        val repository = FakeAuthRepository().apply { signInAction = { throw AuthFailure(AuthError.Network) } }
        val model = model(repository)
        model.signIn()
        model.credentialReceived(model.googleSignInRequests.first().id, "synthetic-google-credential")
        runCurrent()
        assertEquals(AuthError.Network, model.state.value.error)
        assertEquals(AuthSession.Anonymous, model.state.value.session)
        assertFalse(model.state.value.busy)
        model.signIn()
        runCurrent()
        assertNull(model.state.value.error)
    }

    @Test fun `sign out is serialized with pending Firebase sign in`() = runTest {
        val pending = CompletableDeferred<Unit>()
        val repository = FakeAuthRepository().apply { signInAction = { pending.await() } }
        val model = model(repository)
        model.signIn()
        model.credentialReceived(model.googleSignInRequests.first().id, "synthetic-google-credential")
        runCurrent()
        model.signOut()
        assertEquals(0, repository.signOuts)
        pending.complete(Unit)
        runCurrent()
        model.signOut()
        model.signOut()
        runCurrent()
        assertEquals(1, repository.signOuts)
        assertEquals(AuthSession.Anonymous, model.state.value.session)
        assertFalse(model.state.value.busy)
    }

    @Test fun `provider reset failure does not restore a signed out Firebase session`() = runTest {
        val repository = FakeAuthRepository(AuthSession.Authenticated(USER)).apply {
            signOutError = AuthFailure(AuthError.CredentialResetFailed)
        }
        val model = model(repository)
        model.signOut()
        runCurrent()
        assertEquals(AuthSession.Anonymous, model.state.value.session)
        assertEquals(AuthError.CredentialResetFailed, model.state.value.error)
        assertFalse(model.state.value.busy)
    }

    @Test fun `missing Firebase config leaves local mode and never launches sign in`() = runTest {
        val repository = FakeAuthRepository(AuthSession.Unavailable)
        val model = model(repository)
        model.signIn()
        runCurrent()
        assertEquals(AuthSession.Unavailable, model.state.value.session)
        assertEquals(AuthError.NotConfigured, model.state.value.error)
        assertEquals(0, repository.signIns)
        assertFalse(model.state.value.busy)
    }

    private class FakeAuthRepository(initial: AuthSession = AuthSession.Anonymous) : AuthRepository {
        override val session = MutableStateFlow(initial)
        var signIns = 0
        var signOuts = 0
        var signInAction: suspend () -> Unit = {}
        var signOutError: AuthFailure? = null

        override suspend fun signInWithGoogle(idToken: String) {
            signIns++
            signInAction()
            session.value = AuthSession.Authenticated(USER)
        }

        override suspend fun signOut() {
            signOuts++
            session.value = AuthSession.Anonymous
            signOutError?.let { throw it }
        }
    }

    private companion object {
        val USER = AuthUser("synthetic-uid", "Тестовый пользователь", "user@example.test")
    }
}
