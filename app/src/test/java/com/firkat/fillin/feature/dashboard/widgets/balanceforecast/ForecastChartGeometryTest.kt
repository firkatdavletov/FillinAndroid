package com.firkat.fillin.feature.dashboard.widgets.balanceforecast

import com.firkat.fillin.feature.dashboard.domain.model.ForecastPoint
import com.firkat.fillin.feature.dashboard.domain.model.Money
import com.firkat.fillin.feature.dashboard.widgets.balanceforecast.ui.ForecastChartGeometry
import java.time.LocalDate
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class ForecastChartGeometryTest {
    private fun geometry(values: List<String>) = ForecastChartGeometry(values.mapIndexed { index, value ->
        ForecastPoint(LocalDate.of(2026, 10, 25).plusDays(index.toLong()), Money(value.toBigDecimal(), "KZT"))
    })

    @Test fun `curves pass through daily balances and cannot invent extra minima or maxima`() {
        val random = Random(42)
        val fixtures = listOf(
            listOf("0", "0", "1000", "-500", "-500", "0"),
            listOf("100000000000000000000.01", "100000000000000000000.02", "100000000000000000000.00"),
        ) + List(30) { List(31) { random.nextInt(-100000, 100000).toString() } }
        fixtures.forEach { values ->
            val geometry = geometry(values)
            geometry.segments.forEachIndexed { index, segment ->
                assertEquals(geometry.vertices[index], segment.start)
                assertEquals(geometry.vertices[index + 1], segment.end)
                var previous = segment.start.y
                for (sample in 0..100) {
                    val t = sample / 100f
                    val u = 1 - t
                    val y = u * u * u * segment.start.y + 3 * u * u * t * segment.first.y +
                        3 * u * t * t * segment.second.y + t * t * t * segment.end.y
                    assertTrue(y >= minOf(segment.start.y, segment.end.y) - 0.00001f)
                    assertTrue(y <= maxOf(segment.start.y, segment.end.y) + 0.00001f)
                    if (segment.end.y >= segment.start.y) assertTrue(y >= previous - 0.00001f)
                    else assertTrue(y <= previous + 0.00001f)
                    previous = y
                }
            }
        }
    }

    @Test fun `flat empty and single point graphs have finite coordinates and earliest extrema`() {
        assertTrue(geometry(emptyList()).vertices.isEmpty())
        assertTrue(geometry(listOf("0")).segments.isEmpty())
        for (value in listOf("0", "100", "-100")) {
            val graph = geometry(List(31) { value })
            assertEquals(0, graph.minimumIndex)
            assertEquals(0, graph.maximumIndex)
            assertTrue(graph.vertices.all { it.y == 0.5f && it.x.isFinite() })
            assertNull(graph.zeroY)
        }
    }

    @Test fun `zero line appears only for a graph spanning positive and negative balances`() {
        assertEquals(0.25f, geometry(listOf("100", "-300")).zeroY!!, 0.00001f)
        assertNull(geometry(listOf("100", "200")).zeroY)
        assertNull(geometry(listOf("-100", "-200")).zeroY)
    }
}
