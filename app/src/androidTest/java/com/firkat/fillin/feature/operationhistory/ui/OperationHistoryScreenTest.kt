package com.firkat.fillin.feature.operationhistory.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.firkat.fillin.R
import com.firkat.fillin.feature.categories.domain.model.Category
import com.firkat.fillin.feature.categories.domain.model.CategoryIcon
import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import com.firkat.fillin.feature.operationhistory.domain.model.Operation
import com.firkat.fillin.feature.operationhistory.domain.model.OperationHistoryFilter
import com.firkat.fillin.feature.operationhistory.domain.model.OperationStatus
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.ui.component.FeatureScaffold
import com.firkat.fillin.ui.theme.FillinTheme
import java.io.FileInputStream
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OperationHistoryScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun defaultFiltersStayVisibleWithAnEmptyHistory() {
        val events = mutableListOf<OperationHistoryEvent>()
        show(OperationHistoryUiState(filters = FILTER), events)
        composeRule.onNodeWithText("История операций").assertIsDisplayed()
        composeRule.onNodeWithTag("history-type-EXPENSE").assertIsSelected()
        composeRule.onNodeWithText("Все категории").assertIsDisplayed()
        composeRule.onNodeWithText("17.09.2026 — 17.10.2026").assertIsDisplayed()
        composeRule.onNodeWithText("Нет операций по выбранным фильтрам").assertIsDisplayed()
        screenshot("history-default.png")

        composeRule.onNodeWithTag("history-type-INCOME").performClick()
        composeRule.onNodeWithTag("history-category-filter").performClick()
        composeRule.onNodeWithTag("history-period-filter").performClick()
        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    OperationHistoryEvent.TypeSelected(OperationType.Income),
                    OperationHistoryEvent.CategoryFilterOpened,
                    OperationHistoryEvent.PeriodFilterOpened,
                ),
                events,
            )
        }
    }

    @Test
    fun categoryDialogShowsOnlyMatchingCategoriesAndAllowsAllCategories() {
        val events = mutableListOf<OperationHistoryEvent>()
        show(
            OperationHistoryUiState(
                filters = FILTER.copy(categoryId = "food"),
                categories = CATEGORIES,
                isCategoryFilterVisible = true,
            ),
            events,
        )
        composeRule.onNodeWithText("🍔 Продукты").assertIsDisplayed()
        composeRule.onNodeWithText("💰 Зарплата").assertDoesNotExist()
        screenshot("history-category.png")
        composeRule.onNodeWithText("Все категории").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(OperationHistoryEvent.CategorySelected(null)), events)
        }
    }

    @Test
    fun dateRangeDialogAppliesInclusiveCalendarDates() {
        val events = mutableListOf<OperationHistoryEvent>()
        show(OperationHistoryUiState(filters = FILTER, isPeriodFilterVisible = true), events)
        composeRule.onNodeWithText("Выберите период").assertIsDisplayed()
        composeRule.onNodeWithText("Применить").assertIsDisplayed().assertIsEnabled()
        screenshot("history-period.png")
        composeRule.onNodeWithText("Применить").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(OperationHistoryEvent.PeriodSelected(FILTER.dateFrom, FILTER.dateTo)), events)
        }
    }

    @Test
    fun incomeHasPositiveAmountsAndNoPurchaseActions() {
        show(
            OperationHistoryUiState(
                filters = FILTER.copy(type = OperationType.Income),
                operations = listOf(
                    Operation(
                        id = "income",
                        categoryName = "Зарплата",
                        description = "Тестовое поступление",
                        amount = BigDecimal("100.00"),
                        currency = "KZT",
                        originalAmount = BigDecimal("100.00"),
                        originalCurrency = "KZT",
                        occurredOn = FILTER.dateTo,
                        status = OperationStatus.Posted,
                        version = 0,
                        type = OperationType.Income,
                    ),
                ),
                canLoadMore = false,
            ),
            mutableListOf(),
        )
        composeRule.onNodeWithText("Зарплата").assertIsDisplayed()
        composeRule.onAllNodes(hasText("+", substring = true)).assertCountEquals(2)
        composeRule.onNodeWithText("Изменить").assertDoesNotExist()
        screenshot("history-income.png")
    }

    private fun show(state: OperationHistoryUiState, events: MutableList<OperationHistoryEvent>) {
        composeRule.setContent {
            FillinTheme(dynamicColor = false) {
                FeatureScaffold(titleResource = R.string.operation_history_title) {
                    OperationHistoryScreen(state = state, onEvent = events::add)
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

private val FILTER = OperationHistoryFilter.lastMonth(LocalDate.of(2026, 10, 17))
private val CATEGORIES = listOf(
    Category("food", "Продукты", CategoryKind.Expense, CategoryIcon("FOOD", "🍔")),
    Category("salary", "Зарплата", CategoryKind.Income, CategoryIcon("INCOME", "💰")),
)
