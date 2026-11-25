package com.firkat.fillin.analytics

interface AnalyticsTracker {
    fun track(
        event: AnalyticsEvent
    )
}
