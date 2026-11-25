package com.firkat.fillin

import ru.ok.tracer.HasTracerConfiguration
import ru.ok.tracer.TracerConfiguration
import ru.ok.tracer.crash.report.CrashFreeConfiguration
import ru.ok.tracer.crash.report.CrashReportConfiguration

class StoreApplication :
    FillinApplication(),
    HasTracerConfiguration {
    override fun onCreate() {
        super.onCreate()
    }

    override val tracerConfiguration: List<TracerConfiguration>
        get() = listOf(
            CrashReportConfiguration.build {
                setExperimentalNonFatalRateLimitEnabled(true)
            },
            CrashFreeConfiguration.build {
            }
        )
}
