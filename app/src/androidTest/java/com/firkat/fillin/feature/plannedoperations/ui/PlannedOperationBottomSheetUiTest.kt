package com.firkat.fillin.feature.plannedoperations.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.navigation3.runtime.NavEntry
import com.firkat.fillin.feature.categories.domain.model.*
import com.firkat.fillin.feature.categories.domain.repository.CategoriesRepository
import com.firkat.fillin.feature.categories.domain.usecase.GetCategories
import com.firkat.fillin.feature.plannedexpenses.domain.model.*
import com.firkat.fillin.feature.plannedexpenses.domain.repository.PlannedExpensesRepository
import com.firkat.fillin.feature.plannedexpenses.domain.usecase.*
import com.firkat.fillin.feature.plannedexpenses.ui.PlannedExpensesViewModel
import com.firkat.fillin.feature.plannedincomes.domain.model.*
import com.firkat.fillin.feature.plannedincomes.domain.repository.*
import com.firkat.fillin.feature.plannedincomes.domain.usecase.*
import com.firkat.fillin.feature.plannedincomes.ui.PlannedIncomesViewModel
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationType
import com.firkat.fillin.feature.plannedoperations.ui.bottomsheets.PlannedOperationBottomSheet
import com.firkat.fillin.ui.navigation.BottomSheetScene
import com.firkat.fillin.ui.theme.FillinTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
class PlannedOperationBottomSheetUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val models = ViewModelStore()
    private val commands = Commands()
    private var closes = 0

    @After fun cleanup() {
        compose.runOnIdle { models.clear() }
    }

    @Test fun expenseSavingBlocksBackScrimAndSwipeUntilSaveCompletes() {
        show(PlannedOperationType.Expense)
        assertSavingBlocksDismissal()
    }

    @Test fun incomeSavingBlocksBackScrimAndSwipeUntilSaveCompletes() {
        show(PlannedOperationType.Income)
        assertSavingBlocksDismissal()
    }

    @Test fun failedIncomeSaveKeepsInputAndAllowsClosing() {
        commands.failure = PlannedIncomesException.Network
        show(PlannedOperationType.Income)
        compose.onNodeWithTag("planned-editor-amount").performTextReplacement("55.25")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("planned-editor-save").performScrollTo().performClick()
        compose.runOnIdle { commands.gate.complete(Unit) }
        compose.onNodeWithTag("planned-editor-amount").assertIsEnabled().assertTextContains("55.25")
        compose.onNodeWithTag("planned-editor-close").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, closes) }
    }

    @Test fun expenseDeletionClosesTheSheetAfterConfirmation() {
        show(PlannedOperationType.Expense)
        compose.onNodeWithTag("planned-editor-delete").performScrollTo().performClick()
        compose.onNodeWithText("Отмена").performClick()
        compose.onNodeWithText("Отмена").assertDoesNotExist()
        compose.onNodeWithTag("planned-operation-bottom-sheet").assertExists()
        compose.runOnIdle {
            assertEquals(0, closes)
            assertEquals(0, commands.deletions)
        }
        compose.onNodeWithTag("planned-editor-delete").performScrollTo().performClick()
        compose.onNodeWithText("Отключить", ignoreCase = false).performClick()
        compose.runOnIdle { commands.gate.complete(Unit) }
        compose.waitUntil { closes == 1 }
        assertEquals(1, commands.deletions)
    }

    @Test fun incomeDeletionClosesTheSheetAfterConfirmation() {
        show(PlannedOperationType.Income)
        compose.onNodeWithTag("planned-editor-delete").performScrollTo().performClick()
        compose.onNodeWithText("Удалить", ignoreCase = false).performClick()
        compose.runOnIdle { commands.gate.complete(Unit) }
        compose.waitUntil { closes == 1 }
        assertEquals(1, commands.deletions)
    }

    @Test fun onceCreationShowsDateAndSwitchingRestoresScheduleValidation() {
        compose.setContent {
            var editor by remember {
                mutableStateOf(
                    com.firkat.fillin.feature.plannedexpenses.ui.PlannedExpenseRuleEditorUiState(
                        today = DAY,
                        categoryId = "expense",
                        amount = "100",
                        startsOn = DAY.toString()
                    )
                )
            }
            FillinTheme {
                com.firkat.fillin.feature.plannedexpenses.ui.PlannedExpenseRuleEditor(
                    categories = listOf(
                        Category("expense", "Расход", CategoryKind.Expense, CategoryIcon("HOME", "🏠"))
                    ),
                    isCategoriesLoading = false,
                    editor = editor,
                    onEvent = { event ->
                        when (event) {
                            is com.firkat.fillin.feature.plannedexpenses.ui.PlannedExpensesEvent.FrequencyChanged ->
                                editor =
                                    editor.copy(frequency = event.frequency, scheduleValue = "")
                            is com.firkat.fillin.feature.plannedexpenses.ui.PlannedExpensesEvent.StartsOnChanged ->
                                editor =
                                    editor.copy(startsOn = event.startsOn)
                            else -> Unit
                        }
                    }
                )
            }
        }
        compose.onNodeWithText("Одноразовая").assertIsSelected()
        compose.onNodeWithTag("planned-editor-schedule").assertDoesNotExist()
        compose.onNodeWithTag("planned-editor-save").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Ежемесячно").performScrollTo().performClick()
        compose.onNodeWithTag("planned-editor-schedule").assertExists()
        compose.onNodeWithTag("planned-editor-save").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Одноразовая").performScrollTo().performClick()
        compose.onNodeWithTag("planned-editor-starts-on").performTextReplacement(DAY.minusDays(1).toString())
        compose.onNodeWithTag("planned-editor-save").performScrollTo().assertIsNotEnabled()
    }

    private fun assertSavingBlocksDismissal() {
        compose.onNodeWithTag("planned-editor-amount").performTextReplacement("55.25")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("planned-editor-save").performScrollTo().performClick()
        compose.onNodeWithTag("planned-editor-amount").assertIsNotEnabled()
        compose.onNodeWithTag("planned-editor-save").assertIsNotEnabled()
        compose.onNodeWithTag("planned-editor-close").assertIsNotEnabled()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNode(isDialog()).performTouchInput { click(Offset(centerX, 1f)) }
        compose.onNodeWithTag("planned-operation-bottom-sheet").performTouchInput { swipeDown() }
        compose.onNodeWithTag("planned-operation-bottom-sheet").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(0, closes)
            assertEquals(1, commands.attempts)
            commands.gate.complete(Unit)
        }
        compose.waitUntil { closes == 1 }
        assertEquals(BigDecimal("55.25"), commands.savedAmount)
    }

    private fun show(
        type: PlannedOperationType
    ) {
        val expenses = PlannedExpensesViewModel(
            GetPlannedExpenses(commands),
            GetCategories(commands),
            CreatePlannedExpenseRule(commands),
            UpdatePlannedExpenseRule(commands),
            DeletePlannedExpenseRule(commands),
            ClosePlannedExpense(commands),
            editorOnly = true
        )
        val incomes = PlannedIncomesViewModel(
            GetPlannedIncomes(commands),
            GetCategories(commands),
            CreatePlannedIncome(commands),
            UpdatePlannedIncome(commands),
            DeletePlannedIncome(commands),
            editorOnly = true
        )
        models.put("expenses", expenses)
        models.put("incomes", incomes)
        when (type) {
            PlannedOperationType.Expense -> expenses.edit(EXPENSE)
            PlannedOperationType.Income -> incomes.edit(INCOME)
        }
        compose.setContent {
            var visible by remember { mutableStateOf(true) }
            FillinTheme {
                Text("Plans")
                if (visible) {
                    val entry = remember {
                        NavEntry("editor") {
                            PlannedOperationBottomSheet(
                                type,
                                {
                                    closes++
                                    visible = false
                                },
                                expenseEditor = expenses,
                                incomeEditor = incomes
                            )
                        }
                    }
                    val scene = remember {
                        BottomSheetScene(entry, listOf(NavEntry("plans") {}), ModalBottomSheetProperties(), {
                            closes++
                            visible =
                                false
                        })
                    }
                    scene.content()
                }
            }
        }
    }

    private class Commands :
        PlannedExpensesRepository,
        PlannedIncomesRepository,
        CategoriesRepository {
        val gate = CompletableDeferred<Unit>()
        var failure: Exception? = null
        var attempts = 0
        var deletions = 0
        var savedAmount: BigDecimal? = null
        override suspend fun getPlannedExpenses(): PlannedExpenses = error("Unexpected list request")
        override suspend fun getPlannedIncomes(): PlannedIncomes = error("Unexpected list request")
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
            save(amount)
            return PlannedExpenseRule(id, version + 1)
        }
        override suspend fun deleteRule(
            id: String,
            version: Long
        ) {
            gate.await()
            deletions++
        }
        override suspend fun closeExpense(
            id: String,
            version: Long
        ) = error("Unused")
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
            save(requireNotNull(amount))
            return INCOME.copy(version = version + 1, plannedAmount = amount)
        }
        override suspend fun deletePlannedIncome(
            id: String,
            version: Long
        ) {
            gate.await()
            deletions++
        }
        private suspend fun save(
            amount: BigDecimal
        ) {
            attempts++
            gate.await()
            failure?.let { throw it }
            savedAmount = amount
        }
    }
}

private val DAY = LocalDate.of(2026, 10, 25)
private val CREATED = Instant.parse("2026-10-01T00:00:00Z")
private val EXPENSE = PlannedExpense(
    "expense", "home", "Аренда", BigDecimal("100"), BigDecimal.ZERO, BigDecimal("100"), "KZT",
    DAY, false, PlannedExpenseStatus.Scheduled, 0, CREATED, CREATED, ruleId = "rule", ruleVersion = 1
)
private val INCOME = PlannedIncome(
    "income", "salary", "Зарплата",
    BigDecimal(
        "200"
    ),
    "KZT", DAY, false, PlannedIncomeStatus.Scheduled, 0, CREATED, CREATED
)
