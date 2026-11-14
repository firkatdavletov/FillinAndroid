package com.firkat.fillin.feature.dashboard.ui

enum class DashboardWidgetSize(val columns: Int, val rows: Int) {
    OneByOne(1, 1),
    TwoByOne(2, 1),
    TwoByTwo(2, 2),
    TwoByThree(2, 3)
}

enum class DashboardWidgetKind { TotalBalance, AvailableToSpend, DailyLimit, BalanceForecast }

data class DashboardWidget(val id: String, val kind: DashboardWidgetKind, val size: DashboardWidgetSize)

val DefaultDashboardWidgets = listOf(
    DashboardWidget("daily-limit", DashboardWidgetKind.DailyLimit, DashboardWidgetSize.TwoByTwo),
    DashboardWidget("total-balance", DashboardWidgetKind.TotalBalance, DashboardWidgetSize.TwoByOne),
    DashboardWidget("balance-forecast", DashboardWidgetKind.BalanceForecast, DashboardWidgetSize.TwoByThree)
)
