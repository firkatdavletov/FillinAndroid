package com.firkat.fillin.feature.plannedoperations.ui

import com.firkat.fillin.feature.categories.domain.model.Category
import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import com.firkat.fillin.feature.categories.domain.repository.CategoriesRepository
import com.firkat.fillin.feature.categories.domain.usecase.GetCategories
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpense
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpenseFrequency
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpenseRule
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpenseStatus
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpenses
import com.firkat.fillin.feature.plannedexpenses.domain.repository.PlannedExpensesRepository
import com.firkat.fillin.feature.plannedexpenses.domain.usecase.ClosePlannedExpense
import com.firkat.fillin.feature.plannedexpenses.domain.usecase.CreatePlannedExpenseRule
import com.firkat.fillin.feature.plannedexpenses.domain.usecase.DeletePlannedExpenseRule
import com.firkat.fillin.feature.plannedexpenses.domain.usecase.GetPlannedExpenses
import com.firkat.fillin.feature.plannedexpenses.domain.usecase.UpdatePlannedExpenseRule
import com.firkat.fillin.feature.plannedexpenses.ui.PlannedExpensesEvent
import com.firkat.fillin.feature.plannedexpenses.ui.PlannedExpensesViewModel
import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncome
import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncomeStatus
import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncomes
import com.firkat.fillin.feature.plannedincomes.domain.repository.PlannedIncomesRepository
import com.firkat.fillin.feature.plannedincomes.domain.usecase.CreatePlannedIncome
import com.firkat.fillin.feature.plannedincomes.domain.usecase.DeletePlannedIncome
import com.firkat.fillin.feature.plannedincomes.domain.usecase.GetPlannedIncomes
import com.firkat.fillin.feature.plannedincomes.domain.usecase.UpdatePlannedIncome
import com.firkat.fillin.feature.plannedincomes.ui.PlannedIncomesEvent
import com.firkat.fillin.feature.plannedincomes.ui.PlannedIncomesViewModel
import com.firkat.fillin.feature.plannedoperations.domain.model.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlannedOperationEditorsTest {
    @Test fun `once is default and date and schedule validation follow selected frequency`() {
        val day = LocalDate.parse("2026-11-04")
        val editor = com.firkat.fillin.feature.plannedexpenses.ui.PlannedExpenseRuleEditorUiState(
            today = day,
            categoryId = "home",
            amount = "100",
            startsOn = day.toString()
        )
        assertEquals(PlannedExpenseFrequency.Once, editor.frequency)
        org.junit.Assert.assertTrue(editor.canSave)
        org.junit.Assert.assertFalse(editor.copy(startsOn = day.minusDays(1).toString()).canSave)
        org.junit.Assert.assertFalse(editor.copy(categoryId = "").canSave)
        org.junit.Assert.assertFalse(editor.copy(amount = "0").canSave)
        org.junit.Assert.assertFalse(editor.copy(frequency = PlannedExpenseFrequency.Monthly).canSave)
        org.junit.Assert.assertTrue(
            editor.copy(frequency = PlannedExpenseFrequency.Monthly, scheduleValue = "31").canSave
        )
        org.junit.Assert.assertTrue(
            editor.copy(frequency = PlannedExpenseFrequency.Weekly, scheduleValue = "7").canSave
        )
        org.junit.Assert.assertFalse(
            editor.copy(frequency = PlannedExpenseFrequency.Weekly, scheduleValue = "8").canSave
        )
    }

    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `expense editor uses the selected unified item rule version without loading legacy lists`() = runTest {
        val repository = ExpenseCommands()
        val model = expenseEditor(repository)
        runCurrent()
        model.edit(EXPENSE)
        model.onEvent(PlannedExpensesEvent.AmountChanged("120"))
        model.onEvent(PlannedExpensesEvent.SaveRequested)
        runCurrent()
        assertEquals(listOf("update:rule:3:120"), repository.commands)
        assertNull(model.uiState.value.editor)
        assertEquals(1L, model.uiState.value.changeVersion)

        model.edit(EXPENSE.copy(ruleVersion = 4))
        model.onEvent(PlannedExpensesEvent.DeleteRequested)
        model.onEvent(PlannedExpensesEvent.DeleteConfirmed)
        runCurrent()
        assertEquals("delete:rule:4", repository.commands.last())
        assertEquals(2L, model.uiState.value.changeVersion)
    }

    @Test
    fun `close confirmation uses occurrence version and notifies unified list once`() = runTest {
        val repository = ExpenseCommands()
        val model = expenseEditor(repository)
        model.requestClose(EXPENSE)
        assertEquals(EXPENSE.id, model.uiState.value.closeConfirmationExpenseId)
        model.onEvent(PlannedExpensesEvent.CloseConfirmed)
        model.onEvent(PlannedExpensesEvent.CloseConfirmed)
        runCurrent()
        assertEquals(listOf("close:expense:1"), repository.commands)
        assertNull(model.uiState.value.closeConfirmationExpenseId)
        assertEquals(1L, model.uiState.value.changeVersion)
    }

    @Test
    fun `income editor preserves update and delete and invalidates only unified list`() = runTest {
        val repository = IncomeCommands()
        val model = PlannedIncomesViewModel(
            GetPlannedIncomes(repository),
            GetCategories(NoCategories()),
            CreatePlannedIncome(repository),
            UpdatePlannedIncome(repository),
            DeletePlannedIncome(repository),
            editorOnly = true
        )
        runCurrent()
        model.edit(INCOME)
        model.onEvent(PlannedIncomesEvent.AmountChanged("250"))
        model.onEvent(PlannedIncomesEvent.SaveRequested)
        runCurrent()
        assertEquals(listOf("update:income:0:250"), repository.commands)
        assertNull(model.uiState.value.editor)
        assertEquals(1L, model.uiState.value.changeVersion)

        model.edit(INCOME.copy(version = 1))
        model.onEvent(PlannedIncomesEvent.DeleteRequested)
        model.onEvent(PlannedIncomesEvent.DeleteConfirmed)
        runCurrent()
        assertEquals("delete:income:1", repository.commands.last())
        assertEquals(2L, model.uiState.value.changeVersion)
    }

    @Test fun `catalog expense edits and deletes source rule version without a payment`() = runTest {
        val repository = ExpenseCommands()
        val model = expenseEditor(repository)
        val rule = PlanningRule(
            "source", 42, PlannedOperationType.Expense, "rent", "Rent", "HOME", "Rent",
            BigDecimal("100"), frequency = PlanningFrequency.Monthly, startsOn = DAY, scheduleValue = 31
        )
        model.edit(rule)
        assertNull(model.uiState.value.editor?.original)
        model.onEvent(PlannedExpensesEvent.AmountChanged("120"))
        model.onEvent(PlannedExpensesEvent.SaveRequested)
        runCurrent()
        assertEquals(listOf("update:source:42:120"), repository.commands)
        model.edit(rule.copy(version = 43))
        model.onEvent(PlannedExpensesEvent.DeleteRequested)
        model.onEvent(PlannedExpensesEvent.DeleteConfirmed)
        runCurrent()
        assertEquals("delete:source:43", repository.commands.last())
    }

    @Test fun `catalog income edits original anchor and retains input on failure`() = runTest {
        val repository = IncomeCommands()
        val model = PlannedIncomesViewModel(
            GetPlannedIncomes(repository),
            GetCategories(NoCategories()),
            CreatePlannedIncome(repository),
            UpdatePlannedIncome(repository),
            DeletePlannedIncome(repository),
            editorOnly = true,
            repository = repository
        )
        val rule =
            PlanningRule(
                "source-income", 12, PlannedOperationType.Income, "salary", "Salary", "INCOME", "Salary",
                BigDecimal("200"), frequency = PlanningFrequency.Monthly, startsOn = DAY, isIncomeRule = true
            )
        model.edit(rule)
        assertEquals(DAY.toString(), model.uiState.value.editor?.dueOn)
        assertNull(model.uiState.value.editor?.original)
        model.onEvent(PlannedIncomesEvent.AmountChanged("250"))
        model.onEvent(PlannedIncomesEvent.SaveRequested)
        runCurrent()
        assertEquals("rule:source-income:12:$DAY:250", repository.commands.last())
        assertNull(model.uiState.value.editor)
        repository.fail = true
        model.edit(rule)
        model.onEvent(PlannedIncomesEvent.AmountChanged("350"))
        model.onEvent(PlannedIncomesEvent.SaveRequested)
        runCurrent()
        assertEquals("350", model.uiState.value.editor?.amount)
        org.junit.Assert.assertNotNull(model.uiState.value.editor?.error)
    }

    private fun expenseEditor(
        repository: PlannedExpensesRepository
    ) = PlannedExpensesViewModel(
        GetPlannedExpenses(repository),
        GetCategories(NoCategories()),
        CreatePlannedExpenseRule(repository),
        UpdatePlannedExpenseRule(repository),
        DeletePlannedExpenseRule(repository),
        ClosePlannedExpense(repository),
        editorOnly = true
    )
}

private class ExpenseCommands : PlannedExpensesRepository {
    val commands = mutableListOf<String>()
    override suspend fun getPlannedExpenses(): PlannedExpenses = error("Legacy list must not be requested")
    override suspend fun createRule(
        categoryId: String,
        amount: BigDecimal,
        frequency: PlannedExpenseFrequency,
        scheduleValue: Int?,
        startsOn: LocalDate
    ): PlannedExpenseRule = error("Unused")
    override suspend fun updateRule(
        id: String,
        version: Long,
        amount: BigDecimal
    ): PlannedExpenseRule {
        commands += "update:$id:$version:$amount"
        return PlannedExpenseRule(id, version + 1)
    }
    override suspend fun deleteRule(
        id: String,
        version: Long
    ) {
        commands += "delete:$id:$version"
    }
    override suspend fun closeExpense(
        id: String,
        version: Long
    ) {
        commands += "close:$id:$version"
    }
}

private class IncomeCommands : PlannedIncomesRepository {
    val commands = mutableListOf<String>()
    var fail = false
    override suspend fun saveRuleDetails(
        rule: PlanningRule,
        categoryId: String,
        amount: BigDecimal,
        dueOn: LocalDate,
        name: String,
        frequency: com.firkat.fillin.feature.dailylimit.domain.IncomeFrequency,
        active: Boolean
    ) {
        if (fail) throw com.firkat.fillin.feature.plannedincomes.domain.repository.PlannedIncomesException.Network
        commands += "rule:${rule.id}:${rule.version}:$dueOn:$amount"
    }
    override suspend fun getPlannedIncomes(): PlannedIncomes = error("Legacy list must not be requested")
    override suspend fun createPlannedIncome(
        categoryId: String,
        amount: BigDecimal,
        dueOn: LocalDate
    ): PlannedIncome = error("Unused")
    override suspend fun updatePlannedIncome(
        id: String,
        version: Long,
        categoryId: String?,
        amount: BigDecimal?,
        dueOn: LocalDate?
    ): PlannedIncome {
        commands += "update:$id:$version:$amount"
        return INCOME.copy(version = version + 1, plannedAmount = requireNotNull(amount))
    }
    override suspend fun deletePlannedIncome(
        id: String,
        version: Long
    ) {
        commands += "delete:$id:$version"
    }
}

private class NoCategories : CategoriesRepository {
    override suspend fun getCategories(): List<Category> = emptyList()
    override suspend fun createCategory(
        name: String,
        kind: CategoryKind,
        iconCode: String
    ): Category = error("Unused")
    override suspend fun updateCategory(
        id: String,
        name: String?,
        iconCode: String?
    ): Category = error("Unused")
    override suspend fun deleteCategory(
        id: String
    ) = error("Unused")
}

private val DAY = LocalDate.of(2026, 10, 17)
private val CREATED = Instant.parse("2026-10-01T00:00:00Z")
private val EXPENSE = PlannedExpense(
    "expense", "rent", "Тестовый расход", BigDecimal("100"), BigDecimal("40"), BigDecimal("60"), "KZT",
    DAY, false, PlannedExpenseStatus.Scheduled, 1, CREATED, CREATED, ruleId = "rule", ruleVersion = 3
)
private val INCOME = PlannedIncome(
    "income", "salary", "Тестовый доход", BigDecimal("200"), "KZT", DAY,
    false, PlannedIncomeStatus.Scheduled, 0, CREATED, CREATED
)
