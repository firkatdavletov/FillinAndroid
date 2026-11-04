package com.firkat.fillin.feature.plannedoperations.ui

import androidx.lifecycle.SavedStateHandle
import com.firkat.fillin.feature.plannedoperations.domain.model.*
import com.firkat.fillin.feature.plannedoperations.domain.repository.*
import com.firkat.fillin.feature.plannedoperations.domain.usecase.ObservePlanningRules
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.math.BigDecimal
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class PlannedOperationsViewModelTest {
    @Before fun setup() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun cleanup() = Dispatchers.resetMain()
    private val today = LocalDate.of(2026, 2, 15)
    private fun rule(id: String, frequency: PlanningFrequency = PlanningFrequency.Monthly, type: PlannedOperationType = PlannedOperationType.Expense) =
        PlanningRule(id, 2, type, "cat", "Category", "HOME", id, BigDecimal.TEN,
            frequency = frequency, startsOn = today.withDayOfMonth(1), scheduleValue = 31)

    @Test fun `filters and collapsed groups restore independently of repository updates`() = runTest {
        val repository = FakeRepository()
        val saved = SavedStateHandle()
        val model = PlannedOperationsViewModel(ObservePlanningRules(repository), saved)
        runCurrent()
        model.onEvent(PlannedOperationsEvent.TypeSelected(PlannedOperationType.Income))
        model.onEvent(PlannedOperationsEvent.GroupToggled(PlanningFrequency.Monthly))
        repository.rules.value = listOf(rule("expense"), rule("salary", type = PlannedOperationType.Income))
        runCurrent()
        assertEquals(listOf("salary"), model.uiState.value.groups.single().rules.map { it.id })
        assertEquals(setOf(PlanningFrequency.Monthly), model.uiState.value.collapsed)
        val restored = PlannedOperationsViewModel(ObservePlanningRules(repository), saved)
        assertEquals(PlannedOperationType.Income, restored.uiState.value.selectedType)
        assertEquals(model.uiState.value.collapsed, restored.uiState.value.collapsed)
    }

    @Test fun `groups sort by frequency and date before name and stable key`() {
        val state = PlannedOperationsUiState(today = today, rules = listOf(
            rule("Z").copy(startsOn = today.plusMonths(2)), rule("B"), rule("A"),
            rule("past", PlanningFrequency.Once).copy(startsOn = today.minusDays(2)),
            rule("future", PlanningFrequency.Once).copy(startsOn = today.plusDays(2))))
        assertEquals(listOf(PlanningFrequency.Once, PlanningFrequency.Monthly), state.groups.map { it.frequency })
        assertEquals(listOf("past", "future"), state.groups.first().rules.map { it.id })
        assertEquals(listOf("A", "B", "Z"), state.groups.last().rules.map { it.id })
    }

    @Test fun `refresh preserves data and selected type`() = runTest {
        val repository = FakeRepository()
        val model = PlannedOperationsViewModel(ObservePlanningRules(repository))
        runCurrent()
        repository.rules.value = listOf(rule("a")); runCurrent()
        model.onEvent(PlannedOperationsEvent.TypeSelected(PlannedOperationType.Expense))
        model.onEvent(PlannedOperationsEvent.Refresh); runCurrent()
        assertEquals(1, repository.syncs)
        assertEquals(1, model.uiState.value.rules.size)
        assertEquals(PlannedOperationType.Expense, model.uiState.value.selectedType)
    }

    @Test fun `calendar clamps month days and respects future anchors`() {
        val monthly = rule("month")
        assertEquals(LocalDate.of(2026, 2, 28), monthly.nextDate(today))
        assertEquals(LocalDate.of(2026, 3, 31), monthly.nextDate(LocalDate.of(2026, 3, 1)))
        assertEquals(LocalDate.of(2028, 2, 29), monthly.nextDate(LocalDate.of(2028, 2, 1)))
        val weekly = rule("week", PlanningFrequency.Weekly).copy(startsOn = LocalDate.of(2026, 3, 2), scheduleValue = 1)
        assertEquals(weekly.startsOn, weekly.nextDate(today))
        val biweekly = weekly.copy(frequency = PlanningFrequency.Biweekly)
        assertEquals(LocalDate.of(2026, 3, 16), biweekly.nextDate(LocalDate.of(2026, 3, 3)))
    }

    @Test fun `refresh failure keeps catalog but access loss clears it`() = runTest {
        val repository = FakeRepository()
        val model = PlannedOperationsViewModel(ObservePlanningRules(repository))
        repository.rules.value = listOf(rule("a"))
        runCurrent()
        repository.failure = PlannedOperationsException.Network
        model.onEvent(PlannedOperationsEvent.Refresh); runCurrent()
        assertEquals(1, model.uiState.value.rules.size)
        assertEquals(PlannedOperationsException.Network, model.uiState.value.error)
        repository.failure = PlannedOperationsException.Forbidden
        model.onEvent(PlannedOperationsEvent.Retry); runCurrent()
        assertTrue(model.uiState.value.rules.isEmpty())
        assertFalse(model.uiState.value.isLoading)
        repository.failure = null
        model.onEvent(PlannedOperationsEvent.Retry); runCurrent()
        assertEquals(1, model.uiState.value.rules.size)
        assertNull(model.uiState.value.error)
    }

    private class FakeRepository : PlanningRulesRepository {
        val rules = MutableStateFlow<List<PlanningRule>>(emptyList())
        var syncs = 0
        var failure: PlannedOperationsException? = null
        override fun observeRules(): Flow<List<PlanningRule>> = flow {
            failure?.let { throw it }
            emitAll(rules)
        }
        override fun requestSync() { syncs++ }
    }
}
