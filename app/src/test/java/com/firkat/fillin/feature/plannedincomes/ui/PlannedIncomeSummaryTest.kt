package com.firkat.fillin.feature.plannedincomes.ui

import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncome
import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncomeStatus
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class PlannedIncomeSummaryTest {
    @Test
    fun `sums planned amounts separately by currency`() {
        val items = listOf(
            income("1", "1300000.00", "KZT"),
            income("2", "150000.50", "KZT"),
            income("3", "20.00", "USD"),
        )

        val totals = plannedIncomeTotals(items)

        assertEquals(
            listOf(
                PlannedIncomeTotal(BigDecimal("1450000.50"), "KZT"),
                PlannedIncomeTotal(BigDecimal("20.00"), "USD"),
            ),
            totals,
        )
    }

    private fun income(
        id: String,
        plannedAmount: String,
        currency: String,
    ) = PlannedIncome(
        id = id,
        categoryId = "category",
        categoryName = "Категория",
        plannedAmount = BigDecimal(plannedAmount),
        currency = currency,
        dueOn = LocalDate.of(2026, 10, 10),
        overdue = false,
        status = PlannedIncomeStatus.Scheduled,
        version = 0,
        createdAt = Instant.EPOCH,
        modifiedAt = Instant.EPOCH,
    )
}
