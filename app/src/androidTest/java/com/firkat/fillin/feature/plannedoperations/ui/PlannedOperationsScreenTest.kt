package com.firkat.fillin.feature.plannedoperations.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
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

    @Test fun filtersGroupsAndEditing() {
        var state by mutableStateOf(PlannedOperationsUiState(rules = listOf(expense, income), isLoading = false, today = day))
        var edited: PlanningRule? = null
        compose.setContent { FillinTheme {
            PlannedOperationsScreen(state, { event -> state = when (event) {
                is PlannedOperationsEvent.TypeSelected -> state.copy(selectedType = event.type)
                is PlannedOperationsEvent.GroupToggled -> state.copy(collapsed = if (event.frequency in state.collapsed) emptySet() else setOf(event.frequency))
                else -> state
            } }, {}, {}, { edited = it })
        } }
        compose.onNodeWithTag("planning-rule-Expense:rent").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(expense, edited) }
        compose.onNodeWithTag("planning-filter-Income").performClick().assertIsSelected()
        compose.onNodeWithTag("planning-rule-Expense:rent").assertDoesNotExist()
        compose.onNodeWithTag("planning-rule-Income:salary").assertIsDisplayed()
        compose.onNodeWithTag("planning-toggle-Monthly").performClick()
        compose.onNodeWithTag("planning-rule-Income:salary").assertDoesNotExist()
        compose.onNodeWithTag("planning-toggle-Monthly").performClick()
        compose.onNodeWithTag("planning-rule-Income:salary").assertIsDisplayed()
    }

    @Test fun readOnlyHidesCreateAndDisablesEditing() {
        var edits = 0
        compose.setContent { FillinTheme { PlannedOperationsScreen(
            PlannedOperationsUiState(rules = listOf(expense), isLoading = false), {}, {}, {}, { edits++ }, canWrite = false) } }
        compose.onNodeWithTag("planned-create").assertDoesNotExist()
        compose.onNodeWithTag("planning-rule-Expense:rent").assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals(0, edits) }
    }

    @Test fun emptyCatalogKeepsCreateAvailable() {
        compose.setContent { FillinTheme { PlannedOperationsScreen(PlannedOperationsUiState(isLoading = false), {}, {}, {}, {}) } }
        compose.onNodeWithTag("planned-create").assertIsDisplayed().performClick()
        compose.onNodeWithTag("planned-create-expense").assertDoesNotExist()
    }
}
