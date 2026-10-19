package com.firkat.fillin.feature.dashboard.widgets.balanceforecast.domain

import com.firkat.fillin.feature.dashboard.domain.DashboardDataSource
import com.firkat.fillin.feature.dashboard.domain.model.Money
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

data class BalanceForecast(val amount: Money?, val insufficientHistory: Boolean)

class ObserveBalanceForecast(private val source: DashboardDataSource) {
    operator fun invoke() = source.state.map { state ->
        val forecast = state.dashboard?.forecast
        val insufficient = forecast?.estimateSource == "UNAVAILABLE"
        BalanceForecast(forecast?.base?.endingBalance.takeUnless { insufficient }, insufficient)
    }.distinctUntilChanged()
}
