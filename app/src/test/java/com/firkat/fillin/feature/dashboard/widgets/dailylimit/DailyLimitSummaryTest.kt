package com.firkat.fillin.feature.dashboard.widgets.dailylimit

import com.firkat.fillin.feature.dashboard.domain.calculation.*
import com.firkat.fillin.feature.dashboard.widgets.dailylimit.domain.DailyLimitSummary
import com.firkat.fillin.feature.dashboard.widgets.dailylimit.ui.DailyLimitUiState
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class DailyLimitSummaryTest {
    @Test fun `spending and remaining percentages use the daily limit`() {
        val summary = summary("2500")
        assertEquals(decimal("10000"), summary.limit.amount)
        assertEquals(decimal("2500"), summary.spentToday.amount)
        assertEquals(decimal("7500"), summary.remainingToday.amount)
        assertEquals(BigDecimal("25.0"), summary.spentPercent)
        assertEquals(BigDecimal("75.0"), summary.remainingPercent)
        val ui = DailyLimitUiState.from(summary)
        assertEquals(0.25f, ui.progress, 0f)
        assertFalse(ui.isOverspent)
    }

    @Test fun `no expenses leave the entire limit available and reaching it leaves zero`() {
        val empty = DailyLimitUiState.from(summary("0"))
        assertEquals(decimal("10000"), empty.remainingToday!!.amount)
        assertEquals(BigDecimal("100.0"), empty.remainingPercent)
        assertEquals(0f, empty.progress, 0f)
        val reached = DailyLimitUiState.from(summary("10000"))
        assertEquals(decimal("0"), reached.remainingToday!!.amount)
        assertEquals(BigDecimal("0.0"), reached.remainingPercent)
        assertEquals(BigDecimal("100.0"), reached.spentPercent)
        assertEquals(1f, reached.progress, 0f)
        assertFalse(reached.isOverspent)
    }

    @Test fun `overspending has a signed domain remainder and positive display amounts`() {
        val summary = summary("12500")
        assertEquals(decimal("0"), summary.remainingToday.amount)
        assertEquals(BigDecimal("0.0"), summary.remainingPercent)
        val ui = DailyLimitUiState.from(summary)
        assertEquals(decimal("0"), ui.remainingToday!!.amount)
        assertEquals(BigDecimal("0.0"), ui.remainingPercent)
        assertEquals(BigDecimal("125.0"), ui.spentPercent)
        assertTrue(ui.isOverspent)
        assertEquals(1f, ui.progress, 0f)
    }

    @Test fun `zero and negative budget bases have no percentages and safe progress`() {
        for (opening in listOf("0", "-1")) {
            val empty = DailyLimitUiState.from(summary("0", opening))
            assertNull(empty.spentPercent)
            assertNull(empty.remainingPercent)
            assertEquals(0f, empty.progress, 0f)
            assertFalse(empty.isOverspent)
            val spent = DailyLimitUiState.from(summary("10", opening))
            assertEquals(decimal("0"), spent.amount!!.amount)
            assertNull(spent.spentPercent)
            assertNull(spent.remainingPercent)
            assertEquals(decimal("0"), spent.remainingToday!!.amount)
            assertEquals(0f, spent.progress, 0f)
            assertFalse(spent.showProgress)
            assertFalse(spent.showRemaining)
            assertTrue(spent.isOverspent)
        }
    }

    @Test fun `refunds exceeding todays expenses restore the whole limit without negative spending`() {
        val dashboard = dashboard("0").let {
            it.copy(todayVariableNetExpenses = it.todayVariableNetExpenses.copy(amount = decimal("-500")))
        }
        val summary = DailyLimitSummary.from(dashboard)
        assertEquals(decimal("0"), summary.spentToday.amount)
        assertEquals(summary.limit, summary.remainingToday)
        assertEquals(BigDecimal("0.0"), summary.spentPercent)
        assertEquals(BigDecimal("100.0"), summary.remainingPercent)
    }

    @Test fun `percentages round half up while progress retains fractional precision`() {
        val ui = DailyLimitUiState.from(summary("1", "180"))
        assertEquals(decimal("3"), ui.amount!!.amount)
        assertEquals(BigDecimal("33.3"), ui.spentPercent)
        assertEquals(BigDecimal("66.7"), ui.remainingPercent)
        assertEquals(1f / 3f, ui.progress, 0.000001f)
        val half = summary("0.01", "480")
        assertEquals(BigDecimal("0.1"), half.spentPercent)
        assertEquals(BigDecimal("99.9"), half.remainingPercent)
    }

    @Test fun `deficit takes priority over overspending and hides remaining`() {
        val ui = DailyLimitUiState.from(summary("10", "-100"))
        assertTrue(ui.isOverspent)
        assertEquals(com.firkat.fillin.feature.dashboard.widgets.dailylimit.ui.DailyLimitCardStatus.Deficit, ui.status)
        assertFalse(ui.showRemaining)
        assertFalse(ui.showProgress)
    }

    @Test fun `unavailable has no progress or remaining`() {
        val ui = DailyLimitUiState()
        assertFalse(ui.showProgress)
        assertFalse(ui.showRemaining)
        assertNull(ui.amount)
    }

    private fun summary(spent: String, opening: String = "600000") = DailyLimitSummary.from(dashboard(spent, opening))

    private fun dashboard(spent: String, opening: String = "600000") = CalculateDashboard.calculate(
        DashboardCalculationInput(
            asOf = Instant.parse("2026-09-06T03:00:00Z"),
            accountingStartedAt = Instant.parse("2026-08-01T00:00:00Z"),
            openingBalance = decimal(opening),
            protectedReserve = decimal("0"),
            goalReserve = decimal("0"),
            operations = listOf(DashboardOperation(
                "expense", CATEGORY, DashboardOperationKind.Expense, decimal(spent),
                LocalDate.parse("2026-09-06"), Instant.parse("2026-09-06T00:00:00Z"),
            )),
            categories = listOf(DashboardCategory(CATEGORY, "Продукты", true)),
            payments = emptyList(), incomes = emptyList(), excludedVariableExpenseIds = emptySet(),
        ),
    )

    private fun decimal(value: String) = value.toBigDecimal().setScale(2)

    companion object {
        private const val CATEGORY = "00000000-0000-0000-0000-000000000001"
    }
}
