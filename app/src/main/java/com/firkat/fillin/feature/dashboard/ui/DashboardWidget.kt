package com.firkat.fillin.feature.dashboard.ui

enum class DashboardWidgetSize(val columns: Int, val rows: Int) {
    OneByOne(1, 1),
    TwoByOne(2, 1),
    TwoByTwo(2, 2),
    TwoByThree(2, 3)
}

enum class DashboardWidgetKind {
    TotalBalance,
    AvailableToSpend,
    DailyLimit,
    BalanceForecast,
    PlannedOperations
}

data class DashboardWidget(val id: String, val kind: DashboardWidgetKind, val size: DashboardWidgetSize)

val DefaultDashboardWidgets = listOf(
    DashboardWidget(
        id = "daily-limit",
        kind = DashboardWidgetKind.DailyLimit,
        size = DashboardWidgetSize.TwoByTwo
    ),
    DashboardWidget(
        id = "total-balance",
        kind = DashboardWidgetKind.TotalBalance,
        size = DashboardWidgetSize.TwoByOne
    ),
    DashboardWidget(
        id = "planned-operations",
        kind = DashboardWidgetKind.PlannedOperations,
        size = DashboardWidgetSize.TwoByThree
    ),
    DashboardWidget(
        id = "balance-forecast",
        kind = DashboardWidgetKind.BalanceForecast,
        size = DashboardWidgetSize.TwoByThree
    )
)
