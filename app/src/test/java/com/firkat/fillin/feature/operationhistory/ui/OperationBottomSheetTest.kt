package com.firkat.fillin.feature.operationhistory.ui

import androidx.lifecycle.ViewModelStore
import com.firkat.fillin.feature.bottomsheets.presentation.TestBottomSheetAction
import com.firkat.fillin.feature.operationhistory.domain.model.OperationStatus
import com.firkat.fillin.feature.operationhistory.domain.model.OperationSyncStatus
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.feature.operationhistory.domain.usecase.RefundPurchase
import com.firkat.fillin.feature.operationhistory.domain.usecase.UpdatePurchaseAmount
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OperationBottomSheetTest {
    private val models = ViewModelStore()
    @Before fun setup() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun cleanup() { models.clear(); Dispatchers.resetMain() }

    @Test fun `sheet actions respect existing editor limitations`() {
        val expense = FakeOfflineHistory.purchase("operation")
        assertEquals(TestBottomSheetAction.Edit, expense.bottomSheetAction())
        assertEquals(TestBottomSheetAction.None, expense.copy(type = OperationType.Income).bottomSheetAction())
        assertEquals(TestBottomSheetAction.None, expense.copy(status = OperationStatus.Voided).bottomSheetAction())
        assertEquals(TestBottomSheetAction.None, expense.copy(originalCurrency = "USD").bottomSheetAction())
        assertEquals(TestBottomSheetAction.Correct, expense.copy(syncStatus = OperationSyncStatus.Rejected).bottomSheetAction())
    }

    @Test fun `returned action uses the latest operation status and ignores missing records`() = runTest {
        val repository = FakeOfflineHistory()
        val model = OperationHistoryViewModel(
            repository, UpdatePurchaseAmount(repository), RefundPurchase(repository),
            Clock.fixed(Instant.parse("2026-10-25T00:00:00Z"), ZoneId.of("Asia/Almaty")),
        ).also { models.put("history", it) }
        runCurrent()
        model.onEvent(OperationHistoryEvent.OperationActionRequested("first"))
        assertEquals("first", model.uiState.value.editor?.original?.id)
        model.onEvent(OperationHistoryEvent.EditorClosed)
        repository.operations.value = listOf(FakeOfflineHistory.purchase("first").copy(syncStatus = OperationSyncStatus.Rejected))
        runCurrent()
        model.onEvent(OperationHistoryEvent.OperationActionRequested("first"))
        assertNull(model.uiState.value.editor)
        assertEquals("first", model.uiState.value.createEditor?.replacesRejectedId)
        model.onEvent(OperationHistoryEvent.CreateClosed)
        repository.operations.value = emptyList()
        runCurrent()
        model.onEvent(OperationHistoryEvent.OperationActionRequested("first"))
        assertNull(model.uiState.value.editor)
        assertNull(model.uiState.value.createEditor)
    }
}
