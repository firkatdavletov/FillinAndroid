package com.firkat.fillin.feature.purchasehistory.data.repository

import com.firkat.fillin.feature.purchasehistory.data.remote.PurchaseHistoryApi
import com.firkat.fillin.feature.purchasehistory.data.remote.dto.PurchaseDto
import com.firkat.fillin.feature.purchasehistory.domain.model.Purchase
import com.firkat.fillin.feature.purchasehistory.domain.model.PurchasePage
import com.firkat.fillin.feature.purchasehistory.domain.model.PurchaseStatus
import com.firkat.fillin.feature.purchasehistory.domain.repository.PurchaseHistoryException
import com.firkat.fillin.feature.purchasehistory.domain.repository.PurchaseHistoryRepository
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

class PurchaseHistoryRepositoryImpl(
    private val api: PurchaseHistoryApi,
    private val apiToken: String,
) : PurchaseHistoryRepository {
    override suspend fun getPurchases(limit: Int, cursor: Long?): PurchasePage {
        require(limit in 1..50) { "Limit must be between 1 and 50" }
        if (apiToken.isBlank()) throw PurchaseHistoryException.MissingApiToken

        return try {
            val response = api.getPurchases(
                authorization = "Bearer $apiToken",
                limit = limit,
                cursor = cursor,
            )
            PurchasePage(
                items = response.items.map(PurchaseDto::toDomain),
                nextCursor = response.nextCursor,
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: HttpException) {
            throw when (exception.code()) {
                401 -> PurchaseHistoryException.Unauthorized
                403 -> PurchaseHistoryException.Forbidden
                else -> PurchaseHistoryException.Unknown
            }
        } catch (exception: IOException) {
            throw PurchaseHistoryException.Network
        } catch (exception: NumberFormatException) {
            throw PurchaseHistoryException.InvalidResponse
        } catch (exception: DateTimeParseException) {
            throw PurchaseHistoryException.InvalidResponse
        } catch (exception: SerializationException) {
            throw PurchaseHistoryException.InvalidResponse
        }
    }
}

private fun PurchaseDto.toDomain(): Purchase = Purchase(
    id = id,
    categoryName = category.name,
    description = description,
    amount = amountKzt.amount.toBigDecimal(),
    currency = amountKzt.currency,
    occurredOn = LocalDate.parse(occurredOn),
    status = when (status) {
        "POSTED" -> PurchaseStatus.Posted
        "VOIDED" -> PurchaseStatus.Voided
        else -> PurchaseStatus.Unknown
    },
)
