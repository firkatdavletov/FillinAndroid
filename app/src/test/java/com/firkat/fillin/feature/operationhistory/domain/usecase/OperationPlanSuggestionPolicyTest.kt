package com.firkat.fillin.feature.operationhistory.domain.usecase

import com.firkat.fillin.feature.operationhistory.domain.repository.PlanLinkOperation
import com.firkat.fillin.feature.operationhistory.domain.repository.PlanLinkTarget
import com.firkat.fillin.feature.operationhistory.domain.repository.PlanSuggestion
import com.firkat.fillin.feature.operationhistory.domain.repository.SuggestedPlan
import org.junit.Assert.assertEquals
import org.junit.Test

class OperationPlanSuggestionPolicyTest {
    private val policy = OperationPlanSuggestionPolicy()
    private val operation = PlanLinkOperation(
        0, "EXPENSE", "category", "Связь", "COMMUNICATION", "RUB", "2026-11-15", "1250", "1250"
    )

    @Test fun `matches the examples and ranks against the remaining amount`() {
        val tomorrow = payment("2026-11-16")
        assertEquals(PlanSuggestion.Recommended(tomorrow), suggest("1250", tomorrow))
        val yesterday = payment("2026-11-14")
        assertEquals(PlanSuggestion.Choice(listOf(yesterday, tomorrow), false), suggest("1250", tomorrow, yesterday))
        val near = payment("2026-11-16", "5000")
        assertEquals(PlanSuggestion.Recommended(near), suggest("4850", near))
        assertEquals(PlanSuggestion.Recommended(tomorrow), suggest("1320", tomorrow))
        assertEquals(
            PlanSuggestion.None,
            suggest("1250", tomorrow.copy(plannedAmount = "5000", remainingAmount = "3000"))
        )
        val earlier = payment("2026-11-12")
        assertEquals(PlanSuggestion.Choice(listOf(earlier), false), suggest("1250", earlier))
        assertEquals(PlanSuggestion.None, suggest("1250", payment("2026-11-08"), payment("2026-11-07")))
        assertEquals(PlanSuggestion.Recommended(tomorrow), suggest("1250", payment("2026-10-16"), tomorrow))
    }

    @Test fun `uses exact inclusive boundaries for amount and score`() {
        val today = payment("2026-11-15")
        assertEquals(PlanSuggestion.Choice(listOf(today), false), suggest("1000", today))
        assertEquals(PlanSuggestion.None, suggest("999", today))
        assertEquals(PlanSuggestion.Recommended(today), suggest("1125", today))
        assertEquals(PlanSuggestion.Choice(listOf(today), false), suggest("1124.99", today))
        assertEquals(PlanSuggestion.Recommended(today), suggest("1375", today))
    }

    @Test fun `does not round the lead and returns at most three deterministically`() {
        val today = payment("2026-11-15", "92.5", "a")
        val second = payment("2026-11-15", "100", "b")
        assertEquals(PlanSuggestion.Recommended(today), suggest("92.5", second, today))
        assertEquals(PlanSuggestion.Choice(listOf(today, second), false), suggest("92.51", second, today))
        val choices = listOf("a", "b", "c", "d").map { payment("2026-11-15", ruleId = it) }
        assertEquals(PlanSuggestion.Choice(choices.take(3), true), suggest("1250", *choices.reversed().toTypedArray()))
    }

    @Test fun `filters mismatched fields and uses saved date and contribution after refunds`() {
        val payment = payment("2026-11-16")
        val moved = payment.copy(target = payment.target.copy(scheduledOn = "2026-12-16"))
        val mismatches = listOf(
            payment.copy(categoryId = "same-name"),
            payment.copy(currency = "KZT"),
            payment.copy(target = PlanLinkTarget("INCOME", "income", dueOn = "2026-11-16")),
            payment.copy(remainingAmount = "0")
        )
        assertEquals(
            PlanSuggestion.Recommended(moved),
            policy.suggest(operation.copy(amount = "2000"), null, false, false, mismatches + moved)
        )
        assertEquals(PlanSuggestion.None, suggest("1250", *mismatches.toTypedArray()))
        listOf("OTHER", "OTHER_INCOME").forEach {
            assertEquals(PlanSuggestion.None, policy.suggest(operation, it, false, false, listOf(payment)))
        }
        assertEquals(PlanSuggestion.None, policy.suggest(operation, null, true, false, listOf(payment)))
        assertEquals(PlanSuggestion.None, policy.suggest(operation, null, false, true, listOf(payment)))
    }

    @Test fun `the seven day window is inclusive even when the default score hides those payments`() {
        val relaxed = OperationPlanSuggestionPolicy(OperationPlanSuggestionPolicy.Config(choiceThreshold = 0))
        val within = listOf(payment("2026-11-08"), payment("2026-11-22"))
        val outside = listOf(payment("2026-11-07"), payment("2026-11-23"))
        assertEquals(
            PlanSuggestion.Choice(within, false),
            relaxed.suggest(
                operation,
                null,
                false,
                false,
                within + outside
            )
        )
    }

    private fun suggest(
        amount: String,
        vararg plans: SuggestedPlan
    ): PlanSuggestion = policy.suggest(operation.copy(netAmount = amount), null, false, false, plans.toList())

    private fun payment(
        date: String,
        amount: String = "1250",
        ruleId: String = "rule"
    ) = SuggestedPlan(
        PlanLinkTarget("EXPENSE", ruleId = ruleId, dueOn = date),
        "category",
        "Связь",
        amount,
        amount,
        0,
        currency = "RUB"
    )
}
