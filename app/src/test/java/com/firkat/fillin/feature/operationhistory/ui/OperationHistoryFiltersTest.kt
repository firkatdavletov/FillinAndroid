package com.firkat.fillin.feature.operationhistory.ui

import com.firkat.fillin.feature.operationhistory.domain.model.OperationHistoryFilter
import com.firkat.fillin.feature.operationhistory.domain.model.OperationSyncStatus
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
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
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class OperationHistoryFiltersTest {
    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test fun `defaults to current month and observes local totals without a network read`() =
        runTest {
            val repository = FakeOfflineHistory()
            val model = model(repository)
            runCurrent()
            assertEquals(LocalDate.parse("2026-10-01"), model.uiState.value.filters.dateFrom)
            assertEquals(LocalDate.parse("2026-10-18"), model.uiState.value.filters.dateTo)
            assertNull(model.uiState.value.filters.type)
            assertEquals(repository.totals.value, model.uiState.value.periodTotals)
            assertEquals(20, repository.subscriptions.single().second)
            assertEquals(1, repository.syncRequests)
        }

    @Test fun `period reset preserves type and same dates avoid a duplicate subscription`() =
        runTest {
            val repository = FakeOfflineHistory()
            val model = model(repository)
            runCurrent()
            model.onEvent(OperationHistoryEvent.TypeSelected(OperationType.Income))
            runCurrent()
            model.onEvent(OperationHistoryEvent.PeriodSelected(LocalDate.parse("2026-09-10"), LocalDate.parse("2026-10-25")))
            runCurrent()
            assertTrue(model.uiState.value.isCustomPeriod)
            model.onEvent(OperationHistoryEvent.PeriodFilterReset)
            runCurrent()
            assertEquals(OperationType.Income, model.uiState.value.filters.type)
            assertFalse(model.uiState.value.isCustomPeriod)
            val count = repository.subscriptions.size
            model.onEvent(OperationHistoryEvent.PeriodFilterReset)
            runCurrent()
            assertEquals(count, repository.subscriptions.size)
        }

    @Test fun `type changes retain totals while period changes clear them until the local query returns`() =
        runTest {
            val repository = FakeOfflineHistory()
            val model = model(repository)
            runCurrent()
            model.onEvent(OperationHistoryEvent.TypeSelected(OperationType.Income))
            assertEquals(repository.totals.value, model.uiState.value.periodTotals)
            runCurrent()
            model.onEvent(OperationHistoryEvent.PeriodSelected(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30")))
            assertNull(model.uiState.value.periodTotals)
            runCurrent()
            assertTrue(
                model.uiState.value.operations
                    .isEmpty(),
            )
            assertEquals(
                OperationType.Income,
                repository.subscriptions
                    .last()
                    .first.type,
            )
        }

    @Test fun `rapid filter changes leave only the latest local query visible`() =
        runTest {
            val repository = FakeOfflineHistory()
            repository.operations.value =
                listOf(FakeOfflineHistory.purchase("expense"), FakeOfflineHistory.purchase("income", OperationType.Income))
            val model = model(repository)
            runCurrent()
            model.onEvent(OperationHistoryEvent.TypeSelected(OperationType.Expense))
            model.onEvent(OperationHistoryEvent.TypeSelected(OperationType.Income))
            runCurrent()
            repository.operations.value += FakeOfflineHistory.purchase("new-expense")
            runCurrent()
            assertEquals(
                listOf("income"),
                model.uiState.value.operations
                    .map { it.id },
            )
        }

    @Test fun `online corrections and refunds arrive through local observation with their totals`() =
        runTest {
            val repository = FakeOfflineHistory()
            val model = model(repository)
            runCurrent()
            model.onEvent(OperationHistoryEvent.EditRequested("first"))
            model.onEvent(OperationHistoryEvent.AmountChanged("11.01"))
            model.onEvent(OperationHistoryEvent.SaveRequested)
            runCurrent()
            assertNull(model.uiState.value.editor)
            assertEquals(
                "11.01",
                model.uiState.value.operations
                    .single()
                    .amount
                    .toPlainString(),
            )
            assertEquals(repository.totals.value, model.uiState.value.periodTotals)
            model.onEvent(OperationHistoryEvent.EditRequested("first"))
            model.onEvent(OperationHistoryEvent.RefundRequested)
            model.onEvent(OperationHistoryEvent.RefundConfirmed)
            runCurrent()
            assertEquals(1, repository.edits)
            assertEquals(1, repository.refunds)
            assertEquals(
                0,
                model.uiState.value.periodTotals!!
                    .expenses
                    .signum(),
            )
        }

    @Test fun `income pending rejected and offline entries cannot open the online purchase editor`() =
        runTest {
            val repository = FakeOfflineHistory()
            repository.operations.value =
                listOf(
                    FakeOfflineHistory.purchase("income", OperationType.Income),
                    FakeOfflineHistory.purchase("pending").copy(syncStatus = OperationSyncStatus.Pending),
                    FakeOfflineHistory.purchase("rejected").copy(syncStatus = OperationSyncStatus.Rejected),
                )
            val model = model(repository)
            runCurrent()
            for (id in listOf("income", "pending", "rejected")) {
                model.onEvent(OperationHistoryEvent.EditRequested(id))
                assertNull(model.uiState.value.editor)
            }
            repository.operations.value = listOf(FakeOfflineHistory.purchase("first"))
            repository.context.value = repository.context.value.copy(isOnline = false)
            runCurrent()
            model.onEvent(OperationHistoryEvent.EditRequested("first"))
            assertNull(model.uiState.value.editor)
        }

    @Test fun `reversed periods are ignored and a single day is valid`() =
        runTest {
            val model = model(FakeOfflineHistory())
            runCurrent()
            val original = model.uiState.value.filters
            model.onEvent(OperationHistoryEvent.PeriodSelected(LocalDate.parse("2026-10-20"), LocalDate.parse("2026-10-19")))
            assertEquals(original, model.uiState.value.filters)
            model.onEvent(OperationHistoryEvent.PeriodSelected(original.dateTo, original.dateTo))
            runCurrent()
            assertEquals(original.dateTo, model.uiState.value.filters.dateFrom)
        }

    @Test fun `month boundaries and picker days do not depend on device timezone`() {
        val day = LocalDate.parse("2026-01-01")
        assertEquals(day, OperationHistoryFilter.currentMonth(day).dateFrom)
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"))
            assertEquals(day, pickerMillisToDate(day.toPickerMillis()))
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    private fun model(repository: FakeOfflineHistory) =
        OperationHistoryViewModel(
            repository,
            UpdatePurchaseAmount(repository),
            RefundPurchase(repository),
            Clock.fixed(Instant.parse("2026-10-18T05:00:00Z"), ZoneId.of("Asia/Almaty")),
        )
}
