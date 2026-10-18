package com.firkat.fillin.feature.operationhistory.ui

import com.firkat.fillin.feature.operationhistory.domain.model.Operation
import com.firkat.fillin.feature.operationhistory.domain.model.OperationStatus
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.feature.operationhistory.domain.model.OperationSyncStatus
import java.math.BigDecimal
import java.time.LocalDate

internal data class OperationDayGroup(
    val date: LocalDate,
    val operations: List<Operation>,
    val totals: List<OperationDayTotal>,
)

internal data class OperationDayTotal(
    val amount: BigDecimal,
    val currency: String,
    val type: OperationType = OperationType.Expense,
)

internal fun groupOperationsByDate(operations: List<Operation>): List<OperationDayGroup> =
    operations
        .groupBy(Operation::occurredOn)
        .map { (date, operationsForDate) ->
            val postedOperations = operationsForDate.filter {
                it.status == OperationStatus.Posted && it.syncStatus != OperationSyncStatus.Rejected
            }
            val totals = postedOperations
                .groupBy { it.currency to it.type }
                .map { (key, operationsForCurrency) ->
                    OperationDayTotal(
                        amount = operationsForCurrency.fold(BigDecimal.ZERO) { total, purchase ->
                            total + purchase.amount
                        },
                        currency = key.first,
                        type = key.second,
                    )
                }
                .ifEmpty {
                    listOf(
                        OperationDayTotal(
                            amount = BigDecimal.ZERO,
                            currency = operationsForDate.first().currency,
                            type = operationsForDate.first().type,
                        ),
                    )
                }

            OperationDayGroup(
                date = date,
                operations = operationsForDate,
                totals = totals,
            )
        }
