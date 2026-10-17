package com.firkat.fillin.feature.operationhistory.domain.usecase

import com.firkat.fillin.feature.operationhistory.domain.model.Operation
import com.firkat.fillin.feature.operationhistory.domain.model.OperationHistoryFilter
import com.firkat.fillin.feature.operationhistory.domain.model.OperationPage
import com.firkat.fillin.feature.operationhistory.domain.model.OperationStatus
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryException
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryRepository
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class PurchaseCommandsTest {
    @Test
    fun `income cannot be edited or refunded through purchase commands`() {
        val repository = RecordingOperationHistoryRepository()
        val income = purchase().copy(type = OperationType.Income)
        assertThrows(OperationHistoryException.InvalidRequest::class.java) {
            runBlocking { UpdatePurchaseAmount(repository)(income, BigDecimal("35.50")) }
        }
        assertThrows(OperationHistoryException.InvalidRequest::class.java) {
            runBlocking { RefundPurchase(repository)(income) }
        }
        assertEquals(null, repository.id)
    }

    @Test
    fun `update forwards original-currency amount and current version`() = runBlocking {
        val repository = RecordingOperationHistoryRepository()

        UpdatePurchaseAmount(repository)(purchase(), BigDecimal("35.50"))

        assertEquals("purchase-id", repository.id)
        assertEquals(7L, repository.version)
        assertEquals(BigDecimal("35.50"), repository.amount)
    }

    @Test
    fun `update rejects unchanged amount`() {
        val exception = assertThrows(OperationHistoryException.InvalidRequest::class.java) {
            runBlocking { UpdatePurchaseAmount(RecordingOperationHistoryRepository())(
                purchase(),
                BigDecimal("30.0"),
            ) }
        }

        assertSame(OperationHistoryException.InvalidRequest, exception)
    }

    @Test
    fun `refund forwards id and current version`() = runBlocking {
        val repository = RecordingOperationHistoryRepository()

        RefundPurchase(repository)(purchase())

        assertEquals("purchase-id", repository.id)
        assertEquals(7L, repository.version)
    }

    @Test
    fun `refund rejects an already returned purchase`() {
        val exception = assertThrows(OperationHistoryException.InvalidRequest::class.java) {
            runBlocking {
                RefundPurchase(RecordingOperationHistoryRepository())(
                    purchase(status = OperationStatus.Voided),
                )
            }
        }

        assertSame(OperationHistoryException.InvalidRequest, exception)
    }

    private fun purchase(status: OperationStatus = OperationStatus.Posted) = Operation(
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

private class RecordingOperationHistoryRepository : OperationHistoryRepository {
    var id: String? = null
    var version: Long? = null
    var amount: BigDecimal? = null

    override suspend fun getOperations(limit: Int, cursor: Long?, filter: OperationHistoryFilter): OperationPage =
        OperationPage(emptyList(), null)

    override suspend fun updatePurchase(
        id: String,
        version: Long,
        amount: BigDecimal,
    ): Operation {
        this.id = id
        this.version = version
        this.amount = amount
        return Operation(
            id = id,
            categoryName = "Продукты",
            description = null,
            amount = BigDecimal("17750.00"),
            currency = "KZT",
            originalAmount = amount,
            originalCurrency = "USD",
            occurredOn = LocalDate.of(2026, 10, 6),
            status = OperationStatus.Posted,
            version = version + 1,
        )
    }

    override suspend fun refundPurchase(id: String, version: Long) {
        this.id = id
        this.version = version
    }
}
