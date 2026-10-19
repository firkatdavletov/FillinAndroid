package com.firkat.fillin.feature.dashboard.data.repository

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardDayChangesTest {
    @Test fun `signals midnight in Almaty without a database mutation`() = runTest {
        var time = Instant.parse("2026-10-31T18:59:59Z")
        val clock = object : Clock() {
            override fun getZone(): ZoneId = ZoneId.of("UTC")
            override fun withZone(zone: ZoneId): Clock = this
            override fun instant(): Instant = time
        }
        val signals = mutableListOf<Unit>()
        val job = launch { dashboardDayChanges(clock).take(2).toList(signals) }
        runCurrent()
        assertEquals(1, signals.size)
        time = time.plusSeconds(1)
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(2, signals.size)
        job.join()
    }
}
