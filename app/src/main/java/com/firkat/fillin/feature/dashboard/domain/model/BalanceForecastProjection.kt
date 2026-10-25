package com.firkat.fillin.feature.dashboard.domain.model

import java.time.LocalDate

/** End-of-day balances from today through thirty days ahead, inclusive. */
data class BalanceForecastProjection(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val points: List<ForecastPoint>,
    val insufficientHistory: Boolean,
) {
    // min/maxByOrNull retain the first occurrence when several days have the same balance.
    val minimum: ForecastPoint? get() = points.minByOrNull { it.balance.amount }
    val maximum: ForecastPoint? get() = points.maxByOrNull { it.balance.amount }
}

data class ForecastPoint(val date: LocalDate, val balance: Money)
