package com.firkat.fillin.feature.operationhistory.ui

import com.firkat.fillin.feature.operationhistory.domain.model.Operation
import com.firkat.fillin.feature.operationhistory.domain.model.OperationHistoryFilter
import com.firkat.fillin.feature.operationhistory.domain.model.OperationPage
import com.firkat.fillin.feature.operationhistory.domain.model.OperationPeriodTotals
import com.firkat.fillin.feature.operationhistory.domain.model.OperationStatus
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryRepository
import com.firkat.fillin.feature.operationhistory.domain.usecase.GetOperationHistoryPage
import com.firkat.fillin.feature.operationhistory.domain.usecase.RefundPurchase
import com.firkat.fillin.feature.operationhistory.domain.usecase.UpdatePurchaseAmount
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
class OperationHistoryFiltersTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `defaults to all types and uses server totals without loading the rest of the period`() = runTest {
        val repository = FilterHistoryRepository()
        val viewModel = viewModel(repository)
        runCurrent()

        val expected = OperationHistoryFilter(
            type = null,
            categoryId = null,
            dateFrom = LocalDate.of(2026, 10, 1),
            dateTo = LocalDate.of(2026, 10, 18),
        )
        assertEquals(expected, viewModel.uiState.value.filters)
        assertFalse(viewModel.uiState.value.isCustomPeriod)
        assertEquals(listOf(HistoryRequest(null, expected)), repository.requests)
        assertEquals(listOf(20), repository.limits)
        assertEquals(TOTALS, viewModel.uiState.value.periodTotals)
        assertEquals(1, viewModel.uiState.value.operations.size)
        assertTrue(viewModel.uiState.value.canLoadMore)
    }

    @Test
    fun `resetting period preserves other filters and starts pagination from the current month`() = runTest {
        val repository = FilterHistoryRepository()
        val viewModel = viewModel(repository)
        runCurrent()
        val defaultPeriod = viewModel.uiState.value.filters
        viewModel.onEvent(OperationHistoryEvent.TypeSelected(OperationType.Income))
        runCurrent()
        viewModel.onEvent(OperationHistoryEvent.PeriodFilterOpened)
        viewModel.onEvent(OperationHistoryEvent.PeriodSelected(LocalDate.of(2026, 9, 16), TO))
        runCurrent()
        assertTrue(viewModel.uiState.value.isCustomPeriod)
        assertFalse(viewModel.uiState.value.isPeriodFilterVisible)
        viewModel.onEvent(OperationHistoryEvent.LoadNextPage)
        runCurrent()
        assertEquals(10L, repository.requests.last().cursor)

        viewModel.onEvent(OperationHistoryEvent.PeriodFilterReset)
        runCurrent()
        val expected = defaultPeriod.copy(type = OperationType.Income)
        assertEquals(expected, viewModel.uiState.value.filters)
        assertFalse(viewModel.uiState.value.isCustomPeriod)
        assertFalse(viewModel.uiState.value.isPeriodFilterVisible)
        assertEquals(HistoryRequest(null, expected), repository.requests.last())
    }

    @Test
    fun `choosing the default dates restores the month label and resetting them avoids a duplicate load`() = runTest {
        val repository = FilterHistoryRepository()
        val viewModel = viewModel(repository)
        runCurrent()
        val defaultPeriod = viewModel.uiState.value.filters
        viewModel.onEvent(OperationHistoryEvent.PeriodSelected(FROM, TO))
        runCurrent()
        assertTrue(viewModel.uiState.value.isCustomPeriod)

        viewModel.onEvent(OperationHistoryEvent.PeriodSelected(defaultPeriod.dateFrom, defaultPeriod.dateTo))
        runCurrent()
        assertFalse(viewModel.uiState.value.isCustomPeriod)
        val requestCount = repository.requests.size
        viewModel.onEvent(OperationHistoryEvent.PeriodFilterReset)
        runCurrent()
        assertEquals(requestCount, repository.requests.size)
    }

    @Test
    fun `type selection pagination refresh and clearing type preserve the chosen period`() = runTest {
        val repository = FilterHistoryRepository()
        val viewModel = viewModel(repository)
        runCurrent()
        viewModel.onEvent(OperationHistoryEvent.TypeSelected(OperationType.Income))
        runCurrent()
        viewModel.onEvent(OperationHistoryEvent.PeriodSelected(FROM, TO))
        runCurrent()
        val expected = OperationHistoryFilter(OperationType.Income, null, FROM, TO)
        assertEquals(HistoryRequest(null, expected), repository.requests.last())

        viewModel.onEvent(OperationHistoryEvent.LoadNextPage)
        runCurrent()
        assertEquals(HistoryRequest(10, expected), repository.requests.last())
        viewModel.onEvent(OperationHistoryEvent.Refresh)
        runCurrent()
        assertEquals(HistoryRequest(null, expected), repository.requests.last())
        viewModel.onEvent(OperationHistoryEvent.TypeSelected(null))
        runCurrent()
        assertEquals(HistoryRequest(null, expected.copy(type = null)), repository.requests.last())
        assertEquals(TOTALS, viewModel.uiState.value.periodTotals)
    }

    @Test
    fun `changing filters cancels an old page and ignores its late response`() = runTest {
        val repository = FilterHistoryRepository()
        val viewModel = viewModel(repository)
        runCurrent()
        val stale = CompletableDeferred<OperationPage>()
        repository.load = {
            try {
                stale.await()
            } catch (exception: CancellationException) {
                withContext(NonCancellable) { stale.await() }
            }
        }
        viewModel.onEvent(OperationHistoryEvent.LoadNextPage)
        runCurrent()

        repository.load = { OperationPage(listOf(operation("income", OperationType.Income)), 20, TOTALS) }
        viewModel.onEvent(OperationHistoryEvent.TypeSelected(OperationType.Income))
        runCurrent()
        stale.complete(OperationPage(listOf(operation("stale")), 99, OperationPeriodTotals()))
        runCurrent()
        assertEquals(listOf("income"), viewModel.uiState.value.operations.map { it.id })
        assertEquals(TOTALS, viewModel.uiState.value.periodTotals)
        assertNull(viewModel.uiState.value.error)
        viewModel.onEvent(OperationHistoryEvent.LoadNextPage)
        runCurrent()
        assertEquals(20L, repository.requests.last().cursor)
        assertEquals(OperationType.Income, repository.requests.last().filter.type)
    }

    @Test
    fun `type changes keep period totals while a new page is loading`() = runTest {
        val repository = FilterHistoryRepository()
        val viewModel = viewModel(repository)
        runCurrent()
        val response = CompletableDeferred<OperationPage>()
        repository.load = { response.await() }

        viewModel.onEvent(OperationHistoryEvent.TypeSelected(OperationType.Expense))
        runCurrent()
        assertTrue(viewModel.uiState.value.isInitialLoading)
        assertEquals(TOTALS, viewModel.uiState.value.periodTotals)
        response.complete(OperationPage(listOf(operation("expense")), null, TOTALS))
        runCurrent()
        val requestCount = repository.requests.size
        viewModel.onEvent(OperationHistoryEvent.TypeSelected(OperationType.Expense))
        runCurrent()
        assertEquals(requestCount, repository.requests.size)
    }

    @Test
    fun `a new period clears old totals and a late response cannot restore them`() = runTest {
        val repository = FilterHistoryRepository()
        val viewModel = viewModel(repository)
        runCurrent()
        val stale = CompletableDeferred<OperationPage>()
        repository.load = {
            try {
                stale.await()
            } catch (exception: CancellationException) {
                withContext(NonCancellable) { stale.await() }
            }
        }
        viewModel.onEvent(OperationHistoryEvent.LoadNextPage)
        runCurrent()
        val response = CompletableDeferred<OperationPage>()
        repository.load = { response.await() }
        viewModel.onEvent(OperationHistoryEvent.PeriodSelected(FROM, FROM))
        runCurrent()
        assertNull(viewModel.uiState.value.periodTotals)
        assertTrue(viewModel.uiState.value.isInitialLoading)
        stale.complete(OperationPage(listOf(operation("stale")), null, TOTALS))
        runCurrent()
        assertNull(viewModel.uiState.value.periodTotals)
        response.complete(OperationPage(emptyList(), null, OperationPeriodTotals()))
        runCurrent()
        assertEquals(OperationPeriodTotals(), viewModel.uiState.value.periodTotals)
        assertTrue(viewModel.uiState.value.operations.isEmpty())
    }

    @Test
    fun `editing and refunding reload the list together with server totals`() = runTest {
        val repository = FilterHistoryRepository()
        val viewModel = viewModel(repository)
        runCurrent()
        val edited = operation("expense").copy(amount = BigDecimal("200.00"), originalAmount = BigDecimal("200.00"))
        val afterEdit = OperationPeriodTotals(BigDecimal("50100.12"), TOTALS.incomes)
        repository.load = { OperationPage(listOf(edited), null, afterEdit) }
        viewModel.onEvent(OperationHistoryEvent.EditRequested("expense"))
        viewModel.onEvent(OperationHistoryEvent.AmountChanged("200.00"))
        viewModel.onEvent(OperationHistoryEvent.SaveRequested)
        runCurrent()
        assertNull(viewModel.uiState.value.editor)
        assertEquals(edited, viewModel.uiState.value.operations.single())
        assertEquals(afterEdit, viewModel.uiState.value.periodTotals)

        repository.load = { OperationPage(listOf(edited), null, TOTALS) }
        viewModel.onEvent(OperationHistoryEvent.EditRequested("expense"))
        viewModel.onEvent(OperationHistoryEvent.RefundRequested)
        viewModel.onEvent(OperationHistoryEvent.RefundConfirmed)
        runCurrent()
        assertEquals(TOTALS, viewModel.uiState.value.periodTotals)
        assertEquals(listOf<Long?>(null, null, null), repository.requests.map { it.cursor })
    }

    @Test
    fun `cancelling or reversing the period preserves filters and same-day ranges are accepted`() = runTest {
        val repository = FilterHistoryRepository()
        val viewModel = viewModel(repository)
        runCurrent()
        val original = viewModel.uiState.value.filters
        viewModel.onEvent(OperationHistoryEvent.PeriodFilterOpened)
        viewModel.onEvent(OperationHistoryEvent.PeriodFilterClosed)
        viewModel.onEvent(OperationHistoryEvent.PeriodSelected(TO, FROM))
        runCurrent()
        assertEquals(original, viewModel.uiState.value.filters)
        assertEquals(1, repository.requests.size)

        viewModel.onEvent(OperationHistoryEvent.PeriodSelected(FROM, FROM))
        runCurrent()
        assertEquals(original.copy(dateFrom = FROM, dateTo = FROM), repository.requests.last().filter)
    }

    @Test
    fun `income cannot open the purchase editor`() = runTest {
        val repository = FilterHistoryRepository { OperationPage(listOf(operation("income", OperationType.Income)), null) }
        val viewModel = viewModel(repository)
        runCurrent()
        viewModel.onEvent(OperationHistoryEvent.EditRequested("income"))
        assertNull(viewModel.uiState.value.editor)
    }

    @Test
    fun `current month handles month boundaries and picker days do not shift in the device timezone`() {
        listOf(LocalDate.of(2024, 2, 29), LocalDate.of(2024, 3, 31), LocalDate.of(2026, 1, 1)).forEach { today ->
            val month = OperationHistoryFilter.currentMonth(today)
            assertEquals(LocalDate.of(today.year, today.month, 1), month.dateFrom)
            assertEquals(today, month.dateTo)
        }
        val date = LocalDate.of(2026, 10, 17)
        assertEquals(Instant.parse("2026-10-17T00:00:00Z").toEpochMilli(), date.toPickerMillis())
        assertEquals(date, pickerMillisToDate(date.toPickerMillis()))
    }

    private fun viewModel(repository: OperationHistoryRepository) = OperationHistoryViewModel(
        getOperationHistoryPage = GetOperationHistoryPage(repository),
        updatePurchaseAmount = UpdatePurchaseAmount(repository),
        refundPurchase = RefundPurchase(repository),
        clock = Clock.fixed(Instant.parse("2026-10-17T19:30:00Z"), ZoneId.of("Asia/Almaty")),
    )
}

private data class HistoryRequest(val cursor: Long?, val filter: OperationHistoryFilter)

private class FilterHistoryRepository(
    var load: suspend (Long?) -> OperationPage = { OperationPage(listOf(operation("expense")), 10, TOTALS) },
) : OperationHistoryRepository {
    val requests = mutableListOf<HistoryRequest>()
    val limits = mutableListOf<Int>()
    override suspend fun getOperations(limit: Int, cursor: Long?, filter: OperationHistoryFilter): OperationPage {
        requests += HistoryRequest(cursor, filter)
        limits += limit
        return load(cursor)
    }
    override suspend fun updatePurchase(id: String, version: Long, amount: BigDecimal): Operation =
        operation(id).copy(amount = amount, originalAmount = amount, version = version + 1)
    override suspend fun refundPurchase(id: String, version: Long) = Unit
}

private fun operation(id: String, type: OperationType = OperationType.Expense) = Operation(
    id = id,
    categoryName = if (type == OperationType.Expense) "Продукты" else "Зарплата",
    description = null,
    amount = BigDecimal("100.00"),
    currency = "KZT",
    originalAmount = BigDecimal("100.00"),
    originalCurrency = "KZT",
    occurredOn = TO,
    status = OperationStatus.Posted,
    version = 0,
    type = type,
)

private val FROM = LocalDate.of(2026, 10, 1)
private val TO = LocalDate.of(2026, 10, 17)
private val TOTALS = OperationPeriodTotals(BigDecimal("50000.12"), BigDecimal("1000000.34"))
