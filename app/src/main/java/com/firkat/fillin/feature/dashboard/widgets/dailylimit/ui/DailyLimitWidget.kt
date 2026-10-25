package com.firkat.fillin.feature.dashboard.widgets.dailylimit.ui

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.dashboard.domain.model.Money
import com.firkat.fillin.feature.dashboard.widgets.common.ui.MoneyAmount
import com.firkat.fillin.feature.dashboard.widgets.common.ui.WidgetCard
import com.firkat.fillin.feature.dashboard.widgets.common.ui.WidgetHeading
import com.firkat.fillin.ui.format.formatMoney
import com.firkat.fillin.ui.theme.FillinTheme
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DailyLimitRoute(modifier: Modifier = Modifier, viewModel: DailyLimitViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DailyLimitWidget(state, modifier)
}

@Composable
internal fun DailyLimitWidget(state: DailyLimitUiState, modifier: Modifier = Modifier) {
    val progressLabel = stringResource(R.string.dashboard_daily_progress)
    val progressState = state.spentPercent?.let {
        stringResource(R.string.dashboard_percent, formatPercentageNumber(it))
    } ?: stringResource(R.string.dashboard_value_unavailable)
    WidgetCard(modifier) {
        WidgetHeading(R.string.dashboard_daily_limit, R.drawable.ic_calendar)
        Spacer(Modifier.height(4.dp))
        MoneyAmount(state.amount, Modifier.testTag("daily-limit-amount"))
        Spacer(Modifier.weight(1f))
        LinearProgressIndicator(
            progress = { state.progress },
            modifier = Modifier.fillMaxWidth().testTag("daily-limit-progress").semantics {
                contentDescription = progressLabel
                stateDescription = progressState
            },
            color = if (state.isOverspent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        if (LocalDensity.current.fontScale > 1.3f) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DailyLimitStat(
                    title = R.string.dashboard_spent_today,
                    money = state.spentToday,
                    percent = state.spentPercent,
                    modifier = Modifier.fillMaxWidth().testTag("daily-limit-spent"),
                )
                DailyLimitStat(
                    title = if (state.isOverspent) R.string.dashboard_daily_overspending else R.string.dashboard_remaining_today,
                    money = state.remainingToday,
                    percent = state.remainingPercent,
                    modifier = Modifier.fillMaxWidth().testTag("daily-limit-remaining"),
                    isError = state.isOverspent,
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DailyLimitStat(
                    title = R.string.dashboard_spent_today,
                    money = state.spentToday,
                    percent = state.spentPercent,
                    modifier = Modifier.weight(1f).testTag("daily-limit-spent"),
                )
                DailyLimitStat(
                    title = if (state.isOverspent) R.string.dashboard_daily_overspending else R.string.dashboard_remaining_today,
                    money = state.remainingToday,
                    percent = state.remainingPercent,
                    modifier = Modifier.weight(1f).testTag("daily-limit-remaining"),
                    isError = state.isOverspent,
                )
            }
        }
    }
}

@Composable
private fun DailyLimitStat(
    @StringRes title: Int,
    money: Money?,
    percent: BigDecimal?,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    val color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) color else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BasicText(
            text = money?.let { formatMoney(it.amount, it.currency) } ?: stringResource(R.string.dashboard_value_unavailable),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                lineHeight = TextUnit.Unspecified,
                color = color,
            ),
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(
                minFontSize = (8f / LocalDensity.current.fontScale).sp,
                maxFontSize = 20.sp,
                stepSize = 1.sp,
            ),
        )
        Text(
            text = percent?.let { stringResource(R.string.dashboard_percent, formatPercentageNumber(it)) }
                ?: stringResource(R.string.dashboard_value_unavailable),
            style = MaterialTheme.typography.labelSmall,
            color = if (isError) color else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatPercentageNumber(value: BigDecimal): String =
    NumberFormat.getNumberInstance(Locale.forLanguageTag("ru-KZ")).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }.format(value)

private val previewState = DailyLimitUiState(
    amount = Money("10000.00".toBigDecimal(), "KZT"),
    spentToday = Money("2500.00".toBigDecimal(), "KZT"),
    remainingToday = Money("7500.00".toBigDecimal(), "KZT"),
    spentPercent = "25.0".toBigDecimal(),
    remainingPercent = "75.0".toBigDecimal(),
    progress = 0.25f,
)

@Preview(name = "Daily limit", widthDp = 288, heightDp = 150)
@Preview(name = "Daily limit dark", widthDp = 288, heightDp = 150, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Daily limit large text", widthDp = 288, heightDp = 490, fontScale = 2f)
@Preview(name = "Daily limit wide", widthDp = 808, heightDp = 410)
@Composable
private fun DailyLimitPreview() {
    FillinTheme { DailyLimitWidget(previewState) }
}

@Preview(name = "No expenses", widthDp = 288, heightDp = 150)
@Preview(name = "No expenses dark", widthDp = 288, heightDp = 150, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DailyLimitNoExpensesPreview() {
    FillinTheme {
        DailyLimitWidget(previewState.copy(
            spentToday = Money("0.00".toBigDecimal(), "KZT"), remainingToday = previewState.amount,
            spentPercent = "0.0".toBigDecimal(), remainingPercent = "100.0".toBigDecimal(), progress = 0f,
        ))
    }
}

@Preview(name = "Limit reached", widthDp = 288, heightDp = 150)
@Preview(name = "Limit reached dark", widthDp = 288, heightDp = 150, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DailyLimitReachedPreview() {
    FillinTheme {
        DailyLimitWidget(previewState.copy(
            spentToday = previewState.amount, remainingToday = Money("0.00".toBigDecimal(), "KZT"),
            spentPercent = "100.0".toBigDecimal(), remainingPercent = "0.0".toBigDecimal(), progress = 1f,
        ))
    }
}

@Preview(name = "Overspent", widthDp = 288, heightDp = 150)
@Preview(name = "Overspent dark", widthDp = 288, heightDp = 150, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DailyLimitOverspentPreview() {
    FillinTheme {
        DailyLimitWidget(previewState.copy(
            spentToday = Money("12500.00".toBigDecimal(), "KZT"), remainingToday = Money("2500.00".toBigDecimal(), "KZT"),
            spentPercent = "125.0".toBigDecimal(), remainingPercent = "25.0".toBigDecimal(), progress = 1f, isOverspent = true,
        ))
    }
}

@Preview(name = "Zero limit", widthDp = 288, heightDp = 150)
@Preview(name = "Zero limit dark", widthDp = 288, heightDp = 150, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DailyLimitZeroPreview() {
    FillinTheme {
        val zero = Money("0.00".toBigDecimal(), "KZT")
        DailyLimitWidget(DailyLimitUiState(amount = zero, spentToday = zero, remainingToday = zero))
    }
}

@Preview(name = "Zero limit overspent", widthDp = 288, heightDp = 150)
@Preview(name = "Zero limit overspent dark", widthDp = 288, heightDp = 150, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DailyLimitZeroOverspentPreview() {
    FillinTheme {
        DailyLimitWidget(DailyLimitUiState(
            amount = Money("0.00".toBigDecimal(), "KZT"), spentToday = previewState.spentToday,
            remainingToday = previewState.spentToday, progress = 1f, isOverspent = true,
        ))
    }
}

@Preview(name = "No data", widthDp = 288, heightDp = 150)
@Preview(name = "No data dark", widthDp = 288, heightDp = 150, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DailyLimitUnavailablePreview() {
    FillinTheme { DailyLimitWidget(DailyLimitUiState()) }
}

@Preview(name = "Large amounts", widthDp = 288, heightDp = 150)
@Preview(name = "Large amounts dark", widthDp = 288, heightDp = 150, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DailyLimitLargeAmountsPreview() {
    FillinTheme {
        DailyLimitWidget(previewState.copy(
            amount = Money("123456789.00".toBigDecimal(), "KZT"),
            spentToday = Money("30864197.25".toBigDecimal(), "KZT"),
            remainingToday = Money("92592591.75".toBigDecimal(), "KZT"),
        ))
    }
}
