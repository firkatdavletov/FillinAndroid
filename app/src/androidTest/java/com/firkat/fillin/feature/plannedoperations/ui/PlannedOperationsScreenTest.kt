package com.firkat.fillin.feature.plannedoperations.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.firkat.fillin.R
import com.firkat.fillin.feature.plannedoperations.domain.model.*
import com.firkat.fillin.ui.theme.FillinTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class PlannedOperationsScreenTest {
    @get:Rule val compose = createComposeRule()

    private val day = LocalDate.of(2026, 11, 4)
    private val expense = PlanningRule("rent", 3, PlannedOperationType.Expense, "cat", "Жильё", "HOME", "Жильё", BigDecimal("20000"), frequency = PlanningFrequency.Monthly, startsOn = day, scheduleValue = 5)
    private val income = expense.copy(id = "salary", type = PlannedOperationType.Income, name = "Зарплата")
    private val once = expense.copy(id = "repair", name = "Ремонт", frequency = PlanningFrequency.Once)

    @Test fun filtersAndResetsOpenGroupsAndKeepsCounts() {
        var state by mutableStateOf(PlannedOperationsUiState(rules = listOf(expense, income, once), isLoading = false, today = day))
        var edited: PlanningRule? = null
        compose.setContent {
            FillinTheme {
                PlannedOperationsScreen(
                    state = state,
                    onEvent = { event ->
                        if (event is PlannedOperationsEvent.TypeSelected) state = state.copy(selectedType = event.type)
                    },
                    onCreateExpense = {},
                    onCreateIncome = {},
                    onEditRule = { edited = it },
                )
            }
        }

        compose.onNodeWithText("Разовые").assertIsDisplayed().assertHasNoClickAction()
        compose.onNodeWithText("Ежемесячные").assertIsDisplayed().assertHasNoClickAction()
        compose.onNodeWithTag("planning-rule-${once.key}").assertIsDisplayed()
        compose.onNodeWithTag("planning-rule-${income.key}").assertIsDisplayed()
        compose.onNodeWithTag("planning-rule-${expense.key}").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(expense, edited) }

        compose.onNodeWithTag("planning-filter-Expense").assertTextContains(planCount(2))
        compose.onNodeWithTag("planning-filter-Income").assertTextContains(planCount(1)).performClick()
        compose.onNodeWithTag("planning-rule-${expense.key}").assertDoesNotExist()
        compose.onNodeWithTag("planning-rule-${once.key}").assertDoesNotExist()
        compose.onNodeWithTag("planning-rule-${income.key}").assertIsDisplayed()
        compose.onNode(hasText(planCount(1)) and hasAnyAncestor(hasTestTag("planning-filter-selected"))).assertIsDisplayed()
        compose.onNodeWithTag("planning-filter-Expense").assertDoesNotExist()

        compose.onNodeWithTag("planning-filter-reset").performClick()
        compose.onNodeWithTag("planning-rule-${once.key}").assertIsDisplayed()
        compose.onNodeWithTag("planning-rule-${expense.key}").assertIsDisplayed()
        compose.onNodeWithTag("planning-filter-Expense").assertTextContains(planCount(2))
        compose.onNodeWithTag("planning-filter-Income").assertTextContains(planCount(1))
    }

    @Test fun readOnlyHidesCreateAndDisablesEditing() {
        var edits = 0
        compose.setContent {
            FillinTheme {
                PlannedOperationsScreen(
                    PlannedOperationsUiState(rules = listOf(expense), isLoading = false),
                    {}, {}, {}, { edits++ },
                    canWrite = false,
                )
            }
        }
        compose.onNodeWithTag("planned-create").assertDoesNotExist()
        compose.onNodeWithTag("planning-rule-${expense.key}").assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, edits) }
    }

    @Test fun emptyCatalogCreatesPlanForActiveType() {
        var state by mutableStateOf(PlannedOperationsUiState(isLoading = false))
        val created = mutableListOf<PlannedOperationType>()
        compose.setContent {
            FillinTheme {
                PlannedOperationsScreen(
                    state = state,
                    onEvent = { event ->
                        if (event is PlannedOperationsEvent.TypeSelected) state = state.copy(selectedType = event.type)
                    },
                    onCreateExpense = { created.add(PlannedOperationType.Expense) },
                    onCreateIncome = { created.add(PlannedOperationType.Income) },
                    onEditRule = {},
                )
            }
        }

        val add = compose.onNodeWithTag("planned-create").assertIsDisplayed()
        add.assertContentDescriptionEquals("Добавить план")
        compose.onNodeWithText("Добавить план").assertDoesNotExist()
        compose.onNodeWithTag("planning-filter-Expense").assertTextContains(planCount(0))
        compose.onNodeWithTag("planning-filter-Income").assertTextContains(planCount(0))
        add.performClick()
        compose.onNodeWithTag("planning-filter-Income").performClick()
        add.performClick()
        compose.onNodeWithTag("planning-filter-reset").performClick()
        compose.onNodeWithTag("planning-filter-Expense").performClick()
        add.performClick()

        compose.runOnIdle {
            assertEquals(listOf(PlannedOperationType.Expense, PlannedOperationType.Income, PlannedOperationType.Expense), created)
        }
    }

    @Test fun lastPlanScrollsAboveBottomBar() {
        val overlap = 96.dp
        val rules = List(30) { index ->
            once.copy(id = "plan-$index", startsOn = day.plusDays(index.toLong()))
        }
        compose.setContent {
            FillinTheme {
                Box(Modifier.fillMaxSize()) {
                    PlannedOperationsScreen(
                        PlannedOperationsUiState(rules = rules, isLoading = false, today = day),
                        {}, {}, {}, {},
                        bottomBarOverlap = overlap,
                    )
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(overlap).testTag("bottom-bar"))
                }
            }
        }

        compose.onNodeWithTag("planned-operations-list").performScrollToKey(rules.last().key)
        val lastPlan = compose.onNodeWithTag("planning-rule-${rules.last().key}").assertIsDisplayed()
        val bottomBar = compose.onNodeWithTag("bottom-bar")
        assertTrue(lastPlan.getUnclippedBoundsInRoot().bottom <= bottomBar.getUnclippedBoundsInRoot().top)
    }

    private fun planCount(count: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.resources
            .getQuantityString(R.plurals.planning_plan_count, count, count)
}
