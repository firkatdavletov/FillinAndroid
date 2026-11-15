package com.firkat.fillin.feature.operationhistory.domain.usecase

import com.firkat.fillin.feature.operationhistory.domain.repository.PlanLinkOperation
import com.firkat.fillin.feature.operationhistory.domain.repository.PlanSuggestion
import com.firkat.fillin.feature.operationhistory.domain.repository.SuggestedPlan
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** Ranks payable occurrences from one budget snapshot. Manual eligibility is supplied by the caller. */
class OperationPlanSuggestionPolicy(private val config: Config = Config()) {
    data class Config(
        val windowDays: Long = 7,
        val amountTolerance: BigDecimal = BigDecimal("0.20"),
        val datePoints: Int = 60,
        val amountPoints: Int = 40,
        val choiceThreshold: Int = 60,
        val recommendationThreshold: Int = 80,
        val minimumLead: Int = 15
    ) {
        init {
            require(windowDays > 0 && amountTolerance.signum() > 0)
        }
    }

    fun suggest(
        operation: PlanLinkOperation,
        categoryDictionaryKey: String?,
        isLinked: Boolean,
        alreadyProcessed: Boolean,
        candidates: List<SuggestedPlan>
    ): PlanSuggestion {
        val amount = operation.netAmount.toBigDecimal()
        val isSuppressed = isLinked || alreadyProcessed || categoryDictionaryKey in GENERIC_CATEGORIES
        if (isSuppressed || amount.signum() <= 0) {
            return PlanSuggestion.None
        }
        val date = LocalDate.parse(operation.occurredOn)
        val ranked = candidates.mapNotNull { rank(it, operation, date, amount) }
            .sortedWith(
                compareByDescending<Ranked> { it.score }.thenBy { it.days }.thenBy { it.deviation }
                    .thenBy { it.date }.thenBy { it.plan.target.stableKey }
            )
        return present(ranked)
    }

    private fun present(
        ranked: List<Ranked>
    ): PlanSuggestion {
        val minimum = Ratio(config.choiceThreshold.toBigDecimal())
        val first = ranked.firstOrNull()?.takeIf { it.score >= minimum }
        val second = ranked.getOrNull(1)
        return when {
            first == null -> PlanSuggestion.None
            isRecommended(first, second) -> PlanSuggestion.Recommended(first.plan)
            else -> {
                val choices = ranked.takeWhile { it.score >= minimum }
                PlanSuggestion.Choice(choices.take(MAX_CHOICES).map { it.plan }, choices.size > MAX_CHOICES)
            }
        }
    }

    private fun isRecommended(
        first: Ranked,
        second: Ranked?
    ): Boolean = first.score >= Ratio(config.recommendationThreshold.toBigDecimal()) &&
        (second == null || first.score >= second.score + config.minimumLead)

    private fun rank(
        plan: SuggestedPlan,
        operation: PlanLinkOperation,
        date: LocalDate,
        amount: BigDecimal
    ): Ranked? {
        val sameContext = plan.categoryId == operation.categoryId &&
            plan.currency == operation.currency &&
            plan.target.type == operation.type
        val dueOn = LocalDate.parse(plan.target.dueOn)
        val days = abs(ChronoUnit.DAYS.between(date, dueOn))
        val remaining = plan.remainingAmount.toBigDecimal()
        val difference = (amount - remaining).abs()
        val maxDifference = remaining * config.amountTolerance
        val closeEnough = days <= config.windowDays && difference <= maxDifference
        if (!sameContext || !closeEnough || remaining.signum() <= 0) return null

        val window = config.windowDays.toBigDecimal()
        val dateScore = (config.windowDays - days).toBigDecimal() * config.datePoints.toBigDecimal()
        val amountScore = (maxDifference - difference) * config.amountPoints.toBigDecimal()
        return Ranked(
            plan,
            days,
            dueOn,
            Ratio(difference, remaining),
            Ratio(dateScore * maxDifference + amountScore * window, window * maxDifference)
        )
    }

    private data class Ranked(
        val plan: SuggestedPlan,
        val days: Long,
        val date: LocalDate,
        val deviation: Ratio,
        val score: Ratio
    )

    // Cross-multiplication keeps repeating fractions exact at score and tie boundaries.
    private data class Ratio(val numerator: BigDecimal, val denominator: BigDecimal = BigDecimal.ONE) :
        Comparable<Ratio> {
        override fun compareTo(
            other: Ratio
        ): Int = (numerator * other.denominator).compareTo(other.numerator * denominator)

        operator fun plus(
            points: Int
        ): Ratio = copy(numerator = numerator + points.toBigDecimal() * denominator)
    }

    companion object {
        private const val MAX_CHOICES = 3
        private val GENERIC_CATEGORIES = setOf("OTHER", "OTHER_INCOME")
    }
}
