package com.firkat.fillin.feature.budgetmanagement

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.firkat.fillin.feature.budgetmanagement.ui.*
import com.firkat.fillin.ui.theme.FillinTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BudgetManagementUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun deletionRequiresExactNameAndExplicitConfirmation() {
        var confirmed = false
        compose.setContent {
            var state by remember { mutableStateOf(BudgetManagementState("1", "Семья", true, "")) }
            FillinTheme { BudgetManagementSheet(state, { state = state.copy(value = it) }, { confirmed = true }, {}) }
        }
        compose.onNodeWithTag("budget_management_confirm").assertIsNotEnabled()
        compose.onNodeWithTag("budget_management_input").performTextInput("семья")
        compose.onNodeWithTag("budget_management_confirm").assertIsNotEnabled()
        compose.onNodeWithTag("budget_management_input").performTextReplacement(" Семья ")
        compose.onNodeWithTag("budget_management_confirm").assertIsEnabled().performClick()
        compose.runOnIdle { assertTrue(confirmed) }
    }
    @Test fun renameValidationAndBusyState() {
        val state = mutableStateOf(BudgetManagementState("1", "Семья", false, ""))
        compose.setContent { FillinTheme { BudgetManagementSheet(state.value, { state.value = state.value.copy(value = it) }, {}, {}) } }
        compose.onNodeWithTag("budget_management_confirm").assertIsNotEnabled()
        compose.onNodeWithTag("budget_management_input").performTextInput("Дом")
        compose.onNodeWithTag("budget_management_confirm").assertIsEnabled()
        compose.runOnIdle { state.value = state.value.copy(busy = true) }
        compose.onNodeWithTag("budget_management_confirm").assertIsNotEnabled()
        compose.onNodeWithTag("budget_management_input").assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(busy = false, error = true) }
        compose.onNodeWithTag("budget_management_input").assertTextContains("Дом")
        compose.onNodeWithTag("budget_management_confirm").assertIsEnabled()
    }
}
