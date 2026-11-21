package com.firkat.fillin.analytics

import com.firkat.fillin.analytics.model.AnalyticsPayload

sealed interface AnalyticsEvent {

    data object BudgetCreated : AnalyticsEvent

    data class DailyLimitViewed(val source: String) : AnalyticsEvent

    data class TransactionCreated(val type: String) : AnalyticsEvent
}

fun AnalyticsEvent.toPayload(): AnalyticsPayload = when (this) {
    AnalyticsEvent.BudgetCreated ->
        AnalyticsPayload("budget_created")

    is AnalyticsEvent.DailyLimitViewed ->
        AnalyticsPayload(
            name = "daily_limit_viewed",
            params = mapOf("source" to source)
        )

    is AnalyticsEvent.TransactionCreated ->
        AnalyticsPayload(
            name = "transaction_created",
            params = mapOf("type" to type)
        )
}
