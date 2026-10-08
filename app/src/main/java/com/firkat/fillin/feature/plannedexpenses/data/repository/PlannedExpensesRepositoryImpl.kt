package com.firkat.fillin.feature.plannedexpenses.data.repository

import com.firkat.fillin.feature.plannedexpenses.data.remote.PlannedExpensesApi
import com.firkat.fillin.feature.plannedexpenses.data.remote.dto.ClosePlannedExpenseRequestDto
import com.firkat.fillin.feature.plannedexpenses.data.remote.dto.CreatePlannedExpenseRuleRequestDto
import com.firkat.fillin.feature.plannedexpenses.data.remote.dto.PlannedExpenseDto
import com.firkat.fillin.feature.plannedexpenses.data.remote.dto.PlannedExpenseRuleDto
import com.firkat.fillin.feature.plannedexpenses.data.remote.dto.UpdatePlannedExpenseRuleRequestDto
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpense
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpenseFrequency
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpenseRule
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpenses
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpenseStatus
import com.firkat.fillin.feature.plannedexpenses.domain.repository.PlannedExpensesException
import com.firkat.fillin.feature.plannedexpenses.domain.repository.PlannedExpensesRepository
import java.io.IOException
import java.math.BigDecimal
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import retrofit2.Response

class PlannedExpensesRepositoryImpl(
    private val api: PlannedExpensesApi,
    private val apiToken: String,
) : PlannedExpensesRepository {
    override suspend fun getPlannedExpenses(): PlannedExpenses = execute {
        val response = api.getPlannedExpenses(
            authorization = authorization,
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
    }

    override suspend fun createRule(
        categoryId: String,
        amount: BigDecimal,
        frequency: PlannedExpenseFrequency,
        scheduleValue: Int,
        startsOn: LocalDate,
    ): PlannedExpenseRule = execute {
        api.createRule(
            authorization = authorization,
            request = CreatePlannedExpenseRuleRequestDto(
                categoryId = categoryId,
                amount = amount.toPlainString(),
                frequency = frequency.apiValue,
                scheduleValue = scheduleValue,
                startsOn = startsOn.toString(),
            ),
        ).requireBody(expectedCode = 201).toDomain()
    }

    override suspend fun updateRule(
        id: String,
        version: Long,
        amount: BigDecimal,
    ): PlannedExpenseRule = execute {
        api.updateRule(
            authorization = authorization,
            ruleId = id,
            request = UpdatePlannedExpenseRuleRequestDto(
                version = version,
                amount = amount.toPlainString(),
            ),
        ).requireBody(expectedCode = 200).toDomain()
    }

    override suspend fun deleteRule(id: String, version: Long): Unit = execute {
        api.deleteRule(
            authorization = authorization,
            ruleId = id,
            version = version,
        ).requireStatus(expectedCode = 204)
    }

    override suspend fun closeExpense(id: String, version: Long): Unit = execute {
        val closedExpense = api.closeExpense(
            authorization = authorization,
            expenseId = id,
            request = ClosePlannedExpenseRequestDto(version = version),
        ).requireBody(expectedCode = 200).toDomain()

        if (
            closedExpense.id != id ||
            closedExpense.status != PlannedExpenseStatus.Paid ||
            closedExpense.remainingAmount.compareTo(BigDecimal.ZERO) != 0
        ) {
            throw PlannedExpensesException.InvalidResponse
        }
    }

    private suspend fun <T> execute(block: suspend RequestContext.() -> T): T {
        if (apiToken.isBlank()) throw PlannedExpensesException.MissingApiToken

        return try {
            RequestContext("Bearer $apiToken").block()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: HttpException) {
            throw when (exception.code()) {
                400, 422 -> PlannedExpensesException.InvalidRequest
                401 -> PlannedExpensesException.Unauthorized
                403 -> PlannedExpensesException.Forbidden
                404 -> PlannedExpensesException.NotFound
                409 -> PlannedExpensesException.Conflict
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

    private data class RequestContext(val authorization: String)
}

private val PlannedExpenseFrequency.apiValue: String
    get() = when (this) {
        PlannedExpenseFrequency.Monthly -> "MONTHLY"
        PlannedExpenseFrequency.Weekly -> "WEEKLY"
    }

private fun PlannedExpenseRuleDto.toDomain(): PlannedExpenseRule = PlannedExpenseRule(
    id = id,
    version = version,
)

private fun <T> Response<T>.requireBody(expectedCode: Int): T {
    requireStatus(expectedCode)
    return body() ?: throw PlannedExpensesException.InvalidResponse
}

private fun Response<*>.requireStatus(expectedCode: Int) {
    if (!isSuccessful) throw HttpException(this)
    if (code() != expectedCode) throw PlannedExpensesException.InvalidResponse
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
            "PAID" -> PlannedExpenseStatus.Paid
            else -> PlannedExpenseStatus.Unknown
        },
        version = version,
        createdAt = Instant.parse(createdAt),
        modifiedAt = Instant.parse(modifiedAt),
        ruleId = rule?.id ?: id,
        ruleVersion = rule?.version ?: version,
    )
}
