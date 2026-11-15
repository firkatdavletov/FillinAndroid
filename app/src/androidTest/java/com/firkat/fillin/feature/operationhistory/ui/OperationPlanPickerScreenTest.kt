package com.firkat.fillin.feature.operationhistory.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.firkat.fillin.feature.operationhistory.domain.repository.*
import com.firkat.fillin.ui.theme.FillinTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class OperationPlanPickerScreenTest {
    @get:Rule val compose = createComposeRule()
    private val plan =
        SuggestedPlan(PlanLinkTarget("EXPENSE", ruleId = "r", dueOn = "2026-11-04"), "food", "Еда", "100", "50", 1)

    @Test fun selectRuleThenPaymentEnablesExplicitSave() {
        val groups = pickerGroups(listOf(plan), emptyList())
        var state by mutableStateOf(PlanPickerState(groups = groups, category = "Еда", loading = false))
        var saves = 0
        compose.setContent {
            FillinTheme {
                OperationPlanPickerScreen(
                    state,
                    { state = state.copy(group = it) },
                    { state = state.copy(selected = it) },
                    {},
                    { saves++ },
                    {}
                )
            }
        }
        compose.onNodeWithText("Еда", substring = false).performClick()
        compose.onNodeWithText("Связать").assertIsNotEnabled()
        compose.onNodeWithText("Остаток: 50 ₸").performClick()
        compose.onNodeWithText("Связать").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, saves) }
    }

    @Test fun emptyPickerShowsCategoryAndNoSave() {
        compose.setContent {
            FillinTheme {
                OperationPlanPickerScreen(PlanPickerState(category = "Еда", loading = false), {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("Категория операции: Еда").assertIsDisplayed()
        compose.onNodeWithText("Нет открытых планов этой категории").assertIsDisplayed()
        compose.onNodeWithText("Связать").assertDoesNotExist()
    }
}
