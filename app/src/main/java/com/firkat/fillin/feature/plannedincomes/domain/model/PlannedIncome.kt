package com.firkat.fillin.feature.plannedincomes.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class PlannedIncome(
    val id: String,
    val categoryId: String,
    val categoryName: String,
    val plannedAmount: BigDecimal,
    val currency: String,
    val dueOn: LocalDate,
    val overdue: Boolean,
    val status: PlannedIncomeStatus,
    val version: Long,
    val createdAt: Instant,
    val modifiedAt: Instant,
    val name: String = categoryName,
    val frequency: com.firkat.fillin.feature.dailylimit.domain.IncomeFrequency = com.firkat.fillin.feature.dailylimit.domain.IncomeFrequency.ONCE,
    val active: Boolean = true,
    val ruleId: String? = null,
    val scheduledOn: LocalDate = dueOn,
    val remainderConfirmed: Boolean = false,
    val receivedAmount: BigDecimal = BigDecimal.ZERO,
    val remainingAmount: BigDecimal = (plannedAmount - receivedAmount).max(BigDecimal.ZERO),
)

enum class PlannedIncomeStatus {
    Scheduled,
    Unknown,
}

data class PlannedIncomes(
    val asOf: LocalDate,
    val items: List<PlannedIncome>,
)
