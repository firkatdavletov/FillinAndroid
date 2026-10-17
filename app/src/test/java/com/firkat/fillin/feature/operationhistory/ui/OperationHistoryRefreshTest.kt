package com.firkat.fillin.feature.operationhistory.ui

import com.firkat.fillin.feature.operationhistory.domain.model.Operation
import com.firkat.fillin.feature.operationhistory.domain.model.OperationHistoryFilter
import com.firkat.fillin.feature.operationhistory.domain.model.OperationPage
import com.firkat.fillin.feature.operationhistory.domain.model.OperationStatus
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryException
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryRepository
import com.firkat.fillin.feature.operationhistory.domain.usecase.GetOperationHistoryPage
import com.firkat.fillin.feature.operationhistory.domain.usecase.RefundPurchase
import com.firkat.fillin.feature.operationhistory.domain.usecase.UpdatePurchaseAmount
import java.math.BigDecimal
import java.time.LocalDate
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
class OperationHistoryRefreshTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `refresh replaces all loaded pages and uses the new first-page cursor`() = runTest {
        val repository = RefreshRepository { OperationPage(listOf(purchase("old")), 10) }
        val viewModel = viewModel(repository)
        runCurrent()
        repository.load = { OperationPage(listOf(purchase("second")), null) }
        viewModel.onEvent(OperationHistoryEvent.LoadNextPage)
        runCurrent()

        repository.load = { OperationPage(listOf(purchase("new")), 20) }
        viewModel.onEvent(OperationHistoryEvent.Refresh)
        runCurrent()
        assertEquals(listOf("new"), viewModel.uiState.value.operations.map { it.id })
        assertTrue(viewModel.uiState.value.canLoadMore)

        repository.load = { OperationPage(listOf(purchase("new-second")), null) }
        viewModel.onEvent(OperationHistoryEvent.LoadNextPage)
        runCurrent()
        assertEquals(listOf(null, 10L, null, 20L), repository.cursors)
        assertEquals(listOf("new", "new-second"), viewModel.uiState.value.operations.map { it.id })
    }

    @Test
    fun `refresh keeps visible data and ignores duplicate refresh and pagination events`() = runTest {
        val repository = RefreshRepository { OperationPage(listOf(purchase("old")), 10) }
        val viewModel = viewModel(repository)
        runCurrent()
        val response = CompletableDeferred<OperationPage>()
        repository.load = { response.await() }

        viewModel.onEvent(OperationHistoryEvent.Refresh)
        viewModel.onEvent(OperationHistoryEvent.Refresh)
        viewModel.onEvent(OperationHistoryEvent.LoadNextPage)
        runCurrent()

        assertTrue(viewModel.uiState.value.isRefreshing)
        assertFalse(viewModel.uiState.value.isInitialLoading)
        assertFalse(viewModel.uiState.value.isLoadingNextPage)
        assertEquals(listOf("old"), viewModel.uiState.value.operations.map { it.id })
        assertEquals(listOf(null, null), repository.cursors)

        viewModel.onEvent(OperationHistoryEvent.EditRequested("old"))
        assertNull(viewModel.uiState.value.editor)

        response.complete(OperationPage(listOf(purchase("new")), null))
        runCurrent()
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun `failed refresh preserves data and retry requests the first page`() = runTest {
        val repository = RefreshRepository { OperationPage(listOf(purchase("old")), 10) }
        val viewModel = viewModel(repository)
        runCurrent()
        repository.load = { throw OperationHistoryException.Network }

        viewModel.onEvent(OperationHistoryEvent.Refresh)
        runCurrent()

        assertFalse(viewModel.uiState.value.isRefreshing)
        assertEquals(OperationHistoryError.Network, viewModel.uiState.value.error)
        assertEquals(listOf("old"), viewModel.uiState.value.operations.map { it.id })

        repository.load = { OperationPage(listOf(purchase("new")), null) }
        viewModel.onEvent(OperationHistoryEvent.Retry)
        assertTrue(viewModel.uiState.value.isRefreshing)
        runCurrent()
        assertEquals(listOf(null, null, null), repository.cursors)
        assertNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isRefreshing)
        assertEquals(listOf("new"), viewModel.uiState.value.operations.map { it.id })
    }

    @Test
    fun `empty completed history can be refreshed`() = runTest {
        val repository = RefreshRepository { OperationPage(emptyList(), null) }
        val viewModel = viewModel(repository)
        runCurrent()
        assertFalse(viewModel.uiState.value.canLoadMore)

        repository.load = { OperationPage(listOf(purchase("new")), null) }
        viewModel.onEvent(OperationHistoryEvent.Refresh)
        runCurrent()

        assertEquals(listOf(null, null), repository.cursors)
        assertEquals(listOf("new"), viewModel.uiState.value.operations.map { it.id })
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun `refresh supersedes pagination even when cancelled request returns late`() = runTest {
        val repository = RefreshRepository { OperationPage(listOf(purchase("old")), 10) }
        val viewModel = viewModel(repository)
        runCurrent()
        val stalePage = CompletableDeferred<OperationPage>()
        repository.load = {
            try {
                stalePage.await()
            } catch (exception: CancellationException) {
                // Simulate a data source that swallows cancellation and returns a stale response.
                withContext(NonCancellable) { stalePage.await() }
            }
        }
        viewModel.onEvent(OperationHistoryEvent.LoadNextPage)
        runCurrent()

        repository.load = { OperationPage(listOf(purchase("new")), 20) }
        viewModel.onEvent(OperationHistoryEvent.Refresh)
        runCurrent()
        stalePage.complete(OperationPage(listOf(purchase("stale")), 99))
        runCurrent()

        assertEquals(listOf("new"), viewModel.uiState.value.operations.map { it.id })
        assertFalse(viewModel.uiState.value.isRefreshing)
        assertNull(viewModel.uiState.value.error)
        repository.load = { OperationPage(emptyList(), null) }
        viewModel.onEvent(OperationHistoryEvent.LoadNextPage)
        runCurrent()
        assertEquals(listOf(null, 10L, null, 20L), repository.cursors)
    }

    @Test
    fun `refresh during initial load does not start a second request`() = runTest {
        val response = CompletableDeferred<OperationPage>()
        val repository = RefreshRepository { response.await() }
        val viewModel = viewModel(repository)
        runCurrent()

        viewModel.onEvent(OperationHistoryEvent.Refresh)
        runCurrent()
        assertEquals(listOf<Long?>(null), repository.cursors)
        assertTrue(viewModel.uiState.value.isInitialLoading)
        assertFalse(viewModel.uiState.value.isRefreshing)

        response.complete(OperationPage(emptyList(), null))
        runCurrent()
        assertFalse(viewModel.uiState.value.isInitialLoading)
    }

    @Test
    fun `pagination retry retains existing items and retries the failed cursor`() = runTest {
        val repository = RefreshRepository { OperationPage(listOf(purchase("old")), 10) }
        val viewModel = viewModel(repository)
        runCurrent()
        repository.load = { throw OperationHistoryException.Network }
        viewModel.onEvent(OperationHistoryEvent.LoadNextPage)
        runCurrent()

        repository.load = { OperationPage(listOf(purchase("second")), null) }
        viewModel.onEvent(OperationHistoryEvent.Retry)
        runCurrent()

        assertEquals(listOf(null, 10L, 10L), repository.cursors)
        assertEquals(listOf("old", "second"), viewModel.uiState.value.operations.map { it.id })
        assertNull(viewModel.uiState.value.error)
    }

    private fun viewModel(repository: OperationHistoryRepository) = OperationHistoryViewModel(
        getOperationHistoryPage = GetOperationHistoryPage(repository),
        updatePurchaseAmount = UpdatePurchaseAmount(repository),
        refundPurchase = RefundPurchase(repository),
        getCategories = com.firkat.fillin.feature.categories.domain.usecase.GetCategories(HistoryCategoriesRepository()),
    )

    private fun purchase(id: String) = Operation(
        id = id,
        categoryName = "Продукты",
        description = null,
        amount = BigDecimal("1000.00"),
        currency = "KZT",
        originalAmount = BigDecimal("1000.00"),
        originalCurrency = "KZT",
        occurredOn = LocalDate.of(2026, 10, 14),
        status = OperationStatus.Posted,
        version = 1,
    )
}

private class RefreshRepository(
    var load: suspend (Long?) -> OperationPage,
) : OperationHistoryRepository {
    val cursors = mutableListOf<Long?>()

    override suspend fun getOperations(limit: Int, cursor: Long?, filter: OperationHistoryFilter): OperationPage {
        cursors += cursor
        return load(cursor)
    }

    override suspend fun updatePurchase(id: String, version: Long, amount: BigDecimal): Operation =
        error("Unexpected purchase update")

    override suspend fun refundPurchase(id: String, version: Long) =
        error("Unexpected refund")
}
