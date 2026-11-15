package com.firkat.fillin.feature.dashboard.widgets.dailylimit.domain

import com.firkat.fillin.feature.dashboard.domain.DashboardDataSource
import com.firkat.fillin.feature.dashboard.domain.model.DashboardWidgetData
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class ObserveDailyLimit(private val source: DashboardDataSource) {
    operator fun invoke() = states().map { it.value }.distinctUntilChanged()

    internal fun states() = source.state
        .map { DashboardWidgetData(it.dashboard?.let(DailyLimitSummary::from), it.status) }
        .distinctUntilChanged()
}
