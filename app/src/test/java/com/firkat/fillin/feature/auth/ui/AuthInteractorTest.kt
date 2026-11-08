package com.firkat.fillin.feature.auth.ui

import com.firkat.fillin.feature.auth.domain.*
import com.firkat.fillin.feature.auth.ui.interactor.AuthCommand
import com.firkat.fillin.feature.auth.ui.interactor.AuthEvent
import com.firkat.fillin.feature.auth.ui.interactor.AuthInteractor
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthInteractorTest {
    private class Auth : AuthRepository {
        override val session = MutableStateFlow<AuthSession>(AuthSession.Anonymous)
        var signIn: suspend () -> Unit = {}
        var signOut: suspend () -> Unit = {}
        override suspend fun signInWithGoogle(idToken: String) = signIn()
        override suspend fun signOut() = signOut.invoke()
    }

    private val user = AuthUser("a", null, null)
    private fun profile(uid: String) = ServerUser("server-$uid", uid, null, null)

    @Test fun `auth commands use injected dispatcher and map expected and fallback failures`() = runTest {
        val auth = Auth()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val interactor = AuthInteractor(auth, null, dispatcher)
        auth.signIn = { assertSame(dispatcher, currentCoroutineContext()[ContinuationInterceptor]) }
        assertEquals(listOf(AuthEvent.OperationFinished(1)), interactor.execute(AuthCommand.SignInWithGoogle(1, "synthetic")).toList())
        auth.signIn = { throw AuthFailure(AuthError.Network) }
        assertEquals(listOf(AuthEvent.OperationFinished(2, AuthError.Network)), interactor.execute(AuthCommand.SignInWithGoogle(2, "synthetic")).toList())
        auth.signIn = { error("Unexpected") }
        assertEquals(listOf(AuthEvent.OperationFinished(3, AuthError.SignInFailed)), interactor.execute(AuthCommand.SignInWithGoogle(3, "synthetic")).toList())
        assertEquals(listOf(AuthEvent.OperationFinished(4)), interactor.execute(AuthCommand.SignOut(4)).toList())
        auth.signOut = { throw AuthFailure(AuthError.CredentialResetFailed) }
        assertEquals(listOf(AuthEvent.OperationFinished(5, AuthError.CredentialResetFailed)), interactor.execute(
            AuthCommand.SignOut(5)).toList())
        auth.signOut = { error("Unexpected") }
        assertEquals(listOf(AuthEvent.OperationFinished(6, AuthError.SignOutFailed)), interactor.execute(AuthCommand.SignOut(6)).toList())
    }

    @Test fun `profile success and failures retain request identity`() = runTest {
        val auth = Auth().apply { session.value = AuthSession.Authenticated(user) }
        var failure: Exception? = null
        val server = object : ServerAuthRepository {
            override suspend fun authenticate(uid: String): ServerUser {
                failure?.let { throw it }
                return profile(uid)
            }
        }
        val interactor = AuthInteractor(auth, server, StandardTestDispatcher(testScheduler))
        val command = AuthCommand.LoadServerProfile(9, "a")
        assertEquals(listOf(AuthEvent.ProfileLoaded(9, "a", ServerProfileState.Ready(profile("a")))), interactor.execute(command).toList())
        failure = ServerAuthFailure(ServerAuthError.Network)
        assertEquals(listOf(AuthEvent.ProfileLoaded(9, "a", ServerProfileState.Failed(ServerAuthError.Network))), interactor.execute(command).toList())
        failure = IllegalStateException("Unexpected")
        assertEquals(listOf(AuthEvent.ProfileLoaded(9, "a", ServerProfileState.Failed(ServerAuthError.Unavailable))), interactor.execute(command).toList())
    }

    @Test fun `cancellation is never converted into a business error`() = runTest {
        val auth = Auth().apply { session.value = AuthSession.Authenticated(user) }
        val cancellation = CancellationException("Synthetic cancellation")
        auth.signIn = { throw cancellation }
        auth.signOut = { throw cancellation }
        val interactor = AuthInteractor(auth, object : ServerAuthRepository {
            override suspend fun authenticate(uid: String): ServerUser = throw cancellation
        }, StandardTestDispatcher(testScheduler))
        for (command in listOf(AuthCommand.SignInWithGoogle(1, "synthetic"), AuthCommand.SignOut(2), AuthCommand.LoadServerProfile(3, "a"))) {
            val events = mutableListOf<AuthEvent>()
            try {
                interactor.execute(command).toList(events)
                fail("Cancellation must propagate")
            } catch (_: CancellationException) {
                assertTrue(events.isEmpty())
            }
        }
    }

    @Test fun `observe session stays active across commands and stops on cancellation`() = runTest {
        val auth = Auth()
        val interactor = AuthInteractor(auth, null, StandardTestDispatcher(testScheduler))
        val events = mutableListOf<AuthEvent>()
        val observer = backgroundScope.launch { interactor.execute(AuthCommand.ObserveSession).toList(events) }
        runCurrent()
        auth.session.value = AuthSession.Authenticated(user)
        runCurrent()
        interactor.execute(AuthCommand.SignOut(1)).toList()
        auth.session.value = AuthSession.Anonymous
        runCurrent()
        assertEquals(listOf(AuthSession.Anonymous, AuthSession.Authenticated(user), AuthSession.Anonymous), events.map { (it as AuthEvent.SessionChanged).session })
        assertTrue(observer.isActive)
        observer.cancel()
        runCurrent()
        auth.session.value = AuthSession.Unavailable
        runCurrent()
        assertEquals(3, events.size)
        assertEquals(0, auth.session.subscriptionCount.value)
    }

    @Test fun `profile command ignores a changed account before and after the server request`() = runTest {
        val auth = Auth()
        val response = CompletableDeferred<ServerUser>()
        var calls = 0
        val interactor = AuthInteractor(auth, object : ServerAuthRepository {
            override suspend fun authenticate(uid: String): ServerUser {
                calls++; return response.await()
            }
        }, StandardTestDispatcher(testScheduler))
        assertTrue(interactor.execute(AuthCommand.LoadServerProfile(1, "a")).toList().isEmpty())
        assertEquals(0, calls)
        auth.session.value = AuthSession.Authenticated(user)
        val events = mutableListOf<AuthEvent>()
        val job = launch { interactor.execute(AuthCommand.LoadServerProfile(2, "a")).toList(events) }
        runCurrent()
        auth.session.value = AuthSession.Authenticated(user.copy(uid = "b"))
        response.complete(profile("a"))
        job.join()
        assertEquals(1, calls)
        assertTrue(events.isEmpty())
    }

    @Test fun `cancelling a long command cancels repository work without an event`() = runTest {
        val auth = Auth()
        var cancelled = false
        auth.signIn = { try { awaitCancellation() } finally { cancelled = true } }
        val interactor = AuthInteractor(auth, null, StandardTestDispatcher(testScheduler))
        val events = mutableListOf<AuthEvent>()
        val job = launch { interactor.execute(AuthCommand.SignInWithGoogle(1, "synthetic")).toList(events) }
        runCurrent()
        job.cancel()
        job.join()
        assertTrue(cancelled)
        assertTrue(events.isEmpty())
    }
}
