package com.firkat.fillin.feature.dashboard.widgets.dailylimit.ui

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.budgetprofile.data.ActiveBudgetManager
import com.firkat.fillin.feature.dashboard.domain.model.Money
import com.firkat.fillin.feature.dashboard.widgets.common.ui.WidgetCard
import com.firkat.fillin.feature.dashboard.widgets.common.ui.WidgetHeading
import com.firkat.fillin.ui.format.formatMoney
import com.firkat.fillin.ui.navigation.AppNavigationViewModel
import com.firkat.fillin.ui.theme.FillinTheme
import com.firkat.fillin.ui.theme.saldomiDesignColors
import org.koin.compose.koinInject
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DailyLimitRoute(
    modifier: Modifier = Modifier,
    viewModel: DailyLimitViewModel = koinViewModel(),
    onPaymentsClick: (LocalDate, LocalDate) -> Unit = { _, _ -> }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigation: AppNavigationViewModel =
        koinViewModel(
            viewModelStoreOwner = checkNotNull(
                LocalActivity.current as? ComponentActivity
            )
        )
    val budgets: ActiveBudgetManager = koinInject()
    DailyLimitWidget(
        state = state,
        modifier = modifier,
        onDetailsClick = {
            budgets.active.value
                ?.scope
                ?.id
                ?.let(navigation::openDailyLimit)
        },
        onPaymentsClick = {
            state.forecast?.let {
                result -> result.firstDeficitDate?.let { onPaymentsClick(result.horizonStart, it) }
            }
        }
    )
}

@Composable
internal fun DailyLimitWidget(
    state: DailyLimitUiState,
    modifier: Modifier = Modifier,
    onDetailsClick: () -> Unit = {},
    onPaymentsClick: () -> Unit = {}
) {
    val deficit = state.status == DailyLimitCardStatus.Deficit
    val color = if (state.status !=
        DailyLimitCardStatus.Normal
    ) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    WidgetCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                onClick = onDetailsClick
            ),
        containerColor = MaterialTheme.saldomiDesignColors.primaryContainer,
        contentColor = MaterialTheme.saldomiDesignColors.onPrimaryContainer
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WidgetHeading(
                title = R.string.daily_remaining_today,
            )
            Text(
                text = state.displayedRemainingToday?.let { formatMoney(it.amount, it.currency) } ?: "—",
                Modifier.testTag("daily-limit-amount"),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold
            )
            if (state.amount != null) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.dashboard_daily_limit),
                            modifier = Modifier.testTag("daily-limit-spent"),
                        )
                        Text(
                            text = state.amount.let { formatMoney(it.amount, it.currency) } ?: "—"
                        )
                    }

                    if (state.showRemaining) {
                        Column {
                            Text(
                                text = stringResource(R.string.daily_spent_today),
                                modifier = Modifier.testTag("daily-limit-remaining"),
                            )
                            Text(
                                text = state.spentToday?.let { formatMoney(it.amount, it.currency) } ?: "—"
                            )
                        }
                    }
                }
                if (!deficit && state.isOverspent) {
                    Text(
                        stringResource(
                            R.string.daily_excess,
                            requireNotNull(state.overspentAmount).let { formatMoney(it.amount, it.currency) }
                        ),
                        color = color
                    )
                }
            }
            if (deficit) {
                state.forecast?.let { result ->
                    val date = requireNotNull(result.firstDeficitDate)
                    val pattern = if (date.year == result.horizonStart.year) "d MMMM" else "d MMMM yyyy"
                    androidx.compose.material3.Card(
                        onClick = onPaymentsClick,
                        modifier = Modifier.fillMaxWidth().testTag("daily-limit-payments"),
                        colors =
                            androidx.compose.material3.CardDefaults
                                .cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                stringResource(
                                    R.string.daily_shortfall,
                                    formatMoney(
                                        requireNotNull(result.deficitAmount).amount,
                                        result.dailyTarget.currency
                                    ),
                                    date.format(
                                        java.time.format.DateTimeFormatter
                                            .ofPattern(pattern, Locale.forLanguageTag("ru"))
                                    )
                                ),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                stringResource(R.string.daily_view_payments),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

private val previewState =
    DailyLimitUiState(
        amount = Money("10000.00".toBigDecimal(), "KZT"),
        spentToday = Money("2500.00".toBigDecimal(), "KZT"),
        remainingToday = Money("7500.00".toBigDecimal(), "KZT"),
        spentPercent = "25.0".toBigDecimal(),
        remainingPercent = "75.0".toBigDecimal(),
        progress = 0.25f
    )

@Preview(name = "Daily limit", widthDp = 288)
@Preview(name = "Daily limit dark", widthDp = 288, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Daily limit large text", widthDp = 288, fontScale = 2f)
@Preview(name = "Daily limit wide", widthDp = 808)
@Composable
private fun DailyLimitPreview() {
    FillinTheme { DailyLimitWidget(previewState) }
}

@Preview(name = "No expenses", widthDp = 288)
@Preview(name = "No expenses dark", widthDp = 288, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DailyLimitNoExpensesPreview() {
    FillinTheme {
        DailyLimitWidget(
            previewState.copy(
                spentToday = Money("0.00".toBigDecimal(), "KZT"),
                remainingToday = previewState.amount,
                spentPercent = "0.0".toBigDecimal(),
                remainingPercent = "100.0".toBigDecimal(),
                progress = 0f
            )
        )
    }
}

@Preview(name = "Limit reached", widthDp = 288)
@Preview(name = "Limit reached dark", widthDp = 288, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DailyLimitReachedPreview() {
    FillinTheme {
        DailyLimitWidget(
            previewState.copy(
                spentToday = previewState.amount,
                remainingToday = Money("0.00".toBigDecimal(), "KZT"),
                spentPercent = "100.0".toBigDecimal(),
                remainingPercent = "0.0".toBigDecimal(),
                progress = 1f
            )
        )
    }
}

@Preview(name = "Overspent", widthDp = 288)
@Preview(name = "Overspent dark", widthDp = 288, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DailyLimitOverspentPreview() {
    FillinTheme {
        DailyLimitWidget(
            previewState.copy(
                spentToday = Money("12500.00".toBigDecimal(), "KZT"),
                remainingToday = Money("0.00".toBigDecimal(), "KZT"),
                spentPercent = "125.0".toBigDecimal(),
                remainingPercent = "25.0".toBigDecimal(),
                progress = 1f,
                isOverspent = true
            )
        )
    }
}

@Preview(name = "Zero limit", widthDp = 288)
@Preview(name = "Zero limit dark", widthDp = 288, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DailyLimitZeroPreview() {
    FillinTheme {
        val zero = Money("0.00".toBigDecimal(), "KZT")
        DailyLimitWidget(DailyLimitUiState(amount = zero, spentToday = zero, remainingToday = zero))
    }
}

@Preview(name = "Zero limit overspent", widthDp = 288)
@Preview(name = "Zero limit overspent dark", widthDp = 288, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DailyLimitZeroOverspentPreview() {
    FillinTheme {
        DailyLimitWidget(
            DailyLimitUiState(
                amount = Money("0.00".toBigDecimal(), "KZT"),
                spentToday = previewState.spentToday,
                remainingToday = Money(BigDecimal.ZERO, "KZT"),
                progress = 0f,
                isOverspent = true
            )
        )
    }
}

@Preview(name = "No data", widthDp = 288)
@Preview(name = "No data dark", widthDp = 288, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DailyLimitUnavailablePreview() {
    FillinTheme { DailyLimitWidget(DailyLimitUiState()) }
}

@Preview(name = "Large amounts", widthDp = 288)
@Preview(name = "Large amounts dark", widthDp = 288, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DailyLimitLargeAmountsPreview() {
    FillinTheme {
        DailyLimitWidget(
            previewState.copy(
                amount = Money("123456789.00".toBigDecimal(), "KZT"),
                spentToday = Money("30864197.25".toBigDecimal(), "KZT"),
                remainingToday = Money("92592591.75".toBigDecimal(), "KZT")
            )
        )
    }
}

private fun forecastPreviewState(
    kind: Int
): DailyLimitUiState {
    val date = java.time.LocalDate.of(2026, 11, 1)

    fun money(
        value: String
    ) = Money(value.toBigDecimal(), "KZT")
    val events =
        when (kind) {
            0 -> {
                listOf(
                    com.firkat.fillin.feature.dailylimit.domain
                        .ForecastEvent("salary", "Зарплата", date.plusDays(30), money("300000"), true)
                )
            }

            1 -> {
                listOf(
                    com.firkat.fillin.feature.dailylimit.domain
                        .ForecastEvent("salary", "Зарплата", date.plusDays(14), money("300000"), true)
                )
            }

            2 -> {
                listOf(
                    com.firkat.fillin.feature.dailylimit.domain
                        .ForecastEvent("payment", "Аренда", date.plusDays(4), money("100000"), false)
                )
            }

            else -> {
                emptyList()
            }
        }
    val result =
        com.firkat.fillin.feature.dailylimit.domain.DailyLimitCalculator().calculate(
            com.firkat.fillin.feature.dailylimit.domain.DailyLimitInput(
                money(
                    if (kind ==
                        4
                    ) {
                        "57000"
                    } else {
                        "60000"
                    }
                ),
                money(if (kind == 4) "3000" else "0"),
                events,
                date
            )
        )
    return DailyLimitUiState.from(
        com.firkat.fillin.feature.dashboard.widgets.dailylimit.domain.DailyLimitSummary(
            result.dailyTarget,
            result.spentToday,
            result.remainingToday,
            null,
            null,
            result
        )
    )
}

@Preview(name = "OK", widthDp = 360)
@Preview(name = "OK dark", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OKForecastPreview() {
    FillinTheme { DailyLimitWidget(forecastPreviewState(0)) }
}

@Preview(name = "IncomeDependency", widthDp = 360)
@Preview(name = "IncomeDependency dark", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun IncomeDependencyForecastPreview() {
    FillinTheme { DailyLimitWidget(forecastPreviewState(1)) }
}

@Preview(name = "Deficit", widthDp = 360)
@Preview(name = "Deficit dark", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DeficitForecastPreview() {
    FillinTheme { DailyLimitWidget(forecastPreviewState(2)) }
}

@Preview(name = "Incomplete", widthDp = 360)
@Preview(name = "Incomplete dark", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun IncompleteForecastPreview() {
    FillinTheme { DailyLimitWidget(forecastPreviewState(3)) }
}

@Preview(name = "Overspending", widthDp = 360)
@Preview(name = "Overspending dark", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OverspendingForecastPreview() {
    FillinTheme { DailyLimitWidget(forecastPreviewState(4)) }
}

@Preview(name = "Forecast narrow large font", widthDp = 320, fontScale = 2f)
@Preview(name = "Forecast tablet", widthDp = 840)
@Composable
private fun AdaptiveForecastPreview() {
    FillinTheme { DailyLimitWidget(forecastPreviewState(2)) }
}

@Preview(name = "Deficit with expenses", widthDp = 320, fontScale = 2f)
@Preview(name = "Deficit with expenses dark", widthDp = 320, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DeficitWithExpensesPreview() {
    FillinTheme {
        DailyLimitWidget(
            forecastPreviewState(2).copy(spentToday = Money("3500.00".toBigDecimal(), "KZT"), isOverspent = true)
        )
    }
}
