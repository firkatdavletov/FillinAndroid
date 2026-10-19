package com.firkat.fillin.feature.dashboard.widgets.dailylimit.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.dashboard.widgets.common.ui.MetricWidget
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DailyLimitRoute(modifier: Modifier = Modifier, viewModel: DailyLimitViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DailyLimitWidget(state, modifier)
}

@Composable
fun DailyLimitWidget(state: DailyLimitUiState, modifier: Modifier = Modifier) {
    MetricWidget(R.string.dashboard_daily_limit, R.drawable.ic_calendar, state.amount, modifier)
}
