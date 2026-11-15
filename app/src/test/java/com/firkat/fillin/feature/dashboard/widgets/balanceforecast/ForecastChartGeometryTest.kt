package com.firkat.fillin.feature.dashboard.widgets.balanceforecast

import com.firkat.fillin.feature.dashboard.domain.model.*
import com.firkat.fillin.feature.dashboard.widgets.balanceforecast.ui.ForecastChartGeometry
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ForecastChartGeometryTest {
    private val start = LocalDate.of(2026, 11, 3)
    private fun geometry(
        values: List<String>
    ) = ForecastChartGeometry(
        values.mapIndexed { index, value ->
            ForecastPoint(start.plusDays(index.toLong()), Money(value.toBigDecimal(), "KZT"))
        },
        start,
        start.plusDays(30)
    )

    @Test fun `scale always includes zero with padding and finite coordinates`() {
        listOf(
            emptyList(),
            listOf("0"),
            listOf("100", "200"),
            listOf("-100", "-200"),
            listOf("100", "-300")
        ).forEach { values ->
            val graph = geometry(values)
            assertTrue(graph.zeroY > 0f && graph.zeroY < 1f)
            values.forEach { assertTrue(graph.y(it.toBigDecimal()) in 0f..1f) }
        }
    }

    @Test fun `calendar spacing and nearest day do not depend on number of supplied points`() {
        val graph = geometry(listOf("10", "20"))
        assertEquals(0f, graph.x(start), 0.00001f)
        assertEquals(0.5f, graph.x(start.plusDays(15)), 0.00001f)
        assertEquals(1f, graph.x(start.plusDays(30)), 0.00001f)
        assertEquals(0, graph.nearestDay(-1f))
        assertEquals(16, graph.nearestDay(.52f))
        assertEquals(30, graph.nearestDay(2f))
    }

    @Test fun `shared scale preserves order and does not lose large decimal precision`() {
        val graph = geometry(listOf("100000000000000000000.02", "-100000000000000000000.01"))
        assertTrue(graph.y("100000000000000000000.02".toBigDecimal()) < graph.zeroY)
        assertTrue(graph.y("-100000000000000000000.01".toBigDecimal()) > graph.zeroY)
        assertEquals(.5f, geometry(listOf("0", "0")).zeroY, .00001f)
    }
}
