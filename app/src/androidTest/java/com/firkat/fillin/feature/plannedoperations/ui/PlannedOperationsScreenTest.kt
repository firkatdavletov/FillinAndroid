package com.firkat.fillin.feature.plannedoperations.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.firkat.fillin.R
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpense
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpenseStatus
import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncome
import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncomeStatus
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperation
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationPeriod
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationTotals
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationType
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperations
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationsFilter
import com.firkat.fillin.ui.component.FeatureScaffold
import com.firkat.fillin.ui.format.formatMoney
import com.firkat.fillin.ui.theme.FillinTheme
import java.io.FileInputStream
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlannedOperationsScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun filtersRemainAvailableForAnEmptyListAndEmitTypeAndPeriodSelections() {
        val events = mutableListOf<PlannedOperationsEvent>()
        show(PlannedOperationsUiState(today = TODAY, periodTotals = PlannedOperationTotals()), events)
        composeRule.onNodeWithText("Запланированные операции").assertIsDisplayed()
        composeRule.onNodeWithTag("planned-type-ALL").assertDoesNotExist()
        composeRule.onNodeWithTag("planned-type-EXPENSE").assertIsDisplayed().assertIsNotSelected()
        composeRule.onNodeWithTag("planned-type-INCOME").assertIsDisplayed().assertIsNotSelected()
        composeRule.onNodeWithTag("planned-type-reset").assertDoesNotExist()
        composeRule.onNodeWithTag("planned-total-EXPENSE", useUnmergedTree = true)
            .assertTextEquals(formatMoney(BigDecimal.ZERO, "KZT"))
        composeRule.onNodeWithText("Октябрь").assertIsDisplayed()
        composeRule.onNodeWithTag("date-period-dropdown", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("date-period-reset").assertDoesNotExist()
        val periodBounds = composeRule.onNodeWithTag("planned-period-filter").fetchSemanticsNode().boundsInRoot
        val typeBounds = composeRule.onNodeWithTag("planned-type-EXPENSE").fetchSemanticsNode().boundsInRoot
        assertTrue(periodBounds.bottom <= typeBounds.top)
        composeRule.onNodeWithText("Нет запланированных операций по выбранным фильтрам").assertIsDisplayed()
        screenshot("planned-empty.png")
        composeRule.onNodeWithTag("planned-type-INCOME").performClick()
        composeRule.onNodeWithTag("planned-period-filter").performClick()
        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    PlannedOperationsEvent.TypeSelected(PlannedOperationType.Income),
                    PlannedOperationsEvent.CustomPeriodOpened,
                ),
                events,
            )
        }
    }

    @Test
    fun customPeriodChipOpensCalendarAndResetsOnlyThePeriod() {
        val events = mutableListOf<PlannedOperationsEvent>()
        show(
            PlannedOperationsUiState(
                today = TODAY,
                filters = PlannedOperationsFilter(
                    type = PlannedOperationType.Income,
                    period = PlannedOperationPeriod.Custom,
                    dateFrom = LocalDate.of(2026, 9, 16),
                    dateTo = LocalDate.of(2026, 10, 25),
                ),
            ),
            events,
        )
        composeRule.onNodeWithText("16 Снт - 25 Окт").assertIsDisplayed()
        composeRule.onNodeWithTag("planned-period-filter").assertIsSelected()
        composeRule.onNodeWithTag("date-period-dropdown", useUnmergedTree = true).assertDoesNotExist()
        screenshot("planned-custom-period.png")
        composeRule.onNodeWithTag("planned-period-filter").performClick()
        composeRule.onNodeWithTag("date-period-reset").performClick()
        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    PlannedOperationsEvent.CustomPeriodOpened,
                    PlannedOperationsEvent.PeriodSelected(PlannedOperationPeriod.CurrentMonth),
                ),
                events,
            )
        }
    }

    @Test
    fun calendarAppliesBothInclusiveDatesAndCanBeDismissed() {
        val events = mutableListOf<PlannedOperationsEvent>()
        val filter = PlannedOperationsFilter(period = PlannedOperationPeriod.Custom, dateFrom = TODAY, dateTo = TODAY)
        show(PlannedOperationsUiState(filters = filter, today = TODAY, isPeriodPickerVisible = true), events)
        composeRule.onNodeWithText("Выберите период").assertIsDisplayed()
        screenshot("planned-period.png")
        composeRule.onNodeWithText("Применить").performClick()
        composeRule.onNodeWithTag("planned-period-EndOfYear").performScrollTo().performClick()
        composeRule.onNodeWithText("Отмена").performClick()
        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    PlannedOperationsEvent.CustomPeriodApplied(TODAY, TODAY),
                    PlannedOperationsEvent.PeriodSelected(PlannedOperationPeriod.EndOfYear),
                    PlannedOperationsEvent.CustomPeriodClosed,
                ),
                events,
            )
        }
    }

    @Test
    fun mixedListRetainsExpenseAndIncomeActionsAndShowsSeparateTotals() {
        val actions = mutableListOf<String>()
        show(
            PlannedOperationsUiState(
                today = TODAY,
                operations = PlannedOperations(TODAY, TODAY, LocalDate.of(2026, 10, 31), listOf(
                    PlannedOperation.Expense(EXPENSE), PlannedOperation.Income(INCOME),
                ), totals = TOTALS),
            ),
            mutableListOf(),
            actions,
        )
        composeRule.onNodeWithTag("planned-total-EXPENSE", useUnmergedTree = true)
            .assertTextEquals(formatMoney(TOTALS.expenses, "KZT"))
        composeRule.onNodeWithTag("planned-total-INCOME", useUnmergedTree = true)
            .assertTextEquals(formatMoney(TOTALS.incomes, "KZT"))
        composeRule.onNodeWithText("К оплате").assertDoesNotExist()
        composeRule.onNodeWithText("К получению").assertDoesNotExist()
        composeRule.onNodeWithText("Тестовая аренда").assertIsDisplayed()
        screenshot("planned-mixed.png")
        composeRule.onNodeWithText("Управлять правилом").performScrollTo().performClick()
        composeRule.onNodeWithText("Отметить оплаченным").performScrollTo().performClick()
        composeRule.onNodeWithTag("planned-operations-list").performScrollToIndex(1)
        composeRule.onNodeWithText("Изменить").performScrollTo().performClick()
        composeRule.onNodeWithTag("planned-create").performClick()
        composeRule.onNodeWithTag("planned-create-expense").performClick()
        composeRule.onNodeWithTag("planned-create").performClick()
        composeRule.onNodeWithTag("planned-create-income").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("edit-expense", "close-expense", "edit-income", "create-expense", "create-income"), actions)
        }
    }

    @Test
    fun selectingBlocksExpandsThemFiltersTheListAndResetRestoresBothTypes() {
        val events = mutableListOf<PlannedOperationsEvent>()
        show(
            PlannedOperationsUiState(
                today = TODAY,
                operations = PlannedOperations(TODAY, TODAY, LocalDate.of(2026, 10, 31), listOf(
                    PlannedOperation.Expense(EXPENSE), PlannedOperation.Income(INCOME),
                ), totals = TOTALS),
            ),
            events,
        )
        val row = composeRule.onNodeWithTag("planned-type-totals").fetchSemanticsNode().boundsInRoot
        val initial = composeRule.onNodeWithTag("planned-type-EXPENSE").fetchSemanticsNode().boundsInRoot
        val other = composeRule.onNodeWithTag("planned-type-INCOME").fetchSemanticsNode().boundsInRoot
        assertEquals(initial.width, other.width, 1f)
        PlannedOperationType.entries.forEach { type ->
            val hidden = if (type == PlannedOperationType.Expense) PlannedOperationType.Income else PlannedOperationType.Expense
            composeRule.onNodeWithTag("planned-type-${type.apiValue}").performClick().assertIsSelected()
            composeRule.onNodeWithTag("planned-type-${hidden.apiValue}").assertDoesNotExist()
            val expanded = composeRule.onNodeWithTag("planned-type-${type.apiValue}").fetchSemanticsNode().boundsInRoot
            assertEquals(row.width, expanded.width, 1f)
            val visibleCategory = if (type == PlannedOperationType.Expense) "Тестовая аренда" else "Тестовая зарплата"
            val hiddenCategory = if (type == PlannedOperationType.Expense) "Тестовая зарплата" else "Тестовая аренда"
            composeRule.onNodeWithText(visibleCategory).assertIsDisplayed()
            composeRule.onNodeWithText(hiddenCategory).assertDoesNotExist()
            screenshot("planned-selected-${type.apiValue}.png")
            composeRule.onNodeWithTag("planned-type-reset").performClick()
            composeRule.onNodeWithTag("planned-type-EXPENSE").assertIsDisplayed().assertIsNotSelected()
            composeRule.onNodeWithTag("planned-type-INCOME").assertIsDisplayed().assertIsNotSelected()
            val restored = composeRule.onNodeWithTag("planned-type-EXPENSE").fetchSemanticsNode().boundsInRoot
            assertEquals(initial.width, restored.width, 1f)
            composeRule.onNodeWithTag("planned-total-EXPENSE", useUnmergedTree = true)
                .assertTextEquals(formatMoney(TOTALS.expenses, "KZT"))
            composeRule.onNodeWithTag("planned-total-INCOME", useUnmergedTree = true)
                .assertTextEquals(formatMoney(TOTALS.incomes, "KZT"))
        }
        composeRule.runOnIdle {
            assertEquals(listOf(
                PlannedOperationsEvent.TypeSelected(PlannedOperationType.Expense),
                PlannedOperationsEvent.TypeSelected(null),
                PlannedOperationsEvent.TypeSelected(PlannedOperationType.Income),
                PlannedOperationsEvent.TypeSelected(null),
            ), events)
        }
    }

    @Test
    fun totalsShowLoadingWhileThePeriodIsUnknown() {
        show(PlannedOperationsUiState(today = TODAY, isLoading = true), mutableListOf())
        composeRule.onNodeWithTag("planned-totals-loading-EXPENSE", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("planned-totals-loading-INCOME", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("planned-total-EXPENSE", useUnmergedTree = true).assertDoesNotExist()
    }

    private fun show(
        state: PlannedOperationsUiState,
        events: MutableList<PlannedOperationsEvent>,
        actions: MutableList<String> = mutableListOf(),
    ) {
        var currentState by mutableStateOf(state)
        composeRule.setContent {
            FillinTheme(dynamicColor = false) {
                FeatureScaffold(R.string.planned_operations_title) {
                    PlannedOperationsScreen(
                        state = currentState,
                        onEvent = { event ->
                            events += event
                            if (event is PlannedOperationsEvent.TypeSelected) {
                                currentState = currentState.copy(
                                    filters = currentState.filters.copy(type = event.type),
                                    operations = state.operations?.let { plans ->
                                        plans.copy(items = plans.items.filter { event.type == null || it.type == event.type })
                                    },
                                )
                            }
                        },
                        onCreateExpense = { actions += "create-expense" },
                        onCreateIncome = { actions += "create-income" },
                        onEditExpense = { actions += "edit-expense" },
                        onEditIncome = { actions += "edit-income" },
                        onCloseExpense = { actions += "close-expense" },
                    )
                }
            }
        }
    }

    private fun screenshot(name: String) {
        composeRule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand("screencap -p /data/local/tmp/$name").use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
        }
    }
}

private val TODAY = LocalDate.of(2026, 10, 17)
private val CREATED = Instant.parse("2026-10-01T00:00:00Z")
private val EXPENSE = PlannedExpense(
    id = "expense", categoryId = "rent", categoryName = "Тестовая аренда",
    plannedAmount = BigDecimal("100.00"), paidAmount = BigDecimal("40.00"), remainingAmount = BigDecimal("60.00"),
    currency = "KZT", dueOn = TODAY, overdue = false, status = PlannedExpenseStatus.Scheduled,
    version = 1, createdAt = CREATED, modifiedAt = CREATED, ruleId = "rule", ruleVersion = 2,
)
private val INCOME = PlannedIncome(
    id = "income", categoryId = "salary", categoryName = "Тестовая зарплата", plannedAmount = BigDecimal("200.00"),
    currency = "KZT", dueOn = TODAY.plusDays(7), overdue = false, status = PlannedIncomeStatus.Scheduled,
    version = 0, createdAt = CREATED, modifiedAt = CREATED,
)

private val TOTALS = PlannedOperationTotals(EXPENSE.remainingAmount, INCOME.plannedAmount)
