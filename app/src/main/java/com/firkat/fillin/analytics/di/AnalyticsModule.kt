package com.firkat.fillin.analytics.di

import com.firkat.fillin.analytics.AnalyticsProvider
import com.firkat.fillin.analytics.AnalyticsTracker
import com.firkat.fillin.analytics.CompositeAnalyticsTracker
import org.koin.dsl.module

val analyticsModule = module {
    single<AnalyticsTracker> {
        CompositeAnalyticsTracker(
            providers = getAll<AnalyticsProvider>()
        )
    }
}
