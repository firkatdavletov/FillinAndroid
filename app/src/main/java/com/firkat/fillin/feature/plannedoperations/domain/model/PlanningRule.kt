package com.firkat.fillin.feature.plannedoperations.domain.model

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

enum class PlanningFrequency { Once, Weekly, Biweekly, Monthly }

data class PlanningRule(
    val id: String,
    val version: Long,
    val type: PlannedOperationType,
    val categoryId: String,
    val categoryName: String,
    val iconCode: String,
    val name: String,
    val amount: BigDecimal,
    val currency: String = "KZT",
    val frequency: PlanningFrequency,
    val startsOn: LocalDate,
    val scheduleValue: Int? = null,
    val isIncomeRule: Boolean = false
) {
    val key: String get() = "${type.name}:$id"

    fun nextDate(
        today: LocalDate
    ): LocalDate {
        if (frequency == PlanningFrequency.Once) return startsOn
        val from = maxOf(today, startsOn)
        return when (frequency) {
            PlanningFrequency.Monthly -> {
                val day = scheduleValue ?: startsOn.dayOfMonth
                var month = YearMonth.from(from)
                var date = month.atDay(minOf(day, month.lengthOfMonth()))
                if (date < from) {
                    month = month.plusMonths(1)
                    date = month.atDay(minOf(day, month.lengthOfMonth()))
                }
                date
            }

            PlanningFrequency.Weekly -> from.plusDays(
                ((scheduleValue ?: startsOn.dayOfWeek.value) - from.dayOfWeek.value + 7L) % 7
            )

            PlanningFrequency.Biweekly -> {
                val days = java.time.temporal.ChronoUnit.DAYS.between(startsOn, from)
                startsOn.plusDays(((days + 13) / 14) * 14)
            }

            PlanningFrequency.Once -> startsOn
        }
    }
}
