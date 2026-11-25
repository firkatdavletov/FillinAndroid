package com.firkat.fillin.analytics

import android.util.Log

class CompositeAnalyticsTracker(private val providers: List<AnalyticsProvider>) : AnalyticsTracker {

    override fun track(
        event: AnalyticsEvent
    ) {
        val payload = event.toPayload()

        providers.forEach { provider ->
            runCatching {
                provider.send(payload)
            }.onFailure { error ->
                Log.e(
                    "Analytics",
                    "Failed: ${provider.javaClass.simpleName}",
                    error
                )
            }
        }
    }
}
