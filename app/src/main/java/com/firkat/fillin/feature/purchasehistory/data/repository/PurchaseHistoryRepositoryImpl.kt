package com.firkat.fillin.feature.purchasehistory.data.repository

import com.firkat.fillin.feature.purchasehistory.data.remote.PurchaseHistoryApi
import com.firkat.fillin.feature.purchasehistory.data.remote.dto.PurchaseDto
import com.firkat.fillin.feature.purchasehistory.data.remote.dto.UpdatePurchaseRequestDto
import com.firkat.fillin.feature.purchasehistory.domain.model.Purchase
import com.firkat.fillin.feature.purchasehistory.domain.model.PurchasePage
import com.firkat.fillin.feature.purchasehistory.domain.model.PurchaseStatus
import com.firkat.fillin.feature.purchasehistory.domain.repository.PurchaseHistoryException
import com.firkat.fillin.feature.purchasehistory.domain.repository.PurchaseHistoryRepository
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import retrofit2.Response

class PurchaseHistoryRepositoryImpl(
    private val api: PurchaseHistoryApi,
    private val apiToken: String,
) : PurchaseHistoryRepository {
    override suspend fun getPurchases(limit: Int, cursor: Long?): PurchasePage {
        require(limit in 1..50) { "Limit must be between 1 and 50" }
        return execute {
            val response = api.getPurchases(
                authorization = authorization,
                limit = limit,
                cursor = cursor,
            )
            PurchasePage(
                items = response.items.map(PurchaseDto::toDomain),
                nextCursor = response.nextCursor,
            )
        }
    }

    override suspend fun updatePurchase(
        id: String,
        version: Long,
        amount: BigDecimal,
    ): Purchase = execute {
        val updated = api.updatePurchase(
            authorization = authorization,
            purchaseId = id,
            request = UpdatePurchaseRequestDto(
                version = version,
                amount = amount.toPlainString(),
            ),
        ).requireBody(expectedCode = 200).toDomain()

        if (updated.id != id || updated.version != version + 1) {
            throw PurchaseHistoryException.InvalidResponse
        }
        updated
    }

    override suspend fun refundPurchase(id: String, version: Long): Unit = execute {
        api.refundPurchase(
            authorization = authorization,
            purchaseId = id,
            version = version,
        ).requireStatus(expectedCode = 204)
    }

    private suspend fun <T> execute(block: suspend RequestContext.() -> T): T {
        if (apiToken.isBlank()) throw PurchaseHistoryException.MissingApiToken

        return try {
            RequestContext("Bearer $apiToken").block()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: HttpException) {
            throw when (exception.code()) {
                400, 422 -> PurchaseHistoryException.InvalidRequest
                401 -> PurchaseHistoryException.Unauthorized
                403 -> PurchaseHistoryException.Forbidden
                404 -> PurchaseHistoryException.NotFound
                409 -> PurchaseHistoryException.Conflict
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

    private data class RequestContext(val authorization: String)
}

private fun <T> Response<T>.requireBody(expectedCode: Int): T {
    requireStatus(expectedCode)
    return body() ?: throw PurchaseHistoryException.InvalidResponse
}

private fun Response<*>.requireStatus(expectedCode: Int) {
    if (!isSuccessful) throw HttpException(this)
    if (code() != expectedCode) throw PurchaseHistoryException.InvalidResponse
}

private fun PurchaseDto.toDomain(): Purchase = Purchase(
    id = id,
    categoryName = category.name,
    description = description,
    amount = amountKzt.amount.toBigDecimal(),
    currency = amountKzt.currency,
    originalAmount = originalAmount.amount.toBigDecimal(),
    originalCurrency = originalAmount.currency,
    occurredOn = LocalDate.parse(occurredOn),
    status = when (status) {
        "POSTED" -> PurchaseStatus.Posted
        "VOIDED" -> PurchaseStatus.Voided
        else -> PurchaseStatus.Unknown
    },
    version = version,
)
