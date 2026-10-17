package com.firkat.fillin.feature.operationhistory.ui

import com.firkat.fillin.feature.operationhistory.domain.model.Operation
import com.firkat.fillin.feature.operationhistory.domain.model.OperationStatus
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class OperationDayGroupTest {
    @Test
    fun `keeps income and expenses separate in daily totals`() {
        val date = LocalDate.of(2026, 10, 1)
        val operations = listOf(
            purchase("expense", date, "1000.00", "KZT"),
            purchase("income", date, "3000.00", "KZT").copy(type = OperationType.Income),
        )
        assertEquals(
            listOf(
                OperationDayTotal(BigDecimal("1000.00"), "KZT", OperationType.Expense),
                OperationDayTotal(BigDecimal("3000.00"), "KZT", OperationType.Income),
            ),
            groupOperationsByDate(operations).single().totals,
        )
    }

    @Test
    fun `groups operations by date and sums posted expenses`() {
        val octoberFirst = LocalDate.of(2026, 10, 1)
        val octoberSecond = LocalDate.of(2026, 10, 2)
        val operations = listOf(
            purchase("1", octoberFirst, "1000.00", "KZT"),
            purchase("2", octoberFirst, "2500.00", "KZT"),
            purchase("3", octoberSecond, "10.00", "USD"),
        )

        val groups = groupOperationsByDate(operations)

        assertEquals(listOf(octoberFirst, octoberSecond), groups.map(OperationDayGroup::date))
        assertEquals(BigDecimal("3500.00"), groups[0].totals.single().amount)
        assertEquals("KZT", groups[0].totals.single().currency)
        assertEquals(BigDecimal("10.00"), groups[1].totals.single().amount)
    }

    @Test
    fun `does not include voided or unknown operations in total`() {
        val date = LocalDate.of(2026, 10, 1)
        val operations = listOf(
            purchase("1", date, "1000.00", "KZT", OperationStatus.Posted),
            purchase("2", date, "2500.00", "KZT", OperationStatus.Voided),
            purchase("3", date, "500.00", "KZT", OperationStatus.Unknown),
        )

        val total = groupOperationsByDate(operations).single().totals.single()

        assertEquals(BigDecimal("1000.00"), total.amount)
    }

    @Test
    fun `keeps separate totals for different currencies`() {
        val date = LocalDate.of(2026, 10, 1)
        val operations = listOf(
            purchase("1", date, "1000.00", "KZT"),
            purchase("2", date, "20.00", "USD"),
            purchase("3", date, "500.00", "KZT"),
        )

        val totals = groupOperationsByDate(operations).single().totals

        assertEquals(
            listOf(
                OperationDayTotal(BigDecimal("1500.00"), "KZT"),
                OperationDayTotal(BigDecimal("20.00"), "USD"),
            ),
            totals,
        )
    }

    @Test
    fun `shows zero in group currency when there are no posted operations`() {
        val date = LocalDate.of(2026, 10, 1)
        val operations = listOf(
            purchase("1", date, "1000.00", "KZT", OperationStatus.Voided),
        )

        val total = groupOperationsByDate(operations).single().totals.single()

        assertEquals(OperationDayTotal(BigDecimal.ZERO, "KZT"), total)
    }

    private fun purchase(
        id: String,
        date: LocalDate,
        amount: String,
        currency: String,
        status: OperationStatus = OperationStatus.Posted,
    ) = Operation(
        id = id,
        categoryName = "Категория",
        description = null,
        amount = BigDecimal(amount),
        currency = currency,
        originalAmount = BigDecimal(amount),
        originalCurrency = currency,
        occurredOn = date,
        status = status,
        version = 0,
    )
}
