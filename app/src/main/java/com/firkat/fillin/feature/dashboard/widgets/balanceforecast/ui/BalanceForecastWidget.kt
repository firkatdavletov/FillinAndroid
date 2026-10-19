package com.firkat.fillin.feature.dashboard.widgets.balanceforecast.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.dashboard.widgets.common.ui.MetricWidget
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun BalanceForecastRoute(modifier: Modifier = Modifier, viewModel: BalanceForecastViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BalanceForecastWidget(state, modifier)
}

@Composable
fun BalanceForecastWidget(state: BalanceForecastUiState, modifier: Modifier = Modifier) {
    MetricWidget(
        R.string.dashboard_balance_forecast, R.drawable.ic_forecast, state.amount, modifier,
        supportingText = if (state.insufficientHistory) stringResource(R.string.dashboard_insufficient_history) else null,
    )
}
