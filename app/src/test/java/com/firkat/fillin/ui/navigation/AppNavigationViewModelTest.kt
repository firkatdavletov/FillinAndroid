package com.firkat.fillin.ui.navigation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.firkat.fillin.feature.appaccess.domain.AppAccess
import com.firkat.fillin.feature.appaccess.domain.AppMode
import com.firkat.fillin.feature.auth.domain.AuthRepository
import com.firkat.fillin.feature.auth.domain.AuthSession
import com.firkat.fillin.feature.auth.domain.AuthUser
import com.firkat.fillin.feature.budgetsetup.domain.ActiveBudgetReadiness
import com.firkat.fillin.feature.budgetsetup.domain.BudgetSetupMode
import com.firkat.fillin.feature.budgetsetup.ui.interactor.SetupAuthTarget
import com.firkat.fillin.feature.budgetsetup.ui.interactor.SetupPage
import com.firkat.fillin.feature.launch.domain.LocalBudgetRestorer
import com.firkat.fillin.feature.main.ui.AppDestination
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppNavigationViewModelTest {
    private val models = ViewModelStore()

    @Before fun setup() = Dispatchers.setMain(StandardTestDispatcher())

    @After fun cleanup() {
        models.clear()
        Dispatchers.resetMain()
    }

    @Test fun `forecast opens once from Main and Back restores Main`() = runTest {
        val model = model(FakeAuth(AuthSession.Anonymous)) { true }
        model.onLaunchShown()
        runCurrent()
        model.openForecast("local-budget")
        model.openForecast("local-budget")
        assertEquals(listOf(AppRoute.Main, AppRoute.ForecastDetails("local-budget")), model.backStack)
        model.back()
        assertEquals(listOf(AppRoute.Main), model.backStack)
    }

    @Test fun `local launch proceeds while Firebase restores its session`() = runTest {
        val auth = FakeAuth(AuthSession.Loading)
        var reads = 0
        val model = model(auth) {
            reads++
            false
        }
        assertEquals(listOf(AppRoute.Launch), model.backStack)
        model.onLaunchShown()
        runCurrent()
        assertEquals(listOf(AppRoute.BudgetSetup(BudgetSetupMode.Local)), model.backStack)
        assertEquals(1, reads)
        auth.session.value = AuthSession.Authenticated(USER)
        runCurrent()
        assertEquals(listOf(AppRoute.BudgetSetup(BudgetSetupMode.Local)), model.backStack)
        assertEquals(1, reads)
    }

    @Test fun `cold launch without a user opens the created local budget`() = runTest {
        for (session in listOf(AuthSession.Anonymous, AuthSession.Unavailable)) {
            val model = model(FakeAuth(session)) { true }
            model.onLaunchShown()
            runCurrent()
            assertEquals(listOf(AppRoute.Main), model.backStack)
        }
    }

    @Test fun `empty device opens local setup including without Firebase configuration`() = runTest {
        for (session in listOf(AuthSession.Anonymous, AuthSession.Unavailable)) {
            val model = model(FakeAuth(session)) { false }
            model.onLaunchShown()
            runCurrent()
            assertEquals(listOf(AppRoute.BudgetSetup(BudgetSetupMode.Local)), model.backStack)
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

    @Test fun `guest opens local setup as the root route`() = runTest {
        val model = model(FakeAuth()) { false }
        model.onLaunchShown()
        runCurrent()
        assertEquals(listOf(AppRoute.BudgetSetup(BudgetSetupMode.Local)), model.backStack)
        model.back()
        assertFalse(model.configuringBudget)
        assertEquals(listOf(AppRoute.BudgetSetup(BudgetSetupMode.Local)), model.backStack)
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

    @Test fun `profile destinations are separate entries above Main and do not duplicate`() = runTest {
        val model = model(FakeAuth()) { true }
        model.onLaunchShown()
        runCurrent()
        model.openFamilyAccess()
        model.openFamilyAccess()
        model.openCloudSync()
        assertEquals(listOf(AppRoute.Main, AppRoute.FamilyAccess), model.backStack)
        model.back()
        model.openCloudSync()
        model.openCloudSync()
        assertEquals(listOf(AppRoute.Main, AppRoute.CloudSync), model.backStack)
        model.back()
        assertEquals(listOf(AppRoute.Main), model.backStack)
    }

    @Test fun `operation creation is a separate entry above Main and does not duplicate`() = runTest {
        val model = model(FakeAuth()) { true }
        model.onLaunchShown()
        runCurrent()
        model.openOperationCreate()
        model.openOperationCreate()
        assertEquals(listOf(AppRoute.Main, AppRoute.OperationCreate), model.backStack)
        model.back()
        assertEquals(listOf(AppRoute.Main), model.backStack)
    }

    @Test fun `profile setup stays open with a ready budget and only success selects dashboard`() = runTest {
        val ready = MutableStateFlow(true)
        val model = model(FakeAuth(), ready) { true }
        model.onLaunchShown()
        runCurrent()
        val stack = listOf(AppRoute.Main, AppRoute.BudgetSetup(BudgetSetupMode.Local))
        model.openBudgetCreation()
        model.openBudgetCreation()
        ready.value = false
        runCurrent()
        ready.value = true
        runCurrent()
        assertEquals(stack, model.backStack)
        assertFalse(model.exitSetup())
        assertEquals(listOf(AppRoute.Main), model.backStack)
        assertEquals(AppDestination.Dashboard, model.mainDestination)

        model.openBudgetCreation()
        model.finishSetup()
        model.back()
        assertEquals(listOf(AppRoute.Main), model.backStack)
        assertEquals(AppDestination.Dashboard, model.mainDestination)
    }

    @Test fun `profile setup keeps its origin after restored authentication`() = runTest {
        val auth = FakeAuth()
        val saved = SavedStateHandle()
        fun newNavigation() = AppNavigationViewModel(
            auth,
            LocalBudgetRestorer {
                true
            },
            ActiveBudgetReadiness { MutableStateFlow(true) },
            saved
        )
            .also { models.put("setup-${models.keys().size}", it) }

        val original = newNavigation()
        original.onLaunchShown()
        runCurrent()
        original.openBudgetCreation()
        original.authenticateForSetup(SetupAuthTarget.Create)
        assertEquals(AppRoute.Auth, original.backStack.last())

        models.clear()
        auth.signInWithGoogle("synthetic")
        val restored = newNavigation()
        restored.onLaunchShown()
        runCurrent()
        assertTrue(restored.setupFromProfile)
        assertEquals(AppRoute.Main, restored.backStack.first())
        assertTrue(restored.backStack.last().isBudgetSetup())
        assertFalse(restored.exitSetup())
        assertEquals(listOf(AppRoute.Main), restored.backStack)
        assertEquals(AppDestination.Dashboard, restored.mainDestination)
    }

    @Test fun `losing cloud access inside profile setup returns to the local form`() = runTest {
        val access = object : AppAccess {
            override val mode = MutableStateFlow(AppMode.InternalFull)
            override val checking = MutableStateFlow(false)
            override fun installationId() = "test"
            override suspend fun refresh(
                manual: Boolean
            ) = false
            override fun revoke() {
                mode.value = AppMode.OfflineFree
            }
        }
        val model = AppNavigationViewModel(
            FakeAuth(AuthSession.Authenticated(USER)),
            LocalBudgetRestorer { true },
            ActiveBudgetReadiness { MutableStateFlow(true) },
            access = access
        ).also { models.put("access", it) }
        model.onLaunchShown()
        runCurrent()
        model.openBudgetCreation()
        model.showSetupPage(SetupPage.Join)
        assertEquals(AppRoute.BudgetSetupJoin, model.backStack.last())

        access.revoke()
        model.closeRestrictedRoutes()
        assertEquals(listOf(AppRoute.Main, AppRoute.BudgetSetup(BudgetSetupMode.Local)), model.backStack)
        assertTrue(model.setupFromProfile)
        assertFalse(model.exitSetup())
        assertFalse(model.configuringBudget)
    }

    @Test fun `operation creation cannot bypass setup and closes when budget becomes unavailable`() = runTest {
        val ready = MutableStateFlow(false)
        val model = model(FakeAuth(), ready) { false }
        model.onLaunchShown()
        runCurrent()
        model.openOperationCreate()
        assertEquals(listOf(AppRoute.BudgetSetup(BudgetSetupMode.Local)), model.backStack)
        model.continueAsGuest()
        ready.value = true
        runCurrent()
        model.openOperationCreate()
        ready.value = false
        runCurrent()
        assertEquals(listOf(AppRoute.BudgetSetup(BudgetSetupMode.Local)), model.backStack)
    }

    @Test fun `guest can authenticate from each profile detail and back returns to it`() = runTest {
        val model = model(FakeAuth()) { true }
        model.onLaunchShown()
        runCurrent()
        model.openFamilyAccess()
        model.openAuthentication()
        assertEquals(listOf(AppRoute.Main, AppRoute.FamilyAccess, AppRoute.Auth), model.backStack)
        model.back()
        assertEquals(AppRoute.FamilyAccess, model.backStack.last())
        model.back()
        model.openCloudSync()
        model.openAuthentication()
        assertEquals(listOf(AppRoute.Main, AppRoute.CloudSync, AppRoute.Auth), model.backStack)
    }

    @Test fun `profile details cannot bypass budget setup`() = runTest {
        val model = model(FakeAuth()) { false }
        model.onLaunchShown()
        runCurrent()
        model.openFamilyAccess()
        model.openCloudSync()
        assertEquals(listOf(AppRoute.BudgetSetup(BudgetSetupMode.Local)), model.backStack)
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

    @Test fun `logout opens the local budget and clears account history`() = runTest {
        val auth = FakeAuth(AuthSession.Authenticated(USER))
        var reads = 0
        val model = model(auth) {
            reads++
            true
        }
        model.onLaunchShown()
        runCurrent()
        auth.signOut()
        runCurrent()
        model.back()
        assertEquals(listOf(AppRoute.Main), model.backStack)
        assertEquals(1, reads)
    }

    @Test fun `rotation does not restart session observation or local budget inspection`() = runTest {
        var reads = 0
        val model = model(FakeAuth()) {
            reads++
            true
        }
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
        assertEquals(1, model.backStack.size)
        ready.value = true
        runCurrent()
        model.finishSetup()
        model.back()
        assertEquals(listOf(AppRoute.Main), model.backStack)
    }

    @Test fun `authenticated user without a budget starts at local creation`() = runTest {
        val ready = MutableStateFlow(false)
        val model = model(FakeAuth(AuthSession.Authenticated(USER)), ready) { error("Unused") }
        model.onLaunchShown()
        runCurrent()
        assertEquals(listOf(AppRoute.BudgetSetup(BudgetSetupMode.Local)), model.backStack)
        ready.value = true
        runCurrent()
        model.finishSetup()
        assertEquals(listOf(AppRoute.Main), model.backStack)
    }

    @Test fun `account restoration without a budget leaves local setup open`() = runTest {
        val auth = FakeAuth()
        val ready = MutableStateFlow(false)
        val model = model(auth, ready) { false }
        model.onLaunchShown()
        runCurrent()
        auth.signInWithGoogle("synthetic")
        runCurrent()
        assertEquals(AppRoute.BudgetSetup(BudgetSetupMode.Local), model.backStack.last())
    }

    @Test fun `restored account waits for readiness before opening Main`() = runTest {
        val result = CompletableDeferred<Boolean>()
        val model = model(
            FakeAuth(AuthSession.Authenticated(USER)),
            source = ActiveBudgetReadiness {
                flow { emit(result.await()) }
            }
        ) { error("Unused") }
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
        model.finishSetup()
        assertEquals(listOf(AppRoute.Main), model.backStack)
    }

    @Test fun `late budget result for a previous account cannot navigate after logout`() = runTest {
        val auth = FakeAuth(AuthSession.Authenticated(USER))
        val oldResult = CompletableDeferred<Boolean>()
        val model = model(
            auth,
            source = ActiveBudgetReadiness {
                if (auth.session.value is AuthSession.Authenticated) {
                    flow {
                        val result = withContext(NonCancellable) { oldResult.await() }
                        emit(result)
                    }
                } else {
                    MutableStateFlow(false)
                }
            }
        ) { false }
        model.onLaunchShown()
        runCurrent()
        auth.signOut()
        runCurrent()
        oldResult.complete(true)
        runCurrent()
        assertEquals(listOf(AppRoute.BudgetSetup(BudgetSetupMode.Local)), model.backStack)
    }

    @Test fun `readiness failure shows launch error and retry restores budget`() = runTest {
        var attempts = 0
        val model = model(
            FakeAuth(AuthSession.Authenticated(USER)),
            source = ActiveBudgetReadiness {
                flow { if (++attempts == 1) error("Room unavailable") else emit(true) }
            }
        ) { error("Unused") }
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
        val model = model(
            auth,
            source = ActiveBudgetReadiness {
                flow {
                    if (auth.session.value is AuthSession.Authenticated && fail) error("Room unavailable")
                    emit(true)
                }
            }
        ) { true }
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

    @Test fun `bottom sheet is a single entry above Main and ignores stale dismiss callbacks`() = runTest {
        val model = model(FakeAuth()) { true }
        model.onLaunchShown()
        runCurrent()
        model.openOperationDetails("operation", "history", "budget")
        val first = model.backStack.last() as AppRoute.OperationDetails
        model.openOperationDetails("other", "history", "budget")
        assertEquals(listOf(AppRoute.Main, first), model.backStack)
        assertTrue(model.dismissOperationDetails(first))
        assertEquals(listOf(AppRoute.Main), model.backStack)
        model.openOperationDetails("operation", "history", "budget")
        val second = model.backStack.last() as AppRoute.OperationDetails
        assertNotEquals(first.requestId, second.requestId)
        assertFalse(model.dismissOperationDetails(first))
        assertEquals(listOf(AppRoute.Main, second), model.backStack)
        model.back()
        assertEquals(listOf(AppRoute.Main), model.backStack)
    }

    @Test fun `bottom sheet cannot bypass setup and closes when budget becomes unavailable`() = runTest {
        val ready = MutableStateFlow(false)
        val model = model(FakeAuth(), ready) { false }
        model.onLaunchShown()
        runCurrent()
        model.openOperationDetails("operation", "history", "budget")
        assertEquals(listOf(AppRoute.BudgetSetup(BudgetSetupMode.Local)), model.backStack)
        model.continueAsGuest()
        ready.value = true
        runCurrent()
        model.finishSetup()
        model.openOperationDetails("operation", "history", "budget")
        assertTrue(model.backStack.last() is AppRoute.OperationDetails)
        ready.value = false
        runCurrent()
        assertEquals(listOf(AppRoute.BudgetSetup(BudgetSetupMode.Local)), model.backStack)
    }

    @Test fun `logout removes the sheet and prevents a stale action reopening history`() = runTest {
        val auth = FakeAuth(AuthSession.Authenticated(USER))
        val model = model(auth) { true }
        model.onLaunchShown()
        runCurrent()
        model.openOperationDetails("operation", "history", "budget")
        val sheet = model.backStack.last() as AppRoute.OperationDetails
        auth.signOut()
        runCurrent()
        assertEquals(listOf(AppRoute.Main), model.backStack)
        assertFalse(model.dismissOperationDetails(sheet))
    }

    @Test fun `planned editor is a single overlay and stale dismissal cannot close a reopened editor`() = runTest {
        val model = model(FakeAuth()) { true }
        model.onLaunchShown()
        runCurrent()
        model.openPlannedOperationBottomSheet(PlannedOperationType.Expense, "budget")
        val first = model.backStack.last() as AppRoute.PlannedOperationBottomSheet
        model.openPlannedOperationBottomSheet(PlannedOperationType.Income, "budget")
        assertEquals(listOf(AppRoute.Main, first), model.backStack)
        model.dismissPlannedOperationBottomSheet(first)
        model.openPlannedOperationBottomSheet(PlannedOperationType.Income, "budget")
        val second = model.backStack.last() as AppRoute.PlannedOperationBottomSheet
        model.dismissPlannedOperationBottomSheet(first)
        assertEquals(listOf(AppRoute.Main, second), model.backStack)
        assertEquals(PlannedOperationType.Income, second.type)
        model.back()
        assertEquals(listOf(AppRoute.Main), model.backStack)
    }

    @Test fun `planned editor requires a ready budget and closes when access is lost`() = runTest {
        val ready = MutableStateFlow(false)
        val model = model(FakeAuth(), ready) { false }
        model.onLaunchShown()
        runCurrent()
        model.openPlannedOperationBottomSheet(PlannedOperationType.Income, "budget")
        assertEquals(listOf(AppRoute.BudgetSetup(BudgetSetupMode.Local)), model.backStack)
        model.continueAsGuest()
        ready.value = true
        runCurrent()
        model.finishSetup()
        model.openPlannedOperationBottomSheet(PlannedOperationType.Expense, "budget")
        assertTrue(model.backStack.last() is AppRoute.PlannedOperationBottomSheet)
        ready.value = false
        runCurrent()
        assertEquals(listOf(AppRoute.BudgetSetup(BudgetSetupMode.Local)), model.backStack)
    }

    @Test fun `saving closes creation before an offer is ready and ignores duplicate callbacks`() = runTest {
        val model = model(FakeAuth()) { true }
        model.onLaunchShown()
        runCurrent()
        model.openOperationCreate()
        val result = com.firkat.fillin.feature.operationhistory.domain.repository.CreateOperationResult("operation")
        model.operationCreated(result, "budget")
        val request = requireNotNull(model.pendingPlanOffer)
        assertEquals(listOf(AppRoute.Main), model.backStack)
        model.operationCreated(result, "budget")
        assertSame(request, model.pendingPlanOffer)
        model.finishPlanOffer(request, canShow = true)
        val sheet = model.backStack.last() as AppRoute.OperationPlanLinkBottomSheet
        assertEquals("operation", sheet.operationId)
        assertEquals("budget", sheet.budgetScopeId)
        assertNull(model.pendingPlanOffer)
        assertTrue(model.dismissOperationPlanLink(sheet))
        model.finishPlanOffer(request, canShow = true)
        assertEquals(listOf(AppRoute.Main), model.backStack)
    }

    @Test fun `a new navigation action or a lookup without a result prevents an offer`() = runTest {
        val model = model(FakeAuth()) { true }
        model.onLaunchShown()
        runCurrent()
        model.openOperationCreate()
        val result = com.firkat.fillin.feature.operationhistory.domain.repository.CreateOperationResult("operation")
        model.operationCreated(result, "budget")
        model.finishPlanOffer(requireNotNull(model.pendingPlanOffer), canShow = false)
        assertEquals(listOf(AppRoute.Main), model.backStack)
        model.openOperationCreate()
        model.operationCreated(result, "budget")
        val stale = requireNotNull(model.pendingPlanOffer)
        model.openOperationCreate()
        model.back()
        model.finishPlanOffer(stale, canShow = true)
        assertEquals(listOf(AppRoute.Main), model.backStack)
    }

    @Test fun `setup login returns to the draft even when the account has a ready budget`() = runTest {
        val auth = FakeAuth()
        val ready = MutableStateFlow(false)
        val model = model(auth, ready) { false }
        model.onLaunchShown()
        runCurrent()
        model.continueAsGuest()
        model.authenticateForSetup(com.firkat.fillin.feature.budgetsetup.ui.interactor.SetupAuthTarget.Create)
        auth.signInWithGoogle("token")
        ready.value = true
        runCurrent()
        assertEquals(AppRoute.BudgetSetup(BudgetSetupMode.Cloud), model.backStack.last())
        assertFalse(model.backStack.contains(AppRoute.Main))
        model.finishSetup()
        assertEquals(listOf(AppRoute.Main), model.backStack)
    }

    @Test fun `canceling setup login returns to the same setup route`() = runTest {
        val model = model(FakeAuth(), MutableStateFlow(false)) { false }
        model.onLaunchShown()
        runCurrent()
        model.continueAsGuest()
        model.authenticateForSetup(com.firkat.fillin.feature.budgetsetup.ui.interactor.SetupAuthTarget.Create)
        assertEquals(AppRoute.Auth, model.backStack.last())
        model.back()
        assertEquals(AppRoute.BudgetSetup(BudgetSetupMode.Local), model.backStack.last())
    }

    @Test fun `login to join resumes the invitation route`() = runTest {
        val auth = FakeAuth()
        val ready = MutableStateFlow(false)
        val model = model(auth, ready) { false }
        model.onLaunchShown()
        runCurrent()
        model.continueAsGuest()
        model.authenticateForSetup(com.firkat.fillin.feature.budgetsetup.ui.interactor.SetupAuthTarget.Join)
        auth.signInWithGoogle("token")
        runCurrent()
        assertEquals(AppRoute.BudgetSetupJoin, model.backStack.last())
    }

    @Test fun `pending setup login is restored before normal launch navigation`() = runTest {
        val saved = androidx.lifecycle.SavedStateHandle(mapOf("setup_active" to true, "setup_auth" to "Join"))
        val auth = FakeAuth(AuthSession.Authenticated(USER))
        val restored =
            AppNavigationViewModel(
                auth,
                LocalBudgetRestorer {
                    false
                },
                ActiveBudgetReadiness { MutableStateFlow(true) },
                saved
            )
        models.put("restored", restored)
        restored.onLaunchShown()
        runCurrent()
        assertEquals(AppRoute.BudgetSetupJoin, restored.backStack.last())
        assertFalse(restored.backStack.contains(AppRoute.Main))
    }

    private fun model(
        auth: AuthRepository,
        ready: MutableStateFlow<Boolean> = MutableStateFlow(true),
        source: ActiveBudgetReadiness = ActiveBudgetReadiness { ready },
        restore: suspend () -> Boolean
    ) = AppNavigationViewModel(auth, LocalBudgetRestorer { restore().also { ready.value = it } }, source).also {
        models.put("navigation-${models.keys().size}", it)
    }

    private class FakeAuth(initial: AuthSession = AuthSession.Anonymous) : AuthRepository {
        override val session = MutableStateFlow(initial)
        override suspend fun signInWithGoogle(
            idToken: String
        ) {
            session.value = AuthSession.Authenticated(USER)
        }
        override suspend fun signOut() {
            session.value = AuthSession.Anonymous
        }
    }

    private companion object {
        val USER = AuthUser("synthetic-uid", "User", null)
    }
}
