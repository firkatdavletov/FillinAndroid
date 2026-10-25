package com.firkat.fillin.feature.plannedoperations.domain.model

import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpense
import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncome
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

@Serializable
enum class PlannedOperationType(
    val apiValue: String,
) {
    Expense("EXPENSE"),
    Income("INCOME"),
}

enum class PlannedOperationPeriod(
    val apiValue: String,
) {
    CurrentMonth("CUSTOM"),
    EndOfMonth("END_OF_MONTH"),
    EndOfYear("END_OF_YEAR"),
    Custom("CUSTOM"),
}

data class PlannedOperationsFilter(
    val type: PlannedOperationType? = null,
    val period: PlannedOperationPeriod = PlannedOperationPeriod.EndOfMonth,
    val dateFrom: LocalDate? = null,
    val dateTo: LocalDate? = null,
) {
    init {
        if (period == PlannedOperationPeriod.Custom || period == PlannedOperationPeriod.CurrentMonth) {
            require(dateFrom != null && dateTo != null && dateFrom <= dateTo)
            require(dateFrom.year >= 1)
            if (period == PlannedOperationPeriod.CurrentMonth) {
                require(dateFrom.dayOfMonth == 1 && dateTo == YearMonth.from(dateFrom).atEndOfMonth())
            }
        } else {
            require(dateFrom == null && dateTo == null)
        }
    }

    companion object {
        fun currentMonth(today: LocalDate) =
            PlannedOperationsFilter(
                period = PlannedOperationPeriod.CurrentMonth,
                dateFrom = today.withDayOfMonth(1),
                dateTo = YearMonth.from(today).atEndOfMonth(),
            )
    }
}

sealed interface PlannedOperation {
    val id: String
    val dueOn: LocalDate
    val type: PlannedOperationType
    val key: String get() = "${type.apiValue}:$id"

    data class Expense(
        val expense: PlannedExpense,
    ) : PlannedOperation {
        override val key: String get() = "EXPENSE:${expense.ruleId}:${expense.dueOn}"
        override val id: String get() = expense.id
        override val dueOn: LocalDate get() = expense.dueOn
        override val type = PlannedOperationType.Expense
    }

    data class Income(
        val income: PlannedIncome,
    ) : PlannedOperation {
        override val id: String get() = income.id
        override val dueOn: LocalDate get() = income.dueOn
        override val type = PlannedOperationType.Income
    }
}

data class PlannedOperationTotals(
    val expenses: BigDecimal = BigDecimal.ZERO,
    val incomes: BigDecimal = BigDecimal.ZERO,
)

data class PlannedOperations(
    val asOf: LocalDate,
    val dateFrom: LocalDate,
    val dateTo: LocalDate,
    val items: List<PlannedOperation>,
    val totals: PlannedOperationTotals = PlannedOperationTotals(),
)
