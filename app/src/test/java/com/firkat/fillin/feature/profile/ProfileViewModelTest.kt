package com.firkat.fillin.feature.profile

import androidx.lifecycle.ViewModelStore
import com.firkat.fillin.feature.auth.domain.*
import com.firkat.fillin.feature.budgetconnection.domain.*
import com.firkat.fillin.feature.profile.domain.*
import com.firkat.fillin.feature.profile.ui.ProfileViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    private val store = ViewModelStore()
    @Before fun setup() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    @Test fun `cached budgets remain usable while the remote catalog loads`() = runTest {
        val budgets = Budgets().apply { refreshResult = CompletableDeferred() }
        val model = model(budgets)
        runCurrent()
        assertEquals(budgets.rows.value, model.state.value.budgets)
        assertFalse(model.state.value.busy)
        assertFalse(model.state.value.loading)
    }

    @Test fun `selected budgets and repeated clicks cannot start another switch`() = runTest {
        val budgets = Budgets().apply { selectionResult = CompletableDeferred() }
        val model = model(budgets)
        runCurrent()
        model.selectBudget("active")
        model.selectBudget("other")
        model.selectBudget("other")
        runCurrent()
        assertEquals(listOf("other"), budgets.selections)
        assertTrue(model.state.value.busy)
    }

    @Test fun `guest creates a local budget using the entered balance and date`() = runTest {
        val budgets = Budgets()
        val creation = Creation()
        val model = model(budgets, creation = creation)
        runCurrent()
        model.openCreation()
        runCurrent()
        model.editCreation(model.state.value.creation!!.copy(openingBalance = "1500,50", accountingStartedOn = "2026-10-01"))
        model.createLocal()
        runCurrent()
        assertEquals(BigDecimal("1500.50") to LocalDate.of(2026, 10, 1), budgets.local)
        assertNull(model.state.value.creation)
        assertFalse(creation.created)
    }

    @Test fun `pending cloud creation restores the immutable draft and resumes it`() = runTest {
        val saved = BudgetCreationDraft("Семейный", "50.00", "2026-10-01")
        val creation = Creation().apply { pending = PendingBudgetCreation("request", saved) }
        val model = model(auth = Auth(AuthSession.Authenticated(AuthUser("uid", null, null))), creation = creation)
        runCurrent()
        model.openCreation()
        runCurrent()
        assertTrue(model.state.value.pendingCreation)
        model.editCreation(saved.copy(name = "Другой"))
        assertEquals(saved, model.state.value.creation)
        model.createCloud()
        runCurrent()
        assertTrue(creation.resumed)
        assertFalse(creation.created)
        assertNull(model.state.value.creation)
    }

    private fun model(budgets: Budgets = Budgets(), auth: Auth = Auth(), creation: Creation = Creation()) =
        ProfileViewModel(budgets, auth, creation).also { store.put("profile", it) }

    private class Auth(initial: AuthSession = AuthSession.Anonymous) : AuthRepository {
        override val session = MutableStateFlow(initial)
        override suspend fun signInWithGoogle(idToken: String) = Unit
        override suspend fun signOut() { session.value = AuthSession.Anonymous }
    }

    private class Budgets : ProfileBudgetsRepository {
        val rows = MutableStateFlow(listOf(ProfileBudget("active", "Мой бюджет", false, true), ProfileBudget("other", "Другой", true, false)))
        val selections = mutableListOf<String>()
        var local: Pair<BigDecimal, LocalDate>? = null
        var refreshResult: CompletableDeferred<Unit>? = null
        var selectionResult: CompletableDeferred<Unit>? = null
        override fun observe() = rows
        override suspend fun refresh() { refreshResult?.await() }
        override suspend fun select(id: String) { selections += id; selectionResult?.await() }
        override suspend fun createLocal(balance: BigDecimal, startedOn: LocalDate) { local = balance to startedOn }
    }

    private class Creation : BudgetCreationRepository {
        var pending: PendingBudgetCreation? = null
        var created = false
        var resumed = false
        override suspend fun pending(uid: String) = pending
        override suspend fun create(uid: String, draft: BudgetCreationDraft) { created = true }
        override suspend fun resume(uid: String) { resumed = true }
    }
}
