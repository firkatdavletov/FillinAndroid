package com.firkat.fillin.feature.profile

import androidx.lifecycle.ViewModelStore
import com.firkat.fillin.feature.auth.domain.*
import com.firkat.fillin.feature.profile.domain.*
import com.firkat.fillin.feature.profile.ui.ProfileViewModel
import java.math.BigDecimal
import java.time.LocalDate
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
class ProfileViewModelTest {
    private val store = ViewModelStore()

    @Before fun setup() = Dispatchers.setMain(StandardTestDispatcher())

    @After fun cleanup() {
        store.clear()
        Dispatchers.resetMain()
    }

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

    @Test fun `budget selection waits for the active budget and keeps the catalog on failure`() = runTest {
        val budgets = Budgets().apply { selectionResult = CompletableDeferred() }
        val original = budgets.rows.value
        val model = model(budgets)
        runCurrent()

        model.selectBudget("other")
        runCurrent()
        assertEquals(original, model.state.value.budgets)
        assertFalse(model.state.value.loading)
        budgets.selectionResult!!.completeExceptionally(IllegalStateException("Switch failed"))
        runCurrent()
        assertEquals(original, model.state.value.budgets)
        assertNotNull(model.state.value.error)
        assertFalse(model.state.value.busy)

        budgets.selectionResult = CompletableDeferred()
        model.selectBudget("other")
        runCurrent()
        val confirmed = original.map { it.copy(selected = it.id == "other") }
        budgets.rows.value = confirmed
        budgets.selectionResult!!.complete(Unit)
        runCurrent()
        assertEquals(confirmed, model.state.value.budgets)
        assertNull(model.state.value.error)
        assertFalse(model.state.value.busy)
    }

    @Test fun `budget actions toggle exclusively without selecting and close when removed`() = runTest {
        val budgets = Budgets()
        val model = model(budgets)
        runCurrent()
        val manageable = model.state.value.budgets.filter { it.canManage }
        val id = manageable.first().id
        model.toggleBudgetActions(id)
        assertEquals(id, model.state.value.expandedBudgetId)
        assertTrue(budgets.selections.isEmpty())
        model.toggleBudgetActions(id)
        assertNull(model.state.value.expandedBudgetId)
        model.toggleBudgetActions(id)
        manageable.lastOrNull()?.let { last ->
            model.toggleBudgetActions(last.id)
            assertEquals(if (last.id == id) null else last.id, model.state.value.expandedBudgetId)
        }
        model.toggleBudgetActions(id)
        budgets.rows.value = emptyList()
        runCurrent()
        assertNull(model.state.value.expandedBudgetId)
    }

    private fun model(
        budgets: Budgets = Budgets(),
        auth: Auth = Auth()
    ) = ProfileViewModel(budgets, auth).also { store.put("profile", it) }

    private class Auth(initial: AuthSession = AuthSession.Anonymous) : AuthRepository {
        override val session = MutableStateFlow(initial)
        override suspend fun signInWithGoogle(
            idToken: String
        ) = Unit
        override suspend fun signOut() {
            session.value = AuthSession.Anonymous
        }
    }

    private class Budgets : ProfileBudgetsRepository {
        val rows =
            MutableStateFlow(
                listOf(
                    ProfileBudget("active", "Мой бюджет", false, true),
                    ProfileBudget("other", "Другой", true, false)
                )
            )
        val selections = mutableListOf<String>()
        var refreshResult: CompletableDeferred<Unit>? = null
        var selectionResult: CompletableDeferred<Unit>? = null
        override fun observe() = rows
        override suspend fun refresh() {
            refreshResult?.await()
        }
        override suspend fun select(
            id: String
        ) {
            selections += id
            selectionResult?.await()
        }
        override suspend fun createLocal(
            balance: BigDecimal,
            startedOn: LocalDate
        ) = error("Unused")
        override suspend fun createLocal(
            name: String,
            balance: BigDecimal,
            startedOn: LocalDate
        ) = error("Unused")
        override suspend fun createLocal(
            name: String,
            balance: BigDecimal,
            startedOn: LocalDate,
            currency: com.firkat.fillin.feature.budgetprofile.domain.BudgetCurrency
        ) = error("Unused")
    }
}
