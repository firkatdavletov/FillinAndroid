package com.firkat.fillin.feature.dashboard.widgets.monthlybudget.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.dashboard.domain.model.Money
import com.firkat.fillin.feature.dashboard.widgets.common.ui.*
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MonthlyBudgetRoute(modifier: Modifier = Modifier, viewModel: MonthlyBudgetViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MonthlyBudgetWidget(state, modifier)
}

@Composable
fun MonthlyBudgetWidget(state: MonthlyBudgetUiState, modifier: Modifier = Modifier) {
    val metrics = listOf(
        R.string.dashboard_planned to state.budget?.planned,
        R.string.dashboard_spent to state.budget?.spent,
        R.string.dashboard_budget_remaining to state.budget?.remaining,
    )
    val largeFont = LocalDensity.current.fontScale > 1.3f
    BoxWithConstraints(modifier.fillMaxSize()) {
        val stacked = maxWidth < 420.dp || largeFont
        WidgetCard {
            WidgetHeading(R.string.dashboard_monthly_budget, R.drawable.ic_receipt)
            Spacer(Modifier.weight(1f))
            if (stacked) {
                metrics.forEach { (label, amount) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(label), Modifier.weight(1.3f), style = MaterialTheme.typography.bodySmall)
                        MoneyAmount(amount, Modifier.weight(1.7f), compact = true)
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    metrics.forEach { (label, amount) ->
                        BudgetMetric(stringResource(label), amount, Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(
                    progress = { state.progress }, modifier = Modifier.weight(1f).height(6.dp),
                    color = if (state.budget?.remaining?.amount?.signum() == -1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    drawStopIndicator = {},
                )
                Text(
                    state.usedPercent?.let { "${it.stripTrailingZeros().toPlainString()}%" } ?: "—",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BudgetMetric(label: String, amount: Money?, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoneyAmount(amount, compact = true)
    }
}
