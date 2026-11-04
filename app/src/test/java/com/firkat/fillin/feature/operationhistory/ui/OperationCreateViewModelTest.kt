package com.firkat.fillin.feature.operationhistory.ui

import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.feature.operationhistory.domain.repository.NewOperation
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryException
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryStore
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
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
class OperationCreateViewModelTest {
    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `category click saves parsed input offline and closes after local save`() = runTest {
        val repository = FakeOfflineHistory()
        repository.context.value = repository.context.value.copy(isOnline = false)
        val model = model(repository)
        runCurrent()
        model.onEvent(OperationCreateEvent.CreateInputChanged("5000"))
        model.onEvent(OperationCreateEvent.CreateCategoryClicked("expense-category"))
        assertTrue(model.uiState.value.createEditor!!.isSaving)
        runCurrent()
        assertNull(model.uiState.value.createEditor)
        assertEquals("5000.00", repository.created!!.amount.toPlainString())
        assertNull(repository.created!!.description)
        assertEquals(LocalDate.of(2026, 10, 24), repository.created!!.occurredOn)
    }

    @Test fun `invalid repeated attempts emit fresh feedback and editing clears highlighting`() = runTest {
        val repository = FakeOfflineHistory()
        val model = model(repository)
        runCurrent()
        model.onEvent(OperationCreateEvent.CreateCategoryClicked("expense-category"))
        val first = model.uiState.value.createEditor!!
        assertNotNull(first.inputError)
        assertEquals(OperationCreateFeedback.InvalidInput, first.feedback)
        model.onEvent(OperationCreateEvent.CreateFeedbackShown(first.feedbackSequence))
        assertNull(model.uiState.value.createEditor!!.feedback)
        model.onEvent(OperationCreateEvent.CreateCategoryClicked("expense-category"))
        assertEquals(first.feedbackSequence + 1, model.uiState.value.createEditor!!.feedbackSequence)
        model.onEvent(OperationCreateEvent.CreateFeedbackShown(first.feedbackSequence))
        assertNotNull(model.uiState.value.createEditor!!.feedback)
        model.onEvent(OperationCreateEvent.CreateInputChanged("1200"))
        assertNull(model.uiState.value.createEditor!!.inputError)
        assertNull(repository.created)
    }

    @Test fun `missing and mismatched categories cannot save but switching type can`() = runTest {
        val repository = FakeOfflineHistory()
        val model = model(repository)
        runCurrent()
        model.onEvent(OperationCreateEvent.CreateInputChanged("1200,0"))
        listOf("missing", "income-category").forEach {
            model.onEvent(OperationCreateEvent.CreateCategoryClicked(it))
            assertEquals(OperationCreateFeedback.InvalidCategory, model.uiState.value.createEditor!!.feedback)
            assertNull(repository.created)
        }
        model.onEvent(OperationCreateEvent.CreateTypeChanged(OperationType.Income))
        assertEquals("1200,0", model.uiState.value.createEditor!!.input)
        model.onEvent(OperationCreateEvent.CreateCategoryClicked("income-category"))
        runCurrent()
        assertEquals(OperationType.Income, repository.created!!.type)
    }

    @Test fun `pending local save blocks duplicate clicks editing and closing`() = runTest {
        val repository = FakeOfflineHistory()
        val gate = CompletableDeferred<Unit>()
        var attempts = 0
        val history = object : OperationHistoryStore by repository {
            override suspend fun create(operation: NewOperation, replacesRejectedId: String?): com.firkat.fillin.feature.operationhistory.domain.repository.CreateOperationResult {
                attempts++
                gate.await()
                return repository.create(operation, replacesRejectedId)
            }
        }
        val model = model(repository, history)
        runCurrent()
        model.onEvent(OperationCreateEvent.CreateInputChanged("1200"))
        model.onEvent(OperationCreateEvent.CreateCategoryClicked("expense-category"))
        runCurrent()
        model.onEvent(OperationCreateEvent.CreateCategoryClicked("expense-category"))
        model.onEvent(OperationCreateEvent.CreateInputChanged("9999"))
        model.onEvent(OperationCreateEvent.CreateTypeChanged(OperationType.Income))
        model.onEvent(OperationCreateEvent.CreateClosed)
        assertEquals(1, attempts)
        assertEquals("1200", model.uiState.value.createEditor!!.input)
        assertEquals(OperationType.Expense, model.uiState.value.createEditor!!.type)
        gate.complete(Unit)
        runCurrent()
        assertNull(model.uiState.value.createEditor)
    }

    @Test fun `storage failure retains input and allows retry without invalid input highlighting`() = runTest {
        val repository = FakeOfflineHistory().apply { creationError = OperationHistoryException.Unknown }
        val model = model(repository)
        runCurrent()
        model.onEvent(OperationCreateEvent.CreateInputChanged("1200"))
        model.onEvent(OperationCreateEvent.CreateCategoryClicked("expense-category"))
        runCurrent()
        val editor = model.uiState.value.createEditor!!
        assertEquals("1200", editor.input)
        assertFalse(editor.isSaving)
        assertNull(editor.inputError)
        assertEquals(OperationCreateFeedback.SaveFailed, editor.feedback)
        repository.creationError = null
        model.onEvent(OperationCreateEvent.CreateCategoryClicked("expense-category"))
        runCurrent()
        assertNull(model.uiState.value.createEditor)
    }

    @Test fun `category removal and unavailable budget are rechecked at save time`() = runTest {
        val repository = FakeOfflineHistory()
        val model = model(repository)
        runCurrent()
        model.onEvent(OperationCreateEvent.CreateInputChanged("1200"))
        repository.context.value = repository.context.value.copy(categories = emptyList())
        runCurrent()
        model.onEvent(OperationCreateEvent.CreateCategoryClicked("expense-category"))
        assertEquals(OperationCreateFeedback.InvalidCategory, model.uiState.value.createEditor!!.feedback)
        repository.context.value = repository.context.value.copy(initialSyncComplete = false)
        runCurrent()
        model.onEvent(OperationCreateEvent.CreateCategoryClicked("expense-category"))
        assertEquals(OperationCreateFeedback.Unavailable, model.uiState.value.createEditor!!.feedback)
        assertNull(repository.created)
    }

    @Test fun `new operation uses the current Almaty date even if midnight passes while typing`() = runTest {
        var now = Instant.parse("2026-10-24T18:59:59Z")
        val clock = object : Clock() {
            override fun instant() = now
            override fun getZone() = ZoneId.of("Asia/Almaty")
            override fun withZone(zone: ZoneId): Clock = Clock.fixed(now, zone)
        }
        val repository = FakeOfflineHistory()
        val model = model(repository, clock = clock)
        runCurrent()
        assertEquals(LocalDate.of(2026, 10, 24), model.uiState.value.createEditor!!.occurredOn)
        model.onEvent(OperationCreateEvent.CreateInputChanged("1200"))
        now = now.plusSeconds(2)
        model.onEvent(OperationCreateEvent.CreateCategoryClicked("expense-category"))
        runCurrent()
        assertEquals(LocalDate.of(2026, 10, 25), repository.created!!.occurredOn)
    }

    @Test fun `amount rejects text and excess decimals and sheet closes on invalid selection`() = runTest {
        val model = model(FakeOfflineHistory())
        runCurrent()
        model.onEvent(OperationCreateEvent.CreateInputChanged("12,50"))
        listOf("food 12", "12.345", "1,2.3", "-1").forEach {
            model.onEvent(OperationCreateEvent.CreateInputChanged(it))
            assertEquals("12,50", model.uiState.value.createEditor!!.input)
        }
        model.onEvent(OperationCreateEvent.CreateInputChanged(""))
        model.onEvent(OperationCreateEvent.CategoriesOpened)
        assertTrue(model.uiState.value.createEditor!!.categoriesOpen)
        model.onEvent(OperationCreateEvent.CreateCategoryClicked("expense-category"))
        assertFalse(model.uiState.value.createEditor!!.categoriesOpen)
        assertNotNull(model.uiState.value.createEditor!!.inputError)
    }

    private fun model(
        repository: FakeOfflineHistory,
        history: OperationHistoryStore = repository,
        clock: Clock = Clock.fixed(Instant.parse("2026-10-24T05:00:00Z"), ZoneId.of("Asia/Almaty")),
    ) = OperationCreateViewModel(history, clock)
}
