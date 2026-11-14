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
    fun `defaults to expenses through end of month`() {
        assertEquals(PlannedExpensesPeriod.EndOfMonth, PlannedExpensesUiState().selectedPeriod)
    }

    @Test
    fun `sums remaining amounts separately by currency`() {
        val items = listOf(
            expense("1", "100000.00", "KZT"),
            expense("2", "35000.50", "KZT"),
            expense("3", "20.00", "USD")
        )

        val totals = remainingTotals(items)

        assertEquals(
            listOf(
                PlannedExpenseTotal(BigDecimal("135000.50"), "KZT"),
                PlannedExpenseTotal(BigDecimal("20.00"), "USD")
            ),
            totals
        )
    }

    @Test
    fun `month period includes overdue and current month expenses only`() {
        val items = listOf(
            expense("overdue", "1000.00", "KZT", LocalDate.of(2026, 9, 30)),
            expense("month", "2000.00", "KZT", LocalDate.of(2026, 10, 31)),
            expense("year", "3000.00", "KZT", LocalDate.of(2026, 11, 1))
        )

        val visible = plannedExpensesForPeriod(
            items = items,
            asOf = LocalDate.of(2026, 10, 14),
            throughInclusive = LocalDate.of(2026, 12, 31),
            period = PlannedExpensesPeriod.EndOfMonth
        )

        assertEquals(listOf("overdue", "month"), visible.map(PlannedExpense::id))
        assertEquals(BigDecimal("3000.00"), remainingTotals(visible).single().amount)
    }

    @Test
    fun `year period keeps the server horizon`() {
        val items = listOf(
            expense("month", "2000.00", "KZT", LocalDate.of(2026, 10, 31)),
            expense("year", "3000.00", "KZT", LocalDate.of(2026, 12, 20))
        )

        val visible = plannedExpensesForPeriod(
            items = items,
            asOf = LocalDate.of(2026, 10, 14),
            throughInclusive = LocalDate.of(2026, 12, 31),
            period = PlannedExpensesPeriod.EndOfYear
        )

        assertEquals(items, visible)
        assertEquals(
            LocalDate.of(2026, 12, 31),
            plannedExpensesPeriodEnd(
                asOf = LocalDate.of(2026, 10, 14),
                throughInclusive = LocalDate.of(2026, 12, 31),
                period = PlannedExpensesPeriod.EndOfYear
            )
        )
    }

    private fun expense(
        id: String,
        remainingAmount: String,
        currency: String,
        dueOn: LocalDate = LocalDate.of(2026, 10, 10)
    ) = PlannedExpense(
        id = id,
        categoryId = "category",
        categoryName = "Категория",
        plannedAmount = BigDecimal(remainingAmount),
        paidAmount = BigDecimal.ZERO,
        remainingAmount = BigDecimal(remainingAmount),
        currency = currency,
        dueOn = dueOn,
        overdue = dueOn.isBefore(LocalDate.of(2026, 10, 14)),
        status = PlannedExpenseStatus.Scheduled,
        version = 1,
        createdAt = Instant.EPOCH,
        modifiedAt = Instant.EPOCH
    )
}
