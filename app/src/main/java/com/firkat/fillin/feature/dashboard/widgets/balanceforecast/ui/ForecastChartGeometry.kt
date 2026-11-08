package com.firkat.fillin.feature.dashboard.widgets.balanceforecast.ui

import com.firkat.fillin.feature.dashboard.domain.model.ForecastPoint
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** Shared calendar and scale, including zero even for entirely positive or negative series. */
internal class ForecastChartGeometry(
    points: List<ForecastPoint>,
    val start: LocalDate,
    val end: LocalDate,
) {
    val days = ChronoUnit.DAYS.between(start, end).toInt().coerceAtLeast(1)
    private val low = minOf(BigDecimal.ZERO, points.minOfOrNull { it.balance.amount } ?: BigDecimal.ZERO)
    private val high = maxOf(BigDecimal.ZERO, points.maxOfOrNull { it.balance.amount } ?: BigDecimal.ZERO)
    private val padding = (high - low).max(BigDecimal.ONE) * BigDecimal("0.12")
    private val maximum = high + padding
    private val range = high - low + padding * BigDecimal(2)
    fun y(value: BigDecimal): Float = (maximum - value).divide(range, 16, RoundingMode.HALF_UP).toFloat()
    fun x(date: LocalDate): Float = ChronoUnit.DAYS.between(start, date).toFloat() / days
    fun nearestDay(fraction: Float): Int = (fraction.coerceIn(0f, 1f) * days).roundToInt()
    val zeroY: Float = y(BigDecimal.ZERO)
}
