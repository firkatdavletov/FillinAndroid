package com.firkat.fillin.feature.dashboard.widgets.balanceforecast.domain

import com.firkat.fillin.feature.dashboard.domain.DashboardDataSource
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class ObserveBalanceForecast(private val source: DashboardDataSource) {
    operator fun invoke() = source.state.map { it.dashboard?.balanceForecast }.distinctUntilChanged()
}
