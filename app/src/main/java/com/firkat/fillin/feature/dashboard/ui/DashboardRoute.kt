package com.firkat.fillin.feature.dashboard.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.firkat.fillin.feature.dashboard.widgets.availabletospend.ui.AvailableToSpendRoute
import com.firkat.fillin.feature.dashboard.widgets.balanceforecast.ui.BalanceForecastRoute
import com.firkat.fillin.feature.dashboard.widgets.dailylimit.ui.DailyLimitRoute
import com.firkat.fillin.feature.dashboard.widgets.totalbalance.ui.TotalBalanceRoute
import org.koin.compose.viewmodel.koinViewModel
import java.time.LocalDate

@Composable
fun DashboardRoute(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = koinViewModel(),
    onForecastClick: () -> Unit = {},
    onPaymentsClick: (LocalDate, LocalDate) -> Unit = { _, _ -> },
    bottomBarOverlap: Dp = 0.dp,
) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onEvent(DashboardEvent.Resume) 
    }
    DashboardScreen(
        modifier = modifier,
        bottomBarOverlap = bottomBarOverlap,
    ) { widget ->
        when (widget.kind) {
            DashboardWidgetKind.TotalBalance -> TotalBalanceRoute()
            DashboardWidgetKind.AvailableToSpend -> AvailableToSpendRoute()
            DashboardWidgetKind.DailyLimit -> DailyLimitRoute(onPaymentsClick = onPaymentsClick)
            DashboardWidgetKind.BalanceForecast -> BalanceForecastRoute(onDetailsClick = onForecastClick)
        }
    }
}