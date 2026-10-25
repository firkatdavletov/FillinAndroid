package com.firkat.fillin.feature.operationhistory.ui

import com.firkat.fillin.feature.operationhistory.domain.model.OperationSyncStatus
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryException
import com.firkat.fillin.feature.operationhistory.domain.usecase.RefundPurchase
import com.firkat.fillin.feature.operationhistory.domain.usecase.UpdatePurchaseAmount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class OperationHistoryRefreshTest {
    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test fun `refresh schedules sync and keeps visible history and local page window on failure`() =
        runTest {
            val repository = FakeOfflineHistory()
            repository.operations.value = (1..45).map { FakeOfflineHistory.purchase("entry-$it") }
            val model = model(repository)
            runCurrent()
            assertEquals(20, model.uiState.value.operations.size)
            model.onEvent(OperationHistoryEvent.LoadNextPage)
            runCurrent()
            assertEquals(40, model.uiState.value.operations.size)
            model.onEvent(OperationHistoryEvent.Refresh)
            runCurrent()
            repository.context.value = repository.context.value.copy(isSyncing = true)
            runCurrent()
            assertTrue(model.uiState.value.isRefreshing)
            assertEquals(40, model.uiState.value.operations.size)
            repository.context.value = repository.context.value.copy(isSyncing = false, syncError = "network")
            runCurrent()
            assertFalse(model.uiState.value.isRefreshing)
            assertEquals(40, model.uiState.value.operations.size)
            assertEquals(40, repository.subscriptions.last().second)
            model.onEvent(OperationHistoryEvent.Retry)
            assertEquals(2, repository.syncRequests)
        }

    @Test fun `bootstrap blocks creation and incomplete history does not show final totals`() =
        runTest {
            val repository = FakeOfflineHistory()
            repository.context.value = repository.context.value.copy(initialSyncComplete = false)
            val model = model(repository)
            runCurrent()
            model.onEvent(OperationHistoryEvent.CreateRequested)
            assertNull(model.uiState.value.createEditor)
            assertNull(model.uiState.value.periodTotals)
        }

    @Test fun `offline save closes form only after local creation and observes pending entry`() =
        runTest {
            val repository = FakeOfflineHistory()
            repository.context.value = repository.context.value.copy(isOnline = false)
            val model = model(repository)
            runCurrent()
            model.onEvent(OperationHistoryEvent.CreateRequested)
            model.onEvent(OperationHistoryEvent.CreateInputChanged("123,45"))
            model.onEvent(OperationHistoryEvent.CreateCategoryClicked("expense-category"))
            runCurrent()
            assertNull(model.uiState.value.createEditor)
            assertEquals("123.45", repository.created!!.amount.toPlainString())
            assertEquals(
                OperationSyncStatus.Pending,
                model.uiState.value.operations
                    .last()
                    .syncStatus,
            )
        }

    @Test fun `local write failure retains form and error without pretending to save`() =
        runTest {
            val repository = FakeOfflineHistory().apply { creationError = OperationHistoryException.Unknown }
            val model = model(repository)
            runCurrent()
            model.onEvent(OperationHistoryEvent.CreateRequested)
            model.onEvent(OperationHistoryEvent.CreateInputChanged("12.00"))
            model.onEvent(OperationHistoryEvent.CreateCategoryClicked("expense-category"))
            runCurrent()
            assertEquals(
                OperationHistoryError.Unknown,
                model.uiState.value.createEditor!!
                    .error,
            )
            assertFalse(
                model.uiState.value.createEditor!!
                    .isSaving,
            )
            assertNull(repository.created)
        }

    @Test fun `only definitively rejected entries can be corrected into a new local request`() =
        runTest {
            val repository = FakeOfflineHistory()
            repository.operations.value = listOf(FakeOfflineHistory.purchase("rejected").copy(syncStatus = OperationSyncStatus.Rejected))
            val model = model(repository)
            runCurrent()
            model.onEvent(OperationHistoryEvent.CorrectRejectedRequested("rejected"))
            model.onEvent(OperationHistoryEvent.CreateInputChanged("13.00"))
            model.onEvent(OperationHistoryEvent.CreateCategoryClicked("expense-category"))
            runCurrent()
            assertEquals("rejected", repository.replaced)
            assertEquals(
                listOf("created"),
                model.uiState.value.operations
                    .map { it.id },
            )
            model.onEvent(OperationHistoryEvent.CorrectRejectedRequested("created"))
            assertNull(model.uiState.value.createEditor)
        }

    @Test fun `loss of connectivity keeps local editing available`() =
        runTest {
            val repository = FakeOfflineHistory()
            val model = model(repository)
            runCurrent()
            model.onEvent(OperationHistoryEvent.EditRequested("first"))
            model.onEvent(OperationHistoryEvent.AmountChanged("19.00"))
            assertTrue(
                model.uiState.value.editor!!
                    .canSave,
            )
            repository.context.value = repository.context.value.copy(isOnline = false)
            runCurrent()
            assertTrue(
                model.uiState.value.editor!!
                    .canSave,
            )
            model.onEvent(OperationHistoryEvent.SaveRequested)
            runCurrent()
            assertEquals(1, repository.edits)
        }

    private fun model(repository: FakeOfflineHistory) =
        OperationHistoryViewModel(
            repository,
            UpdatePurchaseAmount(repository),
            RefundPurchase(repository),
            Clock.fixed(Instant.parse("2026-10-18T05:00:00Z"), ZoneId.of("Asia/Almaty")),
        )
}
