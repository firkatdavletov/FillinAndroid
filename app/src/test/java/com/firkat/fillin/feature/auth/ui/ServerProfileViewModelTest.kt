package com.firkat.fillin.feature.auth.ui

import androidx.lifecycle.ViewModelStore
import com.firkat.fillin.feature.auth.domain.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServerProfileViewModelTest {
    private val models = ViewModelStore()
    @Before fun setup() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun cleanup() { models.clear(); Dispatchers.resetMain() }

    @Test fun `restored Firebase session loads its server profile without Google picker`() = runTest {
        val model = model(FakeAuth(), object : ServerAuthRepository {
            override suspend fun authenticate(uid: String) = profile(uid)
        })
        runCurrent()
        assertEquals(ServerProfileState.Ready(profile(USER.uid)), model.state.value.serverProfile)
    }

    @Test fun `outage preserves Firebase sign in and permits retry without signing in again`() = runTest {
        var attempts = 0
        val model = model(FakeAuth(), object : ServerAuthRepository {
            override suspend fun authenticate(uid: String): ServerUser {
                if (++attempts == 1) throw ServerAuthFailure(ServerAuthError.Unavailable)
                return profile(uid)
            }
        })
        runCurrent()
        assertEquals(ServerProfileState.Failed(ServerAuthError.Unavailable), model.state.value.serverProfile)
        assertEquals(AuthSession.Authenticated(USER), model.state.value.session)
        model.retryServerAuthentication()
        runCurrent()
        assertEquals(ServerProfileState.Ready(profile(USER.uid)), model.state.value.serverProfile)
        assertEquals(2, attempts)
    }

    @Test fun `sign out cancels pending profile and hides it`() = runTest {
        val response = CompletableDeferred<ServerUser>()
        val auth = FakeAuth()
        val model = model(auth, object : ServerAuthRepository {
            override suspend fun authenticate(uid: String) = response.await()
        })
        runCurrent()
        assertEquals(ServerProfileState.Loading, model.state.value.serverProfile)
        model.signOut()
        runCurrent()
        response.complete(profile(USER.uid))
        runCurrent()
        assertEquals(AuthSession.Anonymous, model.state.value.session)
        assertEquals(ServerProfileState.Idle, model.state.value.serverProfile)
    }

    @Test fun `switching account discards the pending profile and loads the current account`() = runTest {
        val first = CompletableDeferred<ServerUser>()
        val auth = FakeAuth()
        val model = model(auth, object : ServerAuthRepository {
            override suspend fun authenticate(uid: String) = if (uid == USER.uid) first.await() else profile(uid)
        })
        runCurrent()
        auth.session.value = AuthSession.Authenticated(USER.copy(uid = "second-uid"))
        runCurrent()
        first.complete(profile(USER.uid))
        runCurrent()
        assertEquals(ServerProfileState.Ready(profile("second-uid")), model.state.value.serverProfile)
    }

    @Test fun `local mode never calls the server`() = runTest {
        val auth = FakeAuth().apply { session.value = AuthSession.Anonymous }
        val model = model(auth, object : ServerAuthRepository {
            override suspend fun authenticate(uid: String): ServerUser = error("Local mode must not call server")
        })
        model.retryServerAuthentication()
        runCurrent()
        assertEquals(ServerProfileState.Idle, model.state.value.serverProfile)
    }

    private fun model(auth: AuthRepository, server: ServerAuthRepository) = AuthViewModel(auth, server).also { models.put("auth", it) }
    private class FakeAuth : AuthRepository {
        override val session = MutableStateFlow<AuthSession>(AuthSession.Authenticated(USER))
        override suspend fun signInWithGoogle(idToken: String) { session.value = AuthSession.Authenticated(USER) }
        override suspend fun signOut() { session.value = AuthSession.Anonymous }
    }
    private fun profile(uid: String) = ServerUser("server-$uid", uid, USER.displayName, USER.email)
    private companion object { val USER = AuthUser("synthetic-uid", "Synthetic User", "user@example.test") }
}
