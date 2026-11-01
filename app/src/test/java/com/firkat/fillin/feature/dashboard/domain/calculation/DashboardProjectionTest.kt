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
        assertEquals("200.00", dashboard.balanceForecast.points.first().balance.amount.toPlainString())
        assertEquals("100.00", dashboard.balanceForecast.points.last().balance.amount.toPlainString())
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
        assertEquals("280.00", closed.balanceForecast.points.first().balance.amount.toPlainString())
        assertEquals("180.00", closed.balanceForecast.points.last().balance.amount.toPlainString())
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

    @Test fun `queued incomes do not fulfill plans without explicit confirmation`() {
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
        assertEquals("251.00", first.balanceForecast.maximum!!.balance.amount.toPlainString())
        val second = calculate(
            content.copy(
                records = plans,
                operations =
                    content.operations + income("income-1") + income("income-2")
            )
        )
        assertEquals("50.00", second.forecast.base.expectedIncome.amount.toPlainString())
        assertEquals("282.00", second.availableMoney.amount.toPlainString())
        assertEquals("252.00", second.balanceForecast.maximum!!.balance.amount.toPlainString())
    }

    @Test fun `cancelled plans and fulfilled incomes are excluded and next month occurrences are materialized`() {
        val content = input()
        val cancelled = content.records.map { record ->
            if (record.kind == "RECURRING_OCCURRENCE" && record.id == "current") {
                record.copy(payload = json.encodeToString(json.decodeFromString<SyncPlanRecord>(record.payload).copy(status = "CANCELLED")))
            } else record
        }
        val nextMonthRule = record("RECURRING_RULE", SyncPlanRecord(
            "next-month-rule", EXPENSE, amount = "50.00", frequency = "MONTHLY", scheduleValue = 5,
            startsOn = "2026-11-01", active = true,
        ))
        val fulfilledIncome = operation("income", "500.00").copy(type = "INCOME", categoryId = INCOME)
        val records = cancelled + nextMonthRule + listOf(
            record("EXPECTED_INCOME", SyncPlanRecord("fulfilled", INCOME, amount = "500.00", dueOn = "2026-10-20", status = "SCHEDULED")),
            record("INCOME_MATCH", SyncPlanRecord("fulfilled", ledgerId = "income")),
            record("EXPECTED_INCOME", SyncPlanRecord("cancelled-income", INCOME, amount = "1000.00", dueOn = "2026-10-20", status = "CANCELLED")),
        )
        val result = calculate(content.copy(records = records, operations = content.operations + fulfilledIncome))
        assertEquals("700.00", result.balanceForecast.points.first().balance.amount.toPlainString())
        assertEquals("700.00", result.balanceForecast.maximum!!.balance.amount.toPlainString())
        assertEquals(LocalDate.parse("2026-11-05"), result.balanceForecast.minimum!!.date)
        assertEquals("650.00", result.balanceForecast.points.last().balance.amount.toPlainString())
    }
    companion object {
        private const val EXPENSE = "00000000-0000-0000-0000-000000000001"
        private const val INCOME = "00000000-0000-0000-0000-000000000002"
        private const val AT = "2026-10-18T00:00:00Z"
    }
}
