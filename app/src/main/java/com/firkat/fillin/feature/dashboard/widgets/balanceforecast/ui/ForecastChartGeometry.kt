package com.firkat.fillin.feature.dashboard.widgets.balanceforecast.ui

import com.firkat.fillin.feature.dashboard.domain.model.ForecastPoint
import java.math.BigDecimal
import java.math.RoundingMode

internal data class ChartPoint(val x: Float, val y: Float)
internal data class ChartSegment(val start: ChartPoint, val first: ChartPoint, val second: ChartPoint, val end: ChartPoint)

/** Normalized coordinates; interpolation stays within each pair of daily balances. */
internal class ForecastChartGeometry(
    points: List<ForecastPoint>,
    private val minimum: BigDecimal = points.minOfOrNull { it.balance.amount } ?: BigDecimal.ZERO,
    private val maximum: BigDecimal = points.maxOfOrNull { it.balance.amount } ?: BigDecimal.ZERO,
) {
    private val amounts = points.map { it.balance.amount }
    private val range = maximum - minimum

    private fun y(amount: BigDecimal): Float = if (range.signum() == 0) 0.5f else
        (maximum - amount).divide(range, 12, RoundingMode.HALF_UP).toFloat()

    val vertices = amounts.mapIndexed { index, amount ->
        ChartPoint(if (amounts.size == 1) 0.5f else index.toFloat() / (amounts.size - 1), y(amount))
    }
    val minimumIndex = amounts.indices.minByOrNull { amounts[it] }
    val maximumIndex = amounts.indices.maxByOrNull { amounts[it] }
    val zeroY: Float? = if (minimum.signum() < 0 && maximum.signum() > 0) y(BigDecimal.ZERO) else null

    val segments: List<ChartSegment> = if (vertices.size < 2) emptyList() else {
        val slopes = vertices.zipWithNext { a, b -> (b.y - a.y) / (b.x - a.x) }
        val tangents = vertices.indices.map { index ->
            when (index) {
                0 -> slopes.first()
                vertices.lastIndex -> slopes.last()
                else -> {
                    val before = slopes[index - 1]
                    val after = slopes[index]
                    // Harmonic means preserve monotonicity; turns and plateaus have horizontal tangents.
                    if (before * after <= 0f) 0f else 2f * before * after / (before + after)
                }
            }
        }
        vertices.zipWithNext().mapIndexed { index, (a, b) ->
            val third = (b.x - a.x) / 3f
            val low = minOf(a.y, b.y)
            val high = maxOf(a.y, b.y)
            ChartSegment(
                a,
                ChartPoint(a.x + third, (a.y + tangents[index] * third).coerceIn(low, high)),
                ChartPoint(b.x - third, (b.y - tangents[index + 1] * third).coerceIn(low, high)),
                b,
            )
        }
    }
}
