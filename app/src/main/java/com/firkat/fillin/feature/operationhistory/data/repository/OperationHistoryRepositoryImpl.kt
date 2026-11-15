package com.firkat.fillin.feature.operationhistory.data.repository

import com.firkat.fillin.feature.operationhistory.data.remote.OperationHistoryApi
import com.firkat.fillin.feature.operationhistory.data.remote.dto.OperationDto
import com.firkat.fillin.feature.operationhistory.data.remote.dto.OperationTotalsDto
import com.firkat.fillin.feature.operationhistory.data.remote.dto.UpdatePurchaseRequestDto
import com.firkat.fillin.feature.operationhistory.domain.model.Operation
import com.firkat.fillin.feature.operationhistory.domain.model.OperationHistoryFilter
import com.firkat.fillin.feature.operationhistory.domain.model.OperationPage
import com.firkat.fillin.feature.operationhistory.domain.model.OperationPeriodTotals
import com.firkat.fillin.feature.operationhistory.domain.model.OperationStatus
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryException
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryRepository
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import retrofit2.Response

class OperationHistoryRepositoryImpl(private val api: OperationHistoryApi, private val apiToken: String) :
    OperationHistoryRepository {
    override suspend fun getOperations(
        limit: Int,
        cursor: Long?,
        filter: OperationHistoryFilter
    ): OperationPage {
        require(limit in 1..50) { "Limit must be between 1 and 50" }
        return execute {
            val response = api.getOperations(
                authorization = authorization,
                limit = limit,
                cursor = cursor,
                type = filter.type?.apiValue,
                categoryId = filter.categoryId,
                dateFrom = filter.dateFrom.toString(),
                dateTo = filter.dateTo.toString()
            )
            OperationPage(
                items = response.items.map(OperationDto::toDomain),
                nextCursor = response.nextCursor,
                totals = response.totals.toDomain()
            )
        }
    }

    override suspend fun updateOperationAmount(
        id: String,
        version: Long,
        amount: BigDecimal
    ): Operation = execute {
        val updated = api.updatePurchase(
            authorization = authorization,
            purchaseId = id,
            request = UpdatePurchaseRequestDto(
                version = version,
                amount = amount.toPlainString()
            )
        ).requireBody(expectedCode = 200).toDomain()

        if (updated.id != id || updated.version != version + 1) {
            throw OperationHistoryException.InvalidResponse
        }
        updated
    }

    override suspend fun refundPurchase(
        id: String,
        version: Long
    ): Unit = execute {
        api.refundPurchase(
            authorization = authorization,
            purchaseId = id,
            version = version
        ).requireStatus(expectedCode = 204)
    }

    private suspend fun <T> execute(
        block: suspend RequestContext.() -> T
    ): T {
        if (apiToken.isBlank()) throw OperationHistoryException.MissingApiToken

        return try {
            RequestContext("Bearer $apiToken").block()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: HttpException) {
            throw when (exception.code()) {
                400, 422 -> OperationHistoryException.InvalidRequest
                401 -> OperationHistoryException.Unauthorized
                403 -> OperationHistoryException.Forbidden
                404 -> OperationHistoryException.NotFound
                409 -> OperationHistoryException.Conflict
                else -> OperationHistoryException.Unknown
            }
        } catch (exception: IOException) {
            throw OperationHistoryException.Network
        } catch (exception: NumberFormatException) {
            throw OperationHistoryException.InvalidResponse
        } catch (exception: DateTimeParseException) {
            throw OperationHistoryException.InvalidResponse
        } catch (exception: SerializationException) {
            throw OperationHistoryException.InvalidResponse
        }
    }

    private data class RequestContext(val authorization: String)
}

private fun OperationTotalsDto.toDomain(): OperationPeriodTotals {
    if (expenses.currency != "KZT" || incomes.currency != "KZT") {
        throw OperationHistoryException.InvalidResponse
    }
    return OperationPeriodTotals(
        expenses = expenses.amount.toBigDecimal(),
        incomes = incomes.amount.toBigDecimal()
    )
}

private fun <T> Response<T>.requireBody(
    expectedCode: Int
): T {
    requireStatus(expectedCode)
    return body() ?: throw OperationHistoryException.InvalidResponse
}

private fun Response<*>.requireStatus(
    expectedCode: Int
) {
    if (!isSuccessful) throw HttpException(this)
    if (code() != expectedCode) throw OperationHistoryException.InvalidResponse
}

private fun OperationDto.toDomain(): Operation = Operation(
    id = id,
    type = when (type) {
        "EXPENSE" -> OperationType.Expense
        "INCOME" -> OperationType.Income
        else -> throw OperationHistoryException.InvalidResponse
    },
    categoryId = category.id,
    categoryName = category.name,
    description = description,
    amount = amountKzt.amount.toBigDecimal(),
    currency = amountKzt.currency,
    originalAmount = originalAmount.amount.toBigDecimal(),
    originalCurrency = originalAmount.currency,
    occurredOn = LocalDate.parse(occurredOn),
    status = when (status) {
        "POSTED" -> OperationStatus.Posted
        "VOIDED" -> OperationStatus.Voided
        else -> OperationStatus.Unknown
    },
    version = version
)
