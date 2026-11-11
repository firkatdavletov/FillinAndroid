package com.firkat.fillin.feature.dashboard.widgets.balanceforecast.ui

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.dashboard.domain.model.*
import com.firkat.fillin.feature.dashboard.widgets.common.ui.WidgetCard
import com.firkat.fillin.feature.dashboard.widgets.common.ui.WidgetHeading
import com.firkat.fillin.ui.component.SaldomiOutlinedButton
import com.firkat.fillin.ui.format.formatMoney
import com.firkat.fillin.ui.theme.FillinTheme
import com.firkat.fillin.ui.theme.saldomiColors
import org.koin.compose.viewmodel.koinViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun BalanceForecastRoute(
    modifier: Modifier = Modifier,
    onDetailsClick: () -> Unit = {},
    viewModel: BalanceForecastViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BalanceForecastWidget(state, modifier, onDetailsClick)
}

@Composable
internal fun BalanceForecastWidget(
    state: ForecastUiState,
    modifier: Modifier = Modifier,
    onDetailsClick: () -> Unit = {},
) {
    WidgetCard(
        modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ForecastHeading(state)
            if (state.points.isEmpty()) {
                ForecastUnavailable(state.dataStatus)
            } else {
                ForecastStatusMessage(state, showHistoryHints = false)
                ForecastLegend(state.currentPace != null)
                ForecastChart(state, Modifier.fillMaxWidth(), interactive = false)
                HorizontalDivider()
                Text(
                    stringResource(R.string.forecast_ending_on, forecastDate(requireNotNull(state.endDate), "d MMMM")),
                    style = MaterialTheme.typography.bodyMedium,
                )
                ForecastEndings(state)
                SaldomiOutlinedButton (
                    onClick = onDetailsClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.forecast_details),
                    )
                }
            }
        }
    }
}

@Composable
internal fun ForecastHeading(
    state: ForecastUiState,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        WidgetHeading(
            title = R.string.forecast_title,
            icon = R.drawable.ic_forecast
        )

        val start = state.startDate
        val end = state.endDate

        if (start != null && end != null) {
            val pattern = if (start.year == end.year) "d MMM" else "d MMM yyyy"
            val count = (ChronoUnit.DAYS.between(start, end) + 1).toInt()
            Text(
                stringResource(
                    R.string.forecast_period_days,
                    forecastDate(start, pattern),
                    forecastDate(end, pattern),
                    stringResource(
                        when {
                            count %
                                100 in
                                11..14 -> R.string.forecast_days_many

                            count % 10 == 1 -> R.string.forecast_days_one

                            count % 10 in 2..4 -> R.string.forecast_days_few

                            else -> R.string.forecast_days_many
                        },
                        count,
                    ),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
internal fun ForecastUnavailable(
    status: DashboardStatus,
    modifier: Modifier = Modifier,
) {
    Text(
        stringResource(
            when (status) {
                DashboardStatus.Forbidden -> R.string.dashboard_error_forbidden
                DashboardStatus.LocalError -> R.string.dashboard_error_local_data
                else -> R.string.forecast_loading
            },
        ),
        modifier,
    )
}

@Composable
internal fun ForecastStatusMessage(
    state: ForecastUiState,
    modifier: Modifier = Modifier,
    showHistoryHints: Boolean = true,
) {
    val status = state.financialStatus
    if (status == BalanceForecastStatus.INSUFFICIENT_DATA && !showHistoryHints) return

    val color =
        when (status) {
            BalanceForecastStatus.STRUCTURAL_DEFICIT -> MaterialTheme.saldomiColors.expense
            BalanceForecastStatus.CURRENT_PACE_RISK -> MaterialTheme.saldomiColors.forecastWarning
            BalanceForecastStatus.NORMAL -> MaterialTheme.saldomiColors.income
            else -> MaterialTheme.colorScheme.tertiary
        }
    val date = state.projection?.structuralDeficitDate ?: state.currentPace?.firstDeficitDate ?: state.byLimit.firstDeficitDate
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(
                when (status) {
                    BalanceForecastStatus.NORMAL -> R.string.forecast_normal
                    BalanceForecastStatus.CURRENT_PACE_RISK -> R.string.forecast_pace_risk
                    BalanceForecastStatus.STRUCTURAL_DEFICIT -> R.string.forecast_structural
                    BalanceForecastStatus.INSUFFICIENT_DATA -> R.string.forecast_insufficient
                },
                date?.let { forecastDate(it, "d MMMM") }.orEmpty(),
            ),
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
        when (status) {
            BalanceForecastStatus.CURRENT_PACE_RISK -> {
                Text(
                    stringResource(R.string.forecast_limit_helps),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            BalanceForecastStatus.STRUCTURAL_DEFICIT -> {
                Text(
                    stringResource(
                        R.string.forecast_structural_date,
                        date
                            ?.let {
                                forecastDate(it, "d MMMM")
                            }.orEmpty(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            BalanceForecastStatus.INSUFFICIENT_DATA -> {
                Text(
                    stringResource(R.string.forecast_limit_only),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            else -> {
                Unit
            }
        }
        if (showHistoryHints &&
            status == BalanceForecastStatus.STRUCTURAL_DEFICIT &&
            state.currentPace == null
        ) {
            Text(stringResource(R.string.forecast_insufficient), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun ForecastLegend(
    hasPace: Boolean,
    modifier: Modifier = Modifier,
) {
    ForecastAdaptiveRow(modifier) {
        if (hasPace) ForecastLegendItem(true)
        ForecastLegendItem(false)
    }
}

@Composable
private fun ForecastLegendItem(
    pace: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = if (pace) MaterialTheme.saldomiColors.forecastPace else MaterialTheme.saldomiColors.income
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.width(24.dp).height(8.dp)) {
            drawLine(
                color,
                Offset(0f, size.height / 2),
                Offset(size.width, size.height / 2),
                3.dp.toPx(),
                pathEffect = if (pace) PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx())) else null,
            )
        }
        Text(
            stringResource(if (pace) R.string.forecast_current_pace else R.string.forecast_by_limit),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
internal fun ForecastAdaptiveRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth < 330.dp || fontScale > 1.3f) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
        } else {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 2,
            ) {
                content()
            }
        }
    }
}

@Composable
internal fun ForecastEndings(
    state: ForecastUiState,
    modifier: Modifier = Modifier,
) {
    ForecastAdaptiveRow(modifier) {
        state.currentPace?.let { ForecastAmount(R.string.forecast_current_pace, it.endingBalance) }
        ForecastAmount(R.string.forecast_by_limit, state.byLimit.endingBalance)
    }
}

@Composable
internal fun ForecastAmount(
    label: Int,
    money: Money?,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(stringResource(label), style = MaterialTheme.typography.bodySmall)
        BasicText(
            money?.let { signedForecastMoney(it) } ?: stringResource(R.string.dashboard_value_unavailable),
            style =
                MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color =
                        if (money != null &&
                            money.amount.signum() < 0
                        ) {
                            MaterialTheme.saldomiColors.expense
                        } else {
                            MaterialTheme.saldomiColors.income
                        },
                ),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 24.sp),
        )
    }
}

@Composable
internal fun ForecastChart(
    state: ForecastUiState,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
) {
    val start = state.startDate ?: return
    val end = state.endDate ?: return
    val all = state.points + state.currentPacePoints.orEmpty()
    val geometry = remember(all, start, end) { ForecastChartGeometry(all, start, end) }
    val days = geometry.days
    var selected by remember(interactive, start, end, state.points, state.currentPacePoints) { mutableStateOf<Int?>(null) }
    val paceColor = MaterialTheme.saldomiColors.forecastPace
    val limitColor = MaterialTheme.saldomiColors.income
    val negative = MaterialTheme.saldomiColors.expense
    val outline = MaterialTheme.colorScheme.outline
    val previous = stringResource(R.string.forecast_previous_day)
    val next = stringResource(R.string.forecast_next_day)
    val chartLabel = stringResource(R.string.dashboard_forecast_chart)
    val currency = all.firstOrNull()?.balance?.currency ?: state.reserve.currency
    val symbol =
        com.firkat.fillin.feature.budgetprofile.domain.BudgetCurrency
            .valueOf(currency)
            .symbol
    val zero = stringResource(R.string.forecast_zero, symbol)
    val textMeasurer = rememberTextMeasurer()
    val zeroLayout = textMeasurer.measure(zero, MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurface))
    val paceLabel = stringResource(R.string.forecast_current_pace)
    val limitLabel = stringResource(R.string.forecast_by_limit)
    val summary =
        listOfNotNull(
            state.currentPace?.endingBalance?.let { paceLabel + ": " + signedForecastMoney(it) },
            state.byLimit.endingBalance?.let {
                limitLabel +
                    ": " +
                    signedForecastMoney(it)
            },
        ).joinToString("; ")
    val selectionInput = if (interactive) {
        Modifier.pointerInput(geometry) {
            fun select(x: Float) {
                selected = geometry.nearestDay((x - 32.dp.toPx()) / (size.width - 40.dp.toPx()).coerceAtLeast(1f))
            }
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                select(down.position.x)
                try {
                    val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                    if (drag != null) {
                        select(drag.position.x)
                        horizontalDrag(drag.id) { change ->
                            select(change.position.x)
                            change.consume()
                        }
                    }
                } finally {
                    selected = null
                }
            }
        }
    } else {
        Modifier
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(170.dp)
                .testTag("forecast-chart")
                .semantics {
                    contentDescription = chartLabel
                    stateDescription = selected?.let { offset ->
                        val date = start.plusDays(offset.toLong())
                        forecastDate(date) + "; " +
                            listOfNotNull(
                                state.currentPacePoints?.find { it.date == date }?.let {
                                    paceLabel + ": " +
                                        signedForecastMoney(
                                            it.balance,
                                        )
                                },
                                state.points.find { it.date == date }?.let { limitLabel + ": " + signedForecastMoney(it.balance) },
                            ).joinToString("; ")
                    } ?: (zero + "; " + summary)
                    if (interactive) {
                        customActions = listOf(
                            CustomAccessibilityAction(previous) {
                                selected = ((selected ?: days) - 1).coerceAtLeast(0)
                                true
                            },
                            CustomAccessibilityAction(next) {
                                selected = ((selected ?: -1) + 1).coerceAtMost(days)
                                true
                            },
                        )
                    }
                }.then(selectionInput),
        ) {
            val left = 32.dp.toPx()
            val width = (size.width - left - 8.dp.toPx()).coerceAtLeast(1f)

            fun x(
                date: LocalDate,
            ) = left + geometry.x(date) * width
            val zeroY = geometry.zeroY * size.height
            drawText(zeroLayout, topLeft = Offset(0f, (zeroY - zeroLayout.size.height).coerceAtLeast(0f)))
            drawLine(
                outline,
                Offset(left, zeroY),
                Offset(left + width, zeroY),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 5.dp.toPx())),
            )
            listOfNotNull(state.currentPacePoints?.let { it to paceColor }, state.points to limitColor).forEachIndexed {
                index,
                (points, color),
                ->
                fun drawSeries(
                    tint: androidx.compose.ui.graphics.Color,
                ) {
                    if (points.size > 1) {
                        val path =
                            Path().apply {
                                moveTo(x(points.first().date), geometry.y(points.first().balance.amount) * size.height)
                                points.drop(1).forEach { lineTo(x(it.date), geometry.y(it.balance.amount) * size.height) }
                            }
                        drawPath(
                            path,
                            tint,
                            style =
                                Stroke(
                                    width = 2.5.dp.toPx(),
                                    pathEffect =
                                        if (state.currentPacePoints != null &&
                                            index == 0
                                        ) {
                                            PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 3.dp.toPx()))
                                        } else {
                                            null
                                        },
                                ),
                        )
                    }
                    if (points.size ==
                        1
                    ) {
                        drawCircle(tint, 3.dp.toPx(), Offset(x(points[0].date), geometry.y(points[0].balance.amount) * size.height))
                    }
                }
                clipRect(bottom = zeroY) { drawSeries(color) }
                clipRect(top = zeroY) { drawSeries(negative) }
            }
            selected?.let { day ->
                val cursorX = x(start.plusDays(day.toLong()))
                drawLine(outline, Offset(cursorX, 0f), Offset(cursorX, size.height), 1.dp.toPx())
                listOfNotNull(
                    state.points.find { it.date == start.plusDays(day.toLong()) },
                    state.currentPacePoints?.find {
                        it.date ==
                            start.plusDays(day.toLong())
                    },
                ).forEach {
                    drawCircle(
                        outline,
                        4.dp.toPx(),
                        Offset(
                            cursorX,
                            geometry.y(it.balance.amount) * size.height,
                        ),
                    )
                }
            }
        }

        val axisDays = if (LocalDensity.current.fontScale > 1.3f) {
            listOf(0, days)
        } else {
            listOf(0, days / 3, days * 2 / 3, days)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            axisDays.distinct().forEach { day ->
                Text(forecastDate(start.plusDays(day.toLong())), style = MaterialTheme.typography.labelSmall)
            }
        }
        selected?.let { day ->
            val date = start.plusDays(day.toLong())
            Column(Modifier.testTag("forecast-selection")) {
                Text(forecastDate(date, "d MMMM yyyy"), fontWeight = FontWeight.Bold)
                state.currentPacePoints?.find { it.date == date }?.let { Text(paceLabel + ": " + signedForecastMoney(it.balance)) }
                state.points.find { it.date == date }?.let { Text(limitLabel + ": " + signedForecastMoney(it.balance)) }
                state.projection?.events?.filter { (if (it.income) it.availableOn else maxOf(start, it.dueOn)) == date }?.forEach {
                    Text(it.name + ": " + formatMoney(it.amount.amount, it.amount.currency))
                }
            }
        }
    }
}

internal fun forecastDate(
    date: LocalDate,
    pattern: String = "d MMM",
) = date.format(DateTimeFormatter.ofPattern(pattern, Locale.forLanguageTag("ru-KZ")))

internal fun signedForecastMoney(
    money: Money,
) = (if (money.amount.signum() > 0) "+" else "") + formatMoney(money.amount, money.currency)

internal val forecastPreviewState: ForecastUiState =
    run {
        val start = LocalDate.of(2026, 11, 3)

        fun points(
            pace: Boolean,
        ) = (0L..42L).map { day ->
            ForecastPoint(
                start.plusDays(day),
                Money(
                    (
                        100000L - day * (if (pace) 5000 else 2000) +
                            (if (day >= 14) 50000 else 0)
                    ).toBigDecimal(),
                    "KZT",
                ),
            )
        }
        ForecastUiState.from(
            BalanceForecastProjection(
                start,
                start.plusDays(42),
                points(false),
                false,
                points(true),
                currentBalance = Money(100000.toBigDecimal(), "KZT"),
                averageDailyExpenses = Money(5000.toBigDecimal(), "KZT"),
                dailyLimit = Money(2000.toBigDecimal(), "KZT"),
                remainingToday = Money(2000.toBigDecimal(), "KZT"),
                nextDaysLimit = Money(2000.toBigDecimal(), "KZT"),
                dependsOnPlannedIncome = true,
                events =
                    listOf(
                        com.firkat.fillin.feature.dailylimit.domain.ForecastEvent(
                            "salary",
                            "Зарплата",
                            start.plusDays(13),
                            Money(50000.toBigDecimal(), "KZT"),
                            true,
                        ),
                    ),
            ),
        )
    }

@Preview(name = "Current pace risk", widthDp = 390)
@Preview(name = "Current pace risk dark", widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Compact", widthDp = 320)
@Preview(name = "Expanded", widthDp = 840)
@Preview(name = "Large text", widthDp = 320, fontScale = 2f)
@Composable
private fun ForecastPreview() {
    FillinTheme { BalanceForecastWidget(forecastPreviewState) }
}

@Preview(name = "Normal", widthDp = 390)
@Preview(name = "Normal dark", widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NormalPreview() {
    FillinTheme { BalanceForecastWidget(forecastPreviewState.copy(currentPacePoints = forecastPreviewState.points, projection = null)) }
}

@Preview(name = "Insufficient", widthDp = 390)
@Preview(name = "Insufficient dark", widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun InsufficientPreview() {
    FillinTheme {
        BalanceForecastWidget(
            forecastPreviewState.copy(currentPacePoints = null, insufficientHistory = true, projection = null),
        )
    }
}

@Preview(name = "Structural deficit", widthDp = 390)
@Preview(name = "Structural deficit dark", widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StructuralPreview() {
    FillinTheme {
        BalanceForecastWidget(
            ForecastUiState.from(
                requireNotNull(forecastPreviewState.projection).copy(structuralDeficitDate = forecastPreviewState.startDate),
            ),
        )
    }
}

@Preview(name = "Loading", widthDp = 390)
@Preview(name = "Loading dark", widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LoadingPreview() {
    FillinTheme { BalanceForecastWidget(ForecastUiState()) }
}

@Preview(name = "Heading", widthDp = 390)
@Preview(name = "Heading dark", widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HeadingComponentPreview() {
    FillinTheme { ForecastHeading(forecastPreviewState) }
}

@Preview(name = "StatusRisk", widthDp = 390)
@Preview(name = "StatusRisk dark", widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StatusRiskComponentPreview() {
    FillinTheme { ForecastStatusMessage(forecastPreviewState) }
}

@Preview(name = "StatusNormal", widthDp = 390)
@Preview(name = "StatusNormal dark", widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StatusNormalComponentPreview() {
    FillinTheme { ForecastStatusMessage(forecastPreviewState.copy(currentPacePoints = forecastPreviewState.points, projection = null)) }
}

@Preview(name = "StatusInsufficient", widthDp = 390)
@Preview(name = "StatusInsufficient dark", widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StatusInsufficientComponentPreview() {
    FillinTheme { ForecastStatusMessage(forecastPreviewState.copy(currentPacePoints = null, projection = null)) }
}

@Preview(name = "StatusStructural", widthDp = 390)
@Preview(name = "StatusStructural dark", widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StatusStructuralComponentPreview() {
    FillinTheme {
        ForecastStatusMessage(
            ForecastUiState.from(
                requireNotNull(forecastPreviewState.projection).copy(structuralDeficitDate = forecastPreviewState.startDate),
            ),
        )
    }
}

@Preview(name = "LegendBoth", widthDp = 390)
@Preview(name = "LegendBoth dark", widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LegendBothComponentPreview() {
    FillinTheme { ForecastLegend(true) }
}

@Preview(name = "LegendLimit", widthDp = 390)
@Preview(name = "LegendLimit dark", widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LegendLimitComponentPreview() {
    FillinTheme { ForecastLegend(false) }
}

@Preview(name = "LegendItemPace", widthDp = 390)
@Preview(name = "LegendItemPace dark", widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LegendItemPaceComponentPreview() {
    FillinTheme { ForecastLegendItem(true) }
}

@Preview(name = "LegendItemLimit", widthDp = 390)
@Preview(name = "LegendItemLimit dark", widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LegendItemLimitComponentPreview() {
    FillinTheme { ForecastLegendItem(false) }
}

@Preview(name = "Endings", widthDp = 390)
@Preview(name = "Endings dark", widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EndingsComponentPreview() {
    FillinTheme { ForecastEndings(forecastPreviewState) }
}

@Preview(name = "PositiveAmount", widthDp = 390)
@Preview(name = "PositiveAmount dark", widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PositiveAmountComponentPreview() {
    FillinTheme { ForecastAmount(R.string.forecast_by_limit, Money(45000.toBigDecimal(), "KZT")) }
}

@Preview(name = "NegativeAmount", widthDp = 390)
@Preview(name = "NegativeAmount dark", widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NegativeAmountComponentPreview() {
    FillinTheme { ForecastAmount(R.string.forecast_current_pace, Money((-90000).toBigDecimal(), "KZT")) }
}

@Preview(name = "MissingAmount", widthDp = 390)
@Preview(name = "MissingAmount dark", widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MissingAmountComponentPreview() {
    FillinTheme { ForecastAmount(R.string.forecast_current_pace, null) }
}

@Preview(name = "ChartBoth", widthDp = 390)
@Preview(name = "ChartBoth dark", widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChartBothComponentPreview() {
    FillinTheme { ForecastChart(forecastPreviewState) }
}

@Preview(name = "ChartLimit", widthDp = 390)
@Preview(name = "ChartLimit dark", widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChartLimitComponentPreview() {
    FillinTheme { ForecastChart(forecastPreviewState.copy(currentPacePoints = null, projection = null)) }
}

@Preview(name = "Forbidden", widthDp = 390)
@Preview(name = "Forbidden dark", widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ForbiddenComponentPreview() {
    FillinTheme { ForecastUnavailable(DashboardStatus.Forbidden) }
}

@Preview(name = "LocalError", widthDp = 390)
@Preview(name = "LocalError dark", widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LocalErrorComponentPreview() {
    FillinTheme { ForecastUnavailable(DashboardStatus.LocalError) }
}

@Preview(name = "UnavailableLoading", widthDp = 390)
@Preview(name = "UnavailableLoading dark", widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun UnavailableLoadingComponentPreview() {
    FillinTheme { ForecastUnavailable(DashboardStatus.Loading) }
}

@Preview(name = "AdaptiveRow", widthDp = 390)
@Preview(name = "AdaptiveRow dark", widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AdaptiveRowComponentPreview() {
    FillinTheme {
        ForecastAdaptiveRow {
            ForecastLegendItem(true)
            ForecastLegendItem(false)
        }
    }
}
