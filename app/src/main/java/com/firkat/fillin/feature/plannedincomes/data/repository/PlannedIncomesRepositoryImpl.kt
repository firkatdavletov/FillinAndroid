package com.firkat.fillin.feature.plannedincomes.data.repository

import com.firkat.fillin.feature.plannedincomes.data.remote.PlannedIncomesApi
import com.firkat.fillin.feature.plannedincomes.data.remote.dto.CreatePlannedIncomeRequestDto
import com.firkat.fillin.feature.plannedincomes.data.remote.dto.PlannedIncomeDto
import com.firkat.fillin.feature.plannedincomes.data.remote.dto.UpdatePlannedIncomeRequestDto
import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncome
import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncomes
import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncomeStatus
import com.firkat.fillin.feature.plannedincomes.domain.repository.PlannedIncomesException
import com.firkat.fillin.feature.plannedincomes.domain.repository.PlannedIncomesRepository
import java.io.IOException
import java.math.BigDecimal
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import retrofit2.Response

class PlannedIncomesRepositoryImpl(
    private val api: PlannedIncomesApi,
    private val apiToken: String,
) : PlannedIncomesRepository {
    override suspend fun getPlannedIncomes(): PlannedIncomes = execute {
        val response = api.getPlannedIncomes(authorization = authorization)
        PlannedIncomes(
            asOf = LocalDate.parse(response.asOf),
            items = response.items
                .map(PlannedIncomeDto::toDomain)
                .sortedWith(
                    compareByDescending<PlannedIncome> { it.overdue }
                        .thenBy(PlannedIncome::dueOn),
                ),
        )
    }

    override suspend fun createPlannedIncome(
        categoryId: String,
        amount: BigDecimal,
        dueOn: LocalDate,
    ): PlannedIncome = execute {
        val created = api.createPlannedIncome(
            authorization = authorization,
            request = CreatePlannedIncomeRequestDto(
                categoryId = categoryId,
                amount = amount.toPlainString(),
                dueOn = dueOn.toString(),
            ),
        ).requireBody(expectedCode = 201).toDomain()

        if (created.version != 0L) throw PlannedIncomesException.InvalidResponse
        created
    }

    override suspend fun updatePlannedIncome(
        id: String,
        version: Long,
        categoryId: String?,
        amount: BigDecimal?,
        dueOn: LocalDate?,
    ): PlannedIncome = execute {
        val updated = api.updatePlannedIncome(
            authorization = authorization,
            incomeId = id,
            request = UpdatePlannedIncomeRequestDto(
                version = version,
                categoryId = categoryId,
                amount = amount?.toPlainString(),
                dueOn = dueOn?.toString(),
            ),
        ).requireBody(expectedCode = 200).toDomain()

        if (updated.id != id || updated.version != version + 1) {
            throw PlannedIncomesException.InvalidResponse
        }
        updated
    }

    override suspend fun deletePlannedIncome(id: String, version: Long): Unit = execute {
        api.deletePlannedIncome(
            authorization = authorization,
            incomeId = id,
            version = version,
        ).requireStatus(expectedCode = 204)
    }

    private suspend fun <T> execute(block: suspend RequestContext.() -> T): T {
        if (apiToken.isBlank()) throw PlannedIncomesException.MissingApiToken

        return try {
            RequestContext("Bearer $apiToken").block()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: HttpException) {
            throw when (exception.code()) {
                400, 422 -> PlannedIncomesException.InvalidRequest
                401 -> PlannedIncomesException.Unauthorized
                403 -> PlannedIncomesException.Forbidden
                404 -> PlannedIncomesException.NotFound
                409 -> PlannedIncomesException.Conflict
                else -> PlannedIncomesException.Unknown
            }
        } catch (exception: IOException) {
            throw PlannedIncomesException.Network
        } catch (exception: NumberFormatException) {
            throw PlannedIncomesException.InvalidResponse
        } catch (exception: IllegalArgumentException) {
            throw PlannedIncomesException.InvalidResponse
        } catch (exception: DateTimeException) {
            throw PlannedIncomesException.InvalidResponse
        } catch (exception: SerializationException) {
            throw PlannedIncomesException.InvalidResponse
        }
    }

    private data class RequestContext(val authorization: String)
}

private fun <T> Response<T>.requireBody(expectedCode: Int): T {
    requireStatus(expectedCode)
    return body() ?: throw PlannedIncomesException.InvalidResponse
}

private fun Response<*>.requireStatus(expectedCode: Int) {
    if (!isSuccessful) throw HttpException(this)
    if (code() != expectedCode) throw PlannedIncomesException.InvalidResponse
}

private fun PlannedIncomeDto.toDomain(): PlannedIncome = PlannedIncome(
    id = id,
    categoryId = category.id,
    categoryName = category.name,
    plannedAmount = plannedAmount.amount.toBigDecimal(),
    currency = plannedAmount.currency,
    dueOn = LocalDate.parse(dueOn),
    overdue = overdue,
    status = when (status) {
        "SCHEDULED" -> PlannedIncomeStatus.Scheduled
        else -> PlannedIncomeStatus.Unknown
    },
    version = version,
    createdAt = Instant.parse(createdAt),
    modifiedAt = Instant.parse(modifiedAt),
)
