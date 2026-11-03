package com.firkat.fillin.feature.dashboard.widgets.balanceforecast

import com.firkat.fillin.feature.dashboard.widgets.balanceforecast.ui.ForecastUiState
import com.firkat.fillin.feature.dailylimit.domain.*
import com.firkat.fillin.feature.dashboard.domain.model.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ForecastUiStateTest {
    private val today = LocalDate.of(2026, 11, 3)
    private fun money(value: String) = Money(value.toBigDecimal().setScale(2), "KZT")

    @Test fun `coincident scenarios map to identical coordinates and exact totals`() {
        val forecast = CashFlowForecaster().forecast(DailyLimitInput(money("6000"), money("0"), emptyList(), today), money("100"))
        val state = ForecastUiState.from(forecast.projection)
        assertEquals(state.points, state.currentPacePoints)
        assertEquals(state.limitGeometry.vertices, state.paceGeometry!!.vertices)
        assertEquals(money("0"), state.byLimit.endingBalance)
        assertEquals(ForecastRisk.NoDeficit, state.byLimit.risk)
        assertTrue(state.warnings.contains(ForecastWarning.NO_INCOME))
        assertFalse(state.dependsOnPlannedIncome)
    }

    @Test fun `different scenarios share scale and warnings do not mask deficit`() {
        val input = DailyLimitInput(money("6000"), money("0"), emptyList(), today,
            warnings = setOf(ForecastWarning.OVERDUE_INCOME))
        val state = ForecastUiState.from(CashFlowForecaster().forecast(input, money("200")).projection)
        assertEquals(money("-6000").amount, state.scaleMinimum)
        assertEquals(money("5900").amount, state.scaleMaximum)
        assertEquals(ForecastRisk.NoDeficit, state.byLimit.risk)
        assertEquals(ForecastRisk.Deficit, state.currentPace!!.risk)
        assertEquals(today.plusDays(30), state.currentPace.firstDeficitDate)
        assertEquals(money("200"), state.currentPace.deficitAmount)
        assertEquals(1f, state.paceGeometry!!.vertices.last().y, 0.00001f)
        assertTrue(state.limitGeometry.vertices.last().y < 1f)
        assertEquals(state.currentPacePoints!!.last().balance, state.currentPace.endingBalance)
    }

    @Test fun `loading and missing history are explicit without a synthetic pace`() {
        assertEquals(ForecastRisk.Unavailable, ForecastUiState().byLimit.risk)
        val state = ForecastUiState.from(CashFlowForecaster().forecast(
            DailyLimitInput(money("-50"), money("0"), emptyList(), today), null).projection)
        assertNull(state.currentPace)
        assertNull(state.paceGeometry)
        assertTrue(state.insufficientHistory)
        assertEquals(ForecastRisk.Deficit, state.byLimit.risk)
        assertEquals(today, state.minimum!!.date)
        assertEquals(state.minimum, state.maximum)
        assertTrue(state.limitGeometry.vertices.all { it.y == 0.5f })
    }
}
