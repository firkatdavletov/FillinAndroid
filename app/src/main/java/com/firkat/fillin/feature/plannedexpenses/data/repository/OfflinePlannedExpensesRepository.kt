package com.firkat.fillin.feature.plannedexpenses.data.repository

import androidx.room.CoroutinesRoom.Companion.execute
import com.firkat.fillin.feature.plannedexpenses.domain.model.*
import com.firkat.fillin.feature.plannedexpenses.domain.repository.*
import com.firkat.fillin.feature.plannedoperations.domain.model.*
import com.firkat.fillin.feature.sync.data.BudgetLocalFailure
import com.firkat.fillin.feature.sync.data.BudgetStore
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

class OfflinePlannedExpensesRepository(
    private val store: BudgetStore,
) : PlannedExpensesRepository {
    override suspend fun getPlannedExpenses(): PlannedExpenses =
        execute {
            val today = LocalDate.now(ZoneId.of("Asia/Almaty"))
            val plans =
                store.snapshot().plans(
                    PlannedOperationsFilter(
                        period = PlannedOperationPeriod.Custom,
                        dateFrom = LocalDate.of(1, 1, 1),
                        dateTo =
                            today
                                .plusMonths(2)
                                .withDayOfMonth(1)
                                .plusMonths(1)
                                .minusDays(1),
                    ),
                )
            PlannedExpenses(
                plans.asOf,
                plans.dateTo,
                plans.items.filterIsInstance<PlannedOperation.Expense>().map {
                    it.expense
                },
            )
        }

    override suspend fun createRule(
        categoryId: String,
        amount: BigDecimal,
        frequency: PlannedExpenseFrequency,
        scheduleValue: Int?,
        startsOn: LocalDate,
    ): PlannedExpenseRule =
        execute {
            val id =
                store.enqueue("CREATE_RULE") {
                    it.copy(
                        categoryId = categoryId,
                        amount = amount.toPlainString(),
                        frequency = when (frequency) {
                            PlannedExpenseFrequency.Once -> "ONCE"
                            PlannedExpenseFrequency.Monthly -> "MONTHLY"
                            PlannedExpenseFrequency.Weekly -> "WEEKLY"
                        },
                        scheduleValue = scheduleValue,
                        startsOn = startsOn.toString(),
                    )
                }
            PlannedExpenseRule(id, 0)
        }

    override suspend fun updateRule(
        id: String,
        version: Long,
        amount: BigDecimal,
    ): PlannedExpenseRule =
        execute {
            store.enqueue("UPDATE_RULE", id, version) { it.copy(amount = amount.toPlainString()) }
            PlannedExpenseRule(id, version + 1)
        }

    override suspend fun deleteRule(
        id: String,
        version: Long,
    ) {
        execute { store.enqueue("DELETE_RULE", id, version) }
    }

    override suspend fun closeExpense(
        id: String,
        version: Long,
    ) {
        execute { store.enqueue("CLOSE_EXPENSE", id, version) }
    }

    private suspend fun <T> execute(block: suspend () -> T): T =
        try {
            block()
        } catch (e: BudgetLocalFailure) {
            throw when (e.code) {
                "forbidden" -> PlannedExpensesException.Forbidden
                "conflict" -> PlannedExpensesException.Conflict
                "not_found" -> PlannedExpensesException.NotFound
                else -> PlannedExpensesException.InvalidRequest
            }
        } catch (e: IllegalArgumentException) {
            throw PlannedExpensesException.InvalidRequest
        }
}
