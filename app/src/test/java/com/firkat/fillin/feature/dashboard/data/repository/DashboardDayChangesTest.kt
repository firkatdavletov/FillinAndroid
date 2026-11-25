package com.firkat.fillin.feature.dashboard.data.repository

import com.firkat.fillin.feature.dashboard.domain.calculation.CalculateDashboard
import com.firkat.fillin.feature.dashboard.domain.calculation.DashboardCalculationInput
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
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
    private val almatyZone = ZoneId.of("Asia/Almaty")

    @Test fun `signals midnight in Almaty without a database mutation`() = runTest {
        val midnight = LocalDate.of(2026, 11, 1).atStartOfDay(almatyZone).toInstant()
        var time = midnight.minusSeconds(1)
        val clock = object : Clock() {
            override fun getZone(): ZoneId = ZoneId.of("UTC")
            override fun withZone(
                zone: ZoneId
            ): Clock = this
            override fun instant(): Instant = time
        }
        val signals = mutableListOf<Unit>()
        val job = launch { dashboardDayChanges(clock).take(2).toList(signals) }
        runCurrent()
        assertEquals(1, signals.size)
        time = midnight
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(2, signals.size)
        job.join()
    }

    @Test fun `midnight moves both endpoints of the rolling forecast without a database mutation`() = runTest {
        val midnight = LocalDate.of(2027, 1, 1).atStartOfDay(almatyZone).toInstant()
        var time = midnight.minusSeconds(1)
        val clock = object : Clock() {
            override fun getZone(): ZoneId = ZoneId.of("UTC")
            override fun withZone(
                zone: ZoneId
            ): Clock = this
            override fun instant(): Instant = time
        }
        val input = DashboardCalculationInput(
            time, Instant.parse("2026-11-01T00:00:00Z"), BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO,
            emptyList(), emptyList(), emptyList(), emptyList(), emptySet()
        )
        val forecasts = mutableListOf<com.firkat.fillin.feature.dashboard.domain.model.BalanceForecastProjection>()
        val job = launch {
            dashboardDayChanges(clock).take(2).collect {
                forecasts += CalculateDashboard.calculate(input.copy(asOf = clock.instant())).balanceForecast
            }
        }
        runCurrent()
        time = midnight
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(2, forecasts.size)
        assertEquals(forecasts[0].startDate.plusDays(1), forecasts[1].startDate)
        assertEquals(forecasts[0].endDate.plusDays(1), forecasts[1].endDate)
        assertEquals(forecasts[0].points.first().balance, forecasts[1].points.first().balance)
        job.join()
    }
}
