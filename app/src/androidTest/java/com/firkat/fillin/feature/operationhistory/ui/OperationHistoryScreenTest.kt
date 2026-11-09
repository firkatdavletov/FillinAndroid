package com.firkat.fillin.feature.operationhistory.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.firkat.fillin.R
import com.firkat.fillin.feature.operationhistory.domain.model.Operation
import com.firkat.fillin.feature.operationhistory.domain.model.OperationHistoryFilter
import com.firkat.fillin.feature.operationhistory.domain.model.OperationPeriodTotals
import com.firkat.fillin.feature.operationhistory.domain.model.OperationStatus
import com.firkat.fillin.feature.operationhistory.domain.model.OperationSyncStatus
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.ui.format.formatMoney
import com.firkat.fillin.ui.theme.FillinTheme
import java.io.FileInputStream
import java.math.BigDecimal
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
    fun historyDistinguishesFullAndPartialRefundsAndKeepsDetailsAvailable() {
        val full = operation("full").copy(
            originalAmount = BigDecimal("7.00"), originalCurrency = "RUB", refundedAmount = BigDecimal("7"),
        )
        val partial = operation("partial").copy(refundedAmount = BigDecimal("7"))
        val expense = operation("expense")
        val income = operation("income", OperationType.Income)
        val operations = listOf(full, partial, expense, income)
        val opened = mutableListOf<String>()
        show(transitionState(0, null, operations), mutableListOf(), opened::add)

        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val fullLabel = resources.getString(R.string.operation_history_fully_refunded)
        val partialLabel = resources.getString(R.string.operation_history_partially_refunded)
        composeRule.onNodeWithTag("operation-full").assert(hasText(fullLabel)).performClick()
        composeRule.onNodeWithTag("operation-partial").assert(hasText(partialLabel))
        for (operation in listOf(expense, income)) {
            composeRule.onNodeWithTag("operation-${operation.id}").assert(!hasText(fullLabel) and !hasText(partialLabel))
        }

        for (operation in operations) {
            val amount = formatOperationAmount(operation.amount, operation.currency, operation.type)
            val layouts = mutableListOf<TextLayoutResult>()
            composeRule.onNode(
                hasText(amount) and hasAnyAncestor(hasTestTag("operation-${operation.id}")),
                useUnmergedTree = true,
            ).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(
                if (operation == full) TextDecoration.LineThrough else TextDecoration.None,
                layouts.single().layoutInput.style.textDecoration,
            )
        }
        composeRule.runOnIdle { assertEquals(listOf(full.id), opened) }
        screenshot("history-refunds.png")
    }

    @Test
    fun refreshKeepsTotalsAndTheCurrentListInPlace() {
        val initial = transitionState(0, null, listOf(operation("old")))
        val setState = showTransition(initial)
        val totalsBounds = composeRule.onNodeWithTag("history-type-totals").fetchSemanticsNode().boundsInRoot
        val rowBounds = composeRule.onNodeWithTag("operation-old").fetchSemanticsNode().boundsInRoot
        listOf(true, false, true, false).forEach { syncing ->
            setState(initial.copy(isRefreshing = syncing))
            assertEquals(totalsBounds, composeRule.onNodeWithTag("history-type-totals").fetchSemanticsNode().boundsInRoot)
            assertEquals(rowBounds, composeRule.onNodeWithTag("operation-old").fetchSemanticsNode().boundsInRoot)
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
    fun choosingEitherBlockShowsItsSummaryAndResetRestoresTwoEqualBlocks() {
        val events = mutableListOf<OperationHistoryEvent>()
        show(
            OperationHistoryUiState(
                filters = FILTER.copy(dateFrom = LocalDate.of(2026, 9, 16)),
                periodTotals = TOTALS,
                isCustomPeriod = true,
            ),
            events,
        )
        val rowBounds = composeRule.onNodeWithTag("history-type-totals").fetchSemanticsNode().boundsInRoot
        val initialBounds = composeRule.onNodeWithTag("history-type-EXPENSE").fetchSemanticsNode().boundsInRoot
        val otherBounds = composeRule.onNodeWithTag("history-type-INCOME").fetchSemanticsNode().boundsInRoot
        assertEquals(initialBounds.width, otherBounds.width, 1f)
        assertEquals(initialBounds.top, otherBounds.top, 1f)
        assertTrue(initialBounds.width < rowBounds.width)

        OperationType.entries.forEach { type ->
            composeRule.onNodeWithTag("history-type-${type.apiValue}").performClick()
            composeRule.onNodeWithTag("history-type-EXPENSE").assertDoesNotExist()
            composeRule.onNodeWithTag("history-type-INCOME").assertDoesNotExist()
            val summary = composeRule.onNodeWithTag("history-selected-total").fetchSemanticsNode().boundsInRoot
            assertEquals(rowBounds.width, summary.width, 1f)
            assertEquals(rowBounds.height, summary.height, 1f)
            val label = if (type == OperationType.Expense) "Расход" else "Доход"
            val amount = if (type == OperationType.Expense) TOTALS.expenses else TOTALS.incomes
            val amountNode = composeRule.onNodeWithTag("history-total-${type.apiValue}")
                .assertTextEquals(formatMoney(amount, "KZT")).fetchSemanticsNode().boundsInRoot
            val labelNode = composeRule.onNodeWithText(label).fetchSemanticsNode().boundsInRoot
            assertTrue(amountNode.bottom <= labelNode.top)
            val reset = composeRule.onNodeWithTag("history-type-reset").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(reset.center.x > summary.center.x)
            screenshot("history-selected-${type.apiValue}.png")
            composeRule.onNodeWithTag("history-type-reset").performClick()
            composeRule.onNodeWithTag("history-type-EXPENSE").assertIsDisplayed().assertIsNotSelected()
            composeRule.onNodeWithTag("history-type-INCOME").assertIsDisplayed().assertIsNotSelected()
            val restored = composeRule.onNodeWithTag("history-type-${type.apiValue}").fetchSemanticsNode().boundsInRoot
            assertEquals(initialBounds.width, restored.width, 1f)
            composeRule.onNodeWithTag("history-type-reset").assertDoesNotExist()
            composeRule.onNodeWithText("16 Снт - 17 Окт").assertIsDisplayed()
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
    fun incomeHasPositiveAmountsAndOpensEditorOnTap() {
        val opened = mutableListOf<String>()
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
            opened::add,
        )
        composeRule.onNodeWithText("Зарплата").assertIsDisplayed()
        composeRule.onAllNodes(hasText("+", substring = true)).assertCountEquals(2)
        composeRule.onNodeWithText("Изменить").assertDoesNotExist()
        screenshot("history-income.png")
        composeRule.onNodeWithTag("operation-income").performClick()
        composeRule.runOnIdle { assertEquals(listOf("income"), opened) }
    }

    @Test fun rejectedAndPendingRowsHideTheirSyncStateAndStillOpenDetails() {
        val opened = mutableListOf<String>()
        val events = mutableListOf<OperationHistoryEvent>()
        val operations = listOf(
            operation("rejected").copy(syncStatus = OperationSyncStatus.Rejected, syncError = "invalid_amount"),
            operation("pending").copy(syncStatus = OperationSyncStatus.Pending),
        )
        show(OperationHistoryUiState(filters = FILTER, operations = operations, canLoadMore = false), events, opened::add)
        composeRule.onNodeWithText("Ожидает отправки").assertDoesNotExist()
        composeRule.onNodeWithText("Исправить и отправить снова").assertDoesNotExist()
        composeRule.onNodeWithText("Ошибка отправки", substring = true).assertDoesNotExist()
        composeRule.onNodeWithTag("operation-rejected").performClick()
        composeRule.onNodeWithTag("operation-pending").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("rejected", "pending"), opened)
            assertTrue(events.isEmpty())
        }
    }

    @Test fun descriptionUsesOneLineWithEllipsis() {
        val description = "Продукты для семейного ужина и завтрака на выходных. ".repeat(4)
        val operation = operation("long").copy(description = description)
        showTransition(transitionState(0, null, listOf(operation)))
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(description, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.single().lineCount)
        assertTrue(layouts.single().isLineEllipsized(0))
    }

    @Test fun fastTypeResultWaitsForOutgoingListToFade() {
        val setState = showTransition()
        composeRule.onNodeWithTag("operation-old").assertIsDisplayed()
        composeRule.mainClock.autoAdvance = false
        setState(transitionState(1, OperationType.Income, listOf(operation("new", OperationType.Income))))
        composeRule.mainClock.advanceTimeBy(80)
        composeRule.onNodeWithTag("operation-old").assertIsNotEnabled()
        composeRule.onNodeWithTag("operation-new").assertDoesNotExist()
        composeRule.onNodeWithTag("history-list-loading").assertDoesNotExist()
        composeRule.mainClock.advanceTimeBy(320)
        composeRule.onNodeWithTag("operation-old").assertDoesNotExist()
        composeRule.onNodeWithTag("operation-new").assertIsDisplayed().assertIsEnabled()
    }

    @Test fun slowTypeResultShowsDelayedProgressThenFadesInEmptyState() {
        val setState = showTransition()
        composeRule.mainClock.autoAdvance = false
        setState(transitionState(1, OperationType.Expense))
        composeRule.mainClock.advanceTimeBy(240)
        composeRule.onNodeWithTag("operation-old").assertDoesNotExist()
        composeRule.onNodeWithTag("history-list-loading").assertDoesNotExist()
        composeRule.mainClock.advanceTimeBy(120)
        composeRule.onNodeWithTag("history-list-loading").assertIsDisplayed()
        setState(transitionState(1, OperationType.Expense, emptyList()))
        composeRule.mainClock.advanceTimeBy(250)
        composeRule.onNodeWithTag("history-list-loading").assertDoesNotExist()
        composeRule.onNodeWithText("Нет операций по выбранным фильтрам").assertIsDisplayed()
    }

    @Test fun rapidTypeChangesIgnoreOlderResultAndShowLatestError() {
        val setState = showTransition()
        composeRule.mainClock.autoAdvance = false
        setState(transitionState(1, OperationType.Income))
        composeRule.mainClock.advanceTimeBy(80)
        val pending = transitionState(2, null)
        setState(pending)
        composeRule.mainClock.advanceTimeBy(240)
        setState(pending.copy(operations = listOf(operation("stale")), resultRequestId = 1))
        composeRule.mainClock.advanceTimeBy(120)
        composeRule.onNodeWithTag("operation-stale").assertDoesNotExist()
        composeRule.onNodeWithTag("history-list-loading").assertIsDisplayed()
        setState(transitionState(2, null, emptyList()).copy(error = OperationHistoryError.Unknown))
        composeRule.mainClock.advanceTimeBy(260)
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        composeRule.onNodeWithText(resources.getString(R.string.operation_history_retry)).assertIsDisplayed()
        composeRule.onNodeWithTag("history-list-loading").assertDoesNotExist()
    }

    @Test fun dateHeaderScrollsAwayAndSameQueryUpdatesKeepTheScrollPosition() {
        val operations = (1..30).map { operation("row-$it") }
        val initial = transitionState(0, null, operations)
        val setState = showTransition(initial)
        val header = formatDateHeader(FILTER.dateTo)
        composeRule.onNodeWithText(header).assertIsDisplayed()
        composeRule.onNodeWithTag("operation_history_list").performScrollToIndex(20)
        composeRule.onNodeWithText(header).assertIsNotDisplayed()
        composeRule.onNodeWithTag("operation-row-20").assertIsDisplayed()
        setState(initial.copy(operations = operations + operation("added")))
        composeRule.onNodeWithTag("operation-row-20").assertIsDisplayed()
        composeRule.onNodeWithText(header).assertIsNotDisplayed()
    }

    private fun showTransition(
        initial: OperationHistoryUiState = transitionState(0, null, listOf(operation("old"))),
    ): (OperationHistoryUiState) -> Unit {
        var current by mutableStateOf(initial)
        composeRule.setContent {
            FillinTheme(dynamicColor = false) {
                OperationHistoryScreen(current, onEvent = {})
            }
        }
        return { next -> composeRule.runOnUiThread { current = next } }
    }

    private fun transitionState(
        requestId: Long,
        type: OperationType?,
        operations: List<Operation>? = null,
    ) = OperationHistoryUiState(
        filters = FILTER.copy(type = type),
        periodTotals = TOTALS,
        operations = operations.orEmpty(),
        isInitialLoading = operations == null,
        requestId = requestId,
        resultRequestId = requestId.takeIf { operations != null },
        canLoadMore = false,
    )

    private fun operation(id: String, type: OperationType = OperationType.Expense) = Operation(
        id = id, categoryName = "Продукты", description = "Описание операции",
        amount = BigDecimal("42.00"), currency = "KZT", originalAmount = BigDecimal("42.00"),
        originalCurrency = "KZT", occurredOn = FILTER.dateTo, status = OperationStatus.Posted,
        version = 0, type = type,
    )

    private fun show(state: OperationHistoryUiState, events: MutableList<OperationHistoryEvent>, onOperationClick: (String) -> Unit = {}) {
        var currentState by mutableStateOf(state)
        composeRule.setContent {
            FillinTheme(dynamicColor = false) {
                OperationHistoryScreen(
                    state = currentState,
                    onOperationClick = onOperationClick,
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
