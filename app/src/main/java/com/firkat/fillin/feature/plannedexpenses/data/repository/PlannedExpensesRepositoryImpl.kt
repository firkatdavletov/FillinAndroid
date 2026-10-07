package com.firkat.fillin.feature.plannedexpenses.data.repository

import com.firkat.fillin.feature.plannedexpenses.data.remote.PlannedExpensesApi
import com.firkat.fillin.feature.plannedexpenses.data.remote.dto.PlannedExpenseDto
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpense
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpenses
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpenseStatus
import com.firkat.fillin.feature.plannedexpenses.domain.repository.PlannedExpensesException
import com.firkat.fillin.feature.plannedexpenses.domain.repository.PlannedExpensesRepository
import java.io.IOException
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

class PlannedExpensesRepositoryImpl(
    private val api: PlannedExpensesApi,
    private val apiToken: String,
) : PlannedExpensesRepository {
    override suspend fun getPlannedExpenses(): PlannedExpenses {
        if (apiToken.isBlank()) throw PlannedExpensesException.MissingApiToken

        return try {
            val response = api.getPlannedExpenses(
                authorization = "Bearer $apiToken",
            )
            PlannedExpenses(
                asOf = LocalDate.parse(response.asOf),
                throughInclusive = LocalDate.parse(response.throughInclusive),
                items = response.items
                    .map(PlannedExpenseDto::toDomain)
                    .sortedWith(
                        compareByDescending<PlannedExpense> { it.overdue }
                            .thenBy(PlannedExpense::dueOn),
                    ),
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: HttpException) {
            throw when (exception.code()) {
                401 -> PlannedExpensesException.Unauthorized
                403 -> PlannedExpensesException.Forbidden
                else -> PlannedExpensesException.Unknown
            }
        } catch (exception: IOException) {
            throw PlannedExpensesException.Network
        } catch (exception: NumberFormatException) {
            throw PlannedExpensesException.InvalidResponse
        } catch (exception: IllegalArgumentException) {
            throw PlannedExpensesException.InvalidResponse
        } catch (exception: DateTimeException) {
            throw PlannedExpensesException.InvalidResponse
        } catch (exception: SerializationException) {
            throw PlannedExpensesException.InvalidResponse
        }
    }
}

private fun PlannedExpenseDto.toDomain(): PlannedExpense {
    require(
        plannedAmount.currency == paidAmount.currency &&
            plannedAmount.currency == remainingAmount.currency,
    ) { "All monetary values of a planned expense must use the same currency" }

    return PlannedExpense(
        id = id,
        categoryId = category.id,
        categoryName = category.name,
        plannedAmount = plannedAmount.amount.toBigDecimal(),
        paidAmount = paidAmount.amount.toBigDecimal(),
        remainingAmount = remainingAmount.amount.toBigDecimal(),
        currency = plannedAmount.currency,
        dueOn = LocalDate.parse(dueOn),
        overdue = overdue,
        status = when (status) {
            "SCHEDULED" -> PlannedExpenseStatus.Scheduled
            else -> PlannedExpenseStatus.Unknown
        },
        version = version,
        createdAt = Instant.parse(createdAt),
        modifiedAt = Instant.parse(modifiedAt),
    )
}
