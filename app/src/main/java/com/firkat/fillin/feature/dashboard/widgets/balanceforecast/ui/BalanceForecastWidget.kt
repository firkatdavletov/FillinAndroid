package com.firkat.fillin.feature.dashboard.widgets.balanceforecast.ui

import com.firkat.fillin.feature.dashboard.domain.model.ForecastSeries
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
internal fun BalanceForecastWidget(state: ForecastUiState, modifier: Modifier = Modifier) {
    val start = state.startDate
    val end = state.endDate
    val datePattern = if (start?.year != end?.year) "d MMM yyyy" else "d MMM"
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
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val chartSummary = scenarioDescription(R.string.forecast_by_limit, state.byLimit) +
                (state.currentPace?.let { "\n" + scenarioDescription(R.string.forecast_current_pace, it) } ?: "")
            ForecastChart(state.limitGeometry, state.paceGeometry, chartSummary, Modifier.fillMaxWidth().height(110.dp))
            ScenarioSummary(R.string.forecast_by_limit, state.byLimit, false, datePattern)
            state.currentPace?.let { ScenarioSummary(R.string.forecast_current_pace, it, true, datePattern) }
            if (state.insufficientHistory) Text(stringResource(R.string.forecast_pace_unavailable), style = MaterialTheme.typography.bodySmall)
            if (state.dependsOnPlannedIncome) Text(stringResource(R.string.daily_depends_income), style = MaterialTheme.typography.bodySmall)
            if (state.warnings.isNotEmpty()) Text(stringResource(R.string.daily_incomplete), style = MaterialTheme.typography.bodySmall)

        }
    }
}

@Composable
private fun scenarioDescription(@StringRes title: Int, series: ForecastSeries): String {
    val minimum = series.minimum
    val maximum = series.maximum
    val summary = if (minimum != null && maximum != null) stringResource(
        R.string.dashboard_forecast_chart_summary,
        formatMoney(minimum.balance.amount, minimum.balance.currency), formatDate(minimum.date, "d MMMM yyyy"),
        formatMoney(maximum.balance.amount, maximum.balance.currency), formatDate(maximum.date, "d MMMM yyyy"),
    ) else stringResource(R.string.dashboard_value_unavailable)
    return stringResource(title) + ": " + summary
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
private fun ScenarioSummary(
    @StringRes title: Int,
    series: ForecastSeries,
    dashed: Boolean,
    datePattern: String,
    modifier: Modifier = Modifier,
) {
    val lineColor = if (dashed) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Canvas(Modifier.width(24.dp).height(8.dp)) {
                drawLine(lineColor, Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
                    strokeWidth = 2.dp.toPx(), pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null)
            }
            Text(stringResource(title), style = MaterialTheme.typography.labelMedium)
        }
        Text(stringResource(R.string.forecast_ending), style = MaterialTheme.typography.bodySmall)
        MoneyAmount(series.endingBalance)
        val date = series.firstDeficitDate
        Text(if (series.endingBalance == null) stringResource(R.string.dashboard_value_unavailable) else if (date != null) stringResource(R.string.forecast_deficit,
            series.deficitAmount?.let { formatMoney(it.amount, it.currency) }.orEmpty(), formatDate(date, datePattern))
            else stringResource(R.string.forecast_no_deficit),
            style = MaterialTheme.typography.bodySmall,
            color = if (date != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        val minimumTag = if (dashed) "forecast-pace-minimum" else "forecast-minimum"
        val maximumTag = if (dashed) "forecast-pace-maximum" else "forecast-maximum"
        if (LocalDensity.current.fontScale > 1.3f) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ForecastStat(R.string.dashboard_forecast_minimum, series.minimum, datePattern, Modifier.testTag(minimumTag))
                ForecastStat(R.string.dashboard_forecast_maximum, series.maximum, datePattern, Modifier.testTag(maximumTag))
            }
        } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ForecastStat(R.string.dashboard_forecast_minimum, series.minimum, datePattern, Modifier.weight(1f).testTag(minimumTag))
            ForecastStat(R.string.dashboard_forecast_maximum, series.maximum, datePattern, Modifier.weight(1f).testTag(maximumTag))
        }
    }
}

@Composable
private fun ForecastChart(
    geometry: ForecastChartGeometry,
    paceGeometry: ForecastChartGeometry?,
    summary: String,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val paceColor = MaterialTheme.colorScheme.tertiary
    val zeroColor = MaterialTheme.colorScheme.outlineVariant
    val label = stringResource(R.string.dashboard_forecast_chart)
    Canvas(modifier.testTag("forecast-chart").semantics {
        contentDescription = label
        stateDescription = summary
    }) {
        val inset = minOf(10.dp.toPx(), size.width / 2f, size.height / 2f)
        fun ChartPoint.offset() = Offset(inset + x * (size.width - 2 * inset), inset + y * (size.height - 2 * inset))
        geometry.zeroY?.let { y -> drawLine(zeroColor, ChartPoint(0f, y).offset(), ChartPoint(1f, y).offset(), 1.dp.toPx()) }
        listOfNotNull(geometry, paceGeometry).forEachIndexed { index, line ->
            val color = if (index == 0) lineColor else paceColor
            if (line.segments.isNotEmpty()) {
                val path = Path().apply {
                    val start = line.segments.first().start.offset()
                    moveTo(start.x, start.y)
                    line.segments.forEach { segment ->
                        val first = segment.first.offset()
                        val second = segment.second.offset()
                        val end = segment.end.offset()
                        cubicTo(first.x, first.y, second.x, second.y, end.x, end.y)
                    }
                }
                drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round,
                    pathEffect = if (index == 1) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())) else null))
            }
            listOfNotNull(line.minimumIndex, line.maximumIndex).distinct().forEach { i ->
                drawCircle(color, 3.5.dp.toPx(), line.vertices[i].offset())
            }
        }
    }
}

private fun formatDate(date: LocalDate, pattern: String) =
    date.format(DateTimeFormatter.ofPattern(pattern, Locale.forLanguageTag("ru-KZ")))

internal val forecastPreviewState: ForecastUiState = run {
    val start = LocalDate.of(2026, 10, 25)
    ForecastUiState(start, start.plusDays(30), (0L..30L).map { day ->
        val amount = 120000L - day * 3500L + if (day >= 12) 80000L else 0L
        ForecastPoint(start.plusDays(day), Money(amount.toBigDecimal().setScale(2), "KZT"))
    }).let { it.copy(currentPacePoints = it.points.mapIndexed { index, point -> point.copy(balance = point.balance.copy(amount = point.balance.amount - (index * 1500).toBigDecimal())) }) }
}

@Preview(name = "Forecast", widthDp = 288, heightDp = 621)
@Preview(name = "Forecast dark", widthDp = 288, heightDp = 621, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Forecast wide", widthDp = 808, heightDp = 621)
@Preview(name = "Forecast large text", widthDp = 288, heightDp = 741, fontScale = 2f)
@Composable
private fun BalanceForecastPreview() {
    FillinTheme { BalanceForecastWidget(forecastPreviewState) }
}

@Preview(name = "Forecast deficit", widthDp = 288, heightDp = 621)
@Preview(name = "Forecast deficit dark", widthDp = 288, heightDp = 621, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BalanceForecastDeficitPreview() {
    FillinTheme {
        BalanceForecastWidget(forecastPreviewState.copy(points = forecastPreviewState.points.map {
            it.copy(balance = it.balance.copy(amount = it.balance.amount - "100000.00".toBigDecimal()))
        }))
    }
}

@Preview(name = "Forecast entirely negative", widthDp = 288, heightDp = 621)
@Preview(name = "Forecast entirely negative dark", widthDp = 288, heightDp = 621, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BalanceForecastNegativePreview() {
    FillinTheme {
        BalanceForecastWidget(forecastPreviewState.copy(points = forecastPreviewState.points.map {
            it.copy(balance = it.balance.copy(amount = it.balance.amount - "200000.00".toBigDecimal()))
        }))
    }
}

@Preview(name = "Forecast flat", widthDp = 288, heightDp = 621)
@Preview(name = "Forecast flat dark", widthDp = 288, heightDp = 621, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BalanceForecastFlatPreview() {
    FillinTheme {
        BalanceForecastWidget(forecastPreviewState.copy(points = forecastPreviewState.points.map {
            it.copy(balance = Money("10000.00".toBigDecimal(), "KZT"))
        }))
    }
}

@Preview(name = "Forecast insufficient history", widthDp = 288, heightDp = 621)
@Preview(name = "Forecast insufficient history dark", widthDp = 288, heightDp = 621, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BalanceForecastInsufficientHistoryPreview() {
    FillinTheme { BalanceForecastWidget(forecastPreviewState.copy(currentPacePoints = null, insufficientHistory = true)) }
}

@Preview(name = "Forecast loading", widthDp = 288, heightDp = 621)
@Preview(name = "Forecast loading dark", widthDp = 288, heightDp = 621, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BalanceForecastLoadingPreview() {
    FillinTheme { BalanceForecastWidget(ForecastUiState()) }
}

@Preview(name = "Forecast large amounts across years", widthDp = 288, heightDp = 621)
@Preview(name = "Forecast large amounts large text", widthDp = 288, heightDp = 741, fontScale = 2f)
@Composable
private fun BalanceForecastLargeAmountsPreview() {
    val start = LocalDate.of(2026, 12, 25)
    FillinTheme {
        BalanceForecastWidget(ForecastUiState(start, start.plusDays(30), listOf(
            ForecastPoint(start, Money("12345678.90".toBigDecimal(), "KZT")),
            ForecastPoint(start.plusDays(30), Money("-1234567.89".toBigDecimal(), "KZT")),
        )))
    }
}

@Preview(name = "Forecast chart", widthDp = 288, heightDp = 110)
@Preview(name = "Forecast chart dark", widthDp = 288, heightDp = 110, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ForecastChartPreview() {
    FillinTheme { ForecastChart(forecastPreviewState.limitGeometry, forecastPreviewState.paceGeometry, stringResource(R.string.dashboard_forecast_chart)) }
}

@Preview(name = "Forecast stat", widthDp = 138)
@Preview(name = "Forecast stat dark", widthDp = 138, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ForecastStatPreview() {
    FillinTheme { ForecastStat(R.string.dashboard_forecast_minimum, forecastPreviewState.minimum, "d MMM") }
}

@Preview(name = "Forecast warnings", widthDp = 320, heightDp = 621)
@Preview(name = "Forecast warnings dark", widthDp = 320, heightDp = 621, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ForecastWarningsPreview() {
    FillinTheme { BalanceForecastWidget(forecastPreviewState.copy(dependsOnPlannedIncome = true,
        warnings = setOf(com.firkat.fillin.feature.dailylimit.domain.ForecastWarning.OVERDUE_INCOME))) }
}
