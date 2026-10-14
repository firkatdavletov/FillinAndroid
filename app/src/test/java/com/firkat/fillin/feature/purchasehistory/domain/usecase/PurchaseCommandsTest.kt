package com.firkat.fillin.feature.purchasehistory.domain.usecase

import com.firkat.fillin.feature.purchasehistory.domain.model.Purchase
import com.firkat.fillin.feature.purchasehistory.domain.model.PurchasePage
import com.firkat.fillin.feature.purchasehistory.domain.model.PurchaseStatus
import com.firkat.fillin.feature.purchasehistory.domain.repository.PurchaseHistoryException
import com.firkat.fillin.feature.purchasehistory.domain.repository.PurchaseHistoryRepository
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class PurchaseCommandsTest {
    @Test
    fun `update forwards original-currency amount and current version`() = runBlocking {
        val repository = RecordingPurchaseHistoryRepository()

        UpdatePurchaseAmount(repository)(purchase(), BigDecimal("35.50"))

        assertEquals("purchase-id", repository.id)
        assertEquals(7L, repository.version)
        assertEquals(BigDecimal("35.50"), repository.amount)
    }

    @Test
    fun `update rejects unchanged amount`() {
        val exception = assertThrows(PurchaseHistoryException.InvalidRequest::class.java) {
            runBlocking { UpdatePurchaseAmount(RecordingPurchaseHistoryRepository())(
                purchase(),
                BigDecimal("30.0"),
            ) }
        }

        assertSame(PurchaseHistoryException.InvalidRequest, exception)
    }

    @Test
    fun `refund forwards id and current version`() = runBlocking {
        val repository = RecordingPurchaseHistoryRepository()

        RefundPurchase(repository)(purchase())

        assertEquals("purchase-id", repository.id)
        assertEquals(7L, repository.version)
    }

    @Test
    fun `refund rejects an already returned purchase`() {
        val exception = assertThrows(PurchaseHistoryException.InvalidRequest::class.java) {
            runBlocking {
                RefundPurchase(RecordingPurchaseHistoryRepository())(
                    purchase(status = PurchaseStatus.Voided),
                )
            }
        }

        assertSame(PurchaseHistoryException.InvalidRequest, exception)
    }

    private fun purchase(status: PurchaseStatus = PurchaseStatus.Posted) = Purchase(
        id = "purchase-id",
        categoryName = "Продукты",
        description = "Супермаркет",
        amount = BigDecimal("15000.00"),
        currency = "KZT",
        originalAmount = BigDecimal("30.00"),
        originalCurrency = "USD",
        occurredOn = LocalDate.of(2026, 10, 6),
        status = status,
        version = 7,
    )
}

private class RecordingPurchaseHistoryRepository : PurchaseHistoryRepository {
    var id: String? = null
    var version: Long? = null
    var amount: BigDecimal? = null

    override suspend fun getPurchases(limit: Int, cursor: Long?): PurchasePage =
        PurchasePage(emptyList(), null)

    override suspend fun updatePurchase(
        id: String,
        version: Long,
        amount: BigDecimal,
    ): Purchase {
        this.id = id
        this.version = version
        this.amount = amount
        return Purchase(
            id = id,
            categoryName = "Продукты",
            description = null,
            amount = BigDecimal("17750.00"),
            currency = "KZT",
            originalAmount = amount,
            originalCurrency = "USD",
            occurredOn = LocalDate.of(2026, 10, 6),
            status = PurchaseStatus.Posted,
            version = version + 1,
        )
    }

    override suspend fun refundPurchase(id: String, version: Long) {
        this.id = id
        this.version = version
    }
}
