package com.firkat.fillin.feature.dashboard

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.firkat.fillin.R
import com.firkat.fillin.feature.dashboard.domain.model.DashboardStatus
import com.firkat.fillin.feature.dashboard.domain.model.Money
import com.firkat.fillin.feature.dashboard.ui.DashboardScreen
import com.firkat.fillin.feature.dashboard.ui.DashboardWidgetKind
import com.firkat.fillin.feature.dashboard.widgets.availabletospend.ui.AvailableToSpendUiState
import com.firkat.fillin.feature.dashboard.widgets.availabletospend.ui.AvailableToSpendWidget
import com.firkat.fillin.feature.dashboard.widgets.balanceforecast.ui.BalanceForecastWidget
import com.firkat.fillin.feature.dashboard.widgets.balanceforecast.ui.ForecastUiState
import com.firkat.fillin.feature.dashboard.widgets.dailylimit.ui.DailyLimitUiState
import com.firkat.fillin.feature.dashboard.widgets.dailylimit.ui.DailyLimitWidget
import com.firkat.fillin.feature.dashboard.widgets.totalbalance.ui.TotalBalanceUiState
import com.firkat.fillin.feature.dashboard.widgets.totalbalance.ui.TotalBalanceWidget
import com.firkat.fillin.feature.plannedoperationswidget.ui.PlannedOperationsWidget
import com.firkat.fillin.ui.format.formatMoney
import com.firkat.fillin.ui.theme.FillinTheme
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardShimmerTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun dashboardLoadingShowsTitlesWithoutAmountsAndDailyLimitBecomesClickableWhenReady() {
        val dailyLimit = mutableStateOf(DailyLimitUiState())
        val totalBalance = mutableStateOf(TotalBalanceUiState())
        val realAmount = Money(BigDecimal("1200"), "KZT")
        var detailsClicks = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            FillinTheme {
                DashboardScreen(title = null, modifier = Modifier.fillMaxSize()) { widget ->
                    when (widget.kind) {
                        DashboardWidgetKind.DailyLimit -> DailyLimitWidget(
                            dailyLimit.value,
                            onDetailsClick = { detailsClicks++ }
                        )
                        DashboardWidgetKind.TotalBalance -> TotalBalanceWidget(totalBalance.value)
                        DashboardWidgetKind.AvailableToSpend -> AvailableToSpendWidget(AvailableToSpendUiState())
                        DashboardWidgetKind.BalanceForecast -> BalanceForecastWidget(ForecastUiState())
                        DashboardWidgetKind.PlannedOperations -> PlannedOperationsWidget(
                            expenses = emptyList(),
                            income = null,
                            isLoading = true
                        )
                    }
                }
            }
        }

        val dailyTitle = text(R.string.daily_remaining_today)
        val balanceTitle = text(R.string.dashboard_total_balance)
        val loading = hasContentDescription(text(R.string.dashboard_widget_loading))
        val dailySkeleton = compose.onNode(loading and hasAnyAncestor(hasTestTag("dashboard-widget-daily-limit")))
        val balanceSkeleton = compose.onNode(loading and hasAnyAncestor(hasTestTag("dashboard-widget-total-balance")))
        compose.onNodeWithText(dailyTitle).assertIsDisplayed()
        compose.onNodeWithText(balanceTitle).assertIsDisplayed()
        dailySkeleton.assertIsDisplayed()
        balanceSkeleton.assertIsDisplayed()
        compose.onAllNodes(hasClickAction()).assertCountEquals(1) // Profile only.
        compose.onNodeWithText("—").assertDoesNotExist()
        compose.onNodeWithText(formatMoney(BigDecimal.ZERO, realAmount.currency)).assertDoesNotExist()

        compose.runOnIdle {
            dailyLimit.value = DailyLimitUiState(
                amount = realAmount,
                remainingToday = realAmount,
                dataStatus = DashboardStatus.Ready
            )
            totalBalance.value = TotalBalanceUiState(
                amount = realAmount.copy(amount = BigDecimal.ZERO),
                dataStatus = DashboardStatus.Ready
            )
        }
        compose.mainClock.advanceTimeByFrame()
        dailySkeleton.assertDoesNotExist()
        balanceSkeleton.assertDoesNotExist()
        compose.onNodeWithText(formatMoney(realAmount.amount, realAmount.currency)).assertIsDisplayed()
        compose.onNodeWithText(formatMoney(BigDecimal.ZERO, realAmount.currency)).assertIsDisplayed()
        compose.onNodeWithText(dailyTitle).performClick()
        compose.runOnIdle { assertEquals(1, detailsClicks) }
    }

    @Test
    fun forecastErrorReplacesSkeletonWithoutExposingDetailsAction() {
        val state = mutableStateOf(ForecastUiState(dataStatus = DashboardStatus.Loading))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            FillinTheme {
                Box { BalanceForecastWidget(state.value) }
            }
        }
        val loading = compose.onNodeWithContentDescription(text(R.string.dashboard_widget_loading))
        loading.assertIsDisplayed()
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)

        compose.runOnIdle { state.value = ForecastUiState(dataStatus = DashboardStatus.Forbidden) }
        compose.mainClock.advanceTimeByFrame()
        loading.assertDoesNotExist()
        compose.onNodeWithText(text(R.string.dashboard_error_forbidden)).assertIsDisplayed()
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
    }

    private fun text(
        @StringRes id: Int
    ) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
}
