package com.firkat.fillin.feature.plannedincomes.data.repository

import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncome
import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncomeStatus
import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncomes
import com.firkat.fillin.feature.plannedincomes.domain.repository.PlannedIncomesException
import com.firkat.fillin.feature.plannedincomes.domain.repository.PlannedIncomesRepository
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperation
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationPeriod
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationsFilter
import com.firkat.fillin.feature.sync.data.BudgetLocalFailure
import com.firkat.fillin.feature.sync.data.BudgetStore
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class OfflinePlannedIncomesRepository(
    private val store: BudgetStore,
) : PlannedIncomesRepository {
    override suspend fun getPlannedIncomes(): PlannedIncomes =
        execute {
            val today = LocalDate.now(ZoneId.of("Asia/Almaty"))
            val plans =
                store.snapshot().plans(
                    PlannedOperationsFilter(
                        period = PlannedOperationPeriod.Custom,
                        dateFrom = LocalDate.of(1, 1, 1),
                        dateTo = today.plusYears(5),
                    ),
                )
            PlannedIncomes(plans.asOf, plans.items.filterIsInstance<PlannedOperation.Income>().map { it.income })
        }

    override suspend fun createPlannedIncome(
        categoryId: String,
        amount: BigDecimal,
        dueOn: LocalDate,
    ): PlannedIncome =
        execute {
            val id =
                store.enqueue("CREATE_INCOME") {
                    it.copy(categoryId = categoryId, amount = amount.toPlainString(), dueOn = dueOn.toString())
                }
            income(id)
        }

    override suspend fun updatePlannedIncome(
        id: String,
        version: Long,
        categoryId: String?,
        amount: BigDecimal?,
        dueOn: LocalDate?,
    ): PlannedIncome =
        execute {
            store.enqueue("UPDATE_INCOME", id, version) {
                it.copy(categoryId = categoryId, amount = amount?.toPlainString(), dueOn = dueOn?.toString())
            }
            income(id)
        }

    override suspend fun deletePlannedIncome(
        id: String,
        version: Long,
    ) {
        execute { store.enqueue("DELETE_INCOME", id, version) }
    }

    private suspend fun income(id: String): PlannedIncome {
        val p = store.snapshot()
        val i = requireNotNull(p.incomes[id])
        val due = LocalDate.parse(i.dueOn)
        return PlannedIncome(
            id,
            requireNotNull(i.categoryId),
            p.categories[i.categoryId]?.name.orEmpty(),
            requireNotNull(i.amount).toBigDecimal(),
            "KZT",
            due,
            due < LocalDate.now(ZoneId.of("Asia/Almaty")),
            PlannedIncomeStatus.Scheduled,
            i.version ?: 0,
            Instant.parse(i.createdAt),
            Instant.parse(i.modifiedAt),
        )
    }

    private suspend fun <T> execute(block: suspend () -> T): T =
        try {
            block()
        } catch (e: BudgetLocalFailure) {
            throw when (e.code) {
                "forbidden" -> PlannedIncomesException.Forbidden
                "conflict" -> PlannedIncomesException.Conflict
                "not_found" -> PlannedIncomesException.NotFound
                else -> PlannedIncomesException.InvalidRequest
            }
        } catch (_: IllegalArgumentException) {
            throw PlannedIncomesException.InvalidRequest
        }
}
