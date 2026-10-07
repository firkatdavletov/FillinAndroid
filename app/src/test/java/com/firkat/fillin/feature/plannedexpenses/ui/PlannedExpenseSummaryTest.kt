package com.firkat.fillin.feature.plannedexpenses.ui

import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpense
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpenseStatus
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class PlannedExpenseSummaryTest {
    @Test
    fun `sums remaining amounts separately by currency`() {
        val items = listOf(
            expense("1", "100000.00", "KZT"),
            expense("2", "35000.50", "KZT"),
            expense("3", "20.00", "USD"),
        )

        val totals = remainingTotals(items)

        assertEquals(
            listOf(
                PlannedExpenseTotal(BigDecimal("135000.50"), "KZT"),
                PlannedExpenseTotal(BigDecimal("20.00"), "USD"),
            ),
            totals,
        )
    }

    private fun expense(
        id: String,
        remainingAmount: String,
        currency: String,
    ) = PlannedExpense(
        id = id,
        categoryId = "category",
        categoryName = "Категория",
        plannedAmount = BigDecimal(remainingAmount),
        paidAmount = BigDecimal.ZERO,
        remainingAmount = BigDecimal(remainingAmount),
        currency = currency,
        dueOn = LocalDate.of(2026, 10, 10),
        overdue = false,
        status = PlannedExpenseStatus.Scheduled,
        version = 1,
        createdAt = Instant.EPOCH,
        modifiedAt = Instant.EPOCH,
    )
}
