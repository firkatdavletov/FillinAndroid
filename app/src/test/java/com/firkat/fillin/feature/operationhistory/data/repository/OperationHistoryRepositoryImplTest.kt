package com.firkat.fillin.feature.operationhistory.data.repository

import com.firkat.fillin.feature.operationhistory.data.remote.OperationHistoryApi
import com.firkat.fillin.feature.operationhistory.data.remote.dto.MoneyDto
import com.firkat.fillin.feature.operationhistory.data.remote.dto.OperationCategoryDto
import com.firkat.fillin.feature.operationhistory.data.remote.dto.OperationDto
import com.firkat.fillin.feature.operationhistory.data.remote.dto.OperationPageDto
import com.firkat.fillin.feature.operationhistory.data.remote.dto.UpdatePurchaseRequestDto
import com.firkat.fillin.feature.operationhistory.domain.model.OperationHistoryFilter
import com.firkat.fillin.feature.operationhistory.domain.model.OperationStatus
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryException
import com.firkat.fillin.feature.operationhistory.domain.usecase.GetOperationHistoryPage
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import retrofit2.Response

class OperationHistoryRepositoryImplTest {
    @Test
    fun `use case requests 20 items and maps response without losing precision`() = runBlocking {
        val api = RecordingApi(
            response = OperationPageDto(
                items = listOf(
                    OperationDto(
                        id = "purchase-id",
                        type = "EXPENSE",
                        category = OperationCategoryDto(
                            id = "category-id",
                            name = "Продукты",
                        ),
                        originalAmount = MoneyDto(
                            amount = "30.00",
                            currency = "USD",
                        ),
                        amountKzt = MoneyDto(
                            amount = "15000.10",
                            currency = "KZT",
                        ),
                        occurredOn = "2026-10-06",
                        status = "VOIDED",
                        version = 3,
                        description = "супермаркет",
                    ),
                ),
                nextCursor = 42,
            ),
        )
        val repository = OperationHistoryRepositoryImpl(
            api = api,
            apiToken = "test-token",
        )

        val page = GetOperationHistoryPage(repository)(cursor = 12345, filter = TEST_FILTER)

        assertEquals("Bearer test-token", api.authorization)
        assertEquals(20, api.limit)
        assertEquals(12345L, api.cursor)
        assertEquals(42L, page.nextCursor)
        assertEquals(BigDecimal("15000.10"), page.items.single().amount)
        assertEquals(BigDecimal("30.00"), page.items.single().originalAmount)
        assertEquals("USD", page.items.single().originalCurrency)
        assertEquals(3L, page.items.single().version)
        assertEquals(LocalDate.of(2026, 10, 6), page.items.single().occurredOn)
        assertEquals(OperationStatus.Voided, page.items.single().status)
    }

    @Test
    fun `repository rejects missing api token before network request`() {
        val api = RecordingApi(response = OperationPageDto(emptyList(), null))
        val repository = OperationHistoryRepositoryImpl(api = api, apiToken = "")

        val exception = assertThrows(OperationHistoryException.MissingApiToken::class.java) {
            runBlocking { repository.getOperations(limit = 20, cursor = null, filter = TEST_FILTER) }
        }

        assertSame(OperationHistoryException.MissingApiToken, exception)
        assertEquals(0, api.callCount)
    }

    @Test
    fun `updates original amount with current optimistic version`() = runBlocking {
        val api = RecordingApi(response = OperationPageDto(listOf(purchaseDto()), null))
        val repository = OperationHistoryRepositoryImpl(api = api, apiToken = "owner-token")

        val result = repository.updatePurchase(
            id = "purchase-id",
            version = 4,
            amount = BigDecimal("12000.50"),
        )

        assertEquals("Bearer owner-token", api.authorization)
        assertEquals("purchase-id", api.purchaseId)
        assertEquals(UpdatePurchaseRequestDto(4, "12000.50"), api.updateRequest)
        assertEquals(BigDecimal("12000.50"), result.originalAmount)
        assertEquals("USD", result.originalCurrency)
        assertEquals(5L, result.version)
    }

    @Test
    fun `refunds full outstanding amount with current optimistic version`() = runBlocking {
        val api = RecordingApi(response = OperationPageDto(emptyList(), null))
        val repository = OperationHistoryRepositoryImpl(api = api, apiToken = "owner-token")

        repository.refundPurchase(id = "purchase-id", version = 6)

        assertEquals("purchase-id", api.purchaseId)
        assertEquals(6L, api.refundedVersion)
    }
}

private class RecordingApi(
    private val response: OperationPageDto,
) : OperationHistoryApi {
    var authorization: String? = null
    var limit: Int? = null
    var cursor: Long? = null
    var callCount: Int = 0
    var purchaseId: String? = null
    var updateRequest: UpdatePurchaseRequestDto? = null
    var refundedVersion: Long? = null

    override suspend fun getOperations(
        authorization: String,
        limit: Int,
        cursor: Long?,
        type: String?,
        categoryId: String?,
        dateFrom: String,
        dateTo: String,
    ): OperationPageDto {
        callCount += 1
        this.authorization = authorization
        this.limit = limit
        this.cursor = cursor
        return response
    }

    override suspend fun updatePurchase(
        authorization: String,
        purchaseId: String,
        request: UpdatePurchaseRequestDto,
    ): Response<OperationDto> {
        this.authorization = authorization
        this.purchaseId = purchaseId
        updateRequest = request
        return Response.success(
            200,
            purchaseDto(
                originalAmount = request.amount,
                version = request.version + 1,
            ),
        )
    }

    override suspend fun refundPurchase(
        authorization: String,
        purchaseId: String,
        version: Long,
    ): Response<Unit> {
        this.authorization = authorization
        this.purchaseId = purchaseId
        refundedVersion = version
        return Response.success(204, Unit)
    }
}

private fun purchaseDto(
    originalAmount: String = "30.00",
    version: Long = 4,
) = OperationDto(
    id = "purchase-id",
    type = "EXPENSE",
    category = OperationCategoryDto("category-id", "Продукты"),
    originalAmount = MoneyDto(originalAmount, "USD"),
    amountKzt = MoneyDto("15000.10", "KZT"),
    occurredOn = "2026-10-06",
    status = "POSTED",
    version = version,
    description = "супермаркет",
)

private val TEST_FILTER = OperationHistoryFilter.lastMonth(LocalDate.of(2026, 10, 17))
