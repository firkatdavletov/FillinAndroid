package com.firkat.fillin.feature.operationhistory.ui

import androidx.lifecycle.ViewModelStore
import com.firkat.fillin.feature.operationhistory.domain.model.*
import com.firkat.fillin.feature.operationhistory.domain.repository.*
import com.firkat.fillin.feature.operationhistory.domain.usecase.*
import com.firkat.fillin.ui.navigation.BottomSheetDismissController
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditOperationViewModelTest {
    private val models = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun cleanup() { models.clear(); Dispatchers.resetMain() }

    @Test fun `amount validation accepts exact positive decimals and requires a change`() = runTest {
        val repository = FakeOfflineHistory()
        val model = model(repository)
        runCurrent()
        assertFalse(model.uiState.value.canSave)
        for (invalid in listOf("", "0", "-1", "+1", "1.001", "1.", "1 000", "12 products", "100000000000000000")) {
            model.onEvent(EditOperationEvent.InputChanged(invalid))
            assertFalse(invalid, model.uiState.value.canSave)
        }
        for (valid in listOf("12", "12.01", "12,01", " 12.01 ")) {
            model.onEvent(EditOperationEvent.InputChanged(valid))
            assertTrue(valid, model.uiState.value.canSave)
        }
        model.onEvent(EditOperationEvent.InputChanged("10,0"))
        assertFalse(model.uiState.value.canSave)
        model.onEvent(EditOperationEvent.InputChanged("invalid"))
        model.onEvent(EditOperationEvent.SaveClicked)
        assertNotNull(model.uiState.value.inputError)
        model.onEvent(EditOperationEvent.InputChanged("12.34"))
        assertNull(model.uiState.value.inputError)
    }

    @Test fun `only posted KZT expenses can be edited or refunded`() = runTest {
        val purchase = FakeOfflineHistory.purchase("first")
        for (operation in listOf(purchase.copy(type = OperationType.Income), purchase.copy(status = OperationStatus.Voided), purchase.copy(originalCurrency = "USD"))) {
            val repository = FakeOfflineHistory().apply { operations.value = listOf(operation) }
            val model = model(repository)
            runCurrent()
            assertEquals(EditOperationMode.ReadOnly, model.uiState.value.mode)
            assertFalse(model.uiState.value.canSave)
            assertFalse(model.uiState.value.canRefund)
        }
        val repository = FakeOfflineHistory().apply { operations.value = listOf(purchase.copy(refundedAmount = BigDecimal("2"))) }
        val partial = model(repository)
        runCurrent()
        partial.onEvent(EditOperationEvent.InputChanged("20"))
        assertFalse(partial.uiState.value.canSave)
        assertTrue(partial.uiState.value.canRefund)
        repository.operations.value = listOf(purchase.copy(refundedAmount = purchase.originalAmount))
        runCurrent()
        assertFalse(partial.uiState.value.canRefund)
    }

    @Test fun `pending offline save blocks edits duplicate requests and all dismissal callbacks`() = runTest {
        val repository = FakeOfflineHistory()
        repository.context.value = repository.context.value.copy(isOnline = false)
        val gate = CompletableDeferred<Unit>()
        var attempts = 0
        val commands = object : OperationHistoryRepository by repository {
            override suspend fun updatePurchase(id: String, version: Long, amount: BigDecimal): Operation {
                attempts++
                gate.await()
                return repository.updatePurchase(id, version, amount)
            }
        }
        val model = model(repository, commands)
        val effects = mutableListOf<EditOperationEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.effects.toList(effects) }
        val dismissal = BottomSheetDismissController().apply { canDismiss = { model.canDismiss } }
        runCurrent()
        model.onEvent(EditOperationEvent.InputChanged("19,25"))
        model.onEvent(EditOperationEvent.SaveClicked)
        assertTrue(model.uiState.value.isSaving)
        assertFalse(dismissal.canDismiss())
        runCurrent()
        model.onEvent(EditOperationEvent.SaveClicked)
        model.onEvent(EditOperationEvent.InputChanged("999"))
        model.onEvent(EditOperationEvent.CloseClicked)
        model.onEvent(EditOperationEvent.RefundClicked)
        assertEquals("19,25", model.uiState.value.input)
        assertFalse(model.uiState.value.canSave)
        assertFalse(model.uiState.value.canRefund)
        assertEquals(1, attempts)
        assertTrue(effects.isEmpty())
        gate.complete(Unit)
        runCurrent()
        assertFalse(dismissal.canDismiss())
        assertEquals(listOf(EditOperationEffect.Saved(EditOperationResult("first", EditOperationOutcome.Updated))), effects)
        model.onEvent(EditOperationEvent.CloseClicked)
        model.onEvent(EditOperationEvent.SaveClicked)
        assertEquals(1, effects.size)
        assertEquals(BigDecimal("19.25"), repository.operations.value.single().amount)
    }

    @Test fun `failure keeps draft unlocks dismissal and permits retry without a success result`() = runTest {
        val repository = FakeOfflineHistory()
        var fail = true
        val commands = object : OperationHistoryRepository by repository {
            override suspend fun updatePurchase(id: String, version: Long, amount: BigDecimal): Operation {
                if (fail) throw OperationHistoryException.Network
                return repository.updatePurchase(id, version, amount)
            }
        }
        val model = model(repository, commands)
        val effects = mutableListOf<EditOperationEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.effects.toList(effects) }
        runCurrent()
        model.onEvent(EditOperationEvent.InputChanged("19.00"))
        model.onEvent(EditOperationEvent.SaveClicked)
        runCurrent()
        assertEquals("19.00", model.uiState.value.input)
        assertEquals(OperationHistoryError.Network, model.uiState.value.error)
        assertFalse(model.uiState.value.isSaving)
        assertTrue(effects.isEmpty())
        fail = false
        model.onEvent(EditOperationEvent.SaveClicked)
        runCurrent()
        assertEquals(1, effects.size)
    }

    @Test fun `rejected correction preserves date description and replacement id and can change type and category`() = runTest {
        val repository = FakeOfflineHistory()
        val date = LocalDate.of(2026, 10, 18)
        repository.operations.value = listOf(FakeOfflineHistory.purchase("first").copy(syncStatus = OperationSyncStatus.Rejected, description = "нож для овощей", occurredOn = date))
        val model = model(repository)
        runCurrent()
        assertEquals(EditOperationMode.Correct, model.uiState.value.mode)
        assertEquals("нож для овощей 10.00", model.uiState.value.input)
        model.onEvent(EditOperationEvent.TypeChanged(OperationType.Income))
        assertNull(model.uiState.value.categoryId)
        assertFalse(model.uiState.value.canSave)
        model.onEvent(EditOperationEvent.CategorySelected("expense-category"))
        assertNull(model.uiState.value.categoryId)
        model.onEvent(EditOperationEvent.CategorySelected("income-category"))
        model.onEvent(EditOperationEvent.SaveClicked)
        runCurrent()
        assertEquals("first", repository.replaced)
        assertEquals(date, repository.created!!.occurredOn)
        assertEquals("нож для овощей", repository.created!!.description)
        assertEquals(OperationType.Income, repository.created!!.type)
        assertEquals("income-category", repository.created!!.categoryId)
        assertEquals(listOf("created"), repository.operations.value.map { it.id })
    }

    @Test fun `removed category and unready budget disable correction`() = runTest {
        val repository = FakeOfflineHistory()
        repository.operations.value = listOf(FakeOfflineHistory.purchase("first").copy(syncStatus = OperationSyncStatus.Rejected))
        val model = model(repository)
        runCurrent()
        assertTrue(model.uiState.value.canSave)
        repository.context.value = repository.context.value.copy(categories = emptyList())
        runCurrent()
        assertFalse(model.uiState.value.canSave)
        repository.context.value = repository.context.value.copy(initialSyncComplete = false)
        runCurrent()
        model.onEvent(EditOperationEvent.SaveClicked)
        assertNull(repository.created)
    }

    @Test fun `latest status and version are revalidated without discarding draft`() = runTest {
        val repository = FakeOfflineHistory()
        val model = model(repository)
        runCurrent()
        model.onEvent(EditOperationEvent.InputChanged("19.00"))
        repository.operations.value = listOf(repository.operations.value.single().copy(version = 1, amount = BigDecimal("14"), originalAmount = BigDecimal("14")))
        runCurrent()
        model.onEvent(EditOperationEvent.SaveClicked)
        assertEquals(OperationHistoryError.Conflict, model.uiState.value.error)
        assertEquals("19.00", model.uiState.value.input)
        assertEquals(0, repository.edits)
        repository.operations.value = emptyList()
        runCurrent()
        assertEquals(OperationHistoryError.NotFound, model.uiState.value.error)
        assertFalse(model.uiState.value.canSave)
    }

    @Test fun `sync acknowledgement preserves draft and uses the acknowledged version`() = runTest {
        val repository = FakeOfflineHistory()
        repository.operations.value = listOf(FakeOfflineHistory.purchase("first").copy(syncStatus = OperationSyncStatus.Pending))
        val model = model(repository)
        runCurrent()
        model.onEvent(EditOperationEvent.InputChanged("19.00"))
        repository.operations.value = listOf(repository.operations.value.single().copy(version = 1, syncStatus = OperationSyncStatus.Synced))
        runCurrent()
        assertEquals("19.00", model.uiState.value.input)
        model.onEvent(EditOperationEvent.SaveClicked)
        runCurrent()
        assertEquals(1, repository.edits)
        assertEquals(2L, repository.operations.value.single().version)
    }

    @Test fun `loading does not report missing until the local budget is ready`() = runTest {
        val repository = FakeOfflineHistory()
        repository.context.value = repository.context.value.copy(initialSyncComplete = false)
        repository.operations.value = emptyList()
        val model = model(repository)
        runCurrent()
        assertTrue(model.uiState.value.isLoading)
        assertNull(model.uiState.value.error)
        repository.context.value = repository.context.value.copy(initialSyncComplete = true)
        runCurrent()
        assertEquals(OperationHistoryError.NotFound, model.uiState.value.error)
        repository.operations.value = listOf(FakeOfflineHistory.purchase("first"))
        runCurrent()
        assertFalse(model.uiState.value.isLoading)
        assertNull(model.uiState.value.error)
        assertEquals(EditOperationMode.Edit, model.uiState.value.mode)
    }

    @Test fun `refund requires confirmation and emits a distinct result`() = runTest {
        val repository = FakeOfflineHistory()
        val model = model(repository)
        val effects = mutableListOf<EditOperationEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.effects.toList(effects) }
        runCurrent()
        model.onEvent(EditOperationEvent.RefundConfirmed)
        assertEquals(0, repository.refunds)
        model.onEvent(EditOperationEvent.RefundClicked)
        assertTrue(model.uiState.value.isRefundConfirmationVisible)
        model.onEvent(EditOperationEvent.RefundDismissed)
        assertFalse(model.uiState.value.isRefundConfirmationVisible)
        model.onEvent(EditOperationEvent.RefundClicked)
        model.onEvent(EditOperationEvent.RefundConfirmed)
        runCurrent()
        assertEquals(1, repository.refunds)
        assertEquals(listOf(EditOperationEffect.Saved(EditOperationResult("first", EditOperationOutcome.Refunded))), effects)
    }

    @Test fun `ordinary dismissal emits close only once`() = runTest {
        val model = model(FakeOfflineHistory())
        val effects = mutableListOf<EditOperationEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.effects.toList(effects) }
        model.onEvent(EditOperationEvent.CloseClicked)
        model.onEvent(EditOperationEvent.CloseClicked)
        assertEquals(listOf(EditOperationEffect.Close), effects)
    }

    private fun model(history: FakeOfflineHistory, commands: OperationHistoryRepository = history) =
        EditOperationViewModel("first", history, UpdatePurchaseAmount(commands), RefundPurchase(commands),
            Clock.fixed(Instant.parse("2026-10-25T05:00:00Z"), ZoneId.of("Asia/Almaty")))
            .also { models.put("editor-${System.identityHashCode(it)}", it) }
}
