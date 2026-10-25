package com.firkat.fillin.feature.dashboard

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.firkat.fillin.feature.dashboard.domain.model.ForecastPoint
import com.firkat.fillin.feature.dashboard.domain.model.Money
import com.firkat.fillin.feature.dashboard.widgets.balanceforecast.ui.BalanceForecastUiState
import com.firkat.fillin.feature.dashboard.widgets.balanceforecast.ui.BalanceForecastWidget
import com.firkat.fillin.ui.format.formatMoney
import com.firkat.fillin.ui.theme.FillinTheme
import java.io.File
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BalanceForecastWidgetTest {
    @get:Rule val compose = createComposeRule()
    private val start = LocalDate.of(2026, 10, 25)
    private fun point(day: Long, amount: String) = ForecastPoint(start.plusDays(day), Money(amount.toBigDecimal().setScale(2), "KZT"))
    private val state get() = BalanceForecastUiState(start, start.plusDays(30), (0L..30L).map { day ->
        val balance = when {
            day <= 5 -> 100000 - day * 24000
            day <= 12 -> -20000 + (day - 5) * 200000 / 7
            else -> 180000 - (day - 12) * 130000 / 18
        }
        point(day, balance.toString())
    })

    @Test fun showsExtremaDatesAndAccessibleChartSummary() {
        show(state)
        compose.onNodeWithText("Прогноз").assertIsDisplayed()
        amount("forecast-minimum", "-20000")
        amount("forecast-maximum", "180000")
        compose.onNode(hasText("30 окт.") and hasAnyAncestor(hasTestTag("forecast-minimum"))).assertIsDisplayed()
        compose.onNode(hasText("6 нояб.") and hasAnyAncestor(hasTestTag("forecast-maximum"))).assertIsDisplayed()
        compose.onNodeWithContentDescription("Прогноз остатка на конец дня").assertIsDisplayed().assert(
            SemanticsMatcher("Both extrema are described") {
                val text = it.config[SemanticsProperties.StateDescription]
                text.contains("Минимум") && text.contains("Максимум") && text.contains("2026")
            },
        )
        assertNoTextOverflow()
        saveScreenshot("forecast-curve-light.png")
    }

    @Test fun insufficientHistoryHidesChartAndAmounts() {
        show(state.copy(points = emptyList(), insufficientHistory = true))
        compose.onNodeWithText("Недостаточно истории").assertIsDisplayed()
        compose.onNodeWithTag("forecast-chart").assertDoesNotExist()
        compose.onNodeWithTag("forecast-minimum").assertDoesNotExist()
        compose.onNodeWithTag("forecast-maximum").assertDoesNotExist()
    }

    @Test fun loadingShowsPlaceholdersInsteadOfInventedZeroAmounts() {
        show(BalanceForecastUiState())
        compose.onAllNodesWithText("—").assertCountEquals(5)
        compose.onNodeWithText(formatMoney("0".toBigDecimal(), "KZT")).assertDoesNotExist()
    }

    @Test fun flatForecastShowsTheSameEarliestDateForBothExtrema() {
        show(state.copy(points = (0L..30L).map { point(it, "0") }))
        amount("forecast-minimum", "0")
        amount("forecast-maximum", "0")
        compose.onAllNodesWithText("25 окт.").assertCountEquals(2)
        compose.onNodeWithTag("forecast-chart").assertIsDisplayed()
    }

    @Test fun lightThemeFitsLargeNegativeAmounts() = checkAppearance(false, 1f, "forecast-light.png")
    @Test fun darkThemeFitsLargeNegativeAmounts() = checkAppearance(true, 1f, "forecast-dark.png")
    @Test fun lightThemeFitsLargeText() = checkAppearance(false, 2f, "forecast-large-font-light.png")
    @Test fun darkThemeFitsLargeText() = checkAppearance(true, 2f, "forecast-large-font-dark.png")

    @Test fun crossingYearShowsYearsInPeriodAndExtremaDates() {
        val first = LocalDate.of(2026, 12, 25)
        show(BalanceForecastUiState(first, first.plusDays(30), listOf(
            ForecastPoint(first, Money("100.00".toBigDecimal(), "KZT")),
            ForecastPoint(first.plusDays(30), Money("200.00".toBigDecimal(), "KZT")),
        )))
        compose.onNodeWithText("25 дек. 2026 — 24 янв. 2027").assertIsDisplayed()
        assertNoTextOverflow()
    }

    private fun checkAppearance(dark: Boolean, fontScale: Float, screenshot: String) {
        show(state.copy(points = listOf(point(0, "12345678.90"), point(30, "-9876543.21"))), dark, fontScale)
        amount("forecast-minimum", "-9876543.21")
        amount("forecast-maximum", "12345678.90")
        compose.onNodeWithTag("forecast-chart").assertIsDisplayed()
        assertNoTextOverflow()
        saveScreenshot(screenshot)
    }

    private fun saveScreenshot(screenshot: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = requireNotNull(context.getExternalFilesDir("forecast-screenshots"))
        File(directory, screenshot).outputStream().use { stream ->
            compose.onNodeWithTag("forecast-widget").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        // Gradle uninstalls the test APKs and removes app files after the run.
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "cp ${File(directory, screenshot).absolutePath} /data/local/tmp/$screenshot",
        ).use { descriptor -> java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() } }
    }

    private fun amount(tag: String, value: String) {
        compose.onNode(hasText(formatMoney(value.toBigDecimal(), "KZT")) and hasAnyAncestor(hasTestTag(tag))).assertIsDisplayed()
    }

    private fun show(state: BalanceForecastUiState, dark: Boolean = false, fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                FillinTheme(darkTheme = dark, dynamicColor = false) {
                    Box(Modifier.width(288.dp).height(if (fontScale > 1.3f) 741.dp else 231.dp)) {
                        BalanceForecastWidget(state, Modifier.testTag("forecast-widget"))
                    }
                }
            }
        }
    }

    private fun assertNoTextOverflow() {
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes().forEach { node ->
                val results = mutableListOf<TextLayoutResult>()
                compose.runOnIdle { node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results) }
                results.forEach { result ->
                    assertFalse("Truncated text: ${result.layoutInput.text}", result.multiParagraph.didExceedMaxLines)
                    for (line in 0 until result.lineCount) {
                        assertTrue("Text exceeds width: ${result.layoutInput.text}", result.getLineRight(line) <= result.size.width + 1f)
                        assertTrue("Text exceeds height: ${result.layoutInput.text}", result.getLineBottom(line) <= result.size.height + 1f)
                    }
                }
            }
    }
}
