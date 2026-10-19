package com.firkat.fillin.feature.dashboard.widgets.availabletospend.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.dashboard.widgets.common.ui.MetricWidget
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AvailableToSpendRoute(modifier: Modifier = Modifier, viewModel: AvailableToSpendViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AvailableToSpendWidget(state, modifier)
}

@Composable
fun AvailableToSpendWidget(state: AvailableToSpendUiState, modifier: Modifier = Modifier) {
    MetricWidget(R.string.dashboard_available_to_spend, R.drawable.ic_wallet, state.amount, modifier)
}
