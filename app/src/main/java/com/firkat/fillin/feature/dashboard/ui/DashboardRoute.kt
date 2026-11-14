package com.firkat.fillin.feature.dashboard.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.feature.dashboard.widgets.availabletospend.ui.AvailableToSpendRoute
import com.firkat.fillin.feature.dashboard.widgets.balanceforecast.ui.BalanceForecastRoute
import com.firkat.fillin.feature.dashboard.widgets.dailylimit.ui.DailyLimitRoute
import com.firkat.fillin.feature.dashboard.widgets.totalbalance.ui.TotalBalanceRoute
import com.firkat.fillin.feature.plannedoperationswidget.ui.PlannedOperationsWidget
import java.time.LocalDate
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DashboardRoute(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = koinViewModel(),
    onForecastClick: () -> Unit = {},
    onPaymentsClick: (LocalDate, LocalDate) -> Unit = { _, _ -> },
    onProfileClick: () -> Unit = {},
    bottomBarOverlap: Dp = 0.dp
) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onEvent(DashboardEvent.Resume)
    }

    val title by viewModel.budgetState.collectAsStateWithLifecycle()
    val expenses by viewModel.plannedExpenses.collectAsStateWithLifecycle()
    val income by viewModel.plannedIncome.collectAsStateWithLifecycle()

    DashboardScreen(
        modifier = modifier,
        title = title,
        bottomBarOverlap = bottomBarOverlap,
        onProfileClick = onProfileClick
    ) { widget ->
        when (widget.kind) {
            DashboardWidgetKind.DailyLimit -> DailyLimitRoute(onPaymentsClick = onPaymentsClick)

            DashboardWidgetKind.TotalBalance -> TotalBalanceRoute()

            DashboardWidgetKind.AvailableToSpend -> AvailableToSpendRoute()

            DashboardWidgetKind.BalanceForecast -> BalanceForecastRoute(onDetailsClick = onForecastClick)

            DashboardWidgetKind.PlannedOperations -> if (expenses.isNotEmpty() || income != null) {
                PlannedOperationsWidget(
                    expenses = expenses,
                    income = income
                )
            }
        }
    }
}
