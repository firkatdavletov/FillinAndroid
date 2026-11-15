package com.firkat.fillin.feature.plannedoperations.data.repository

import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpense
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpenseStatus
import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncome
import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncomeStatus
import com.firkat.fillin.feature.plannedoperations.data.remote.PlannedOperationsApi
import com.firkat.fillin.feature.plannedoperations.data.remote.dto.PlannedOperationDto
import com.firkat.fillin.feature.plannedoperations.data.remote.dto.PlannedOperationMoneyDto
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperation
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationTotals
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperations
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationsFilter
import com.firkat.fillin.feature.plannedoperations.domain.repository.PlannedOperationsException
import com.firkat.fillin.feature.plannedoperations.domain.repository.PlannedOperationsRepository
import java.io.IOException
import java.math.BigDecimal
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

class PlannedOperationsRepositoryImpl(private val api: PlannedOperationsApi, private val apiToken: String) :
    PlannedOperationsRepository {
    override suspend fun getPlannedOperations(
        filter: PlannedOperationsFilter
    ): PlannedOperations {
        if (apiToken.isBlank()) throw PlannedOperationsException.MissingApiToken
        return try {
            val response = api.getPlannedOperations(
                authorization = "Bearer $apiToken",
                type = filter.type?.apiValue,
                period = filter.period.apiValue,
                dateFrom = filter.dateFrom?.toString(),
                dateTo = filter.dateTo?.toString()
            )
            val from = LocalDate.parse(response.dateFrom)
            val to = LocalDate.parse(response.dateTo)
            require(from <= to)
            if (filter.dateFrom != null) require(filter.dateFrom == from && filter.dateTo == to)
            val items = response.items.map(PlannedOperationDto::toDomain)
            require(items.all { it.dueOn in from..to && (filter.type == null || it.type == filter.type) })
            PlannedOperations(
                asOf = LocalDate.parse(response.asOf),
                dateFrom = from,
                dateTo = to,
                items = items.sortedWith(compareBy({ it.dueOn }, { it.id })),
                totals = PlannedOperationTotals(
                    expenses = response.totals.expenses.amountValue(),
                    incomes = response.totals.incomes.amountValue()
                )
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: HttpException) {
            throw when (exception.code()) {
                400, 422 -> PlannedOperationsException.InvalidRequest
                401 -> PlannedOperationsException.Unauthorized
                403 -> PlannedOperationsException.Forbidden
                else -> PlannedOperationsException.Unknown
            }
        } catch (exception: IOException) {
            throw PlannedOperationsException.Network
        } catch (exception: IllegalArgumentException) {
            throw PlannedOperationsException.InvalidResponse
        } catch (exception: DateTimeException) {
            throw PlannedOperationsException.InvalidResponse
        } catch (exception: SerializationException) {
            throw PlannedOperationsException.InvalidResponse
        }
    }
}

private fun PlannedOperationMoneyDto.amountValue(): BigDecimal {
    require(currency == "KZT")
    return amount.toBigDecimal().also { require(it.signum() >= 0 && it.scale() <= 2) }
}

private fun PlannedOperationDto.toDomain(): PlannedOperation {
    require(status == "SCHEDULED" && version >= 0)
    val planned = plannedAmount.amountValue().also { require(it > BigDecimal.ZERO) }
    val paid = paidAmount.amountValue()
    val remaining = remainingAmount.amountValue()
    val dueDate = LocalDate.parse(dueOn)
    val created = Instant.parse(createdAt)
    val modified = Instant.parse(modifiedAt)
    return when (type) {
        "EXPENSE" -> {
            val rule = requireNotNull(rule)
            require(rule.version >= 0 && remaining > BigDecimal.ZERO)
            PlannedOperation.Expense(
                PlannedExpense(
                    id = id,
                    categoryId = category.id,
                    categoryName = category.name,
                    plannedAmount = planned,
                    paidAmount = paid,
                    remainingAmount = remaining,
                    currency = plannedAmount.currency,
                    dueOn = dueDate,
                    overdue = overdue,
                    status = PlannedExpenseStatus.Scheduled,
                    version = version,
                    createdAt = created,
                    modifiedAt = modified,
                    ruleId = rule.id,
                    ruleVersion = rule.version
                )
            )
        }
        "INCOME" -> {
            require(paid.signum() == 0 && remaining.compareTo(planned) == 0)
            PlannedOperation.Income(
                PlannedIncome(
                    id = id,
                    categoryId = category.id,
                    categoryName = category.name,
                    plannedAmount = planned,
                    currency = plannedAmount.currency,
                    dueOn = dueDate,
                    overdue = overdue,
                    status = PlannedIncomeStatus.Scheduled,
                    version = version,
                    createdAt = created,
                    modifiedAt = modified
                )
            )
        }
        else -> throw PlannedOperationsException.InvalidResponse
    }
}
