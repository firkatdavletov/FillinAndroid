package com.firkat.fillin.feature.purchasehistory.ui

import com.firkat.fillin.feature.purchasehistory.domain.model.Purchase
import com.firkat.fillin.feature.purchasehistory.domain.model.PurchasePage
import com.firkat.fillin.feature.purchasehistory.domain.model.PurchaseStatus
import com.firkat.fillin.feature.purchasehistory.domain.repository.PurchaseHistoryException
import com.firkat.fillin.feature.purchasehistory.domain.repository.PurchaseHistoryRepository
import com.firkat.fillin.feature.purchasehistory.domain.usecase.GetPurchaseHistoryPage
import com.firkat.fillin.feature.purchasehistory.domain.usecase.RefundPurchase
import com.firkat.fillin.feature.purchasehistory.domain.usecase.UpdatePurchaseAmount
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
class PurchaseHistoryRefreshTest {
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
        val repository = RefreshRepository { PurchasePage(listOf(purchase("old")), 10) }
        val viewModel = viewModel(repository)
        runCurrent()
        repository.load = { PurchasePage(listOf(purchase("second")), null) }
        viewModel.onEvent(PurchaseHistoryEvent.LoadNextPage)
        runCurrent()

        repository.load = { PurchasePage(listOf(purchase("new")), 20) }
        viewModel.onEvent(PurchaseHistoryEvent.Refresh)
        runCurrent()
        assertEquals(listOf("new"), viewModel.uiState.value.purchases.map { it.id })
        assertTrue(viewModel.uiState.value.canLoadMore)

        repository.load = { PurchasePage(listOf(purchase("new-second")), null) }
        viewModel.onEvent(PurchaseHistoryEvent.LoadNextPage)
        runCurrent()
        assertEquals(listOf(null, 10L, null, 20L), repository.cursors)
        assertEquals(listOf("new", "new-second"), viewModel.uiState.value.purchases.map { it.id })
    }

    @Test
    fun `refresh keeps visible data and ignores duplicate refresh and pagination events`() = runTest {
        val repository = RefreshRepository { PurchasePage(listOf(purchase("old")), 10) }
        val viewModel = viewModel(repository)
        runCurrent()
        val response = CompletableDeferred<PurchasePage>()
        repository.load = { response.await() }

        viewModel.onEvent(PurchaseHistoryEvent.Refresh)
        viewModel.onEvent(PurchaseHistoryEvent.Refresh)
        viewModel.onEvent(PurchaseHistoryEvent.LoadNextPage)
        runCurrent()

        assertTrue(viewModel.uiState.value.isRefreshing)
        assertFalse(viewModel.uiState.value.isInitialLoading)
        assertFalse(viewModel.uiState.value.isLoadingNextPage)
        assertEquals(listOf("old"), viewModel.uiState.value.purchases.map { it.id })
        assertEquals(listOf(null, null), repository.cursors)

        viewModel.onEvent(PurchaseHistoryEvent.EditRequested("old"))
        assertNull(viewModel.uiState.value.editor)

        response.complete(PurchasePage(listOf(purchase("new")), null))
        runCurrent()
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun `failed refresh preserves data and retry requests the first page`() = runTest {
        val repository = RefreshRepository { PurchasePage(listOf(purchase("old")), 10) }
        val viewModel = viewModel(repository)
        runCurrent()
        repository.load = { throw PurchaseHistoryException.Network }

        viewModel.onEvent(PurchaseHistoryEvent.Refresh)
        runCurrent()

        assertFalse(viewModel.uiState.value.isRefreshing)
        assertEquals(PurchaseHistoryError.Network, viewModel.uiState.value.error)
        assertEquals(listOf("old"), viewModel.uiState.value.purchases.map { it.id })

        repository.load = { PurchasePage(listOf(purchase("new")), null) }
        viewModel.onEvent(PurchaseHistoryEvent.Retry)
        assertTrue(viewModel.uiState.value.isRefreshing)
        runCurrent()
        assertEquals(listOf(null, null, null), repository.cursors)
        assertNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isRefreshing)
        assertEquals(listOf("new"), viewModel.uiState.value.purchases.map { it.id })
    }

    @Test
    fun `empty completed history can be refreshed`() = runTest {
        val repository = RefreshRepository { PurchasePage(emptyList(), null) }
        val viewModel = viewModel(repository)
        runCurrent()
        assertFalse(viewModel.uiState.value.canLoadMore)

        repository.load = { PurchasePage(listOf(purchase("new")), null) }
        viewModel.onEvent(PurchaseHistoryEvent.Refresh)
        runCurrent()

        assertEquals(listOf(null, null), repository.cursors)
        assertEquals(listOf("new"), viewModel.uiState.value.purchases.map { it.id })
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun `refresh supersedes pagination even when cancelled request returns late`() = runTest {
        val repository = RefreshRepository { PurchasePage(listOf(purchase("old")), 10) }
        val viewModel = viewModel(repository)
        runCurrent()
        val stalePage = CompletableDeferred<PurchasePage>()
        repository.load = {
            try {
                stalePage.await()
            } catch (exception: CancellationException) {
                // Simulate a data source that swallows cancellation and returns a stale response.
                withContext(NonCancellable) { stalePage.await() }
            }
        }
        viewModel.onEvent(PurchaseHistoryEvent.LoadNextPage)
        runCurrent()

        repository.load = { PurchasePage(listOf(purchase("new")), 20) }
        viewModel.onEvent(PurchaseHistoryEvent.Refresh)
        runCurrent()
        stalePage.complete(PurchasePage(listOf(purchase("stale")), 99))
        runCurrent()

        assertEquals(listOf("new"), viewModel.uiState.value.purchases.map { it.id })
        assertFalse(viewModel.uiState.value.isRefreshing)
        assertNull(viewModel.uiState.value.error)
        repository.load = { PurchasePage(emptyList(), null) }
        viewModel.onEvent(PurchaseHistoryEvent.LoadNextPage)
        runCurrent()
        assertEquals(listOf(null, 10L, null, 20L), repository.cursors)
    }

    @Test
    fun `refresh during initial load does not start a second request`() = runTest {
        val response = CompletableDeferred<PurchasePage>()
        val repository = RefreshRepository { response.await() }
        val viewModel = viewModel(repository)
        runCurrent()

        viewModel.onEvent(PurchaseHistoryEvent.Refresh)
        runCurrent()
        assertEquals(listOf<Long?>(null), repository.cursors)
        assertTrue(viewModel.uiState.value.isInitialLoading)
        assertFalse(viewModel.uiState.value.isRefreshing)

        response.complete(PurchasePage(emptyList(), null))
        runCurrent()
        assertFalse(viewModel.uiState.value.isInitialLoading)
    }

    @Test
    fun `pagination retry retains existing items and retries the failed cursor`() = runTest {
        val repository = RefreshRepository { PurchasePage(listOf(purchase("old")), 10) }
        val viewModel = viewModel(repository)
        runCurrent()
        repository.load = { throw PurchaseHistoryException.Network }
        viewModel.onEvent(PurchaseHistoryEvent.LoadNextPage)
        runCurrent()

        repository.load = { PurchasePage(listOf(purchase("second")), null) }
        viewModel.onEvent(PurchaseHistoryEvent.Retry)
        runCurrent()

        assertEquals(listOf(null, 10L, 10L), repository.cursors)
        assertEquals(listOf("old", "second"), viewModel.uiState.value.purchases.map { it.id })
        assertNull(viewModel.uiState.value.error)
    }

    private fun viewModel(repository: PurchaseHistoryRepository) = PurchaseHistoryViewModel(
        getPurchaseHistoryPage = GetPurchaseHistoryPage(repository),
        updatePurchaseAmount = UpdatePurchaseAmount(repository),
        refundPurchase = RefundPurchase(repository),
    )

    private fun purchase(id: String) = Purchase(
        id = id,
        categoryName = "Продукты",
        description = null,
        amount = BigDecimal("1000.00"),
        currency = "KZT",
        originalAmount = BigDecimal("1000.00"),
        originalCurrency = "KZT",
        occurredOn = LocalDate.of(2026, 10, 14),
        status = PurchaseStatus.Posted,
        version = 1,
    )
}

private class RefreshRepository(
    var load: suspend (Long?) -> PurchasePage,
) : PurchaseHistoryRepository {
    val cursors = mutableListOf<Long?>()

    override suspend fun getPurchases(limit: Int, cursor: Long?): PurchasePage {
        cursors += cursor
        return load(cursor)
    }

    override suspend fun updatePurchase(id: String, version: Long, amount: BigDecimal): Purchase =
        error("Unexpected purchase update")

    override suspend fun refundPurchase(id: String, version: Long) =
        error("Unexpected refund")
}
