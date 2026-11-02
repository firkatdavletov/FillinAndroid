package com.firkat.fillin.feature.budgetsetup

import com.firkat.fillin.feature.budgetsetup.domain.*
import com.firkat.fillin.feature.budgetsetup.ui.interactor.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetSetupInteractorTest {
    @Test fun `parsing boundaries validation and storage failures use existing rules`() = runTest {
        val repository = Repository()
        val interactor = BudgetSetupInteractor(repository, CreateLocalBudget(repository), StandardTestDispatcher(testScheduler))
        for (amount in listOf(" 123,45 ", "99999999999999999.99", "-99999999999999999.99", "0")) {
            val result = interactor.execute(BudgetSetupCommand.Create(1, amount, "0001-01-01")).single() as BudgetSetupEvent.Created
            assertNull(result.error)
            assertEquals(amount.trim().replace(',', '.').toBigDecimal(), repository.balance)
            assertEquals(LocalDate.of(1, 1, 1), repository.date)
        }
        val calls = repository.calls
        for ((amount, date) in listOf("bad" to "2026-10-22", "1.234" to "2026-10-22", "100000000000000000" to "2026-10-22", "0" to "0000-01-01", "0" to "2026-10-23", "0" to "bad")) {
            val result = interactor.execute(BudgetSetupCommand.Create(2, amount, date)).single() as BudgetSetupEvent.Created
            assertEquals("Проверьте баланс и дату начала учёта.", result.error)
        }
        assertEquals(calls, repository.calls)
        repository.failure = IllegalStateException("storage")
        assertNotNull((interactor.execute(BudgetSetupCommand.Create(3, "0", "2026-10-22")).single() as BudgetSetupEvent.Created).error)
        repository.failure = CancellationException("cancelled")
        try {
            interactor.execute(BudgetSetupCommand.Create(4, "0", "2026-10-22")).toList()
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }
    @Test fun `readiness is a long observation with several updates`() = runTest {
        val repository = Repository()
        val events = mutableListOf<BudgetSetupEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            BudgetSetupInteractor(repository, CreateLocalBudget(repository), UnconfinedTestDispatcher(testScheduler))
                .execute(BudgetSetupCommand.ObserveReadiness).collect { events += it }
        }
        repository.ready.value = true
        repository.ready.value = false
        assertEquals(listOf(false, true, false), events.map { (it as BudgetSetupEvent.ReadinessChanged).ready })
        job.cancel()
        assertEquals(0, repository.ready.subscriptionCount.value)
    }
    private class Repository : BudgetSetupRepository {
        override val today = LocalDate.of(2026, 10, 22)
        override val ready = MutableStateFlow(false)
        var calls = 0
        var balance: BigDecimal? = null
        var date: LocalDate? = null
        var failure: Exception? = null
        override suspend fun createLocal(openingBalance: BigDecimal, startedOn: LocalDate) {
            calls++
            failure?.let { throw it }
            balance = openingBalance; date = startedOn
        }
        override suspend fun connectExisting() = error("Unused")
    }
}
