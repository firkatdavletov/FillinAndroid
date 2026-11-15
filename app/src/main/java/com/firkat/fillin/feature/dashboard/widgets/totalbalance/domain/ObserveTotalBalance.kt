package com.firkat.fillin.feature.dashboard.widgets.totalbalance.domain

import com.firkat.fillin.feature.dashboard.domain.DashboardDataSource
import com.firkat.fillin.feature.dashboard.domain.model.DashboardWidgetData
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class ObserveTotalBalance(private val source: DashboardDataSource) {
    operator fun invoke() = states().map { it.value }.distinctUntilChanged()

    internal fun states() = source.state
        .map { DashboardWidgetData(it.dashboard?.availableMoney, it.status) }
        .distinctUntilChanged()
}
