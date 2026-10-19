package com.firkat.fillin.feature.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.firkat.fillin.feature.dashboard.domain.calculation.CalculateDashboard
import com.firkat.fillin.feature.dashboard.domain.calculation.DashboardCalculationInput
import com.firkat.fillin.feature.dashboard.ui.DashboardScreen
import com.firkat.fillin.feature.dashboard.ui.DashboardUiState
import com.firkat.fillin.ui.theme.FillinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class DashboardScreenOfflineTest {
    @get:Rule val compose = createComposeRule()

    @Test fun initialSyncDoesNotShowZeroFinancialFigures() {
        show(DashboardUiState())
        compose.onNodeWithText("Для расчёта Главной нужна первая полная синхронизация").assertIsDisplayed()
        compose.onNodeWithText("Свободно до конца месяца").assertDoesNotExist()
        compose.onNodeWithText("Месячный бюджет").assertDoesNotExist()
    }

    @Test fun emptyBudgetShowsNoFreeFundsAndUndefinedPercent() {
        val dashboard = CalculateDashboard.calculate(
            DashboardCalculationInput(
                Instant.parse("2026-10-19T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                emptyList(), emptyList(), emptyList(), emptyList(), emptySet(),
            ),
        )
        show(DashboardUiState(dashboard = dashboard, awaitingInitialSync = false))
        compose.onNodeWithText("Свободных средств нет", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Использовано —%").performScrollTo().assertIsDisplayed()
    }

    private fun show(state: DashboardUiState) {
        compose.setContent {
            FillinTheme {
                DashboardScreen(state, {}, {}, {}, {})
            }
        }
    }
}
