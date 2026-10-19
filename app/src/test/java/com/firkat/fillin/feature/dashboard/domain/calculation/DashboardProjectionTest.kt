package com.firkat.fillin.feature.dashboard.domain.calculation

import com.firkat.fillin.core.database.*
import com.firkat.fillin.feature.operationhistory.data.local.*
import com.firkat.fillin.feature.sync.data.*
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class DashboardProjectionTest {
    private val json = Json
    private fun record(kind: String, record: SyncPlanRecord) =
        LocalSyncRecord("scope", kind, record.id, json.encodeToString(record), 1)
    private fun operation(id: String, amount: String) = LocalOperation(
        id, "scope", EXPENSE, "EXPENSE", amount, "KZT",
        amount, "2026-10-18", AT, "POSTED", 0, 1, null, null, null, occurredAt = AT
    )
    private fun input() = BudgetContent(
        OperationSyncState(
            scopeKey = "scope",
            accountingStartedAt = "2026-08-01T00:00:00Z",
            initialSyncComplete = true,
            role = "OWNER"
        ),
        listOf(
            LocalCategory("scope", EXPENSE, "Платёж", "EXPENSE", "HOME", "🏠", false, 1),
            LocalCategory("scope", INCOME, "Доход", "INCOME", "INCOME", "💰", false, 1)
        ),
        listOf(operation("payment", "20.00")),
        listOf(
            LocalSyncRecord(
                "scope",
                "BUDGET_SETTINGS",
                "household",
                json.encodeToString(SyncBudgetSettings("household", "300.00", "0.00")),
                1
            ),
            record(
                "RECURRING_RULE",
                SyncPlanRecord(
                    "rule",
                    EXPENSE,
                    amount = "100.00",
                    frequency = "MONTHLY",
                    scheduleValue = 25,
                    startsOn = "2026-09-25",
                    active = true
                )
            ),
            record(
                "RECURRING_OCCURRENCE",
                SyncPlanRecord(
                    "overdue",
                    EXPENSE,
                    "rule",
                    amount = "100.00",
                    dueOn = "2026-09-25",
                    status = "SCHEDULED"
                )
            ),
            record(
                "RECURRING_OCCURRENCE",
                SyncPlanRecord(
                    "current",
                    EXPENSE,
                    "rule",
                    amount = "100.00",
                    dueOn = "2026-10-25",
                    status = "SCHEDULED"
                )
            ),
            record("OCCURRENCE_PAYMENT", SyncPlanRecord("overdue", ledgerId = "payment"))
        ),
        emptyList()
    )
    private fun calculate(content: BudgetContent) = CalculateDashboard.calculate(
        BudgetProjection(
            content,
            json,
            LocalDate.parse("2026-10-19")
        ).dashboardInput(Instant.parse("2026-10-19T00:00:00Z"))
    )

    @Test fun `overdue partial payments reserve only their remainder and closing creates no expense`() {
        val content = input()
        val dashboard = calculate(content)
        assertEquals("280.00", dashboard.availableMoney.amount.toPlainString())
        assertEquals("180.00", dashboard.freeMoney.remainingObligations.amount.toPlainString())
        assertEquals("100.00", dashboard.freeMoney.amount.amount.toPlainString())
        val command = BudgetSyncCommand("request", "household", "member", "CLOSE_EXPENSE", "overdue", "2026-10-19")
        val closed =
            calculate(
                content.copy(
                    commands = listOf(
                        BudgetOutbox(
                            "request",
                            "scope",
                            "overdue",
                            "RECURRING_OCCURRENCE",
                            json.encodeToString(command),
                            1
                        )
                    )
                )
            )
        assertEquals(dashboard.availableMoney, closed.availableMoney)
        assertEquals(dashboard.monthActuals, closed.monthActuals)
        assertEquals("100.00", closed.freeMoney.remainingObligations.amount.toPlainString())
        assertEquals("180.00", closed.freeMoney.amount.amount.toPlainString())
    }

    @Test fun `accepted regular expenses and their refunds do not inflate variable spending`() {
        val regular = operation("subscription", "30.00")
        val refund = operation("refund", "10.00").copy(type = "EXPENSE_REFUND", refundOf = regular.id)
        val content = input()
        val result = calculate(
            content.copy(
                operations = content.operations + regular + refund,
                records = content.records + LocalSyncRecord(
                    "scope",
                    "VARIABLE_SPENDING_EXCLUSION",
                    regular.id,
                    json.encodeToString(SyncVariableSpendingExclusion(regular.id)),
                    1
                )
            )
        )
        assertEquals("40.00", result.monthActuals.netExpenses.amount.toPlainString())
        assertEquals("0.00", result.budgetExecution.variable.spent!!.amount.toPlainString())
    }

    @Test fun `queued incomes use nearest plan date then due date and fulfill plans independently of amount`() {
        val content = input()
        fun incomePlan(id: String, date: String) =
            record("EXPECTED_INCOME", SyncPlanRecord(id, INCOME, amount = "50.00", dueOn = date, status = "SCHEDULED"))
        fun income(id: String) = operation(id, "1.00").copy(
            type = "INCOME",
            categoryId = INCOME,
            occurredOn = "2026-10-19",
            syncStatus = "PENDING",
            requestId = id
        )
        val plans =
            content.records + incomePlan("overdue-income", "2026-10-16") + incomePlan("future-income", "2026-10-22")
        val first = calculate(content.copy(records = plans, operations = content.operations + income("income-1")))
        assertEquals("50.00", first.forecast.base.expectedIncome.amount.toPlainString())
        val second = calculate(
            content.copy(
                records = plans,
                operations =
                    content.operations + income("income-1") + income("income-2")
            )
        )
        assertEquals("0.00", second.forecast.base.expectedIncome.amount.toPlainString())
        assertEquals("282.00", second.availableMoney.amount.toPlainString())
    }
    @Test fun `month plan keeps original amount after partial payment full payment and manual close`() {
        val content = input()
        val partial = content.copy(records = content.records.map { row ->
            if (row.kind == "OCCURRENCE_PAYMENT") record(row.kind, SyncPlanRecord("current", ledgerId = "payment")) else row
        })
        assertEquals("100.00", calculate(partial).monthBudget.planned.amount.toPlainString())
        val full = partial.copy(operations = listOf(operation("payment", "100.00")))
        assertEquals("100.00", calculate(full).monthBudget.planned.amount.toPlainString())
        assertEquals("0.00", calculate(full).monthBudget.remaining.amount.toPlainString())
        val close = BudgetSyncCommand("close", "household", "member", "CLOSE_EXPENSE", "current", "2026-10-19")
        val closed = partial.copy(commands = listOf(BudgetOutbox("close", "scope", "current", "RECURRING_OCCURRENCE", json.encodeToString(close), 1)))
        assertEquals(calculate(partial).monthBudget, calculate(closed).monthBudget)
        val paid = content.copy(records = content.records.map { row ->
            if (row.kind == "RECURRING_OCCURRENCE" && row.id == "current") {
                record(row.kind, json.decodeFromString<SyncPlanRecord>(row.payload).copy(status = "PAID"))
            } else row
        })
        assertEquals("100.00", calculate(paid).monthBudget.planned.amount.toPlainString())
    }

    @Test fun `month plan applies queued create update delete and cancellation before summing`() {
        val content = input()
        fun withCommand(command: BudgetSyncCommand) = content.copy(commands = listOf(
            BudgetOutbox(command.requestId, "scope", command.entityId, "RECURRING_RULE", json.encodeToString(command), 1),
        ))
        val update = BudgetSyncCommand("update", "household", "member", "UPDATE_RULE", "rule", "2026-10-19", amount = "150.00")
        assertEquals("150.00", calculate(withCommand(update)).monthBudget.planned.amount.toPlainString())
        val delete = update.copy(requestId = "delete", type = "DELETE_RULE")
        assertEquals("0.00", calculate(withCommand(delete)).monthBudget.planned.amount.toPlainString())
        val create = update.copy(requestId = "create", type = "CREATE_RULE", entityId = "new-rule", categoryId = EXPENSE,
            amount = "30.00", frequency = "MONTHLY", scheduleValue = 20, startsOn = "2026-10-19")
        assertEquals("130.00", calculate(withCommand(create)).monthBudget.planned.amount.toPlainString())
        val cancelled = content.copy(records = content.records.map { row ->
            if (row.kind == "RECURRING_OCCURRENCE" && row.id == "current") record(row.kind,
                json.decodeFromString<SyncPlanRecord>(row.payload).copy(status = "CANCELLED")) else row
        })
        assertEquals("0.00", calculate(cancelled).monthBudget.planned.amount.toPlainString())
    }

    @Test fun `month plan generates every weekly occurrence without summing other months`() {
        val content = input()
        val weekly = content.copy(records = content.records.filter { it.kind !in setOf("RECURRING_OCCURRENCE", "OCCURRENCE_PAYMENT") }.map { row ->
            if (row.kind == "RECURRING_RULE") record(row.kind, json.decodeFromString<SyncPlanRecord>(row.payload).copy(
                amount = "10.00", startsOn = "2026-10-01", frequency = "WEEKLY", scheduleValue = 4,
            )) else row
        })
        assertEquals("50.00", calculate(weekly).monthBudget.planned.amount.toPlainString())
    }

    companion object {
        private const val EXPENSE = "00000000-0000-0000-0000-000000000001"
        private const val INCOME = "00000000-0000-0000-0000-000000000002"
        private const val AT = "2026-10-18T00:00:00Z"
    }
}
