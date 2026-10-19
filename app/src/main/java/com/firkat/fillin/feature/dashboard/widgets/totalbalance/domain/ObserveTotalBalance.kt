package com.firkat.fillin.feature.dashboard.widgets.totalbalance.domain

import com.firkat.fillin.feature.dashboard.domain.DashboardDataSource
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class ObserveTotalBalance(private val source: DashboardDataSource) {
    operator fun invoke() = source.state.map { it.dashboard?.availableMoney }.distinctUntilChanged()
}
