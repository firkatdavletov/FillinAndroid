package com.firkat.fillin.feature.plannedoperations.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.firkat.fillin.feature.categories.domain.model.*
import com.firkat.fillin.feature.plannedoperations.domain.model.*
import com.firkat.fillin.ui.theme.FillinTheme
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlanningRuleEditorScreenTest {
    @get:Rule val compose = createComposeRule()
    private val category = Category("food", "Продукты", CategoryKind.Expense, CategoryIcon("HOME"))
    private var state by mutableStateOf(RuleEditorState(loading = false, categories = listOf(category)))
    private var saves = 0
    private fun show(
        canWrite: Boolean = true
    ) {
        compose.setContent {
            FillinTheme {
                PlanningRuleEditorScreen(
                    state, canWrite,
                    onChange = { transform -> state = state.copy(draft = transform(state.draft)) },
                    onType = { state = state.copy(draft = state.draft.copy(type = it)) },
                    onWindow = { state = state.copy(window = it) },
                    onCategory = { state = state.copy(draft = state.draft.copy(categoryId = it), window = null) },
                    onBack = { state = state.copy(window = RuleWindow.Discard) }, onDiscard = {},
                    onSave = { saves++ }, onDelete = {}, onRetryCategories = {}, onRetry = {}
                )
            }
        }
    }

    @Test fun creationOpensWithExpenseAndDisabledSave() {
        show()
        compose.onNodeWithText("Расход").assertIsSelected()
        compose.onNodeWithText("Создание правила").assertIsDisplayed()
        compose.onNodeWithTag("planned-editor-save").assertIsNotEnabled()
        compose.onNodeWithText("Доход").performClick().assertIsSelected()
        compose.onNodeWithText("Расход").performClick().assertIsSelected()
        compose.onNodeWithTag("rule-name").performTextInput("Покупки")
        compose.onNodeWithTag("rule-name").assertTextContains("Покупки")
    }

    @Test fun categorySheetSelectsAndCloses() {
        show()
        compose.onNodeWithText("Выбрать").performClick()
        compose.onNodeWithText("Продукты").performClick()
        compose.runOnIdle {
            assertEquals("food", state.draft.categoryId)
            assertNull(state.window)
        }
    }

    @Test fun calendarAndDiscardAreIndependentWindows() {
        state = state.copy(window = RuleWindow.Date)
        show()
        compose.onNodeWithText("Отмена").performClick()
        compose.onNodeWithTag("planning-rule-editor").assertExists()
        compose.runOnIdle { state = state.copy(window = RuleWindow.Discard) }
        compose.onNodeWithText("Отменить изменения?").assertIsDisplayed()
        compose.onNodeWithText("Отмена").performClick()
        compose.runOnIdle { assertNull(state.window) }
    }

    @Test fun editLocksTypeAndReadOnlyDisablesSave() {
        val draft = state.draft.copy(name = "Еда", categoryId = "food", amount = "100")
        state =
            state.copy(
                draft = draft,
                original = PlanningRule("r", 1, PlannedOperationType.Expense, "food", "Продукты", "HOME", "Еда", BigDecimal.TEN, frequency = PlanningFrequency.Once, startsOn = draft.date)
            )
        show(canWrite = false)
        compose.onNodeWithText("Редактирование правила").assertIsDisplayed()
        compose.onNodeWithText("Доход").assertIsNotEnabled()
        compose.onNodeWithTag("planned-editor-save").assertIsNotEnabled()
    }
}
