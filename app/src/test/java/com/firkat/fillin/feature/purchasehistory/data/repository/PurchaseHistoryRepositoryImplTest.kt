package com.firkat.fillin.feature.purchasehistory.data.repository

import com.firkat.fillin.feature.purchasehistory.data.remote.PurchaseHistoryApi
import com.firkat.fillin.feature.purchasehistory.data.remote.dto.MoneyDto
import com.firkat.fillin.feature.purchasehistory.data.remote.dto.PurchaseCategoryDto
import com.firkat.fillin.feature.purchasehistory.data.remote.dto.PurchaseDto
import com.firkat.fillin.feature.purchasehistory.data.remote.dto.PurchasePageDto
import com.firkat.fillin.feature.purchasehistory.domain.model.PurchaseStatus
import com.firkat.fillin.feature.purchasehistory.domain.repository.PurchaseHistoryException
import com.firkat.fillin.feature.purchasehistory.domain.usecase.GetPurchaseHistoryPage
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class PurchaseHistoryRepositoryImplTest {
    @Test
    fun `use case requests 20 items and maps response without losing precision`() = runBlocking {
        val api = RecordingApi(
            response = PurchasePageDto(
                items = listOf(
                    PurchaseDto(
                        id = "purchase-id",
                        category = PurchaseCategoryDto(
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
                        description = "супермаркет",
                    ),
                ),
                nextCursor = 42,
            ),
        )
        val repository = PurchaseHistoryRepositoryImpl(
            api = api,
            apiToken = "test-token",
        )

        val page = GetPurchaseHistoryPage(repository)(cursor = 12345)

        assertEquals("Bearer test-token", api.authorization)
        assertEquals(20, api.limit)
        assertEquals(12345L, api.cursor)
        assertEquals(42L, page.nextCursor)
        assertEquals(BigDecimal("15000.10"), page.items.single().amount)
        assertEquals(LocalDate.of(2026, 10, 6), page.items.single().occurredOn)
        assertEquals(PurchaseStatus.Voided, page.items.single().status)
    }

    @Test
    fun `repository rejects missing api token before network request`() {
        val api = RecordingApi(response = PurchasePageDto(emptyList(), null))
        val repository = PurchaseHistoryRepositoryImpl(api = api, apiToken = "")

        val exception = assertThrows(PurchaseHistoryException.MissingApiToken::class.java) {
            runBlocking { repository.getPurchases(limit = 20, cursor = null) }
        }

        assertSame(PurchaseHistoryException.MissingApiToken, exception)
        assertEquals(0, api.callCount)
    }
}

private class RecordingApi(
    private val response: PurchasePageDto,
) : PurchaseHistoryApi {
    var authorization: String? = null
    var limit: Int? = null
    var cursor: Long? = null
    var callCount: Int = 0

    override suspend fun getPurchases(
        authorization: String,
        limit: Int,
        cursor: Long?,
    ): PurchasePageDto {
        callCount += 1
        this.authorization = authorization
        this.limit = limit
        this.cursor = cursor
        return response
    }
}
