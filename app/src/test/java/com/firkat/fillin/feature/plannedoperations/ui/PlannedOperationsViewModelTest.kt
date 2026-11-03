package com.firkat.fillin.feature.plannedoperations.ui

import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationPeriod
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationTotals
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationType
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperations
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationsFilter
import com.firkat.fillin.feature.plannedoperations.domain.repository.PlannedOperationsException
import com.firkat.fillin.feature.plannedoperations.domain.repository.PlannedOperationsRepository
import com.firkat.fillin.feature.plannedoperations.domain.usecase.GetPlannedOperations
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlannedOperationsViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `risk filter is atomic repeatable and isolated to its budget model`() = runTest {
        val repository = FakePlansRepository()
        val model = model(repository)
        runCurrent()
        val from = LocalDate.of(2026, 10, 18)
        val filter = PlannedOperationsFilter(PlannedOperationType.Expense, PlannedOperationPeriod.Custom,
            from, LocalDate.of(2026, 12, 1))
        model.onEvent(PlannedOperationsEvent.FilterApplied(filter))
        runCurrent()
        assertEquals(filter, repository.requests.last())
        model.onEvent(PlannedOperationsEvent.TypeSelected(PlannedOperationType.Income))
        runCurrent()
        model.onEvent(PlannedOperationsEvent.FilterApplied(filter.copy(dateTo = from)))
        runCurrent()
        assertEquals(filter.copy(dateTo = from), model.uiState.value.filters)
        val otherBudget = model(FakePlansRepository())
        runCurrent()
        assertNull(otherBudget.uiState.value.filters.type)
        assertEquals(PlannedOperationPeriod.CurrentMonth, otherBudget.uiState.value.filters.period)
    }

    @Test
    fun `defaults to both types for the whole current month using the Almaty date`() = runTest {
        val repository = FakePlansRepository()
        val model = model(repository)
        runCurrent()
        assertEquals(LocalDate.of(2026, 10, 18), model.uiState.value.today)
        val expected = PlannedOperationsFilter(period = PlannedOperationPeriod.CurrentMonth, dateFrom = FROM, dateTo = TO)
        assertEquals(expected, model.uiState.value.filters)
        assertEquals(listOf(expected), repository.requests)
        assertFalse(model.uiState.value.isLoading)
        assertEquals(EMPTY.totals, model.uiState.value.periodTotals)
    }

    @Test
    fun `type year and custom ranges stay combined through refresh retry and mutation reload`() = runTest {
        val repository = FakePlansRepository()
        val model = model(repository)
        runCurrent()
        model.onEvent(PlannedOperationsEvent.TypeSelected(PlannedOperationType.Income))
        runCurrent()
        model.onEvent(PlannedOperationsEvent.PeriodSelected(PlannedOperationPeriod.EndOfYear))
        runCurrent()
        assertEquals(PlannedOperationPeriod.EndOfYear, repository.requests.last().period)
        model.onEvent(PlannedOperationsEvent.CustomPeriodApplied(FROM, TO))
        runCurrent()
        val expected = PlannedOperationsFilter(PlannedOperationType.Income, PlannedOperationPeriod.Custom, FROM, TO)
        assertEquals(expected, repository.requests.last())
        repository.load = { throw PlannedOperationsException.Network }
        model.onEvent(PlannedOperationsEvent.Refresh)
        runCurrent()
        assertEquals(PlannedOperationsException.Network, model.uiState.value.error)
        assertFalse(model.uiState.value.isRefreshing)
        repository.load = { EMPTY }
        model.onEvent(PlannedOperationsEvent.Retry)
        runCurrent()
        assertNull(model.uiState.value.error)
        assertEquals(expected, repository.requests.last())
        model.reloadAfterMutation()
        assertNull(model.uiState.value.operations)
        runCurrent()
        assertEquals(expected, repository.requests.last())
        model.onEvent(PlannedOperationsEvent.PeriodSelected(PlannedOperationPeriod.EndOfMonth))
        runCurrent()
        assertEquals(
            expected.copy(period = PlannedOperationPeriod.EndOfMonth, dateFrom = null, dateTo = null),
            repository.requests.last(),
        )
        model.onEvent(PlannedOperationsEvent.CustomPeriodOpened)
        assertTrue(model.uiState.value.isPeriodPickerVisible)
        model.onEvent(PlannedOperationsEvent.PeriodSelected(PlannedOperationPeriod.CurrentMonth))
        runCurrent()
        assertFalse(model.uiState.value.isPeriodPickerVisible)
        assertEquals(expected.copy(period = PlannedOperationPeriod.CurrentMonth), repository.requests.last())
    }

    @Test
    fun `changing filters ignores a cancelled request even if it later succeeds`() = runTest {
        val stale = CompletableDeferred<PlannedOperations>()
        val repository = FakePlansRepository {
            try { stale.await() } catch (exception: CancellationException) {
                withContext(NonCancellable) { stale.await() }
            }
        }
        val model = model(repository)
        runCurrent()
        val fresh = EMPTY.copy(dateTo = LocalDate.of(2026, 12, 31), totals = TOTALS)
        repository.load = { fresh }
        model.onEvent(PlannedOperationsEvent.PeriodSelected(PlannedOperationPeriod.EndOfYear))
        runCurrent()
        stale.complete(EMPTY)
        runCurrent()
        assertEquals(fresh, model.uiState.value.operations)
        assertEquals(TOTALS, model.uiState.value.periodTotals)
        assertNull(model.uiState.value.error)
    }

    @Test
    fun `cancelling calendar and invalid ranges leave the current filters and list intact`() = runTest {
        val repository = FakePlansRepository()
        val model = model(repository)
        runCurrent()
        model.onEvent(PlannedOperationsEvent.CustomPeriodOpened)
        assertTrue(model.uiState.value.isPeriodPickerVisible)
        model.onEvent(PlannedOperationsEvent.CustomPeriodClosed)
        model.onEvent(PlannedOperationsEvent.CustomPeriodApplied(TO, FROM))
        model.onEvent(PlannedOperationsEvent.CustomPeriodApplied(FROM, LocalDate.of(9999, 1, 1)))
        runCurrent()
        assertEquals(listOf(PlannedOperationsFilter.currentMonth(EMPTY.asOf)), repository.requests)
        assertEquals(EMPTY, model.uiState.value.operations)
        model.onEvent(PlannedOperationsEvent.CustomPeriodApplied(FROM, FROM))
        runCurrent()
        assertEquals(FROM, repository.requests.last().dateTo)
    }

    @Test
    fun `lost membership clears previously loaded data`() = runTest {
        val repository = FakePlansRepository()
        val model = model(repository)
        runCurrent()
        repository.load = { throw PlannedOperationsException.Forbidden }
        model.onEvent(PlannedOperationsEvent.Refresh)
        runCurrent()
        assertNull(model.uiState.value.operations)
        assertEquals(PlannedOperationsException.Forbidden, model.uiState.value.error)
        assertNull(model.uiState.value.periodTotals)
    }

    @Test
    fun `current month includes leap day and picker dates round trip without device timezone conversion`() {
        val leapDay = LocalDate.of(2028, 2, 29)
        val filter = PlannedOperationsUiState(today = leapDay).filters
        assertEquals(LocalDate.of(2028, 2, 1), filter.dateFrom)
        assertEquals(leapDay, filter.dateTo)
        assertEquals(Instant.parse("2028-02-29T00:00:00Z").toEpochMilli(), leapDay.pickerMillis())
        assertEquals(leapDay, pickerDate(leapDay.pickerMillis()))
    }

    @Test
    fun `type selection keeps whole period totals and clearing type reloads all operations`() = runTest {
        val repository = FakePlansRepository { EMPTY.copy(totals = TOTALS) }
        val model = model(repository)
        runCurrent()
        assertEquals(1, repository.requests.size)
        assertEquals(TOTALS, model.uiState.value.periodTotals)
        assertTrue(model.uiState.value.operations?.items.orEmpty().isEmpty())
        val response = CompletableDeferred<PlannedOperations>()
        repository.load = { response.await() }
        model.onEvent(PlannedOperationsEvent.TypeSelected(PlannedOperationType.Income))
        runCurrent()
        assertNull(model.uiState.value.operations)
        assertEquals(TOTALS, model.uiState.value.periodTotals)
        response.complete(EMPTY.copy(totals = TOTALS))
        runCurrent()
        model.onEvent(PlannedOperationsEvent.TypeSelected(null))
        runCurrent()
        assertEquals(listOf(null, PlannedOperationType.Income, null), repository.requests.map { it.type })
        assertEquals(TOTALS, model.uiState.value.periodTotals)
    }

    @Test
    fun `changing dates clears old totals until the new period response arrives`() = runTest {
        val repository = FakePlansRepository { EMPTY.copy(totals = TOTALS) }
        val model = model(repository)
        runCurrent()
        val response = CompletableDeferred<PlannedOperations>()
        repository.load = { response.await() }
        model.onEvent(PlannedOperationsEvent.CustomPeriodApplied(FROM, FROM))
        runCurrent()
        assertNull(model.uiState.value.operations)
        assertNull(model.uiState.value.periodTotals)
        assertTrue(model.uiState.value.isLoading)
        response.complete(EMPTY.copy(dateTo = FROM))
        runCurrent()
        assertEquals(PlannedOperationTotals(), model.uiState.value.periodTotals)
    }

    @Test
    fun `failed refresh keeps totals and mutation reload replaces them with the latest server values`() = runTest {
        val repository = FakePlansRepository { EMPTY.copy(totals = TOTALS) }
        val model = model(repository)
        runCurrent()
        repository.load = { throw PlannedOperationsException.Network }
        model.onEvent(PlannedOperationsEvent.Refresh)
        runCurrent()
        assertEquals(TOTALS, model.uiState.value.periodTotals)
        repository.load = { EMPTY }
        model.reloadAfterMutation()
        assertNull(model.uiState.value.periodTotals)
        runCurrent()
        assertEquals(PlannedOperationTotals(), model.uiState.value.periodTotals)
        assertNull(model.uiState.value.error)
    }

    private fun model(repository: PlannedOperationsRepository) = PlannedOperationsViewModel(
        GetPlannedOperations(repository), Clock.fixed(Instant.parse("2026-10-17T20:00:00Z"), ZoneOffset.UTC),
    )
}

private class FakePlansRepository(var load: suspend () -> PlannedOperations = { EMPTY }) : PlannedOperationsRepository {
    val requests = mutableListOf<PlannedOperationsFilter>()
    override suspend fun getPlannedOperations(filter: PlannedOperationsFilter): PlannedOperations {
        requests += filter
        return load()
    }
}

private val FROM = LocalDate.of(2026, 10, 1)
private val TO = LocalDate.of(2026, 10, 31)
private val EMPTY = PlannedOperations(LocalDate.of(2026, 10, 18), FROM, TO, emptyList())

private val TOTALS = PlannedOperationTotals(BigDecimal("50000.12"), BigDecimal("1000000.34"))
