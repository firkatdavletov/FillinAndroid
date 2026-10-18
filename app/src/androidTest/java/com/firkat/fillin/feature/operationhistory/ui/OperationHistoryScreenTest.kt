package com.firkat.fillin.feature.operationhistory.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.firkat.fillin.R
import com.firkat.fillin.feature.operationhistory.domain.model.Operation
import com.firkat.fillin.feature.operationhistory.domain.model.OperationHistoryFilter
import com.firkat.fillin.feature.operationhistory.domain.model.OperationPeriodTotals
import com.firkat.fillin.feature.operationhistory.domain.model.OperationStatus
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.feature.operationhistory.domain.repository.OfflineHistoryContext
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
class OperationHistoryScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun syncReplacesLastSyncTimeWithoutMovingContent() {
        assertSyncDoesNotMoveContent(Instant.parse("2026-10-18T05:00:00Z"))
    }

    @Test
    fun syncStatusReservesItsLineBeforeTheFirstTimestamp() {
        assertSyncDoesNotMoveContent(null)
    }

    private fun assertSyncDoesNotMoveContent(lastSyncedAt: Instant?) {
        var state by mutableStateOf(
            OperationHistoryUiState(
                filters = FILTER,
                offline = OfflineHistoryContext(initialSyncComplete = true, isOnline = true, lastSyncedAt = lastSyncedAt),
            ),
        )
        composeRule.setContent {
            FillinTheme(dynamicColor = false) {
                FeatureScaffold(titleResource = R.string.operation_history_title) {
                    OperationHistoryScreen(state, onEvent = {})
                }
            }
        }
        val contentBounds = composeRule.onNodeWithTag("history-type-totals").fetchSemanticsNode().boundsInRoot
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val running = resources.getString(R.string.operation_sync_running)
        val lastSync = resources.getString(R.string.operation_sync_last, "18.10 10:00")
        listOf(true, false, true, false).forEach { syncing ->
            composeRule.runOnIdle { state = state.copy(offline = state.offline.copy(isSyncing = syncing)) }
            val updatedBounds = composeRule.onNodeWithTag("history-type-totals").fetchSemanticsNode().boundsInRoot
            assertEquals(contentBounds, updatedBounds)
            if (syncing) {
                composeRule.onNodeWithText(running).assertIsDisplayed()
                composeRule.onNodeWithText(lastSync).assertDoesNotExist()
            } else {
                composeRule.onNodeWithText(running).assertDoesNotExist()
                if (lastSyncedAt != null) composeRule.onNodeWithText(lastSync).assertIsDisplayed()
            }
        }
    }

    @Test
    fun defaultFiltersStayVisibleWithAnEmptyHistory() {
        val events = mutableListOf<OperationHistoryEvent>()
        show(OperationHistoryUiState(filters = FILTER, periodTotals = TOTALS), events)
        composeRule.onNodeWithText("История операций").assertIsDisplayed()
        composeRule.onNodeWithTag("history-type-EXPENSE").assertIsDisplayed().assertIsNotSelected()
        composeRule.onNodeWithTag("history-type-INCOME").assertIsDisplayed().assertIsNotSelected()
        composeRule.onNodeWithTag("history-type-ALL").assertDoesNotExist()
        composeRule.onNodeWithTag("history-category-filter").assertDoesNotExist()
        composeRule.onNodeWithTag("history-type-reset").assertDoesNotExist()
        composeRule.onNodeWithTag("history-total-EXPENSE", useUnmergedTree = true)
            .assertTextEquals(formatMoney(TOTALS.expenses, "KZT"))
        composeRule.onNodeWithTag("history-total-INCOME", useUnmergedTree = true)
            .assertTextEquals(formatMoney(TOTALS.incomes, "KZT"))
        composeRule.onNodeWithText("Октябрь").assertIsDisplayed()
        composeRule.onNodeWithTag("date-period-dropdown", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("date-period-reset").assertDoesNotExist()
        val periodBounds = composeRule.onNodeWithTag("history-period-filter").fetchSemanticsNode().boundsInRoot
        val typeBounds = composeRule.onNodeWithTag("history-type-EXPENSE").fetchSemanticsNode().boundsInRoot
        assertTrue(periodBounds.bottom <= typeBounds.top)
        composeRule.onNodeWithText("Нет операций по выбранным фильтрам").assertIsDisplayed()
        screenshot("history-default.png")

        composeRule.onNodeWithTag("history-type-INCOME").performClick()
        composeRule.onNodeWithTag("history-period-filter").performClick()
        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    OperationHistoryEvent.TypeSelected(OperationType.Income),
                    OperationHistoryEvent.PeriodFilterOpened,
                ),
                events,
            )
        }
    }

    @Test
    fun customPeriodChipOpensThePickerAndItsCrossOnlyResetsThePeriod() {
        val events = mutableListOf<OperationHistoryEvent>()
        show(
            OperationHistoryUiState(
                filters = FILTER.copy(dateFrom = LocalDate.of(2026, 9, 16), dateTo = LocalDate.of(2026, 10, 25)),
                isCustomPeriod = true,
            ),
            events,
        )
        composeRule.onNodeWithText("16 Снт - 25 Окт").assertIsDisplayed()
        composeRule.onNodeWithTag("history-period-filter").assertIsSelected()
        composeRule.onNodeWithTag("date-period-dropdown", useUnmergedTree = true).assertDoesNotExist()
        screenshot("history-custom-period.png")
        composeRule.onNodeWithTag("history-period-filter").performClick()
        composeRule.onNodeWithTag("date-period-reset").performClick()
        composeRule.runOnIdle {
            assertEquals(
                listOf(OperationHistoryEvent.PeriodFilterOpened, OperationHistoryEvent.PeriodFilterReset),
                events,
            )
        }
    }

    @Test
    fun periodChipIncludesYearsWhenTheRangeCrossesNewYear() {
        show(
            OperationHistoryUiState(
                filters = FILTER.copy(dateFrom = LocalDate.of(2025, 12, 31), dateTo = LocalDate.of(2026, 1, 2)),
                isCustomPeriod = true,
            ),
            mutableListOf(),
        )
        composeRule.onNodeWithText("31 Дек 2025 - 2 Янв 2026").assertIsDisplayed()
    }

    @Test
    fun choosingEitherBlockExpandsItAndItsCrossRestoresTwoEqualBlocks() {
        val events = mutableListOf<OperationHistoryEvent>()
        show(OperationHistoryUiState(filters = FILTER, periodTotals = TOTALS), events)
        val rowBounds = composeRule.onNodeWithTag("history-type-totals").fetchSemanticsNode().boundsInRoot
        val initialBounds = composeRule.onNodeWithTag("history-type-EXPENSE").fetchSemanticsNode().boundsInRoot
        val otherBounds = composeRule.onNodeWithTag("history-type-INCOME").fetchSemanticsNode().boundsInRoot
        assertEquals(initialBounds.width, otherBounds.width, 1f)
        assertEquals(initialBounds.top, otherBounds.top, 1f)
        assertTrue(initialBounds.width < rowBounds.width)

        OperationType.entries.forEach { type ->
            val hiddenType = if (type == OperationType.Expense) OperationType.Income else OperationType.Expense
            composeRule.onNodeWithTag("history-type-${type.apiValue}").performClick().assertIsSelected()
            composeRule.onNodeWithTag("history-type-${hiddenType.apiValue}").assertDoesNotExist()
            val expanded = composeRule.onNodeWithTag("history-type-${type.apiValue}").fetchSemanticsNode().boundsInRoot
            assertEquals(rowBounds.width, expanded.width, 1f)
            val reset = composeRule.onNodeWithTag("history-type-reset").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(reset.center.x > expanded.center.x)
            screenshot("history-selected-${type.apiValue}.png")
            composeRule.onNodeWithTag("history-type-reset").performClick()
            composeRule.onNodeWithTag("history-type-EXPENSE").assertIsDisplayed().assertIsNotSelected()
            composeRule.onNodeWithTag("history-type-INCOME").assertIsDisplayed().assertIsNotSelected()
            val restored = composeRule.onNodeWithTag("history-type-${type.apiValue}").fetchSemanticsNode().boundsInRoot
            assertEquals(initialBounds.width, restored.width, 1f)
            composeRule.onNodeWithTag("history-type-reset").assertDoesNotExist()
        }
        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    OperationHistoryEvent.TypeSelected(OperationType.Expense),
                    OperationHistoryEvent.TypeSelected(null),
                    OperationHistoryEvent.TypeSelected(OperationType.Income),
                    OperationHistoryEvent.TypeSelected(null),
                ),
                events,
            )
        }
    }

    @Test
    fun loadingDoesNotDisplayUnknownTotalsAsZero() {
        val events = mutableListOf<OperationHistoryEvent>()
        show(OperationHistoryUiState(filters = FILTER, isInitialLoading = true), events)
        composeRule.onNodeWithTag("history-totals-loading-EXPENSE", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("history-totals-loading-INCOME", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("history-total-EXPENSE", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("history-total-INCOME", useUnmergedTree = true).assertDoesNotExist()
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
        var currentState by mutableStateOf(state)
        composeRule.setContent {
            FillinTheme(dynamicColor = false) {
                FeatureScaffold(titleResource = R.string.operation_history_title) {
                    OperationHistoryScreen(
                        state = currentState,
                        onEvent = { event ->
                            events += event
                            if (event is OperationHistoryEvent.TypeSelected) {
                                currentState = currentState.copy(filters = currentState.filters.copy(type = event.type))
                            }
                        },
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

private val FILTER = OperationHistoryFilter.currentMonth(LocalDate.of(2026, 10, 17))
private val TOTALS = OperationPeriodTotals(BigDecimal("15000.12"), BigDecimal("1500000.34"))
