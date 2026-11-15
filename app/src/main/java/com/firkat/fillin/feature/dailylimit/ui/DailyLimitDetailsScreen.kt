package com.firkat.fillin.feature.dailylimit.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.dailylimit.domain.DailyLimitResult
import com.firkat.fillin.feature.dashboard.widgets.dailylimit.ui.DailyLimitViewModel
import com.firkat.fillin.ui.component.FeatureScaffold
import com.firkat.fillin.ui.format.formatMoney
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DailyLimitDetailsRoute(
    onBack: () -> Unit,
    viewModel: DailyLimitViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FeatureScaffold(titleResource = R.string.daily_details_title, onNavigateBack = onBack) {
        state.forecast?.let { DailyLimitDetailsScreen(it) } ?: CircularProgressIndicator()
    }
}

@Composable
internal fun DailyLimitDetailsScreen(
    result: DailyLimitResult,
    modifier: Modifier = Modifier
) {
    fun amount(
        value: com.firkat.fillin.feature.dashboard.domain.model.Money
    ) = formatMoney(value.amount, value.currency)
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            if (result.firstDeficitDate != null) {
                Text(
                    stringResource(
                        R.string.daily_deficit_value,
                        amount(requireNotNull(result.deficitAmount)),
                        result.firstDeficitDate.toString()
                    ),
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (result.dependsOnPlannedIncome) Text(stringResource(R.string.daily_depends_income))
            if (result.warnings.isNotEmpty()) Text(stringResource(R.string.daily_incomplete))
            if (result.overspentToday.amount.signum() > 0) {
                Text(
                    stringResource(
                        R.string.daily_overspent_value,
                        amount(result.overspentToday),
                        amount(result.nextDaysLimit)
                    ),
                    color = MaterialTheme.colorScheme.error
                )
            }
            Text(stringResource(R.string.daily_horizon_value, result.horizonDays, result.horizonEnd.toString()))
            Text(amount(result.dailyTarget), style = MaterialTheme.typography.headlineLarge)
            Text(
                stringResource(
                    R.string.daily_forecast_period,
                    result.horizonStart.toString(),
                    result.horizonEnd.toString()
                )
            )
            Text(stringResource(R.string.daily_balance_value, amount(result.currentBalance)))
            Text(stringResource(R.string.daily_income_value, amount(result.plannedIncome)))
            Text(stringResource(R.string.daily_payments_value, amount(result.plannedPayments)))
            Text(stringResource(R.string.daily_available_value, amount(result.availableBeforeDailySpending)))
            Text(stringResource(R.string.daily_limiting_value, result.limitingDate.toString()))
            Text(stringResource(R.string.daily_rounding_explanation))
        }
        items(result.projectedDailyBalances, key = { it.date.toString() }) { day ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HorizontalDivider()
                Text(day.date.toString(), style = MaterialTheme.typography.titleMedium)
                day.events.forEach { event ->
                    Text(
                        stringResource(
                            R.string.daily_event_value,
                            event.name,
                            (if (event.income) "+" else "−") + amount(event.amount),
                            event.dueOn.toString()
                        )
                    )
                }
                Text(stringResource(R.string.daily_projected_balance, amount(day.afterDailySpending)))
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(widthDp = 360, heightDp = 800)
@androidx.compose.ui.tooling.preview.Preview(
    widthDp = 360,
    heightDp = 800,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
)
@androidx.compose.runtime.Composable
private fun DailyLimitDetailsPreview() {
    val money = com.firkat.fillin.feature.dashboard.domain.model.Money(java.math.BigDecimal("60000"), "KZT")
    val result = com.firkat.fillin.feature.dailylimit.domain.DailyLimitCalculator().calculate(
        com.firkat.fillin.feature.dailylimit.domain.DailyLimitInput(
            money,
            money.copy(amount = java.math.BigDecimal.ZERO),
            emptyList(),
            java.time.LocalDate.of(2026, 11, 1)
        )
    )
    com.firkat.fillin.ui.theme.FillinTheme { DailyLimitDetailsScreen(result) }
}
