package com.firkat.fillin.feature.budgetsetup

import androidx.lifecycle.ViewModelStore
import com.firkat.fillin.feature.budgetsetup.domain.BudgetSetupRepository
import com.firkat.fillin.feature.budgetsetup.ui.BudgetSetupViewModel
import com.firkat.fillin.feature.budgetsetup.ui.interactor.BudgetSetupAction
import com.firkat.fillin.feature.budgetsetup.ui.interactor.BudgetSetupEffect
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.math.BigDecimal
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetSetupViewModelTest {
    private val models = ViewModelStore()
    private val repository = FakeSetup()
    @Before fun setup() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun cleanup() { models.clear(); Dispatchers.resetMain() }

    @Test fun `creation normalizes decimal comma and waits for committed readiness`() = runTest {
        val model = model()
        create(model, " 123,45 ", "2026-10-21")
        assertTrue(model.state.value.busy)
        runCurrent()
        assertEquals(BigDecimal("123.45"), repository.balance)
        assertEquals(LocalDate.of(2026, 10, 21), repository.date)
        assertEquals(false, model.state.value.ready)
        repository.commit.complete(Unit)
        runCurrent()
        assertEquals(true, model.state.value.ready)
        assertFalse(model.state.value.busy)
        assertNull(model.state.value.error)
    }

    @Test fun `repeated taps while saving and after success do not recreate budget`() = runTest {
        val model = model()
        create(model, "0", "2026-10-22")
        create(model, "100", "2026-10-22")
        runCurrent()
        assertEquals(1, repository.calls)
        repository.commit.complete(Unit)
        runCurrent()
        create(model, "200", "2026-10-22")
        runCurrent()
        assertEquals(1, repository.calls)
    }

    @Test fun `invalid amount and date remain on form with validation error`() = runTest {
        val model = model()
        for ((amount, date) in listOf("abc" to "2026-10-21", "1.234" to "2026-10-21", "1" to "wrong", "1" to "2026-10-23")) {
            create(model, amount, date)
            runCurrent()
            assertFalse(model.state.value.busy)
            assertEquals("Проверьте баланс и дату начала учёта.", model.state.value.error)
            assertEquals(0, repository.calls)
        }
        create(model, "0", "2026-10-22")
        assertNull(model.state.value.error)
        repository.commit.complete(Unit)
        runCurrent()
        assertEquals(true, model.state.value.ready)
    }

    @Test fun `storage error permits retry without publishing readiness`() = runTest {
        repository.fail = true
        val model = model()
        create(model, "0", "2026-10-22")
        runCurrent()
        assertFalse(model.state.value.busy)
        assertNotNull(model.state.value.error)
        assertEquals(false, model.state.value.ready)
        repository.fail = false
        repository.commit.complete(Unit)
        create(model, "0", "2026-10-22")
        runCurrent()
        assertEquals(true, model.state.value.ready)
    }

    @Test fun `back effects respect busy and readiness observation survives error and cancellation`() = runTest {
        val model = model()
        val effects = mutableListOf<BudgetSetupEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.effects.collect { effects += it } }
        runCurrent()
        assertEquals(1, repository.ready.subscriptionCount.value)
        model.onAction(BudgetSetupAction.Back); runCurrent()
        assertEquals(listOf(BudgetSetupEffect.Back), effects)
        repository.cancel = true
        create(model, "0", "2026-10-22")
        model.onAction(BudgetSetupAction.Back); runCurrent()
        assertEquals(1, effects.size)
        assertFalse(model.state.value.busy)
        assertNull(model.state.value.error)
        repository.ready.value = true; runCurrent()
        assertEquals(true, model.state.value.ready)
        repository.ready.value = false; runCurrent()
        repository.cancel = false; repository.fail = true
        create(model, "0", "2026-10-22"); runCurrent()
        assertNotNull(model.state.value.error)
        assertEquals(1, repository.ready.subscriptionCount.value)
        model.onAction(BudgetSetupAction.Back); runCurrent()
        assertEquals(2, effects.size)
        models.clear(); runCurrent()
        assertEquals(0, repository.ready.subscriptionCount.value)
    }

    private fun create(model: BudgetSetupViewModel, balance: String, date: String) {
        model.onAction(BudgetSetupAction.BalanceChanged(balance))
        model.onAction(BudgetSetupAction.DateChanged(date))
        model.onAction(BudgetSetupAction.Create)
    }

    private fun model() = BudgetSetupViewModel(repository).also { models.put("setup", it) }
    private class FakeSetup : BudgetSetupRepository {
        override val ready = MutableStateFlow(false)
        override val today = LocalDate.of(2026, 10, 22)
        val commit = CompletableDeferred<Unit>()
        var calls = 0
        var balance: BigDecimal? = null
        var date: LocalDate? = null
        var fail = false
        var cancel = false
        override suspend fun createLocal(openingBalance: BigDecimal, startedOn: LocalDate) {
            calls++
            if (cancel) throw CancellationException("cancelled")
            if (fail) error("Storage unavailable")
            balance = openingBalance
            date = startedOn
            commit.await()
            ready.value = true
        }
        override suspend fun connectExisting() = error("Unused")
    }
}
