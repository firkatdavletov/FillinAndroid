package com.firkat.fillin.feature.plannedincomes.data.repository

import com.firkat.fillin.feature.plannedincomes.data.remote.PlannedIncomesApi
import com.firkat.fillin.feature.plannedincomes.data.remote.dto.PlannedIncomeDto
import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncome
import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncomes
import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncomeStatus
import com.firkat.fillin.feature.plannedincomes.domain.repository.PlannedIncomesException
import com.firkat.fillin.feature.plannedincomes.domain.repository.PlannedIncomesRepository
import java.io.IOException
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

class PlannedIncomesRepositoryImpl(
    private val api: PlannedIncomesApi,
    private val apiToken: String,
) : PlannedIncomesRepository {
    override suspend fun getPlannedIncomes(): PlannedIncomes {
        if (apiToken.isBlank()) throw PlannedIncomesException.MissingApiToken

        return try {
            val response = api.getPlannedIncomes(
                authorization = "Bearer $apiToken",
            )
            PlannedIncomes(
                asOf = LocalDate.parse(response.asOf),
                items = response.items
                    .map(PlannedIncomeDto::toDomain)
                    .sortedBy(PlannedIncome::dueOn),
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: HttpException) {
            throw when (exception.code()) {
                401 -> PlannedIncomesException.Unauthorized
                403 -> PlannedIncomesException.Forbidden
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
