package com.firkat.fillin.feature.dashboard.ui

import androidx.lifecycle.ViewModelStore
import com.firkat.fillin.feature.dashboard.domain.calculation.CalculateDashboard
import com.firkat.fillin.feature.dashboard.domain.calculation.DashboardCalculationInput
import com.firkat.fillin.feature.dashboard.domain.repository.DashboardRepository
import com.firkat.fillin.feature.dashboard.domain.usecase.ObserveDashboard
import com.firkat.fillin.feature.sync.domain.BudgetSyncContext
import com.firkat.fillin.feature.sync.domain.BudgetSyncControl
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    private val models = ViewModelStore()

    @Before fun setup() = Dispatchers.setMain(StandardTestDispatcher())

    @After fun cleanup() {
        models.clear()
        Dispatchers.resetMain()
    }

    @Test fun `local emissions update screen and refresh keeps cached data through network failure`() = runTest {
        val repository = FakeRepository()
        val sync = FakeSync()
        val model = model(repository, sync)
        runCurrent()
        assertTrue(model.uiState.value.awaitingInitialSync)
        assertNull(model.uiState.value.dashboard)
        sync.context.value = BudgetSyncContext(ready = true)
        repository.data.value = EMPTY
        runCurrent()
        repository.data.value = EMPTY.copy(availableMoney = EMPTY.availableMoney.copy(amount = BigDecimal("25.00")))
        runCurrent()
        assertEquals(BigDecimal("25.00"), model.uiState.value.dashboard!!.availableMoney.amount)
        model.onEvent(DashboardEvent.Refresh)
        sync.context.value = BudgetSyncContext(ready = true, error = "network")
        runCurrent()
        assertEquals(1, repository.requests)
        assertNotNull(model.uiState.value.dashboard)
        assertNull(model.uiState.value.error)
        assertFalse(model.uiState.value.isRefreshing)
        model.onEvent(DashboardEvent.Resume)
        runCurrent()
        assertEquals(1, repository.requests)
    }

    @Test fun `revoked access clears numbers and blocks cached replay until restored`() = runTest {
        val repository = FakeRepository()
        val sync = FakeSync()
        sync.context.value = BudgetSyncContext(ready = true)
        repository.data.value = EMPTY
        val model = model(repository, sync)
        runCurrent()
        assertNotNull(model.uiState.value.dashboard)
        sync.context.value = BudgetSyncContext(ready = true, error = "scope_changed")
        runCurrent()
        model.onEvent(DashboardEvent.Resume)
        runCurrent()
        assertNull(model.uiState.value.dashboard)
        assertEquals(DashboardError.Forbidden, model.uiState.value.error)
        sync.context.value = BudgetSyncContext(ready = true)
        model.onEvent(DashboardEvent.Retry)
        runCurrent()
        assertNotNull(model.uiState.value.dashboard)
        assertNull(model.uiState.value.error)
    }

    private fun model(repository: FakeRepository, sync: FakeSync) =
        DashboardViewModel(ObserveDashboard(repository), sync).also { models.put("dashboard", it) }

    private class FakeRepository : DashboardRepository {
        val data = MutableStateFlow<com.firkat.fillin.feature.dashboard.domain.model.Dashboard?>(null)
        var requests = 0
        override fun observeDashboard() = data.filterNotNull()
        override fun requestSync() {
            requests++
        }
    }
    private class FakeSync : BudgetSyncControl {
        override val context = MutableStateFlow(BudgetSyncContext())
        override fun requestSync() = Unit
        override suspend fun resolve(id: String, reapply: Boolean) = Unit
    }
    companion object {
        private val EMPTY = CalculateDashboard.calculate(
            DashboardCalculationInput(
                Instant.parse("2026-10-19T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, emptyList(), emptyList(), emptyList(), emptyList(), emptySet()
            )
        )
    }
}
