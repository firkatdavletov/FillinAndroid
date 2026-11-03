package com.firkat.fillin.feature.auth.ui

import androidx.lifecycle.ViewModelStore
import com.firkat.fillin.feature.auth.domain.AuthError
import com.firkat.fillin.feature.auth.domain.AuthFailure
import com.firkat.fillin.feature.auth.domain.AuthRepository
import com.firkat.fillin.feature.auth.domain.AuthSession
import com.firkat.fillin.feature.auth.domain.AuthUser
import com.firkat.fillin.feature.auth.ui.interactor.AuthAction
import com.firkat.fillin.feature.auth.ui.interactor.AuthEffect
import com.firkat.fillin.feature.auth.ui.interactor.AuthInteractor
import com.firkat.fillin.feature.auth.ui.interactor.AuthOperation
import com.firkat.fillin.feature.auth.ui.interactor.AuthReducer
import com.firkat.fillin.feature.auth.ui.interactor.AuthState
import com.firkat.fillin.feature.auth.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

    private fun model(repository: FakeAuthRepository) = AuthViewModel(
        AuthState(session = repository.session.value),
        AuthReducer(),
        AuthInteractor(repository, null, StandardTestDispatcher())
    ).also {
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
        val requests = mutableListOf<AuthEffect.LaunchGooglePicker>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.effects.collect { if (it is AuthEffect.LaunchGooglePicker) requests += it } }
        model.onAction(AuthAction.UiReady)
        model.onAction(AuthAction.GoogleClicked)
        model.onAction(AuthAction.GoogleClicked)
        runCurrent()
        assertEquals(1, requests.size)
        assertEquals(0, repository.signIns)
        assertTrue(model.state.value.busy)
        model.onAction(AuthAction.CredentialReceived(requests.single().requestId, "synthetic-google-credential"))
        model.onAction(AuthAction.CredentialReceived(requests.single().requestId, "duplicate-result"))
        runCurrent()
        assertEquals(1, repository.signIns)
        assertEquals(AuthSession.Authenticated(USER), model.state.value.session)
        assertFalse(model.state.value.busy)
    }

    @Test fun `cancelled picker leaves anonymous session with no error and permits retry`() = runTest {
        val repository = FakeAuthRepository()
        val model = model(repository)
        model.onAction(AuthAction.GoogleClicked)
        val request = model.state.value.operationId
        model.onAction(AuthAction.CredentialCancelled(request))
        runCurrent()
        assertEquals(AuthSession.Anonymous, model.state.value.session)
        assertFalse(model.state.value.busy)
        assertNull(model.state.value.error)
        assertEquals(0, repository.signIns)
        model.onAction(AuthAction.GoogleClicked)
        val retry = model.state.value.operationId
        assertNotEquals(request, retry)
    }

    @Test fun `stale picker result cannot sign in during a newer request`() = runTest {
        val repository = FakeAuthRepository()
        val model = model(repository)
        model.onAction(AuthAction.GoogleClicked)
        val first = model.state.value.operationId
        model.onAction(AuthAction.UiDetached)
        model.onAction(AuthAction.GoogleClicked)
        val second = model.state.value.operationId
        model.onAction(AuthAction.CredentialReceived(first, "stale-result"))
        model.onAction(AuthAction.CredentialCancelled(first))
        runCurrent()
        assertEquals(0, repository.signIns)
        assertTrue(model.state.value.busy)
        model.onAction(AuthAction.CredentialReceived(second, "current-result"))
        runCurrent()
        assertEquals(1, repository.signIns)
        assertEquals(AuthSession.Authenticated(USER), model.state.value.session)
    }

    @Test fun `network failure is recoverable and cleared for the next attempt`() = runTest {
        val repository = FakeAuthRepository().apply { signInAction = { throw AuthFailure(AuthError.Network) } }
        val model = model(repository)
        model.onAction(AuthAction.GoogleClicked)
        model.onAction(AuthAction.CredentialReceived(model.state.value.operationId, "synthetic-google-credential"))
        runCurrent()
        assertEquals(AuthError.Network, model.state.value.error)
        assertEquals(AuthSession.Anonymous, model.state.value.session)
        assertFalse(model.state.value.busy)
        model.onAction(AuthAction.GoogleClicked)
        runCurrent()
        assertNull(model.state.value.error)
    }

    @Test fun `sign out is serialized with pending Firebase sign in`() = runTest {
        val pending = CompletableDeferred<Unit>()
        val repository = FakeAuthRepository().apply { signInAction = { pending.await() } }
        val model = model(repository)
        model.onAction(AuthAction.GoogleClicked)
        model.onAction(AuthAction.CredentialReceived(model.state.value.operationId, "synthetic-google-credential"))
        runCurrent()
        model.onAction(AuthAction.SignOut)
        assertEquals(0, repository.signOuts)
        pending.complete(Unit)
        runCurrent()
        model.onAction(AuthAction.SignOut)
        model.onAction(AuthAction.SignOut)
        runCurrent()
        assertEquals(1, repository.signOuts)
        assertEquals(AuthSession.Anonymous, model.state.value.session)
        assertFalse(model.state.value.busy)
    }

    @Test fun `repository cancellation during sign in clears busy without error and permits retry`() = runTest {
        val repository = FakeAuthRepository().apply {
            signInAction = { throw CancellationException("Sign in cancelled") }
        }
        val model = model(repository)
        model.onAction(AuthAction.GoogleClicked)
        val cancelledId = model.state.value.operationId
        model.onAction(AuthAction.CredentialReceived(cancelledId, "synthetic"))
        model.onAction(AuthAction.UiDetached)
        runCurrent()
        assertEquals(1, repository.signIns)
        assertEquals(AuthSession.Anonymous, model.state.value.session)
        assertFalse(model.state.value.busy)
        assertNull(model.state.value.error)
        assertEquals(1, repository.session.subscriptionCount.value)

        repository.signInAction = {}
        model.onAction(AuthAction.GoogleClicked)
        val retryId = model.state.value.operationId
        assertNotEquals(cancelledId, retryId)
        model.onAction(AuthAction.CredentialReceived(retryId, "retry"))
        model.onAction(AuthAction.CredentialReceived(retryId, "duplicate"))
        runCurrent()
        assertEquals(2, repository.signIns)
        assertEquals(AuthSession.Authenticated(USER), model.state.value.session)
        assertFalse(model.state.value.busy)
        assertNull(model.state.value.error)
    }

    @Test fun `repository cancellation during sign out clears busy without error and permits retry`() = runTest {
        val repository = FakeAuthRepository(AuthSession.Authenticated(USER)).apply {
            signOutAction = { throw CancellationException("Sign out cancelled") }
        }
        val model = model(repository)
        model.onAction(AuthAction.SignOut)
        val cancelledId = model.state.value.operationId
        model.onAction(AuthAction.UiDetached)
        runCurrent()
        assertEquals(1, repository.signOuts)
        assertEquals(AuthSession.Authenticated(USER), model.state.value.session)
        assertFalse(model.state.value.busy)
        assertNull(model.state.value.error)
        assertEquals(1, repository.session.subscriptionCount.value)

        repository.signOutAction = {}
        model.onAction(AuthAction.SignOut)
        assertNotEquals(cancelledId, model.state.value.operationId)
        model.onAction(AuthAction.SignOut)
        runCurrent()
        assertEquals(2, repository.signOuts)
        assertEquals(AuthSession.Anonymous, model.state.value.session)
        assertFalse(model.state.value.busy)
        assertNull(model.state.value.error)
    }

    @Test fun `provider reset failure does not restore a signed out Firebase session`() = runTest {
        val repository = FakeAuthRepository(AuthSession.Authenticated(USER)).apply {
            signOutError = AuthFailure(AuthError.CredentialResetFailed)
        }
        val model = model(repository)
        model.onAction(AuthAction.SignOut)
        runCurrent()
        assertEquals(AuthSession.Anonymous, model.state.value.session)
        assertEquals(AuthError.CredentialResetFailed, model.state.value.error)
        assertFalse(model.state.value.busy)
    }

    @Test fun `missing Firebase config leaves local mode and never launches sign in`() = runTest {
        val repository = FakeAuthRepository(AuthSession.Unavailable)
        val model = model(repository)
        model.onAction(AuthAction.GoogleClicked)
        runCurrent()
        assertEquals(AuthSession.Unavailable, model.state.value.session)
        assertEquals(AuthError.NotConfigured, model.state.value.error)
        assertEquals(0, repository.signIns)
        assertFalse(model.state.value.busy)
    }

    @Test fun `pending picker survives missing subscriber and reconnect does not duplicate a launched picker`() = runTest {
        val repository = FakeAuthRepository()
        val model = model(repository)
        model.onAction(AuthAction.GoogleClicked)
        runCurrent()
        assertTrue(model.state.value.busy)
        assertEquals(AuthOperation.AwaitingPicker, model.state.value.operation)
        val requests = mutableListOf<AuthEffect.LaunchGooglePicker>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.effects.onSubscription { model.onAction(AuthAction.UiReady) }.collect {
                if (it is AuthEffect.LaunchGooglePicker) {
                    requests += it
                    model.onAction(AuthAction.PickerStarted(it.requestId))
                }
            }
        }
        runCurrent()
        assertEquals(1, requests.size)
        model.onAction(AuthAction.UiReady)
        runCurrent()
        assertEquals(1, requests.size)
        collector.cancel()
        model.onAction(AuthAction.UiDetached)
        assertFalse(model.state.value.busy)
        val oldId = requests.single().requestId
        model.onAction(AuthAction.GoogleClicked)
        val nextId = model.state.value.operationId
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.effects.onSubscription { model.onAction(AuthAction.UiReady) }.collect {
                if (it is AuthEffect.LaunchGooglePicker) requests += it
            }
        }
        runCurrent()
        assertEquals(listOf(oldId, nextId), requests.map { it.requestId })
        model.onAction(AuthAction.CredentialFailed(oldId, AuthError.Network))
        assertTrue(model.state.value.busy)
        assertNull(model.state.value.error)
    }

    @Test fun `detach before picker effect delivery cancels it and allows another attempt`() = runTest {
        val model = model(FakeAuthRepository())
        val effects = mutableListOf<AuthEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.effects.collect { effects += it } }
        model.onAction(AuthAction.UiReady)
        model.onAction(AuthAction.GoogleClicked)
        model.onAction(AuthAction.UiDetached)
        runCurrent()
        assertTrue(effects.isEmpty())
        assertFalse(model.state.value.busy)
        model.onAction(AuthAction.UiReady)
        model.onAction(AuthAction.GoogleClicked)
        runCurrent()
        assertEquals(1, effects.size)
    }

    @Test fun `leaving screen after accepting credential keeps Firebase exchange active`() = runTest {
        val pending = CompletableDeferred<Unit>()
        val repository = FakeAuthRepository().apply { signInAction = { pending.await() } }
        val model = model(repository)
        model.onAction(AuthAction.GoogleClicked)
        val id = model.state.value.operationId
        model.onAction(AuthAction.CredentialReceived(id, "synthetic"))
        model.onAction(AuthAction.UiDetached)
        model.onAction(AuthAction.CredentialCancelled(id))
        runCurrent()
        assertEquals(1, repository.signIns)
        assertTrue(model.state.value.busy)
        pending.complete(Unit)
        runCurrent()
        assertEquals(AuthSession.Authenticated(USER), model.state.value.session)
        assertFalse(model.state.value.busy)
    }

    @Test fun `errors remain in state without subscribers until the matching error is acknowledged`() = runTest {
        val repository = FakeAuthRepository().apply { signInAction = { throw AuthFailure(AuthError.Network) } }
        val model = model(repository)
        model.onAction(AuthAction.GoogleClicked)
        model.onAction(AuthAction.CredentialReceived(model.state.value.operationId, "synthetic"))
        runCurrent()
        val firstErrorId = model.state.value.errorId
        model.onAction(AuthAction.UiDetached)
        assertEquals(AuthError.Network, model.state.value.error)
        model.onAction(AuthAction.GoogleClicked)
        model.onAction(AuthAction.CredentialReceived(model.state.value.operationId, "synthetic"))
        runCurrent()
        model.onAction(AuthAction.ErrorShown(firstErrorId))
        assertEquals(AuthError.Network, model.state.value.error)
        model.onAction(AuthAction.ErrorShown(model.state.value.errorId))
        assertNull(model.state.value.error)
    }

    @Test fun `local actions publish their effects and are ignored during auth operation`() = runTest {
        val model = model(FakeAuthRepository())
        val effects = mutableListOf<AuthEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.effects.collect { effects += it } }
        model.onAction(AuthAction.EmailClicked)
        model.onAction(AuthAction.GuestClicked)
        runCurrent()
        assertEquals(listOf(AuthEffect.ContinueAsGuest), effects)
        model.onAction(AuthAction.GoogleClicked)
        model.onAction(AuthAction.EmailClicked)
        model.onAction(AuthAction.GuestClicked)
        runCurrent()
        assertEquals(1, effects.size)
        model.onAction(AuthAction.CredentialReceived(model.state.value.operationId, "synthetic"))
        runCurrent()
        model.onAction(AuthAction.GoogleClicked)
        runCurrent()
        assertEquals(AuthEffect.ContinueWithAccount, effects.last())
    }

    @Test fun `clearing ViewModel cancels auth operation and long session observation`() = runTest {
        var cancelled = false
        val repository = FakeAuthRepository().apply {
            signInAction = { try { awaitCancellation() } finally { cancelled = true } }
        }
        val model = model(repository)
        model.onAction(AuthAction.GoogleClicked)
        model.onAction(AuthAction.CredentialReceived(model.state.value.operationId, "synthetic"))
        runCurrent()
        assertEquals(1, repository.session.subscriptionCount.value)
        models.clear()
        runCurrent()
        assertTrue(cancelled)
        assertEquals(0, repository.session.subscriptionCount.value)
        assertNull(model.state.value.error)
    }

    private class FakeAuthRepository(initial: AuthSession = AuthSession.Anonymous) : AuthRepository {
        override val session = MutableStateFlow(initial)
        var signIns = 0
        var signOuts = 0
        var signInAction: suspend () -> Unit = {}
        var signOutAction: suspend () -> Unit = {}
        var signOutError: AuthFailure? = null

        override suspend fun signInWithGoogle(idToken: String) {
            signIns++
            signInAction()
            session.value = AuthSession.Authenticated(USER)
        }

        override suspend fun signOut() {
            signOuts++
            signOutAction()
            session.value = AuthSession.Anonymous
            signOutError?.let { throw it }
        }
    }

    private companion object {
        val USER = AuthUser("synthetic-uid", "Тестовый пользователь", "user@example.test")
    }
}
