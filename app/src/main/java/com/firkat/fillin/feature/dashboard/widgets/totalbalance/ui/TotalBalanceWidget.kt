package com.firkat.fillin.feature.dashboard.widgets.totalbalance.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.dashboard.widgets.common.ui.MetricWidget
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TotalBalanceRoute(
    modifier: Modifier = Modifier,
    viewModel: TotalBalanceViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TotalBalanceWidget(state, modifier)
}

@Composable
fun TotalBalanceWidget(
    state: TotalBalanceUiState,
    modifier: Modifier = Modifier,
) {
    MetricWidget(
        title = R.string.dashboard_total_balance,
        icon = R.drawable.ic_account_balance, state.amount, modifier
    )
}
