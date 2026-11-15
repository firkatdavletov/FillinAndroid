package com.firkat.fillin.feature.dashboard

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.firkat.fillin.feature.dailylimit.domain.ForecastEvent
import com.firkat.fillin.feature.dashboard.domain.model.*
import com.firkat.fillin.feature.dashboard.widgets.balanceforecast.ui.*
import com.firkat.fillin.ui.theme.FillinTheme
import java.io.File
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class BalanceForecastWidgetTest {
    @get:Rule val compose = createComposeRule()
    private val start = LocalDate.of(2026, 11, 3)
    private fun money(
        value: Int
    ) = Money(value.toBigDecimal().setScale(2), "KZT")
    private fun points(
        vararg amounts: Int
    ) = amounts.mapIndexed { index, value -> ForecastPoint(start.plusDays(index.toLong()), money(value)) }
    private val projection get() = BalanceForecastProjection(
        start,
        start.plusDays(2),
        points(100000, 50000, 45000),
        false,
        points(100000, -10000, -90000),
        averageDailyExpenses = money(8500),
        dailyLimit = money(5681)
    )
    private fun show(
        value: BalanceForecastProjection? = projection,
        dark: Boolean = true,
        scale: Float = 1f,
        onDetails: () -> Unit = {}
    ) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                FillinTheme(darkTheme = dark, dynamicColor = false) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                        BalanceForecastWidget(
                            value?.let(ForecastUiState::from) ?: ForecastUiState(),
                            Modifier.testTag("forecast-widget"),
                            onDetails
                        )
                    }
                }
            }
        }
    }

    @Test fun riskShowsTwoScenariosAndOpensDetails() {
        var clicks = 0
        show(onDetails = { clicks++ })
        compose.onNodeWithText("Прогноз баланса").assertIsDisplayed()
        compose.onAllNodesWithText("Текущий темп").assertCountEquals(2)
        compose.onAllNodesWithText("По дневному лимиту").assertCountEquals(2)
        compose.onNodeWithText("При текущем темпе возможен дефицит с 4 ноября").assertExists()
        compose.onNodeWithText("Подробный прогноз").performScrollTo().performClick()
        assertEquals(1, clicks)
        compose.onNodeWithText("Максимум").assertDoesNotExist()
        compose.onNodeWithText("Минимум").assertDoesNotExist()
        assertNoTextOverflow()
        screenshot("forecast-risk-dark.png")
    }

    @Test fun normalState() {
        show(projection.copy(currentPacePoints = points(100000, 50000, 20000)))
        compose.onNodeWithText("Дефицит не ожидается").assertExists()
        screenshot("forecast-normal-dark.png")
    }

    @Test fun insufficientHistoryKeepsOnlyLimit() {
        show(projection.copy(currentPacePoints = null, insufficientHistory = true))
        assertNoHistoryHints()
        compose.onNodeWithText("Текущий темп").assertDoesNotExist()
        compose.onAllNodesWithText("По дневному лимиту").assertCountEquals(2)
        compose.onNodeWithTag("forecast-chart").assertExists()
        screenshot("forecast-insufficient-dark.png")
    }

    @Test fun structuralDeficitWinsWithoutHistory() {
        show(
            projection.copy(
                currentPacePoints = null,
                insufficientHistory = true,
                points = points(10000, -40000, -10000),
                structuralDeficitDate = start.plusDays(1)
            )
        )
        compose.onNodeWithText("Не хватит денег на обязательные платежи").assertExists()
        assertNoHistoryHints()
        screenshot("forecast-structural-dark.png")
    }

    @Test fun loadingDoesNotInventAmounts() {
        show(null)
        compose.onNodeWithText("Загружаем прогноз…").assertExists()
        compose.onNodeWithTag("forecast-chart").assertDoesNotExist()
    }

    @Test fun widgetChartIgnoresTouchAndHasNoSelectionActions() {
        show()
        val chart = compose.onNodeWithTag("forecast-chart").performScrollTo()
        chart.assert(hasContentDescription("Прогноз остатка на конец дня"))
        chart.assert(SemanticsMatcher.keyNotDefined(SemanticsActions.CustomActions))
        chart.performTouchInput {
            down(center)
            moveTo(centerRight)
        }
        compose.onNodeWithTag("forecast-selection").assertDoesNotExist()
        chart.performTouchInput { up() }
    }

    @Test fun detailsSelectionIsClearedAfterRelease() {
        showDetails()
        compose.onNodeWithTag("forecast-chart").performTouchInput {
            down(center)
            advanceEventTime(100)
        }
        compose.onNodeWithTag("forecast-selection").assertExists()
        compose.onNodeWithTag("forecast-chart").performTouchInput { up() }
        compose.onNodeWithTag("forecast-selection").assertDoesNotExist()
    }

    @Test fun detailsAccessibilityCanSelectCalendarDay() {
        showDetails()
        compose.onNodeWithTag("forecast-chart").performCustomAccessibilityActionWithLabel("Следующий день")
        compose.onNodeWithTag("forecast-selection").assertExists()
        compose.onNodeWithText("3 ноября 2026").assertExists()
    }

    @Test fun detailsTabsAndRecommendation() {
        showDetails(projection.copy(events = listOf(ForecastEvent("salary", "Зарплата", start, money(100000), true))))
        compose.onAllNodesWithText("Минимум").assertCountEquals(2)
        compose.onNodeWithText("События").performClick()
        compose.onNodeWithText("Зарплата").assertExists()
        compose.onNodeWithText("Доступен в прогнозе: 4 ноября 2026").assertExists()
        compose.onNodeWithText("Параметры").performClick()
        compose.onNodeWithText("Средние расходы в день").assertExists()
        compose.onNodeWithText("Прогноз").performClick()
        compose.onNodeWithTag(
            "forecast-details-list"
        ).performScrollToNode(hasText("Чтобы придерживаться финансового плана", substring = true))
        compose.onNodeWithText("Чтобы придерживаться финансового плана", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("forecast-details-list").performScrollToIndex(0)
        screenshot("forecast-details-dark.png", "forecast-details")
    }

    @Test fun largeTextDark() {
        show(scale = 2f)
        compose.onNodeWithText("Подробный прогноз").performScrollTo().assertIsDisplayed()
        assertNoTextOverflow()
        screenshot("forecast-large-dark.png")
    }

    @Test fun largeAmountsAndYears() {
        show(
            projection.copy(
                startDate = LocalDate.of(2026, 12, 31),
                endDate = LocalDate.of(2027, 1, 2),
                points = points(12345678, -9876543, -1234567).mapIndexed {
                        index,
                        point
                    ->
                    point.copy(date = LocalDate.of(2026, 12, 31).plusDays(index.toLong()))
                }
            ),
            scale = 2f
        )
        compose.onNodeWithText("Подробный прогноз").performScrollTo().assertIsDisplayed()
        assertNoTextOverflow()
    }

    @Test fun lightTheme() {
        show(dark = false)
        assertNoTextOverflow()
        screenshot("forecast-risk-light.png")
    }
    private fun showDetails(
        value: BalanceForecastProjection = projection
    ) {
        compose.setContent {
            var tab by remember { mutableStateOf(ForecastDetailsTab.Forecast) }
            FillinTheme(darkTheme = true, dynamicColor = false) {
                ForecastDetailsScreen(
                    ForecastDetailsUiState(ForecastUiState.from(value), tab),
                    { if (it is ForecastDetailsEvent.SelectTab) tab = it.tab },
                    Modifier.testTag("forecast-details")
                )
            }
        }
    }

    private fun assertNoHistoryHints() {
        compose.onNodeWithText("Прогноз текущего темпа появится на второй день учёта").assertDoesNotExist()
        compose.onNodeWithText("Показываем прогноз по дневному лимиту").assertDoesNotExist()
    }

    private fun screenshot(
        name: String,
        tag: String = "forecast-widget"
    ) {
        val file =
            File(
                InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("forecast-screenshots"),
                name
            )
        file.outputStream().use {
            compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "cp ${file.absolutePath} /data/local/tmp/$name"
        ).close()
    }
    private fun assertNoTextOverflow() {
        compose.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
            useUnmergedTree = true
        ).fetchSemanticsNodes().forEach { node ->
            val results = mutableListOf<TextLayoutResult>()
            compose.runOnIdle { node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results) }
            results.forEach { result ->
                assertFalse("Truncated: ${result.layoutInput.text}", result.multiParagraph.didExceedMaxLines)
                for (line in 0 until result.lineCount) {
                    assertTrue(
                        "Width overflow: ${result.layoutInput.text}",
                        result.getLineRight(line) <= result.size.width + 1f
                    )
                    assertTrue(
                        "Height overflow: ${result.layoutInput.text}",
                        result.getLineBottom(line) <= result.size.height + 1f
                    )
                }
            }
        }
    }
}
