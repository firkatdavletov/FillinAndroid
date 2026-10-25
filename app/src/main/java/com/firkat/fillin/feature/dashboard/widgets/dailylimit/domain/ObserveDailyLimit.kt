package com.firkat.fillin.feature.dashboard.widgets.dailylimit.domain

import com.firkat.fillin.feature.dashboard.domain.DashboardDataSource
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class ObserveDailyLimit(private val source: DashboardDataSource) {
    operator fun invoke() = source.state.map { it.dashboard?.let(DailyLimitSummary::from) }.distinctUntilChanged()
}
