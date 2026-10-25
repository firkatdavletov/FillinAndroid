package com.firkat.fillin.feature.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.firkat.fillin.feature.dashboard.domain.model.Money
import com.firkat.fillin.feature.dashboard.widgets.dailylimit.domain.DailyLimitSummary
import com.firkat.fillin.feature.dashboard.widgets.dailylimit.ui.DailyLimitUiState
import com.firkat.fillin.feature.dashboard.widgets.dailylimit.ui.DailyLimitWidget
import com.firkat.fillin.ui.format.formatMoney
import com.firkat.fillin.ui.theme.FillinTheme
import java.math.BigDecimal
import java.math.RoundingMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DailyLimitWidgetTest {
    @get:Rule val compose = createComposeRule()

    @Test fun showsDailyLimitSpendingRemainingAndAccessibleProgress() {
        show(state("10000", "2500"))
        amount("daily-limit-amount", "10000")
        amount("daily-limit-spent", "2500")
        amount("daily-limit-remaining", "7500")
        compose.onNodeWithText("25,0%").assertIsDisplayed()
        compose.onNodeWithText("75,0%").assertIsDisplayed()
        compose.onNodeWithContentDescription("Расход дневного лимита").assertIsDisplayed()
        progress(0.25f)
    }

    @Test fun noExpensesShowTheEntireRemainingLimit() {
        show(state("10000", "0"))
        amount("daily-limit-remaining", "10000")
        compose.onNodeWithText("100,0%").assertIsDisplayed()
        compose.onNodeWithText("Перерасход").assertDoesNotExist()
        progress(0f)
    }

    @Test fun reachingTheLimitShowsZeroRemainingWithoutOverspending() {
        show(state("10000", "10000"))
        amount("daily-limit-remaining", "0")
        compose.onNodeWithText("Осталось сегодня").assertIsDisplayed()
        compose.onNodeWithText("Перерасход").assertDoesNotExist()
        progress(1f)
    }

    @Test fun overspendingShowsPositiveExcessAndCapsTheProgress() {
        show(state("10000", "12500"))
        compose.onNodeWithText("Перерасход").assertIsDisplayed()
        compose.onNodeWithText("Осталось сегодня").assertDoesNotExist()
        amount("daily-limit-remaining", "2500")
        compose.onNodeWithText("125,0%").assertIsDisplayed()
        compose.onNodeWithText("25,0%").assertIsDisplayed()
        progress(1f)
    }

    @Test fun zeroLimitHasUnavailablePercentagesAndEmptyProgress() {
        show(state("0", "0"))
        compose.onAllNodesWithText("—").assertCountEquals(2)
        compose.onNodeWithText("Осталось сегодня").assertIsDisplayed()
        progress(0f)
    }

    @Test fun spendingWithoutALimitShowsOverspendingWithUnavailablePercentages() {
        show(state("0", "10"))
        compose.onAllNodesWithText("—").assertCountEquals(2)
        compose.onNodeWithText("Перерасход").assertIsDisplayed()
        amount("daily-limit-remaining", "10")
        progress(1f)
    }

    @Test fun missingSnapshotShowsPlaceholdersInsteadOfZeroAmounts() {
        show(DailyLimitUiState())
        compose.onAllNodesWithText("—").assertCountEquals(5)
        compose.onNodeWithText(formatMoney(BigDecimal.ZERO, "KZT")).assertDoesNotExist()
        progress(0f)
    }

    private fun show(state: DailyLimitUiState) {
        compose.setContent {
            FillinTheme(dynamicColor = false) {
                DailyLimitWidget(state, Modifier.width(288.dp).height(150.dp))
            }
        }
    }

    private fun amount(tag: String, value: String) {
        compose.onNode(
            hasText(formatMoney(value.toBigDecimal(), "KZT")) and
                (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))),
        ).assertIsDisplayed()
    }

    private fun progress(value: Float) {
        compose.onNodeWithTag("daily-limit-progress").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(value, 0f..1f)),
        )
    }

    private fun state(limit: String, spent: String): DailyLimitUiState {
        val amount = limit.toBigDecimal().setScale(2)
        val expense = spent.toBigDecimal().setScale(2)
        fun percent(value: BigDecimal) = amount.takeIf { it.signum() > 0 }?.let {
            value.multiply(BigDecimal(100)).divide(it, 1, RoundingMode.HALF_UP)
        }
        return DailyLimitUiState.from(DailyLimitSummary(
            limit = Money(amount, "KZT"), spentToday = Money(expense, "KZT"),
            remainingToday = Money(amount - expense, "KZT"),
            spentPercent = percent(expense), remainingPercent = percent(amount - expense),
        ))
    }
}
