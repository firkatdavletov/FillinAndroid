package com.firkat.fillin.feature.operationhistory.ui

import com.firkat.fillin.feature.categories.domain.model.Category
import com.firkat.fillin.feature.categories.domain.model.CategoryIcon
import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import com.firkat.fillin.feature.categories.domain.repository.CategoriesException
import com.firkat.fillin.feature.categories.domain.usecase.GetCategories
import com.firkat.fillin.feature.operationhistory.domain.model.Operation
import com.firkat.fillin.feature.operationhistory.domain.model.OperationHistoryFilter
import com.firkat.fillin.feature.operationhistory.domain.model.OperationPage
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
    fun `defaults to expenses in all categories from the first day of the current month in Almaty`() = runTest {
        val repository = FilterHistoryRepository()
        val viewModel = viewModel(repository)
        runCurrent()

        val expected = OperationHistoryFilter(
            type = OperationType.Expense,
            categoryId = null,
            dateFrom = LocalDate.of(2026, 10, 1),
            dateTo = LocalDate.of(2026, 10, 18),
        )
        assertEquals(expected, viewModel.uiState.value.filters)
        assertFalse(viewModel.uiState.value.isCustomPeriod)
        assertEquals(listOf(HistoryRequest(null, expected)), repository.requests)
    }

    @Test
    fun `resetting period preserves other filters and starts pagination from the current month`() = runTest {
        val repository = FilterHistoryRepository()
        val viewModel = viewModel(repository)
        runCurrent()
        val defaultPeriod = viewModel.uiState.value.filters
        viewModel.onEvent(OperationHistoryEvent.TypeSelected(OperationType.Income))
        runCurrent()
        viewModel.onEvent(OperationHistoryEvent.CategoryFilterOpened)
        runCurrent()
        viewModel.onEvent(OperationHistoryEvent.CategorySelected("salary"))
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
        val expected = defaultPeriod.copy(type = OperationType.Income, categoryId = "salary")
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
    fun `uses the same combined filters for pagination refresh and all categories`() = runTest {
        val repository = FilterHistoryRepository()
        val viewModel = viewModel(repository)
        runCurrent()
        viewModel.onEvent(OperationHistoryEvent.TypeSelected(OperationType.Income))
        runCurrent()
        viewModel.onEvent(OperationHistoryEvent.CategoryFilterOpened)
        runCurrent()
        viewModel.onEvent(OperationHistoryEvent.CategorySelected("salary"))
        runCurrent()
        viewModel.onEvent(OperationHistoryEvent.PeriodSelected(FROM, TO))
        runCurrent()
        val expected = OperationHistoryFilter(OperationType.Income, "salary", FROM, TO)
        assertEquals(HistoryRequest(null, expected), repository.requests.last())

        viewModel.onEvent(OperationHistoryEvent.LoadNextPage)
        runCurrent()
        assertEquals(HistoryRequest(10, expected), repository.requests.last())
        viewModel.onEvent(OperationHistoryEvent.Refresh)
        runCurrent()
        assertEquals(HistoryRequest(null, expected), repository.requests.last())
        viewModel.onEvent(OperationHistoryEvent.CategorySelected(null))
        runCurrent()
        assertEquals(HistoryRequest(null, expected.copy(categoryId = null)), repository.requests.last())
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

        repository.load = { OperationPage(listOf(operation("income", OperationType.Income)), 20) }
        viewModel.onEvent(OperationHistoryEvent.TypeSelected(OperationType.Income))
        runCurrent()
        stale.complete(OperationPage(listOf(operation("stale")), 99))
        runCurrent()
        assertEquals(listOf("income"), viewModel.uiState.value.operations.map { it.id })
        assertNull(viewModel.uiState.value.error)
        viewModel.onEvent(OperationHistoryEvent.LoadNextPage)
        runCurrent()
        assertEquals(20L, repository.requests.last().cursor)
        assertEquals(OperationType.Income, repository.requests.last().filter.type)
    }

    @Test
    fun `switching type resets an incompatible category and preserves a compatible one`() = runTest {
        val viewModel = viewModel(FilterHistoryRepository())
        runCurrent()
        viewModel.onEvent(OperationHistoryEvent.CategoryFilterOpened)
        runCurrent()
        assertEquals(listOf("food"), viewModel.uiState.value.availableCategories.map { it.id })
        viewModel.onEvent(OperationHistoryEvent.CategorySelected("food"))
        runCurrent()
        viewModel.onEvent(OperationHistoryEvent.TypeSelected(null))
        runCurrent()
        assertEquals("food", viewModel.uiState.value.filters.categoryId)
        assertEquals(2, viewModel.uiState.value.availableCategories.size)
        viewModel.onEvent(OperationHistoryEvent.TypeSelected(OperationType.Income))
        runCurrent()
        assertNull(viewModel.uiState.value.filters.categoryId)
        assertEquals(listOf("salary"), viewModel.uiState.value.availableCategories.map { it.id })
        viewModel.onEvent(OperationHistoryEvent.CategorySelected("food"))
        runCurrent()
        assertNull(viewModel.uiState.value.filters.categoryId)
    }

    @Test
    fun `category loading failure can be retried without losing history`() = runTest {
        val repository = FilterHistoryRepository()
        val categories = HistoryCategoriesRepository { throw CategoriesException.Network }
        val viewModel = viewModel(repository, categories)
        runCurrent()
        viewModel.onEvent(OperationHistoryEvent.CategoryFilterOpened)
        runCurrent()
        assertEquals(OperationHistoryError.Network, viewModel.uiState.value.categoriesError)
        assertFalse(viewModel.uiState.value.isCategoriesLoading)
        assertTrue(viewModel.uiState.value.isCategoryFilterVisible)

        categories.load = { CATEGORIES }
        viewModel.onEvent(OperationHistoryEvent.CategoriesRetry)
        runCurrent()
        assertNull(viewModel.uiState.value.categoriesError)
        assertEquals(CATEGORIES, viewModel.uiState.value.categories)
        assertEquals(listOf("expense"), viewModel.uiState.value.operations.map { it.id })
        assertEquals(1, repository.requests.size)
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

    private fun viewModel(
        repository: OperationHistoryRepository,
        categories: HistoryCategoriesRepository = HistoryCategoriesRepository { CATEGORIES },
    ) = OperationHistoryViewModel(
        getOperationHistoryPage = GetOperationHistoryPage(repository),
        updatePurchaseAmount = UpdatePurchaseAmount(repository),
        refundPurchase = RefundPurchase(repository),
        getCategories = GetCategories(categories),
        clock = Clock.fixed(Instant.parse("2026-10-17T19:30:00Z"), ZoneId.of("Asia/Almaty")),
    )
}

private data class HistoryRequest(val cursor: Long?, val filter: OperationHistoryFilter)

private class FilterHistoryRepository(
    var load: suspend (Long?) -> OperationPage = { OperationPage(listOf(operation("expense")), 10) },
) : OperationHistoryRepository {
    val requests = mutableListOf<HistoryRequest>()
    override suspend fun getOperations(limit: Int, cursor: Long?, filter: OperationHistoryFilter): OperationPage {
        requests += HistoryRequest(cursor, filter)
        return load(cursor)
    }
    override suspend fun updatePurchase(id: String, version: Long, amount: BigDecimal): Operation =
        error("Unexpected mutation")
    override suspend fun refundPurchase(id: String, version: Long) = error("Unexpected mutation")
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
private val CATEGORIES = listOf(
    Category("food", "Продукты", CategoryKind.Expense, CategoryIcon("FOOD", "🍔")),
    Category("salary", "Зарплата", CategoryKind.Income, CategoryIcon("INCOME", "💰")),
)
