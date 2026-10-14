package com.firkat.fillin.feature.purchasehistory.ui

import com.firkat.fillin.feature.purchasehistory.domain.model.Purchase
import com.firkat.fillin.feature.purchasehistory.domain.model.PurchaseStatus
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class PurchaseDayGroupTest {
    @Test
    fun `groups purchases by date and sums posted expenses`() {
        val octoberFirst = LocalDate.of(2026, 10, 1)
        val octoberSecond = LocalDate.of(2026, 10, 2)
        val purchases = listOf(
            purchase("1", octoberFirst, "1000.00", "KZT"),
            purchase("2", octoberFirst, "2500.00", "KZT"),
            purchase("3", octoberSecond, "10.00", "USD"),
        )

        val groups = groupPurchasesByDate(purchases)

        assertEquals(listOf(octoberFirst, octoberSecond), groups.map(PurchaseDayGroup::date))
        assertEquals(BigDecimal("3500.00"), groups[0].totals.single().amount)
        assertEquals("KZT", groups[0].totals.single().currency)
        assertEquals(BigDecimal("10.00"), groups[1].totals.single().amount)
    }

    @Test
    fun `does not include voided or unknown purchases in total`() {
        val date = LocalDate.of(2026, 10, 1)
        val purchases = listOf(
            purchase("1", date, "1000.00", "KZT", PurchaseStatus.Posted),
            purchase("2", date, "2500.00", "KZT", PurchaseStatus.Voided),
            purchase("3", date, "500.00", "KZT", PurchaseStatus.Unknown),
        )

        val total = groupPurchasesByDate(purchases).single().totals.single()

        assertEquals(BigDecimal("1000.00"), total.amount)
    }

    @Test
    fun `keeps separate totals for different currencies`() {
        val date = LocalDate.of(2026, 10, 1)
        val purchases = listOf(
            purchase("1", date, "1000.00", "KZT"),
            purchase("2", date, "20.00", "USD"),
            purchase("3", date, "500.00", "KZT"),
        )

        val totals = groupPurchasesByDate(purchases).single().totals

        assertEquals(
            listOf(
                PurchaseDayTotal(BigDecimal("1500.00"), "KZT"),
                PurchaseDayTotal(BigDecimal("20.00"), "USD"),
            ),
            totals,
        )
    }

    @Test
    fun `shows zero in group currency when there are no posted purchases`() {
        val date = LocalDate.of(2026, 10, 1)
        val purchases = listOf(
            purchase("1", date, "1000.00", "KZT", PurchaseStatus.Voided),
        )

        val total = groupPurchasesByDate(purchases).single().totals.single()

        assertEquals(PurchaseDayTotal(BigDecimal.ZERO, "KZT"), total)
    }

    private fun purchase(
        id: String,
        date: LocalDate,
        amount: String,
        currency: String,
        status: PurchaseStatus = PurchaseStatus.Posted,
    ) = Purchase(
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
