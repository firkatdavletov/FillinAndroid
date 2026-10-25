package com.firkat.fillin.feature.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.firkat.fillin.feature.dashboard.domain.calculation.*
import com.firkat.fillin.feature.dashboard.domain.model.*
import com.firkat.fillin.feature.dashboard.ui.*
import com.firkat.fillin.feature.dashboard.widgets.availabletospend.ui.*
import com.firkat.fillin.feature.dashboard.widgets.balanceforecast.ui.*
import com.firkat.fillin.feature.dashboard.widgets.dailylimit.ui.*
import com.firkat.fillin.feature.dashboard.widgets.dailylimit.domain.DailyLimitSummary
import com.firkat.fillin.feature.dashboard.widgets.totalbalance.ui.*
import com.firkat.fillin.ui.format.formatMoney
import com.firkat.fillin.ui.theme.FillinTheme
import java.io.FileInputStream
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardScreenOfflineTest {
    @get:Rule val compose = createComposeRule()

    @Test fun initialSyncDoesNotShowZeroFinancialFigures() {
        show(DashboardUiState())
        compose.onNodeWithText("Для расчёта Главной нужна первая полная синхронизация").assertIsDisplayed()
        compose.onNodeWithText("Общий баланс").assertDoesNotExist()
        compose.onNodeWithText("Бюджет месяца").assertDoesNotExist()
    }

    @Test fun emptyBudgetAndMissingHistoryAreShownWithoutInventedForecast() {
        checkMissingHistory(1f)
    }

    @Test fun largeFontKeepsMissingHistoryVisible() {
        checkMissingHistory(2f)
    }

    private fun checkMissingHistory(fontScale: Float) {
        val empty = calculate().let { it.copy(balanceForecast = it.balanceForecast.copy(points = emptyList(), insufficientHistory = true)) }
        show(DashboardUiState(empty.date, DashboardStatus.Ready), empty, fontScale = fontScale)
        compose.onNodeWithText("Общий баланс").assertIsDisplayed()
        scrollTo("balance-forecast")
        compose.onNodeWithText("Недостаточно истории").assertIsDisplayed()
        assertNoTextOverflow()
        compose.onNodeWithText("Бюджет месяца").assertDoesNotExist()
    }

    @Test fun fourWidgetsFollowFixedOrderAndOldSectionsAreRemoved() {
        showReady()
        val balance = bounds("total-balance")
        val available = bounds("available-to-spend")
        val daily = bounds("daily-limit")
        assertEquals(balance.top, available.top, 1f)
        assertTrue(balance.right < available.left)
        val gap = available.left - balance.right
        assertEquals(balance.width * 2 + gap, daily.width, 1f)
        assertEquals(balance.height * 2 + gap, daily.height, 1f)
        assertTrue(balance.bottom < daily.top)
        scrollTo("balance-forecast")
        assertTrue(bounds("daily-limit").bottom < bounds("balance-forecast").top)
        val forecast = bounds("balance-forecast")
        assertEquals(daily.width, forecast.width, 1f)
        assertEquals(balance.height * 3 + gap * 2, forecast.height, 1f)
        assertEquals(balance.width * 0.5f, balance.height, 1f)
        assertEquals(4, DefaultDashboardWidgets.size)
        compose.onNodeWithText("Бюджет месяца").assertDoesNotExist()
        compose.onNodeWithText("Крупнейшие категории").assertDoesNotExist()
        compose.onNodeWithText("Ближайшие платежи").assertDoesNotExist()
        compose.onNodeWithText("Добавить операцию", substring = true).assertDoesNotExist()
    }

    @Test fun containerSupportsTwoByTwoWithoutAddingAnotherProductionWidget() {
        compose.setContent {
            FillinTheme(dynamicColor = false) {
                Box(Modifier.width(320.dp).fillMaxHeight()) {
                    DashboardGrid(widgets = listOf(
                        DashboardWidget("one", DashboardWidgetKind.TotalBalance, DashboardWidgetSize.OneByOne),
                        DashboardWidget("second", DashboardWidgetKind.TotalBalance, DashboardWidgetSize.OneByOne),
                        DashboardWidget("wide", DashboardWidgetKind.TotalBalance, DashboardWidgetSize.TwoByOne),
                        DashboardWidget("large", DashboardWidgetKind.TotalBalance, DashboardWidgetSize.TwoByTwo),
                    )) { Text(it.id) }
                }
            }
        }
        val one = bounds("one")
        val second = bounds("second")
        val gap = second.left - one.right
        val wide = bounds("wide")
        assertEquals(one.width * 2 + gap, wide.width, 1f)
        assertEquals(one.height, wide.height, 1f)
        scrollTo("large")
        val large = bounds("large")
        assertEquals(wide.width, large.width, 1f)
        assertEquals(one.height * 2 + gap, large.height, 1f)
        assertEquals(4, DefaultDashboardWidgets.size)
    }

    @Test fun lightThemeShowsLargeAndNegativeAmountsWithoutOverflow() = checkAppearance(false, 1f, "dashboard-light.png")
    @Test fun darkThemeShowsLargeAndNegativeAmountsWithoutOverflow() = checkAppearance(true, 1f, "dashboard-dark.png")
    @Test fun largeFontKeepsAllTextVisibleInLightTheme() = checkAppearance(false, 2f, "dashboard-large-font-light.png")
    @Test fun largeFontKeepsAllTextVisibleInDarkTheme() = checkAppearance(true, 2f, "dashboard-large-font-dark.png")

    @Test fun forbiddenStateHidesWidgetsAndRetryEmitsAnEvent() {
        val events = mutableListOf<DashboardEvent>()
        compose.setContent {
            FillinTheme { DashboardScreen(DashboardUiState(status = DashboardStatus.Forbidden), events::add) { Text(it.id) } }
        }
        compose.onNodeWithText("Доступ к семейному бюджету отозван").assertIsDisplayed()
        compose.onNodeWithTag("dashboard-grid").assertDoesNotExist()
        compose.onNodeWithText("Повторить").performClick()
        assertEquals(listOf(DashboardEvent.Retry), events)
    }

    private fun checkAppearance(dark: Boolean, fontScale: Float, screenshotName: String) {
        showReady(dark, fontScale)
        compose.onNode(
            hasText(formatMoney(SAMPLE.availableMoney.amount, "KZT")) and
                hasAnyAncestor(hasTestTag("dashboard-widget-total-balance")),
        ).assertIsDisplayed()
        screenshot("initial-$screenshotName")
        assertNoTextOverflow()
        scrollTo("daily-limit")
        compose.onNodeWithText("Потрачено сегодня").assertIsDisplayed()
        compose.onNodeWithText("Осталось сегодня").assertIsDisplayed()
        assertNoTextOverflow()
        screenshot("daily-$screenshotName")
        scrollTo("balance-forecast")
        assertNoTextOverflow()
        compose.onNodeWithText("Прогноз").assertIsDisplayed()
        screenshot(screenshotName)
    }

    private fun assertNoTextOverflow() {
        val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true).fetchSemanticsNodes()
        nodes.forEach { node ->
            val results = mutableListOf<TextLayoutResult>()
            compose.runOnIdle { node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results) }
            results.forEach { result ->
                assertFalse("Truncated text: ${result.layoutInput.text}", result.multiParagraph.didExceedMaxLines)
                // Paragraph width can include unused constraint width. Inspect the glyph lines.
                for (line in 0 until result.lineCount) {
                    assertTrue("Text exceeds width: ${result.layoutInput.text}", result.getLineRight(line) <= result.size.width + 1f)
                    assertTrue("Text exceeds height: ${result.layoutInput.text}", result.getLineBottom(line) <= result.size.height + 1f)
                }
            }
        }
    }

    private fun showReady(dark: Boolean = false, fontScale: Float = 1f) = show(DashboardUiState(SAMPLE.date, DashboardStatus.Ready), SAMPLE, dark, fontScale)

    private fun show(state: DashboardUiState, dashboard: Dashboard = calculate(), dark: Boolean = false, fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                FillinTheme(darkTheme = dark, dynamicColor = false) {
                    Box(Modifier.width(320.dp).fillMaxHeight()) {
                        DashboardScreen(state, {}) { widget -> SampleWidget(widget, dashboard) }
                    }
                }
            }
        }
    }

    @Composable
    private fun SampleWidget(widget: DashboardWidget, dashboard: Dashboard) {
        when (widget.kind) {
            DashboardWidgetKind.TotalBalance -> TotalBalanceWidget(TotalBalanceUiState(dashboard.availableMoney))
            DashboardWidgetKind.AvailableToSpend -> AvailableToSpendWidget(AvailableToSpendUiState(dashboard.freeMoney.amount))
            DashboardWidgetKind.DailyLimit -> DailyLimitWidget(DailyLimitUiState.from(DailyLimitSummary.from(dashboard)))
            DashboardWidgetKind.BalanceForecast -> BalanceForecastWidget(BalanceForecastUiState.from(dashboard.balanceForecast))
        }
    }

    private fun bounds(id: String) = compose.onNodeWithTag("dashboard-widget-$id").fetchSemanticsNode().boundsInRoot
    private fun scrollTo(id: String) { compose.onNodeWithTag("dashboard-grid").performScrollToNode(hasTestTag("dashboard-widget-$id")) }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/$name").use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
        }
    }

    companion object {
        private fun money(value: String) = Money(value.toBigDecimal().setScale(2), "KZT")
        private fun calculate() = CalculateDashboard.calculate(DashboardCalculationInput(
            Instant.parse("2026-10-19T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"),
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, emptyList(), emptyList(), emptyList(), emptyList(), emptySet(),
        ))
        private val SAMPLE = calculate().let { empty -> empty.copy(
            availableMoney = money("12345678.90"),
            freeMoney = empty.freeMoney.copy(amount = money("-12345.67"), safeDailyLimit = money("123456.78")),
            balanceForecast = empty.balanceForecast.copy(points = (0L..30L).map { day ->
                ForecastPoint(empty.date.plusDays(day), money(if (day < 15) "12345678.90" else "-98765.43"))
            }),
            todayVariableNetExpenses = money("30864.19"),
        ) }
    }
}
