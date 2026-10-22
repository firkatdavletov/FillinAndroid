package com.firkat.fillin.feature.budgetconnection

import androidx.lifecycle.ViewModelStore
import com.firkat.fillin.feature.auth.domain.*
import com.firkat.fillin.feature.budgetconnection.domain.*
import com.firkat.fillin.feature.budgetconnection.ui.BudgetConnectionViewModel
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
        model.refresh(); runCurrent()
        assertEquals(listOf(budget("owner")), model.state.value.budgets)
        assertNull(model.state.value.error)
        model.connect(budget("owner")); model.connect(budget("owner")); runCurrent()
        assertEquals(1, connects)
        finish.complete(Unit); runCurrent()
        assertNull(model.state.value.connecting)
    }

    @Test fun `onboarding opens the empty account form without creating a budget`() = runTest {
        val model = model(Auth(), repository(), autoStartCreation = true)
        runCurrent()
        assertTrue(model.state.value.loaded)
        assertEquals("0", model.state.value.draft?.openingBalance)
        model.editDraft(model.state.value.draft!!.copy(name = "Семья"))
        model.refresh(); runCurrent()
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
        model.editDraft(pending.draft.copy(name = "Другое"))
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
        model.refresh(); runCurrent()
        assertNotNull(model.state.value.draft)
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
