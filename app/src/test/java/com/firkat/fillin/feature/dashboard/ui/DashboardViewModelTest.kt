package com.firkat.fillin.feature.dashboard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.firkat.fillin.feature.dashboard.domain.DashboardDataSource
import com.firkat.fillin.feature.dashboard.domain.calculation.CalculateDashboard
import com.firkat.fillin.feature.dashboard.domain.calculation.DashboardCalculationInput
import com.firkat.fillin.feature.dashboard.domain.model.*
import com.firkat.fillin.feature.dashboard.domain.repository.DashboardRepository
import com.firkat.fillin.feature.dashboard.widgets.availabletospend.domain.ObserveAvailableToSpend
import com.firkat.fillin.feature.dashboard.widgets.availabletospend.ui.AvailableToSpendViewModel
import com.firkat.fillin.feature.dashboard.widgets.balanceforecast.domain.ObserveBalanceForecast
import com.firkat.fillin.feature.dashboard.widgets.balanceforecast.ui.BalanceForecastViewModel
import com.firkat.fillin.feature.dashboard.widgets.dailylimit.domain.ObserveDailyLimit
import com.firkat.fillin.feature.dashboard.widgets.dailylimit.ui.DailyLimitViewModel
import com.firkat.fillin.feature.dashboard.widgets.monthlybudget.domain.ObserveMonthlyBudget
import com.firkat.fillin.feature.dashboard.widgets.monthlybudget.ui.MonthlyBudgetUiState
import com.firkat.fillin.feature.dashboard.widgets.monthlybudget.ui.MonthlyBudgetViewModel
import com.firkat.fillin.feature.dashboard.widgets.totalbalance.domain.ObserveTotalBalance
import com.firkat.fillin.feature.dashboard.widgets.totalbalance.ui.TotalBalanceViewModel
import com.firkat.fillin.feature.sync.domain.BudgetSyncContext
import com.firkat.fillin.feature.sync.domain.BudgetSyncControl
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    private val models = ViewModelStore()
    @Before fun setup() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun cleanup() { models.clear(); Dispatchers.resetMain() }

    private fun <T : ViewModel> keep(key: String, model: T) = model.also { models.put(key, it) }
    private fun TestScope.watch(flow: StateFlow<*>) = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect {} }

    @Test fun `all five widget models share one observation and receive the same updated snapshot`() = runTest {
        val repository = FakeRepository()
        val sync = FakeSync()
        val source = DashboardDataSource(repository, sync, backgroundScope)
        val host = keep("host", DashboardViewModel(source))
        val balance = keep("balance", TotalBalanceViewModel(ObserveTotalBalance(source)))
        val available = keep("available", AvailableToSpendViewModel(ObserveAvailableToSpend(source)))
        val daily = keep("daily", DailyLimitViewModel(ObserveDailyLimit(source)))
        val forecast = keep("forecast", BalanceForecastViewModel(ObserveBalanceForecast(source)))
        val month = keep("month", MonthlyBudgetViewModel(ObserveMonthlyBudget(source)))
        listOf(host.uiState, balance.uiState, available.uiState, daily.uiState, forecast.uiState, month.uiState).forEach { watch(it) }
        runCurrent()
        assertEquals(DashboardStatus.WaitingForBudget, host.uiState.value.status)
        assertNull(balance.uiState.value.amount)
        assertEquals(0, repository.subscriptions)
        sync.context.value = BudgetSyncContext(ready = true)
        repository.data.value = EMPTY
        runCurrent()
        assertEquals(1, repository.subscriptions)
        val updated = EMPTY.copy(
            availableMoney = money("25"),
            freeMoney = EMPTY.freeMoney.copy(amount = money("-7"), safeDailyLimit = money("0")),
            forecast = EMPTY.forecast.copy(base = EMPTY.forecast.base.copy(endingBalance = money("-50"))),
            monthBudget = MonthBudget(money("100"), money("130"), money("-30")),
        )
        repository.data.value = updated
        runCurrent()
        assertEquals(DashboardStatus.Ready, host.uiState.value.status)
        assertEquals(updated.date, host.uiState.value.date)
        assertEquals(money("25"), balance.uiState.value.amount)
        assertEquals(money("-7"), available.uiState.value.amount)
        assertEquals(money("0"), daily.uiState.value.amount)
        assertEquals(money("-50"), forecast.uiState.value.amount)
        assertEquals(updated.monthBudget, month.uiState.value.budget)
        assertEquals(1, repository.subscriptions)
        host.onEvent(DashboardEvent.Refresh)
        sync.context.value = BudgetSyncContext(ready = true, error = "network", syncing = true)
        runCurrent()
        assertTrue(host.uiState.value.isRefreshing)
        assertEquals(money("25"), balance.uiState.value.amount)
        sync.context.value = sync.context.value.copy(syncing = false)
        runCurrent()
        assertEquals(1, repository.requests)
        assertEquals(2, repository.subscriptions)
        assertFalse(host.uiState.value.isRefreshing)
        host.onEvent(DashboardEvent.Resume)
        runCurrent()
        assertEquals(1, repository.requests)
        assertEquals(3, repository.subscriptions)
    }

    @Test fun `revoked access clears every value and prevents stale replay until restored`() = runTest {
        val repository = FakeRepository().also { it.data.value = EMPTY }
        val sync = FakeSync().also { it.context.value = BudgetSyncContext(ready = true) }
        val source = DashboardDataSource(repository, sync, backgroundScope)
        val balance = keep("balance", TotalBalanceViewModel(ObserveTotalBalance(source)))
        val available = keep("available", AvailableToSpendViewModel(ObserveAvailableToSpend(source)))
        val daily = keep("daily", DailyLimitViewModel(ObserveDailyLimit(source)))
        val forecast = keep("forecast", BalanceForecastViewModel(ObserveBalanceForecast(source)))
        val month = keep("month", MonthlyBudgetViewModel(ObserveMonthlyBudget(source)))
        listOf(balance.uiState, available.uiState, daily.uiState, forecast.uiState, month.uiState).forEach { watch(it) }
        runCurrent()
        assertNotNull(balance.uiState.value.amount)
        sync.context.value = BudgetSyncContext(ready = true, error = "scope_changed")
        runCurrent()
        source.resume()
        runCurrent()
        assertEquals(DashboardStatus.Forbidden, source.state.value.status)
        assertNull(balance.uiState.value.amount)
        assertNull(available.uiState.value.amount)
        assertNull(daily.uiState.value.amount)
        assertNull(forecast.uiState.value.amount)
        assertNull(month.uiState.value.budget)
        assertEquals(1, repository.subscriptions)
        sync.context.value = BudgetSyncContext(ready = true)
        runCurrent()
        assertNotNull(balance.uiState.value.amount)
        assertEquals(DashboardStatus.Ready, source.state.value.status)
    }

    @Test fun `v4 retains local access on cloud authorization errors`() = runTest {
        val repository = FakeRepository().also { it.data.value = EMPTY }
        val sync = FakeSync().also { it.context.value = BudgetSyncContext(ready = true) }
        val source = DashboardDataSource(repository, sync, backgroundScope)
        watch(source.state)
        runCurrent()
        for (error in listOf("unauthorized", "access_denied", "scope_changed")) {
            sync.context.value = BudgetSyncContext(ready = true, keepLocalAccess = true, error = error)
            runCurrent()
            assertEquals(EMPTY, source.state.value.dashboard)
            assertEquals(DashboardStatus.Ready, source.state.value.status)
        }
        assertEquals(1, repository.subscriptions)
    }

    @Test fun `uninitialized and failed local data never turn into zero balances and retry recovers`() = runTest {
        val repository = FakeRepository().also { it.fail = true }
        val sync = FakeSync()
        val source = DashboardDataSource(repository, sync, backgroundScope)
        watch(source.state)
        runCurrent()
        assertNull(source.state.value.dashboard)
        sync.context.value = BudgetSyncContext(ready = true)
        runCurrent()
        assertEquals(DashboardStatus.LocalError, source.state.value.status)
        assertNull(source.state.value.dashboard)
        repository.fail = false
        repository.data.value = EMPTY
        source.refresh()
        runCurrent()
        assertEquals(EMPTY, source.state.value.dashboard)
        sync.context.value = BudgetSyncContext(ready = false)
        runCurrent()
        assertNull(source.state.value.dashboard)
        assertEquals(DashboardStatus.WaitingForBudget, source.state.value.status)
    }

    @Test fun `forecast hides its amount until fourteen completed days are available`() = runTest {
        val repository = FakeRepository().also { it.data.value = EMPTY.copy(forecast = EMPTY.forecast.copy(estimateSource = "UNAVAILABLE")) }
        val sync = FakeSync().also { it.context.value = BudgetSyncContext(ready = true) }
        val source = DashboardDataSource(repository, sync, backgroundScope)
        val model = keep("forecast", BalanceForecastViewModel(ObserveBalanceForecast(source)))
        watch(model.uiState)
        runCurrent()
        assertNull(model.uiState.value.amount)
        assertTrue(model.uiState.value.insufficientHistory)
        repository.data.value = EMPTY
        runCurrent()
        assertEquals(EMPTY.forecast.base.endingBalance, model.uiState.value.amount)
        assertFalse(model.uiState.value.insufficientHistory)
    }

    @Test fun `month progress has no percentage for zero plan and clamps refunds and overspending`() {
        assertNull(MonthlyBudgetUiState(MonthBudget(money("0"), money("5"), money("-5"))).usedPercent)
        val over = MonthlyBudgetUiState(MonthBudget(money("10"), money("15"), money("-5")))
        assertEquals(BigDecimal("150.0"), over.usedPercent)
        assertEquals(1f, over.progress)
        assertEquals(0f, MonthlyBudgetUiState(MonthBudget(money("10"), money("-5"), money("15"))).progress)
    }

    private class FakeRepository : DashboardRepository {
        val data = MutableStateFlow<Dashboard?>(null)
        var requests = 0
        var subscriptions = 0
        var fail = false
        override fun observeDashboard() = flow {
            subscriptions++
            if (fail) error("local read failed")
            emitAll(data.filterNotNull())
        }
        override fun requestSync() { requests++ }
    }
    private class FakeSync : BudgetSyncControl {
        override val context = MutableStateFlow(BudgetSyncContext())
        override fun requestSync() = Unit
        override suspend fun resolve(id: String, reapply: Boolean) = Unit
    }
    companion object {
        private fun money(value: String) = Money(value.toBigDecimal().setScale(2), "KZT")
        private val EMPTY = CalculateDashboard.calculate(DashboardCalculationInput(
            Instant.parse("2026-10-19T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"),
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, emptyList(), emptyList(), emptyList(), emptyList(), emptySet(),
        ))
    }
}
