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
    private val currentDate: () -> LocalDate = store::currentDate,
) : PlannedIncomesRepository {
    override suspend fun saveDetails(income: PlannedIncome?, categoryId: String, amount: BigDecimal, dueOn: LocalDate,
        name: String, frequency: com.firkat.fillin.feature.dailylimit.domain.IncomeFrequency, active: Boolean,
        confirmRemainder: Boolean): PlannedIncome = execute {
        require(name.isNotBlank() && name.length <= 160 && amount.signum() > 0 && amount.scale() <= 2)
        val source = store.snapshot()
        val targetId = if (confirmRemainder) requireNotNull(income).id else income?.ruleId ?: income?.id ?: java.util.UUID.randomUUID().toString()
        val target = source.incomeRules[targetId] ?: source.incomes[targetId]
        val effectiveDue = if (!confirmRemainder && income?.ruleId != null && dueOn == income.dueOn) LocalDate.parse(target?.dueOn) else dueOn
        val isRule = !confirmRemainder && (income?.ruleId != null || frequency != com.firkat.fillin.feature.dailylimit.domain.IncomeFrequency.ONCE)
        val type = if (confirmRemainder) "CONFIRM_REMAINDER_INCOME" else (if (income == null) "CREATE_INCOME" else "UPDATE_INCOME") + if (isRule) "_RULE" else ""
        val id = store.enqueue(type,
            targetId, target?.version) {
            if (confirmRemainder) it.copy(entityId = requireNotNull(income).id, amount = null, ruleId = income.ruleId, scheduledOn = income.scheduledOn.toString(),
                remainderAmount = amount.toPlainString(), remainderDueOn = dueOn.toString(), remainderReceived = income.receivedAmount.toPlainString())
            else it.copy(categoryId = categoryId, amount = amount.toPlainString(), dueOn = effectiveDue.toString(),
                incomeName = name.trim(), incomeFrequency = frequency.name, incomeActive = active)
        }
        val p = store.snapshot().plans(com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationsFilter(
            period = com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationPeriod.Custom,
            dateFrom = LocalDate.of(1,1,1), dateTo = currentDate().plusYears(5)))
        p.items.filterIsInstance<com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperation.Income>()
            .firstOrNull { it.id == id || it.income.ruleId == id }?.income ?: income ?: error("Missing income")
    }

    override suspend fun getPlannedIncomes(): PlannedIncomes =
        execute {
            val today = currentDate()
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
        execute {
            val source = store.snapshot()
            val item = source.incomes[id]
            val targetId = item?.ruleId ?: id
            store.enqueue(if (targetId in source.incomeRules) "DELETE_INCOME_RULE" else "DELETE_INCOME", targetId, (source.incomeRules[targetId] ?: source.incomes[targetId])?.version ?: version)
        }
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
            due < currentDate(),
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
