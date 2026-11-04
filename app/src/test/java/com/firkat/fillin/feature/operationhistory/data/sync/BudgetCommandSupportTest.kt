package com.firkat.fillin.feature.operationhistory.data.sync

import com.firkat.fillin.feature.operationhistory.domain.repository.PlanLinkTarget
import com.firkat.fillin.feature.sync.data.BudgetCapabilities
import com.firkat.fillin.feature.sync.data.BudgetSyncCommand
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class BudgetCommandSupportTest {
    private val command = BudgetSyncCommand("request", "household", "member", "CREATE_INCOME", "income", "2026-11-04")
    private val incomeCommands = listOf(
        command.copy(type = "CREATE_INCOME_RULE"),
        command.copy(type = "UPDATE_INCOME_RULE"),
        command.copy(type = "DELETE_INCOME_RULE"),
        command.copy(incomeName = "Зарплата"),
        command.copy(type = "UPDATE_INCOME", incomeFrequency = "ONCE"),
        command.copy(type = "UPDATE_INCOME", incomeActive = false),
    )
    private val remainderCommands = listOf(
        command.copy(type = "CONFIRM_REMAINDER_INCOME"),
        command.copy(remainderAmount = "10.00"),
        command.copy(remainderDueOn = "2026-11-05"),
        command.copy(remainderReceived = "20.00"),
    )

    @Test fun `income editing works with narrow capability or full daily planning`() = runTest {
        for (capability in listOf(BudgetCapabilities.INCOME_RULE_EDITING, BudgetCapabilities.DAILY_LIMIT_PLANNING)) {
            val support = BudgetCommandSupport { setOf(capability) }
            incomeCommands.forEach { support.ensure(it) }
        }
    }

    @Test fun `confirmed linking alone does not allow income editing`() = runTest {
        for (capabilities in listOf(emptySet(), setOf(BudgetCapabilities.CONFIRMED_PLAN_LINKING))) {
            val support = BudgetCommandSupport { capabilities }
            incomeCommands.forEach { assertBlocked { support.ensure(it) } }
        }
    }

    @Test fun `narrow income support never allows remainder command or fields`() = runTest {
        val support = BudgetCommandSupport {
            setOf(BudgetCapabilities.INCOME_RULE_EDITING, BudgetCapabilities.CONFIRMED_PLAN_LINKING)
        }
        remainderCommands.forEach { assertBlocked { support.ensure(it) } }
        assertBlocked { support.ensure(command.copy(incomeName = "Зарплата", remainderAmount = "10.00")) }
    }

    @Test fun `full daily planning allows every remainder field`() = runTest {
        val support = BudgetCommandSupport { setOf(BudgetCapabilities.DAILY_LIMIT_PLANNING) }
        remainderCommands.forEach { support.ensure(it) }
    }

    @Test fun `recurring income links only need confirmed linking`() = runTest {
        val support = BudgetCommandSupport { setOf(BudgetCapabilities.CONFIRMED_PLAN_LINKING) }
        support.ensure(command.copy(type = "SET_OPERATION_PLAN_LINK", planTarget =
            PlanLinkTarget("INCOME", planId = "occurrence", ruleId = "rule", dueOn = "2026-11-04", scheduledOn = "2026-11-04")))
        support.ensure(command.copy(type = "REMOVE_OPERATION_PLAN_LINK"))
        support.ensure(command.copy(type = "CREATE_OPERATION", planMatchingMode = "CONFIRM_REQUIRED"))
    }

    @Test fun `income and daily capabilities do not replace confirmed linking`() = runTest {
        val support = BudgetCommandSupport {
            setOf(BudgetCapabilities.INCOME_RULE_EDITING, BudgetCapabilities.DAILY_LIMIT_PLANNING)
        }
        assertBlocked { support.ensure(command.copy(type = "SET_OPERATION_PLAN_LINK")) }
        assertBlocked { support.ensure(command.copy(type = "REMOVE_OPERATION_PLAN_LINK")) }
        assertBlocked { support.ensure(command.copy(type = "CREATE_OPERATION", planMatchingMode = "CONFIRM_REQUIRED")) }
    }

    @Test fun `legacy commands and expense editing do not require new capabilities`() = runTest {
        val support = BudgetCommandSupport { error("Unexpected capability request") }
        for (type in listOf("CREATE_INCOME", "UPDATE_INCOME", "DELETE_INCOME", "CREATE_RULE", "UPDATE_RULE", "DELETE_RULE")) {
            support.ensure(command.copy(type = type))
        }
        support.ensure(command.copy(type = "UPDATE_RULE", replaceDetails = true, ruleName = "Квартира"))
    }

    @Test fun `one capability read serves mixed commands and reset rechecks backend`() = runTest {
        var reads = 0
        var capabilities = setOf(BudgetCapabilities.INCOME_RULE_EDITING, BudgetCapabilities.CONFIRMED_PLAN_LINKING)
        val support = BudgetCommandSupport { reads++; capabilities }
        incomeCommands.forEach { support.ensure(it) }
        support.ensure(command.copy(type = "SET_OPERATION_PLAN_LINK"))
        assertBlocked { support.ensure(remainderCommands.first()) }
        assertEquals(1, reads)

        capabilities = setOf(BudgetCapabilities.DAILY_LIMIT_PLANNING)
        support.reset()
        support.ensure(remainderCommands.first())
        assertEquals(2, reads)
    }

    @Test fun `failed capability request cannot enable delivery`() = runTest {
        var reads = 0
        val support = BudgetCommandSupport {
            if (++reads == 1) throw IOException("Lost budget response")
            emptySet()
        }
        try {
            support.ensure(incomeCommands.first())
            fail("Expected network failure")
        } catch (_: IOException) {
            // A failed lookup must not be cached as support.
        }
        assertBlocked { support.ensure(incomeCommands.first()) }
        assertEquals(2, reads)
    }

    private suspend fun assertBlocked(action: suspend () -> Unit) {
        try {
            action()
            fail("Expected unsupported backend to block delivery")
        } catch (failure: SyncBlockedException) {
            assertEquals("backend_update_required", failure.code)
        }
    }
}
