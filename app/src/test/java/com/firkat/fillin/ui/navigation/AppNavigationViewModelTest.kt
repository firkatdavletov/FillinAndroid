package com.firkat.fillin.ui.navigation

import androidx.lifecycle.ViewModelStore
import com.firkat.fillin.feature.auth.domain.AuthRepository
import com.firkat.fillin.feature.auth.domain.AuthSession
import com.firkat.fillin.feature.auth.domain.AuthUser
import com.firkat.fillin.feature.budgetsetup.domain.ActiveBudgetReadiness
import com.firkat.fillin.feature.budgetsetup.domain.BudgetSetupMode
import com.firkat.fillin.feature.launch.domain.LocalBudgetRestorer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppNavigationViewModelTest {
    private val models = ViewModelStore()
    @Before fun setup() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun cleanup() { models.clear(); Dispatchers.resetMain() }

    @Test fun `Launch is first and waits for Firebase restoration`() = runTest {
        val auth = FakeAuth(AuthSession.Loading)
        var reads = 0
        val model = model(auth) { reads++; false }
        assertEquals(listOf(AppRoute.Launch), model.backStack)
        model.onLaunchShown()
        runCurrent()
        assertEquals(listOf(AppRoute.Launch), model.backStack)
        assertEquals(0, reads)
        auth.session.value = AuthSession.Authenticated(USER)
        runCurrent()
        assertEquals(listOf(AppRoute.Main), model.backStack)
        assertEquals(0, reads)
    }

    @Test fun `cold launch without a user opens the created local budget`() = runTest {
        for (session in listOf(AuthSession.Anonymous, AuthSession.Unavailable)) {
            val model = model(FakeAuth(session)) { true }
            model.onLaunchShown()
            runCurrent()
            assertEquals(listOf(AppRoute.Main), model.backStack)
        }
    }

    @Test fun `empty device opens Auth including without Firebase configuration`() = runTest {
        for (session in listOf(AuthSession.Anonymous, AuthSession.Unavailable)) {
            val model = model(FakeAuth(session)) { false }
            model.onLaunchShown()
            runCurrent()
            assertEquals(listOf(AppRoute.Auth), model.backStack)
        }
    }

    @Test fun `local budget restoration completes before opening Main`() = runTest {
        val result = CompletableDeferred<Boolean>()
        val model = model(FakeAuth()) { result.await() }
        model.onLaunchShown()
        runCurrent()
        assertEquals(listOf(AppRoute.Launch), model.backStack)
        result.complete(true)
        runCurrent()
        assertEquals(listOf(AppRoute.Main), model.backStack)
    }

    @Test fun `storage failure stays at Launch and supports retry`() = runTest {
        var reads = 0
        val model = model(FakeAuth()) { if (++reads == 1) error("Storage unavailable") else true }
        model.onLaunchShown()
        runCurrent()
        assertTrue(model.launchFailed.value)
        assertEquals(listOf(AppRoute.Launch), model.backStack)
        model.retryLaunch()
        runCurrent()
        assertFalse(model.launchFailed.value)
        assertEquals(listOf(AppRoute.Main), model.backStack)
    }

    @Test fun `a restored user cancels an obsolete device budget check`() = runTest {
        val auth = FakeAuth()
        val result = CompletableDeferred<Boolean>()
        val model = model(auth) { result.await() }
        model.onLaunchShown()
        runCurrent()
        auth.session.value = AuthSession.Authenticated(USER)
        runCurrent()
        result.complete(false)
        runCurrent()
        assertEquals(listOf(AppRoute.Main), model.backStack)
    }

    @Test fun `guest opens local setup directly and back returns to Auth`() = runTest {
        val model = model(FakeAuth()) { false }
        model.onLaunchShown()
        runCurrent()
        model.continueAsGuest()
        assertEquals(listOf(AppRoute.Auth, AppRoute.BudgetSetup(BudgetSetupMode.Local)), model.backStack)
        model.back()
        assertEquals(listOf(AppRoute.Auth), model.backStack)
    }

    @Test fun `guest can open Auth once and return to the existing Main`() = runTest {
        val model = model(FakeAuth()) { true }
        model.onLaunchShown()
        runCurrent()
        model.openAuthentication()
        model.openAuthentication()
        assertEquals(listOf(AppRoute.Main, AppRoute.Auth), model.backStack)
        model.back()
        assertEquals(listOf(AppRoute.Main), model.backStack)
        model.openAuthentication()
        model.continueAsGuest()
        assertEquals(listOf(AppRoute.Main), model.backStack)
    }

    @Test fun `successful login returns to Main without duplicate entries`() = runTest {
        val auth = FakeAuth()
        val model = model(auth) { true }
        model.onLaunchShown()
        runCurrent()
        model.openAuthentication()
        auth.signInWithGoogle("synthetic")
        runCurrent()
        assertEquals(listOf(AppRoute.Main), model.backStack)
    }

    @Test fun `logout opens Auth even with a local budget and clears back history`() = runTest {
        val auth = FakeAuth(AuthSession.Authenticated(USER))
        var reads = 0
        val model = model(auth) { reads++; true }
        model.onLaunchShown()
        runCurrent()
        auth.signOut()
        runCurrent()
        model.back()
        assertEquals(listOf(AppRoute.Auth), model.backStack)
        assertEquals(0, reads)
    }

    @Test fun `rotation does not restart session observation or local budget inspection`() = runTest {
        var reads = 0
        val model = model(FakeAuth()) { reads++; true }
        model.onLaunchShown()
        runCurrent()
        model.openAuthentication()
        model.onLaunchShown()
        runCurrent()
        assertEquals(listOf(AppRoute.Main, AppRoute.Auth), model.backStack)
        assertEquals(1, reads)
    }

    @Test fun `Main waits for local commit and startup history is removed`() = runTest {
        val ready = MutableStateFlow(false)
        val model = model(FakeAuth(), ready) { false }
        model.onLaunchShown()
        runCurrent()
        model.continueAsGuest()
        assertEquals(AppRoute.BudgetSetup(BudgetSetupMode.Local), model.backStack.last())
        model.continueAsGuest()
        assertEquals(2, model.backStack.size)
        ready.value = true
        runCurrent()
        model.back()
        assertEquals(listOf(AppRoute.Main), model.backStack)
    }

    @Test fun `authenticated user without budget opens cloud setup and back remains at Auth`() = runTest {
        val ready = MutableStateFlow(false)
        val model = model(FakeAuth(AuthSession.Authenticated(USER)), ready) { error("Unused") }
        model.onLaunchShown()
        runCurrent()
        assertEquals(listOf(AppRoute.Auth, AppRoute.BudgetSetup(BudgetSetupMode.Cloud)), model.backStack)
        model.back()
        runCurrent()
        assertEquals(listOf(AppRoute.Auth), model.backStack)
        model.continueWithAccount()
        assertEquals(AppRoute.BudgetSetup(BudgetSetupMode.Cloud), model.backStack.last())
        ready.value = true
        runCurrent()
        assertEquals(listOf(AppRoute.Main), model.backStack)
    }

    @Test fun `Google login without budget opens cloud instead of Main`() = runTest {
        val auth = FakeAuth()
        val ready = MutableStateFlow(false)
        val model = model(auth, ready) { false }
        model.onLaunchShown()
        runCurrent()
        auth.signInWithGoogle("synthetic")
        runCurrent()
        assertEquals(AppRoute.BudgetSetup(BudgetSetupMode.Cloud), model.backStack.last())
    }

    @Test fun `restored account waits for readiness before opening Main`() = runTest {
        val result = CompletableDeferred<Boolean>()
        val model = model(FakeAuth(AuthSession.Authenticated(USER)), source = ActiveBudgetReadiness {
            flow { emit(result.await()) }
        }) { error("Unused") }
        model.onLaunchShown()
        runCurrent()
        assertEquals(listOf(AppRoute.Launch), model.backStack)
        assertTrue(model.resolvingBudget.value)
        result.complete(true)
        runCurrent()
        assertEquals(listOf(AppRoute.Main), model.backStack)
        assertFalse(model.resolvingBudget.value)
    }

    @Test fun `switching to an unconfigured profile requires setup again`() = runTest {
        val ready = MutableStateFlow(true)
        val model = model(FakeAuth(), ready) { true }
        model.onLaunchShown()
        runCurrent()
        ready.value = false
        runCurrent()
        assertEquals(AppRoute.BudgetSetup(BudgetSetupMode.Local), model.backStack.last())
        ready.value = true
        runCurrent()
        assertEquals(listOf(AppRoute.Main), model.backStack)
    }

    @Test fun `late budget result for a previous account cannot navigate after logout`() = runTest {
        val auth = FakeAuth(AuthSession.Authenticated(USER))
        val oldResult = CompletableDeferred<Boolean>()
        val model = model(auth, source = ActiveBudgetReadiness {
            if (auth.session.value is AuthSession.Authenticated) flow {
                val result = withContext(NonCancellable) { oldResult.await() }
                emit(result)
            } else MutableStateFlow(false)
        }) { false }
        model.onLaunchShown()
        runCurrent()
        auth.signOut()
        runCurrent()
        oldResult.complete(true)
        runCurrent()
        assertEquals(listOf(AppRoute.Auth), model.backStack)
    }

    @Test fun `readiness failure shows launch error and retry restores budget`() = runTest {
        var attempts = 0
        val model = model(FakeAuth(AuthSession.Authenticated(USER)), source = ActiveBudgetReadiness {
            flow { if (++attempts == 1) error("Room unavailable") else emit(true) }
        }) { error("Unused") }
        model.onLaunchShown()
        runCurrent()
        assertTrue(model.launchFailed.value)
        model.retryLaunch()
        runCurrent()
        assertEquals(listOf(AppRoute.Main), model.backStack)
        assertFalse(model.launchFailed.value)
    }

    @Test fun `failed account restoration cannot be bypassed with back and retry keeps Main`() = runTest {
        val auth = FakeAuth()
        var fail = true
        val model = model(auth, source = ActiveBudgetReadiness {
            flow {
                if (auth.session.value is AuthSession.Authenticated && fail) error("Room unavailable")
                emit(true)
            }
        }) { true }
        model.onLaunchShown()
        runCurrent()
        model.openAuthentication()
        auth.signInWithGoogle("synthetic")
        runCurrent()
        assertTrue(model.launchFailed.value)
        model.back()
        assertEquals(AppRoute.Launch, model.backStack.last())
        fail = false
        model.retryLaunch()
        runCurrent()
        assertEquals(listOf(AppRoute.Main), model.backStack)
    }

    private fun model(
        auth: AuthRepository,
        ready: MutableStateFlow<Boolean> = MutableStateFlow(true),
        source: ActiveBudgetReadiness = ActiveBudgetReadiness { ready },
        restore: suspend () -> Boolean,
    ) = AppNavigationViewModel(auth, LocalBudgetRestorer { restore().also { ready.value = it } }, source).also {
            models.put("navigation-${models.keys().size}", it)
        }

    private class FakeAuth(initial: AuthSession = AuthSession.Anonymous) : AuthRepository {
        override val session = MutableStateFlow(initial)
        override suspend fun signInWithGoogle(idToken: String) { session.value = AuthSession.Authenticated(USER) }
        override suspend fun signOut() { session.value = AuthSession.Anonymous }
    }

    private companion object { val USER = AuthUser("synthetic-uid", "User", null) }
}
