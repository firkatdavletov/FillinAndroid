package com.firkat.fillin.feature.plannedoperations.ui

import com.firkat.fillin.feature.categories.domain.model.*
import com.firkat.fillin.feature.categories.domain.repository.CategoriesRepository
import com.firkat.fillin.feature.categories.domain.usecase.GetCategories
import com.firkat.fillin.feature.plannedexpenses.domain.model.*
import com.firkat.fillin.feature.plannedexpenses.domain.repository.PlannedExpensesRepository
import com.firkat.fillin.feature.plannedincomes.domain.model.*
import com.firkat.fillin.feature.plannedincomes.domain.repository.PlannedIncomesRepository
import com.firkat.fillin.feature.plannedoperations.domain.model.*
import com.firkat.fillin.feature.plannedoperations.domain.repository.PlanningRulesRepository
import com.firkat.fillin.feature.plannedoperations.domain.usecase.ObservePlanningRules
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class PlanningRuleEditorViewModelTest {
    @Before fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After fun teardown() {
        Dispatchers.resetMain()
    }
    private val categories = object : CategoriesRepository {
        override suspend fun getCategories() =
            listOf(Category("food", "Продукты", CategoryKind.Expense, CategoryIcon("HOME")))
        override suspend fun createCategory(
            name: String,
            kind: CategoryKind,
            iconCode: String
        ): Category = error("unused")
        override suspend fun updateCategory(
            id: String,
            name: String?,
            iconCode: String?
        ): Category = error("unused")
        override suspend fun deleteCategory(
            id: String
        ) = Unit
    }
    private class Expenses : PlannedExpensesRepository {
        var fail = false
        var writes = 0
        var start: LocalDate? = null
        var schedule: Int? = null
        override suspend fun saveDetails(
            rule: PlanningRule?,
            name: String,
            categoryId: String,
            amount: BigDecimal,
            frequency: PlannedExpenseFrequency,
            scheduleValue: Int?,
            startsOn: LocalDate
        ) {
            writes++
            start = startsOn
            schedule = scheduleValue
            yield()
            if (fail) error("failed")
        }
        override suspend fun getPlannedExpenses(): PlannedExpenses = error("unused")
        override suspend fun createRule(
            categoryId: String,
            amount: BigDecimal,
            frequency: PlannedExpenseFrequency,
            scheduleValue: Int?,
            startsOn: LocalDate
        ): PlannedExpenseRule = error("unused")
        override suspend fun updateRule(
            id: String,
            version: Long,
            amount: BigDecimal
        ): PlannedExpenseRule = error("unused")
        override suspend fun deleteRule(
            id: String,
            version: Long
        ) = Unit
        override suspend fun closeExpense(
            id: String,
            version: Long
        ) = Unit
    }
    private val incomes = object : PlannedIncomesRepository {
        override suspend fun getPlannedIncomes(): PlannedIncomes = error("unused")
        override suspend fun createPlannedIncome(
            categoryId: String,
            amount: BigDecimal,
            dueOn: LocalDate
        ): PlannedIncome = error("unused")
        override suspend fun updatePlannedIncome(
            id: String,
            version: Long,
            categoryId: String?,
            amount: BigDecimal?,
            dueOn: LocalDate?
        ): PlannedIncome = error("unused")
        override suspend fun deletePlannedIncome(
            id: String,
            version: Long
        ) = Unit
    }
    private fun model(
        expenses: Expenses = Expenses(),
        original: PlanningRule? = null
    ): PlanningRuleEditorViewModel {
        val rules = object : PlanningRulesRepository {
            override fun observeRules() = flowOf(listOfNotNull(original))
            override fun requestSync() = Unit
        }
        return PlanningRuleEditorViewModel(
            ObservePlanningRules(rules),
            GetCategories(categories),
            expenses,
            incomes,
            PlannedOperationType.Expense,
            original?.id
        )
    }

    @Test fun `type switching preserves draft but clears category and unsupported frequency`() = runTest {
        val model = model()
        runCurrent()
        model.category("food")
        model.change { it.copy(amount = "100", frequency = PlanningFrequency.Biweekly) }
        model.type(PlannedOperationType.Income)
        model.type(PlannedOperationType.Expense)
        assertEquals("", model.state.value.draft.categoryId)
        assertEquals("Продукты", model.state.value.draft.name)
        assertEquals("100", model.state.value.draft.amount)
        assertEquals(PlanningFrequency.Once, model.state.value.draft.frequency)
        model.back()
        assertEquals(RuleWindow.Discard, model.state.value.window)
        model.back()
        assertNull(model.state.value.window)
        assertFalse(model.state.value.closed)
    }

    @Test fun `save failure keeps draft and retry closes after local write`() = runTest {
        val expenses = Expenses().apply { fail = true }
        val model = model(expenses)
        runCurrent()
        model.category("food")
        model.change { it.copy(amount = "20,50") }
        model.save()
        model.save()
        model.back()
        assertTrue(model.state.value.saving)
        assertFalse(model.state.value.closed)
        runCurrent()
        assertEquals(1, expenses.writes)
        assertTrue(model.state.value.error)
        assertEquals("20,50", model.state.value.draft.amount)
        expenses.fail = false
        model.save()
        runCurrent()
        assertTrue(model.state.value.closed)
        assertEquals(2, expenses.writes)
    }

    @Test fun `amount edit retains legacy start and day 31 instead of clamped first date`() = runTest {
        val original =
            PlanningRule(
                "r", 3, PlannedOperationType.Expense, "food", "Продукты", "HOME", "Покупки", BigDecimal.TEN,
                frequency = PlanningFrequency.Monthly, startsOn = LocalDate.of(2020, 2, 1), scheduleValue = 31
            )
        val expenses = Expenses()
        val model = model(expenses, original)
        runCurrent()
        assertEquals(LocalDate.of(2020, 2, 29), model.state.value.draft.date)
        model.type(PlannedOperationType.Income)
        assertEquals(PlannedOperationType.Expense, model.state.value.draft.type)
        model.change { it.copy(amount = "12") }
        model.save()
        runCurrent()
        assertEquals(original.startsOn, expenses.start)
        assertEquals(31, expenses.schedule)
        assertTrue(model.state.value.closed)
    }
}
