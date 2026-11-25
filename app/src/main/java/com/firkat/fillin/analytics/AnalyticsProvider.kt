package com.firkat.fillin.analytics

import com.firkat.fillin.analytics.model.AnalyticsPayload

interface AnalyticsProvider {
    fun send(
        payload: AnalyticsPayload
    )
}
