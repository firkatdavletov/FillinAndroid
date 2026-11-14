package com.firkat.fillin.feature.sync.data

import com.firkat.fillin.core.database.*
import com.firkat.fillin.feature.operationhistory.data.local.*
import com.firkat.fillin.feature.operationhistory.domain.repository.*
import com.firkat.fillin.feature.plannedoperations.domain.model.*
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ConfirmedPlanLinksTest {
    private val today = LocalDate.parse("2026-10-30")
    private val json = Json
    private val filter =
        PlannedOperationsFilter(
            PlannedOperationType.Income,
            PlannedOperationPeriod.Custom,
            today.minusYears(1),
            today.plusYears(1)
        )
    private fun income(
        id: String,
        due: String,
        amount: String = "100.00"
    ) = SyncPlanRecord(id, "salary", amount = amount, dueOn = due, status = "SCHEDULED", version = 0)
    private fun record(
        kind: String,
        record: SyncPlanRecord
    ) = LocalSyncRecord("scope", kind, "${record.id}:${record.ledgerId}", json.encodeToString(record), 1)
    private fun op(
        id: String,
        amount: String = "30.00"
    ) =
        LocalOperation(id, "scope", "salary", "INCOME", amount, "KZT", amount, today.toString(), "2026-10-30T00:00:00Z", "POSTED", 0, 0, null, null, id, "PENDING")
    private fun content(
        plans: List<SyncPlanRecord>,
        operations: List<LocalOperation> = listOf(op("op")),
        commands: List<BudgetOutbox> = emptyList(),
        links: List<SyncPlanRecord> = emptyList()
    ) = BudgetContent(
        OperationSyncState(
            scopeKey = "scope",
            protocolVersion = 4,
            localReady = true,
            role = "OWNER",
            accountingStartedAt = "2026-01-01T00:00:00Z"
        ),
        listOf(LocalCategory("scope", "salary", "Зарплата", "INCOME", "INCOME", "", false, 0)),
        operations,
        plans.map { record("EXPECTED_INCOME", it) } + links.map { record("INCOME_MATCH", it) } +
            LocalSyncRecord(
                "scope",
                "BUDGET_SETTINGS",
                "h",
                json.encodeToString(SyncBudgetSettings("h", "0.00", "0.00")),
                1
            ),
        commands
    )
    private fun command(
        type: String,
        plan: SyncPlanRecord? = null,
        order: Long = 1,
        operationId: String = "op",
        status: String = "PENDING"
    ): BudgetOutbox {
        val c =
            BudgetSyncCommand(
                "request$order",
                "h",
                "m",
                type,
                operationId,
                today.toString(),
                planTarget = plan?.let {
                    PlanLinkTarget("INCOME", it.id, dueOn = requireNotNull(it.dueOn))
                },
                expectedPlanVersion = 0
            )
        return BudgetOutbox(c.requestId, "scope", operationId, "LEDGER", json.encodeToString(c), order, status = status)
    }

    @Test fun `oldest overdue wins then today then future with stable ties`() {
        val plans =
            listOf(
                income("future", "2026-11-01"),
                income("today", "2026-10-30"),
                income("b", "2026-10-01"),
                income("a", "2026-10-01")
            )
        val p = BudgetProjection(content(plans), json, today)
        assertEquals(listOf("a", "b", "today", "future"), p.links("op").candidates.map { it.target.planId })
        assertEquals(BigDecimal("400.00"), p.plans(filter).totals.incomes)
        assertEquals(
            BigDecimal("400.00"),
            p.dashboardInput(java.time.Instant.parse("2026-10-30T12:00:00Z")).incomes.sumOf {
                it.amount
            }
        )
    }

    @Test fun `partial income confirmations accumulate without transferring overflow`() {
        val first = income("first", "2026-10-30")
        val second = income("second", "2026-11-01")
        val commands =
            listOf(command("SET_OPERATION_PLAN_LINK", first), command("SET_OPERATION_PLAN_LINK", first, 2, "op2"))
        val data = content(listOf(first, second), listOf(op("op"), op("op2", "20.00")), commands)
        val p = BudgetProjection(data, json, today)
        assertEquals(BigDecimal("150.00"), p.plans(filter).totals.incomes)
        assertEquals(
            BigDecimal("50.00"),
            p.plans(filter).items.filterIsInstance<PlannedOperation.Income>().first().income.receivedAmount
        )
        assertEquals(p.plans(filter), p.plans(filter))
        val overpaid = BudgetProjection(data.copy(operations = listOf(op("op", "200.00"), op("op2"))), json, today)
        assertEquals(listOf("second"), overpaid.plans(filter).items.map { it.id })
        assertEquals(BigDecimal("100.00"), overpaid.plans(filter).totals.incomes)
    }

    @Test fun `replacement and removal restore old plan and never change ledger amounts`() {
        val a = income("a", "2026-10-30")
        val b = income("b", "2026-11-01")
        val data =
            content(
                listOf(a, b),
                links = listOf(SyncPlanRecord("a", ledgerId = "op")),
                commands = listOf(command("SET_OPERATION_PLAN_LINK", b))
            )
        val changed = BudgetProjection(data, json, today)
        assertEquals("b", changed.links("op").current.single().target.planId)
        assertEquals("100.00", changed.links("op").candidates.first().remainingAmount)
        val removed = BudgetProjection(
            data.copy(
                commands =
                data.commands + command("REMOVE_OPERATION_PLAN_LINK", order = 2)
            ),
            json,
            today
        )
        assertTrue(removed.links("op").current.isEmpty())
        assertEquals(BigDecimal("200.00"), removed.plans(filter).totals.incomes)
        assertEquals("30.00", removed.operations.getValue("op").amountKzt)
    }

    @Test fun `failed links do not affect totals and voided facts restore legacy partial income`() {
        val plan = income("plan", "2026-10-30")
        listOf("REJECTED", "CONFLICT", "BLOCKED", "CANCELLED").forEach { status ->
            val p =
                BudgetProjection(
                    content(listOf(plan), commands = listOf(command("SET_OPERATION_PLAN_LINK", plan, status = status))),
                    json,
                    today
                )
            assertEquals(BigDecimal("100.00"), p.plans(filter).totals.incomes)
        }
        val legacy = content(listOf(plan), links = listOf(SyncPlanRecord("plan", ledgerId = "op")))
        assertEquals(BigDecimal("70.00"), BudgetProjection(legacy, json, today).plans(filter).totals.incomes)
        assertEquals(
            BigDecimal("100.00"),
            BudgetProjection(
                legacy.copy(operations = listOf(op("op").copy(status = "VOIDED"))),
                json,
                today
            ).plans(filter).totals.incomes
        )
    }

    @Test fun `link outside the visible period is projected before dashboard exclusions and later lookup`() {
        val rule =
            SyncPlanRecord(
                "rule",
                "salary",
                amount = "100.00",
                frequency = "MONTHLY",
                scheduleValue = 30,
                startsOn = today.toString(),
                active = true,
                version = 0
            )
        val c =
            BudgetSyncCommand(
                "link",
                "h",
                "m",
                "SET_OPERATION_PLAN_LINK",
                "op",
                today.toString(),
                planTarget = PlanLinkTarget("EXPENSE", ruleId = "rule", dueOn = "2027-03-30"),
                expectedPlanVersion = 0
            )
        val base = content(emptyList())
        val updateRule =
            BudgetSyncCommand("update", "h", "m", "UPDATE_RULE", "rule", today.toString(), amount = "200.00")
        val source = base.copy(
            categories = base.categories.map {
                it.copy(kind = "EXPENSE")
            },
            operations = listOf(op("op").copy(type = "EXPENSE")),
            records = base.records + record("RECURRING_RULE", rule),
            commands = listOf(
                BudgetOutbox("link", "scope", "op", "LEDGER", json.encodeToString(c), 1),
                BudgetOutbox("update", "scope", "rule", "RECURRING_RULE", json.encodeToString(updateRule), 2)
            )
        )
        val projection = BudgetProjection(source, json, today)
        projection.plans(PlannedOperationsFilter.currentMonth(today))
        assertEquals("2027-03-30", projection.links("op").current.single().target.dueOn)
        assertEquals("70.00", projection.links("op").current.single().remainingAmount)
        assertTrue(
            "op" in
                projection.dashboardInput(java.time.Instant.parse("2026-10-30T12:00:00Z")).excludedVariableExpenseIds
        )
    }

    @Test fun `failed dependency excludes optimistic link`() {
        val plan = income("plan", "2026-10-30")
        val failedParent = command("REMOVE_OPERATION_PLAN_LINK", order = 1).copy(status = "CONFLICT")
        val child = command(
            "SET_OPERATION_PLAN_LINK",
            plan,
            2
        ).copy(dependencies = json.encodeToString(listOf(failedParent.requestId)))
        val projection = BudgetProjection(content(listOf(plan), commands = listOf(failedParent, child)), json, today)
        assertEquals(BigDecimal("100.00"), projection.plans(filter).totals.incomes)
        assertTrue(projection.links("op").current.isEmpty())
    }

    @Test fun `canonical occurrence replaces local identity and refunds reopen remaining amount`() {
        val rule =
            SyncPlanRecord(
                "rule",
                "salary",
                amount = "100.00",
                frequency = "MONTHLY",
                scheduleValue = 30,
                startsOn = today.toString(),
                active = true,
                version = 0
            )
        val occurrence =
            SyncPlanRecord(
                "server-occurrence",
                "salary",
                ruleId = "rule",
                amount = "100.00",
                dueOn = today.toString(),
                status = "SCHEDULED",
                version = 0
            )
        val c =
            BudgetSyncCommand(
                "link",
                "h",
                "m",
                "SET_OPERATION_PLAN_LINK",
                "op",
                today.toString(),
                planTarget = PlanLinkTarget("EXPENSE", ruleId = "rule", dueOn = today.toString()),
                expectedPlanVersion = 0
            )
        val source = content(emptyList()).let {
            it.copy(
                categories = it.categories.map { category ->
                    category.copy(kind = "EXPENSE")
                },
                operations = listOf(
                    op("op", "100.00").copy(type = "EXPENSE"),
                    op("refund", "40.00").copy(type = "EXPENSE_REFUND", refundOf = "op")
                ),
                records = listOf(record("RECURRING_RULE", rule), record("RECURRING_OCCURRENCE", occurrence)),
                commands = listOf(BudgetOutbox("link", "scope", "op", "LEDGER", json.encodeToString(c), 1))
            )
        }
        val projection = BudgetProjection(source, json, today)
        assertEquals("server-occurrence", projection.links("op").current.single().target.planId)
        assertEquals("40.00", projection.links("op").current.single().remainingAmount)
    }
}
