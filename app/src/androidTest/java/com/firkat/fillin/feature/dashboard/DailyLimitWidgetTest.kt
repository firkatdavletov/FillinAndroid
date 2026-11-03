package com.firkat.fillin.feature.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.firkat.fillin.feature.dashboard.domain.model.Money
import com.firkat.fillin.feature.dashboard.widgets.dailylimit.domain.DailyLimitSummary
import com.firkat.fillin.feature.dashboard.widgets.dailylimit.ui.*
import com.firkat.fillin.feature.dailylimit.domain.*
import com.firkat.fillin.ui.theme.FillinTheme
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DailyLimitWidgetTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @org.junit.Before fun keepTestActivityVisible() {
        compose.activity.runOnUiThread {
            compose.activity.setShowWhenLocked(true)
            compose.activity.setTurnScreenOn(true)
            compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    private fun money(value: String) = Money(BigDecimal(value), "KZT")
    private fun state(balance: String, spent: String): DailyLimitUiState {
        val result = DailyLimitCalculator().calculate(DailyLimitInput(money(balance),money(spent),emptyList(),LocalDate.of(2026,11,1)))
        return DailyLimitUiState.from(DailyLimitSummary(result.dailyTarget,result.spentToday,result.remainingToday,null,null,result))
    }
    private fun show(state: DailyLimitUiState, onClick: () -> Unit = {}) {
        compose.setContent { FillinTheme(dynamicColor=false) { DailyLimitWidget(state,Modifier.width(360.dp),onClick) } }
    }
    @Test fun showsSpendingRemainingHorizonAndProgress() {
        show(state("59000","1000"))
        compose.onNodeWithTag("daily-limit-spent",useUnmergedTree=true).assertTextContains("Потрачено",substring=true)
        compose.onNodeWithTag("daily-limit-remaining",useUnmergedTree=true).assertTextContains("Осталось",substring=true)
        compose.onNodeWithText("Прогноз на",substring=true,useUnmergedTree=true).assertIsDisplayed()
        compose.onNodeWithTag("daily-limit-progress",useUnmergedTree=true).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo,ProgressBarRangeInfo(1f,0f..1f)))
    }
    @Test fun overspendingIsSeparateFromZeroRemaining() {
        show(state("57000","3000"))
        compose.onNodeWithText("Превышение",substring=true,useUnmergedTree=true).assertIsDisplayed()
        compose.onNodeWithText("На следующие дни",substring=true,useUnmergedTree=true).assertIsDisplayed()
    }
    @Test fun deficitShowsWarning() {
        show(state("-100","3000"))
        compose.onNodeWithText("Не хватит",substring=true,useUnmergedTree=true).assertIsDisplayed()
    }
    @Test fun infoOpensDetails() {
        var opened=false
        show(state("60000","0")) { opened=true }
        compose.onNodeWithContentDescription("Расчёт дневного лимита").performClick()
        compose.runOnIdle { assertTrue(opened) }
    }
    @Test fun missingSnapshotShowsPlaceholder() {
        show(DailyLimitUiState())
        compose.onNodeWithTag("daily-limit-amount",useUnmergedTree=true).assertTextEquals("—")
    }
}
