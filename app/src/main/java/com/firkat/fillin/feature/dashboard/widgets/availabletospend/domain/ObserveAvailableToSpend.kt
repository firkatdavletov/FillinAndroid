package com.firkat.fillin.feature.dashboard.widgets.availabletospend.domain

import com.firkat.fillin.feature.dashboard.domain.DashboardDataSource
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class ObserveAvailableToSpend(private val source: DashboardDataSource) {
    operator fun invoke() = source.state.map { it.dashboard?.freeMoney?.amount }.distinctUntilChanged()
}
