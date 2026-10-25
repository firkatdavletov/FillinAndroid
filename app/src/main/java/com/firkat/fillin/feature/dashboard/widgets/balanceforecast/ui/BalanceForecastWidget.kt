package com.firkat.fillin.feature.dashboard.widgets.balanceforecast.ui

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.dashboard.domain.model.ForecastPoint
import com.firkat.fillin.feature.dashboard.domain.model.Money
import com.firkat.fillin.feature.dashboard.widgets.common.ui.MoneyAmount
import com.firkat.fillin.feature.dashboard.widgets.common.ui.WidgetCard
import com.firkat.fillin.feature.dashboard.widgets.common.ui.WidgetHeading
import com.firkat.fillin.ui.format.formatMoney
import com.firkat.fillin.ui.theme.FillinTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun BalanceForecastRoute(modifier: Modifier = Modifier, viewModel: BalanceForecastViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BalanceForecastWidget(state, modifier)
}

@Composable
internal fun BalanceForecastWidget(state: BalanceForecastUiState, modifier: Modifier = Modifier) {
    val start = state.startDate
    val end = state.endDate
    val datePattern = if (start?.year != end?.year) "d MMM yyyy" else "d MMM"
    val minimum = state.minimum
    val maximum = state.maximum
    WidgetCard(modifier) {
        WidgetHeading(R.string.dashboard_balance_forecast, R.drawable.ic_forecast)
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (start != null && end != null) stringResource(
                R.string.dashboard_forecast_period, formatDate(start, datePattern), formatDate(end, datePattern),
            ) else stringResource(R.string.dashboard_value_unavailable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.insufficientHistory) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.dashboard_insufficient_history),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val chartSummary = if (minimum != null && maximum != null) stringResource(
                R.string.dashboard_forecast_chart_summary,
                formatMoney(minimum.balance.amount, minimum.balance.currency), formatDate(minimum.date, "d MMMM yyyy"),
                formatMoney(maximum.balance.amount, maximum.balance.currency), formatDate(maximum.date, "d MMMM yyyy"),
            ) else stringResource(R.string.dashboard_value_unavailable)
            ForecastChart(state.points, chartSummary, Modifier.fillMaxWidth().weight(1f))
            if (LocalDensity.current.fontScale > 1.3f) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ForecastStat(R.string.dashboard_forecast_minimum, minimum, datePattern, Modifier.testTag("forecast-minimum"))
                    ForecastStat(R.string.dashboard_forecast_maximum, maximum, datePattern, Modifier.testTag("forecast-maximum"))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ForecastStat(R.string.dashboard_forecast_minimum, minimum, datePattern, Modifier.weight(1f).testTag("forecast-minimum"))
                    ForecastStat(R.string.dashboard_forecast_maximum, maximum, datePattern, Modifier.weight(1f).testTag("forecast-maximum"))
                }
            }
        }
    }
}

@Composable
private fun ForecastStat(
    @StringRes title: Int,
    point: ForecastPoint?,
    datePattern: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoneyAmount(point?.balance)
        Text(
            point?.let { formatDate(it.date, datePattern) } ?: stringResource(R.string.dashboard_value_unavailable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ForecastChart(points: List<ForecastPoint>, summary: String, modifier: Modifier = Modifier) {
    val geometry = remember(points) { ForecastChartGeometry(points) }
    val lineColor = MaterialTheme.colorScheme.primary
    val minimumColor = if (points.any { it.balance.amount.signum() < 0 }) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.tertiary
    val zeroColor = MaterialTheme.colorScheme.outlineVariant
    val label = stringResource(R.string.dashboard_forecast_chart)
    Canvas(modifier.testTag("forecast-chart").semantics {
        contentDescription = label
        stateDescription = summary
    }) {
        val inset = minOf(10.dp.toPx(), size.width / 2f, size.height / 2f)
        fun ChartPoint.offset() = Offset(inset + x * (size.width - 2 * inset), inset + y * (size.height - 2 * inset))
        geometry.zeroY?.let { y ->
            drawLine(
                zeroColor, ChartPoint(0f, y).offset(), ChartPoint(1f, y).offset(),
                strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            )
        }
        if (geometry.segments.isNotEmpty()) {
            val path = Path().apply {
                val start = geometry.segments.first().start.offset()
                moveTo(start.x, start.y)
                geometry.segments.forEach { segment ->
                    val first = segment.first.offset()
                    val second = segment.second.offset()
                    val end = segment.end.offset()
                    cubicTo(first.x, first.y, second.x, second.y, end.x, end.y)
                }
            }
            drawPath(path, lineColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
        listOfNotNull(geometry.minimumIndex, geometry.maximumIndex).distinct().forEach { index ->
            val color = if (index == geometry.minimumIndex) minimumColor else lineColor
            val center = geometry.vertices[index].offset()
            drawCircle(color.copy(alpha = 0.15f), 8.dp.toPx(), center)
            drawCircle(color, 3.5.dp.toPx(), center)
        }
    }
}

private fun formatDate(date: LocalDate, pattern: String) =
    date.format(DateTimeFormatter.ofPattern(pattern, Locale.forLanguageTag("ru-KZ")))

internal val forecastPreviewState: BalanceForecastUiState = run {
    val start = LocalDate.of(2026, 10, 25)
    BalanceForecastUiState(start, start.plusDays(30), (0L..30L).map { day ->
        val amount = 120000L - day * 3500L + if (day >= 12) 80000L else 0L
        ForecastPoint(start.plusDays(day), Money(amount.toBigDecimal().setScale(2), "KZT"))
    })
}

@Preview(name = "Forecast", widthDp = 288, heightDp = 231)
@Preview(name = "Forecast dark", widthDp = 288, heightDp = 231, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Forecast wide", widthDp = 808, heightDp = 621)
@Preview(name = "Forecast large text", widthDp = 288, heightDp = 741, fontScale = 2f)
@Composable
private fun BalanceForecastPreview() {
    FillinTheme { BalanceForecastWidget(forecastPreviewState) }
}

@Preview(name = "Forecast deficit", widthDp = 288, heightDp = 231)
@Preview(name = "Forecast deficit dark", widthDp = 288, heightDp = 231, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BalanceForecastDeficitPreview() {
    FillinTheme {
        BalanceForecastWidget(forecastPreviewState.copy(points = forecastPreviewState.points.map {
            it.copy(balance = it.balance.copy(amount = it.balance.amount - "100000.00".toBigDecimal()))
        }))
    }
}

@Preview(name = "Forecast entirely negative", widthDp = 288, heightDp = 231)
@Preview(name = "Forecast entirely negative dark", widthDp = 288, heightDp = 231, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BalanceForecastNegativePreview() {
    FillinTheme {
        BalanceForecastWidget(forecastPreviewState.copy(points = forecastPreviewState.points.map {
            it.copy(balance = it.balance.copy(amount = it.balance.amount - "200000.00".toBigDecimal()))
        }))
    }
}

@Preview(name = "Forecast flat", widthDp = 288, heightDp = 231)
@Preview(name = "Forecast flat dark", widthDp = 288, heightDp = 231, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BalanceForecastFlatPreview() {
    FillinTheme {
        BalanceForecastWidget(forecastPreviewState.copy(points = forecastPreviewState.points.map {
            it.copy(balance = Money("10000.00".toBigDecimal(), "KZT"))
        }))
    }
}

@Preview(name = "Forecast insufficient history", widthDp = 288, heightDp = 231)
@Preview(name = "Forecast insufficient history dark", widthDp = 288, heightDp = 231, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BalanceForecastInsufficientHistoryPreview() {
    FillinTheme { BalanceForecastWidget(forecastPreviewState.copy(points = emptyList(), insufficientHistory = true)) }
}

@Preview(name = "Forecast loading", widthDp = 288, heightDp = 231)
@Preview(name = "Forecast loading dark", widthDp = 288, heightDp = 231, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BalanceForecastLoadingPreview() {
    FillinTheme { BalanceForecastWidget(BalanceForecastUiState()) }
}

@Preview(name = "Forecast large amounts across years", widthDp = 288, heightDp = 231)
@Preview(name = "Forecast large amounts large text", widthDp = 288, heightDp = 741, fontScale = 2f)
@Composable
private fun BalanceForecastLargeAmountsPreview() {
    val start = LocalDate.of(2026, 12, 25)
    FillinTheme {
        BalanceForecastWidget(BalanceForecastUiState(start, start.plusDays(30), listOf(
            ForecastPoint(start, Money("12345678.90".toBigDecimal(), "KZT")),
            ForecastPoint(start.plusDays(30), Money("-1234567.89".toBigDecimal(), "KZT")),
        )))
    }
}

@Preview(name = "Forecast chart", widthDp = 288, heightDp = 110)
@Preview(name = "Forecast chart dark", widthDp = 288, heightDp = 110, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ForecastChartPreview() {
    FillinTheme { ForecastChart(forecastPreviewState.points, stringResource(R.string.dashboard_forecast_chart)) }
}

@Preview(name = "Forecast stat", widthDp = 138)
@Preview(name = "Forecast stat dark", widthDp = 138, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ForecastStatPreview() {
    FillinTheme { ForecastStat(R.string.dashboard_forecast_minimum, forecastPreviewState.minimum, "d MMM") }
}
