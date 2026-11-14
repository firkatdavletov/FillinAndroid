package com.firkat.fillin.feature.dailylimit.domain

import com.firkat.fillin.feature.dashboard.domain.model.Money
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

enum class IncomeFrequency { ONCE, WEEKLY, BIWEEKLY, MONTHLY }
enum class ForecastStatus { OK, DEPENDS_ON_INCOME, DEFICIT, INCOMPLETE_DATA }
enum class ForecastWarning { NO_INCOME, OVERDUE_INCOME, UNCONFIRMED_REMAINDER }
data class ForecastEvent(
    val id: String,
    val name: String,
    val dueOn: LocalDate,
    val amount: Money,
    val income: Boolean
) {
    val availableOn: LocalDate get() = if (income) dueOn.plusDays(1) else dueOn
}
data class DailyLimitInput(
    val balance: Money,
    val todayVariableNet: Money,
    val events: List<ForecastEvent>,
    val today: LocalDate,
    val reserve: Money = Money(BigDecimal.ZERO, balance.currency),
    val warnings: Set<ForecastWarning> = emptySet()
)
data class DailyBalance(
    val date: LocalDate,
    val events: List<ForecastEvent>,
    val withoutDailySpending: Money,
    val afterDailySpending: Money
)
data class DailyLimitResult(
    val dailyTarget: Money,
    val remainingToday: Money,
    val spentToday: Money,
    val horizonStart: LocalDate,
    val horizonEnd: LocalDate,
    val status: ForecastStatus,
    val firstDeficitDate: LocalDate?,
    val deficitAmount: Money?,
    val projectedDailyBalances: List<DailyBalance>,
    val dependsOnPlannedIncome: Boolean,
    val limitingDate: LocalDate,
    val overspentToday: Money,
    val nextDaysLimit: Money,
    val warnings: Set<ForecastWarning>,
    val currentBalance: Money
) {
    val plannedIncome: Money =
        Money(
            projectedDailyBalances.flatMap {
                it.events
            }.filter { it.income }.sumOf { it.amount.amount }.setScale(2),
            dailyTarget.currency
        )
    val plannedPayments: Money =
        Money(
            projectedDailyBalances.flatMap {
                it.events
            }.filterNot { it.income }.sumOf { it.amount.amount }.setScale(2),
            dailyTarget.currency
        )
    val availableBeforeDailySpending: Money = projectedDailyBalances.last().withoutDailySpending
    val horizonDays: Int get() = ChronoUnit.DAYS.between(horizonStart, horizonEnd).toInt() + 1
}

/** Always derive monthly dates from the anchor, never from the clamped February date. */
object ForecastEventGenerator {
    fun dates(
        anchor: LocalDate,
        frequency: IncomeFrequency,
        through: LocalDate
    ): Sequence<LocalDate> = sequence {
        var index = 0L
        while (true) {
            val date = when (frequency) {
                IncomeFrequency.ONCE -> if (index == 0L) anchor else return@sequence
                IncomeFrequency.WEEKLY -> anchor.plusWeeks(index)
                IncomeFrequency.BIWEEKLY -> anchor.plusWeeks(index * 2)
                IncomeFrequency.MONTHLY -> YearMonth.from(anchor).plusMonths(index).let {
                    it.atDay(minOf(anchor.dayOfMonth, it.lengthOfMonth()))
                }
            }
            if (date > through) return@sequence
            yield(date)
            index++
        }
    }
}

internal fun calendar(
    input: DailyLimitInput,
    end: LocalDate
): List<DailyBalance> {
    require(
        input.balance.currency == input.todayVariableNet.currency && input.reserve.currency == input.balance.currency
    )
    require(input.events.all { it.amount.currency == input.balance.currency && it.amount.amount.signum() >= 0 })
    val events = input.events.filter { !it.income || it.dueOn >= input.today }
        .groupBy { if (it.income) it.availableOn else maxOf(input.today, it.dueOn) }
    var balance = input.balance.amount
    return (0..ChronoUnit.DAYS.between(input.today, end).toInt()).map { offset ->
        val date = input.today.plusDays(offset.toLong())
        val dayEvents = events[date].orEmpty().sortedBy { it.id }
        balance += dayEvents.sumOf { if (it.income) it.amount.amount else -it.amount.amount }
        val money = Money(balance, input.balance.currency)
        DailyBalance(date, dayEvents, money, money)
    }
}

internal fun horizonEnd(
    input: DailyLimitInput
): LocalDate {
    val eligible = input.events.filter { it.income && it.dueOn >= input.today && it.amount.amount.signum() > 0 }
    val boundary = eligible.map {
        it.dueOn
    }.filter { it in input.today.plusDays(30)..input.today.plusDays(60) }.minOrNull()
    val end = boundary?.minusDays(1) ?: input.today.plusDays(59)
    return end
}

class DailyLimitCalculator {
    fun calculate(
        input: DailyLimitInput
    ): DailyLimitResult = calculate(input, calendar(input, horizonEnd(input)))

    internal fun calculate(
        input: DailyLimitInput,
        days: List<DailyBalance>
    ): DailyLimitResult {
        val eligible = input.events.filter { it.income && it.dueOn >= input.today && it.amount.amount.signum() > 0 }
        val end = days.last().date
        fun money(
            value: BigDecimal
        ) = Money(value.setScale(2), input.balance.currency)
        fun floor(
            value: BigDecimal
        ) = value.max(BigDecimal.ZERO).setScale(0, RoundingMode.FLOOR)
        val constraints = days.mapIndexed { index, day ->
            (day.withoutDailySpending.amount + input.todayVariableNet.amount - input.reserve.amount)
                .divide(BigDecimal(index + 1), 16, RoundingMode.FLOOR)
        }
        val index = constraints.indices.minBy { constraints[it] }
        val reference = floor(constraints[index])
        val deficit = days.firstOrNull { it.withoutDailySpending.amount < input.reserve.amount }
        val target = if (deficit == null) reference else BigDecimal.ZERO
        val spent = input.todayVariableNet.amount.max(BigDecimal.ZERO)
        val remaining = if (deficit == null) (target - spent).max(BigDecimal.ZERO) else BigDecimal.ZERO
        val next = floor(
            days.drop(1).mapIndexed { i, day ->
                (day.withoutDailySpending.amount - remaining - input.reserve.amount).divide(
                    BigDecimal(i + 1),
                    16,
                    RoundingMode.FLOOR
                )
            }.minOrNull() ?: BigDecimal.ZERO
        )
        val warnings = input.warnings + buildSet {
            if (eligible.isEmpty()) add(ForecastWarning.NO_INCOME)
            if (input.events.any { it.income && it.dueOn < input.today }) add(ForecastWarning.OVERDUE_INCOME)
        }
        val depends = days.any { day -> day.events.any { it.income } }
        return DailyLimitResult(
            money(target), money(remaining), money(spent), input.today, end,
            when {
                deficit != null -> ForecastStatus.DEFICIT
                warnings.isNotEmpty() -> ForecastStatus.INCOMPLETE_DATA
                depends -> ForecastStatus.DEPENDS_ON_INCOME
                else -> ForecastStatus.OK
            },
            deficit?.date, deficit?.let { money(input.reserve.amount - it.withoutDailySpending.amount) },
            days.mapIndexed { i, day ->
                day.copy(
                    afterDailySpending = money(
                        day.withoutDailySpending.amount - remaining - next * BigDecimal(i)
                    )
                )
            },
            depends, days[index].date,
            money(
                (spent - reference).max(BigDecimal.ZERO)
            ),
            money(next), warnings, input.balance
        )
    }
}
