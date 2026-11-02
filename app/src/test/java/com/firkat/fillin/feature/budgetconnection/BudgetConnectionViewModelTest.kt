package com.firkat.fillin.feature.budgetconnection

import androidx.lifecycle.ViewModelStore
import com.firkat.fillin.feature.auth.domain.*
import com.firkat.fillin.feature.budgetconnection.domain.*
import com.firkat.fillin.feature.budgetconnection.ui.BudgetConnectionViewModel
import com.firkat.fillin.feature.budgetconnection.ui.interactor.BudgetConnectionAction
import com.firkat.fillin.feature.sync.domain.BudgetConnectionFailure
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetConnectionViewModelTest {
    private val models = ViewModelStore()
    @Before fun setup() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun cleanup() { models.clear(); Dispatchers.resetMain() }

    @Test fun `switching account discards the old pending list and logout hides budgets`() = runTest {
        val first = CompletableDeferred<List<AvailableBudget>>()
        val auth = Auth()
        val repository = object : BudgetConnectionRepository {
            override suspend fun budgets(uid: String) = if (uid == "owner") first.await() else listOf(budget("other"))
            override suspend fun connect(uid: String, budget: AvailableBudget) = Unit
        }
        val model = model(auth, repository)
        runCurrent()
        auth.session.value = AuthSession.Authenticated(AuthUser("other", null, null))
        runCurrent()
        first.complete(listOf(budget("owner")))
        runCurrent()
        assertEquals(listOf(budget("other")), model.state.value.budgets)
        auth.signOut(); runCurrent()
        assertTrue(model.state.value.budgets.isEmpty())
        assertFalse(model.state.value.loading)
    }

    @Test fun `server error can be retried and repeated connect taps do not start another exchange`() = runTest {
        var reads = 0
        var connects = 0
        val finish = CompletableDeferred<Unit>()
        val model = model(Auth(), object : BudgetConnectionRepository {
            override suspend fun budgets(uid: String): List<AvailableBudget> {
                if (++reads == 1) throw BudgetConnectionFailure("network")
                return listOf(budget(uid))
            }
            override suspend fun connect(uid: String, budget: AvailableBudget) { connects++; finish.await() }
        })
        runCurrent()
        assertNotNull(model.state.value.error)
        model.onAction(BudgetConnectionAction.Refresh); runCurrent()
        assertEquals(listOf(budget("owner")), model.state.value.budgets)
        assertNull(model.state.value.error)
        model.onAction(BudgetConnectionAction.Connect(budget("owner"))); model.onAction(BudgetConnectionAction.Connect(budget("owner"))); runCurrent()
        assertEquals(1, connects)
        finish.complete(Unit); runCurrent()
        assertNull(model.state.value.connecting)
    }

    @Test fun `onboarding opens the empty account form without creating a budget`() = runTest {
        val model = model(Auth(), repository(), autoStartCreation = true)
        runCurrent()
        assertTrue(model.state.value.loaded)
        assertEquals("0", model.state.value.draft?.openingBalance)
        model.onAction(BudgetConnectionAction.EditDraft(model.state.value.draft!!.copy(name = "Семья")))
        model.onAction(BudgetConnectionAction.Refresh); runCurrent()
        assertEquals("Семья", model.state.value.draft?.name)
    }

    @Test fun `ordinary cloud section keeps the empty account choice`() = runTest {
        val model = model(Auth(), repository())
        runCurrent()
        assertTrue(model.state.value.loaded)
        assertNull(model.state.value.draft)
    }

    @Test fun `onboarding keeps existing budgets available for explicit selection`() = runTest {
        val model = model(Auth(), repository(listOf(budget("owner"))), autoStartCreation = true)
        runCurrent()
        assertEquals(listOf(budget("owner")), model.state.value.budgets)
        assertNull(model.state.value.draft)
    }

    @Test fun `pending creation takes precedence over a new onboarding draft`() = runTest {
        val pending = PendingBudgetCreation("saved-request", BudgetCreationDraft("Сохранённый", "25.00", "2026-10-20"))
        val model = model(Auth(), repository(listOf(budget("owner"))), autoStartCreation = true, pending = pending)
        runCurrent()
        assertEquals(pending, model.state.value.pending)
        assertEquals(pending.draft, model.state.value.draft)
        model.onAction(BudgetConnectionAction.EditDraft(pending.draft.copy(name = "Другое")))
        assertEquals(pending.draft, model.state.value.draft)
    }

    @Test fun `failed list load does not open creation until a successful retry`() = runTest {
        var fail = true
        val model = model(Auth(), object : BudgetConnectionRepository {
            override suspend fun budgets(uid: String): List<AvailableBudget> {
                if (fail) throw BudgetConnectionFailure("network")
                return emptyList()
            }
            override suspend fun connect(uid: String, budget: AvailableBudget) = error("Must not connect automatically")
        }, autoStartCreation = true)
        runCurrent()
        assertNotNull(model.state.value.error)
        assertNull(model.state.value.draft)
        fail = false
        model.onAction(BudgetConnectionAction.Refresh); runCurrent()
        assertNotNull(model.state.value.draft)
    }

    @Test fun `additional creation remains available when the account already has budgets`() = runTest {
        val model = model(Auth(), repository(listOf(budget("owner"))))
        runCurrent()
        model.onAction(BudgetConnectionAction.ChooseCreate())
        assertNotNull(model.state.value.draft)
        model.onAction(BudgetConnectionAction.CancelCreate)
        assertEquals(listOf(budget("owner")), model.state.value.budgets)
        assertNull(model.state.value.draft)
    }

    @Test fun `offline list keeps names roles and active selection but hides them on account change`() = runTest {
        val auth = Auth()
        val selected = MutableStateFlow<String?>("budget-owner")
        var chosen: AvailableBudget? = null
        val model = model(auth, object : BudgetConnectionRepository {
            override val activeBudgetId = selected
            override suspend fun cachedBudgets(uid: String) = if (uid == "owner") listOf(budget(uid)) else emptyList()
            override suspend fun budgets(uid: String): List<AvailableBudget> = throw BudgetConnectionFailure("network")
            override suspend fun connect(uid: String, budget: AvailableBudget) { chosen = budget }
        })
        runCurrent()
        assertTrue(model.state.value.showingSaved)
        assertEquals("budget-owner", model.state.value.activeBudgetId)
        assertEquals(listOf(budget("owner")), model.state.value.budgets)
        model.onAction(BudgetConnectionAction.Connect(budget("owner"))); runCurrent()
        assertEquals(budget("owner"), chosen)
        auth.session.value = AuthSession.Authenticated(AuthUser("other", null, null)); runCurrent()
        assertTrue(model.state.value.budgets.isEmpty())
        assertFalse(model.state.value.showingSaved)
    }

    @Test fun `additional pending creation cannot be replaced by the create action`() = runTest {
        val pending = PendingBudgetCreation("saved", BudgetCreationDraft("Дополнительный", "12.00", "2026-10-20"))
        val model = model(Auth(), repository(listOf(budget("owner"))), pending = pending)
        runCurrent()
        model.onAction(BudgetConnectionAction.ChooseCreate())
        assertEquals(pending.draft, model.state.value.draft)
        assertEquals(pending, model.state.value.pending)
    }

    private fun repository(budgets: List<AvailableBudget> = emptyList()) = object : BudgetConnectionRepository {
        override suspend fun budgets(uid: String) = budgets
        override suspend fun connect(uid: String, budget: AvailableBudget) = error("Must not connect automatically")
    }

    private fun model(
        auth: AuthRepository,
        repository: BudgetConnectionRepository,
        autoStartCreation: Boolean = false,
        pending: PendingBudgetCreation? = null,
    ) = BudgetConnectionViewModel(auth, repository, object : BudgetCreationRepository {
        override suspend fun pending(uid: String): PendingBudgetCreation? = pending
        override suspend fun create(uid: String, draft: BudgetCreationDraft) = error("Unused")
        override suspend fun resume(uid: String) = error("Unused")
    }, autoStartCreation).also { models.put("connection", it) }
    private fun budget(uid: String) = AvailableBudget("budget-$uid", "Бюджет", "member-$uid", "OWNER")
    private class Auth : AuthRepository {
        override val session = MutableStateFlow<AuthSession>(AuthSession.Authenticated(AuthUser("owner", null, null)))
        override suspend fun signOut() { session.value = AuthSession.Anonymous }
        override suspend fun signInWithGoogle(idToken: String) = Unit
    }
}
