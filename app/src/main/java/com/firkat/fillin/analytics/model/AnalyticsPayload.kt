package com.firkat.fillin.analytics.model

data class AnalyticsPayload(val name: String, val params: Map<String, Any> = emptyMap())
