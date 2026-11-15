package com.firkat.fillin.feature.dailylimit

import com.firkat.fillin.core.database.*
import com.firkat.fillin.feature.dailylimit.domain.*
import com.firkat.fillin.feature.operationhistory.data.local.*
import com.firkat.fillin.feature.plannedoperations.domain.model.*
import com.firkat.fillin.feature.sync.data.*
import java.time.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class IncomeForecastProjectionTest {
    private val json = Json
    private val date = LocalDate.of(2026, 11, 1)
    private fun record(
        kind: String,
        value: SyncPlanRecord
    ) = LocalSyncRecord("scope", kind, value.id, json.encodeToString(value), 1)
    private fun content(
        records: List<LocalSyncRecord>,
        operations: List<LocalOperation> = emptyList(),
        commands: List<BudgetOutbox> = emptyList()
    ) = BudgetContent(
        OperationSyncState(
            scopeKey = "scope",
            accountingStartedAt = "2026-01-01T00:00:00Z",
            localReady = true,
            role = "OWNER",
            protocolVersion = 4
        ),
        listOf(LocalCategory("scope", "category", "Salary", "INCOME", "INCOME", "", false, 1)),
        operations,
        records +
            LocalSyncRecord(
                "scope",
                "BUDGET_SETTINGS",
                "household",
                json.encodeToString(SyncBudgetSettings("household", "1000.00", "0.00")),
                1
            ),
        commands
    )
    private fun income() = SyncPlanRecord(
        "income",
        categoryId = "category",
        amount = "300.00",
        dueOn = "2026-11-01",
        status = "SCHEDULED",
        version = 0
    )
    private fun posted(
        amount: String
    ) = LocalOperation(
        "actual", "scope", "category", "INCOME", amount, "KZT", amount,
        "2026-11-01", "2026-11-01T00:00:00Z", "POSTED", 0, 1, null, null, null, occurredAt = "2026-11-01T00:00:00Z"
    )
    private fun input(
        content: BudgetContent
    ) = BudgetProjection(content, json, date).dashboardInput(Instant.parse("2026-11-01T01:00:00Z"))
    private fun command(
        value: BudgetSyncCommand
    ) = BudgetOutbox(value.requestId, "scope", value.entityId, value.entityKind(), json.encodeToString(value), 1)

    @Test fun `DL06 fully received income is only counted in actual balance`() {
        val c =
            content(
                listOf(
                    record("EXPECTED_INCOME", income()),
                    record("INCOME_MATCH", SyncPlanRecord("income", ledgerId = "actual"))
                ),
                listOf(posted("300.00"))
            )
        assertTrue(input(c).incomes.isEmpty())
        assertEquals("300.00", input(c).operations.single().amount.toPlainString())
    }

    @Test fun `partial income does not automatically forecast remainder`() {
        val c =
            content(
                listOf(
                    record("EXPECTED_INCOME", income()),
                    record("INCOME_MATCH", SyncPlanRecord("income", ledgerId = "actual"))
                ),
                listOf(posted("100.00"))
            )
        assertTrue(input(c).incomes.isEmpty())
        assertTrue(ForecastWarning.UNCONFIRMED_REMAINDER in input(c).incomeWarnings)
        val confirmed = income().copy(
            remainderAmount = "150.00",
            remainderDueOn = "2026-11-03",
            remainderReceived = "100.00"
        )
        val updated = c.copy(
            records =
            c.records.filterNot { it.kind == "EXPECTED_INCOME" } + record("EXPECTED_INCOME", confirmed)
        )
        assertEquals("150.00", input(updated).incomes.single().amount.toPlainString())
        assertEquals(date.plusDays(2), input(updated).incomes.single().dueOn)
        assertTrue(input(updated.copy(operations = listOf(posted("120.00")))).incomes.isEmpty())
    }

    @Test fun `offline confirmation applies once and preserves original schedule`() {
        val c =
            content(
                listOf(
                    record("EXPECTED_INCOME", income()),
                    record("INCOME_MATCH", SyncPlanRecord("income", ledgerId = "actual"))
                ),
                listOf(posted("100.00")),
                listOf(
                    command(
                        BudgetSyncCommand("confirm", "h", "m", "CONFIRM_REMAINDER_INCOME", "income", date.toString(), remainderAmount = "200.00", remainderDueOn = "2026-11-04", remainderReceived = "100.00")
                    )
                )
            )
        val p = BudgetProjection(c, json, date)
        assertEquals(1L, p.incomes.getValue("income").version)
        assertEquals("2026-11-01", p.incomes.getValue("income").dueOn)
        assertEquals(date.plusDays(3), input(c).incomes.single().dueOn)
    }

    @Test fun `regular rules expand with stable identity and retain future after missed income`() {
        val rule = income().copy(
            id = "rule",
            incomeFrequency = "MONTHLY",
            incomeName = "Salary",
            incomeActive = true,
            dueOn = "2026-10-31"
        )
        val c = content(listOf(record("INCOME_RULE", rule)))
        val p = BudgetProjection(c, json, date)
        val dates = p.incomes.values.map { it.dueOn }
        assertTrue("2026-11-30" in dates)
        assertTrue("2026-12-31" in dates)
        assertEquals(p.incomes.keys, BudgetProjection(c, json, date).incomes.keys)
        assertTrue(input(c).incomes.any { it.dueOn == LocalDate.of(2026, 11, 30) })
        assertTrue(input(c).incomes.any { it.dueOn < date })
    }

    @Test fun `offline recurring create expands rule separately from occurrences`() {
        val value =
            BudgetSyncCommand(
                "create", "h", "m", "CREATE_INCOME_RULE", "rule", date.toString(), categoryId = "category", amount = "300.00",
                dueOn = date.toString(), incomeName = "Salary", incomeFrequency = "BIWEEKLY", incomeActive = true
            )
        val p = BudgetProjection(content(emptyList(), commands = listOf(command(value))), json, date)
        assertEquals(1, p.incomeRules.size)
        assertFalse("rule" in p.incomes)
        assertTrue(p.incomes.values.all { it.ruleId == "rule" })
        assertTrue(p.incomes.values.any { it.dueOn == "2026-11-15" })
    }

    @Test fun `disabled rule is excluded from forecast`() {
        val rule = income().copy(id = "rule", incomeFrequency = "WEEKLY", incomeName = "Salary", incomeActive = false)
        assertTrue(input(content(listOf(record("INCOME_RULE", rule)))).incomes.isEmpty())
    }

    @Test fun `rule edit advances persisted unpaid occurrence version`() {
        val rule = income().copy(id = "rule", incomeFrequency = "WEEKLY", incomeName = "Salary", incomeActive = true)
        val occurrence = income().copy(
            id = "future",
            ruleId = "rule",
            scheduledOn = "2026-11-08",
            dueOn = "2026-11-08",
            version = 5,
            incomeFrequency = "WEEKLY",
            incomeName = "Salary",
            incomeActive = true
        )
        val update = BudgetSyncCommand(
            "update", "h", "m", "UPDATE_INCOME_RULE", "rule", date.toString(),
            categoryId = "category", amount = "400.00", dueOn = date.toString(), incomeFrequency = "MONTHLY", incomeName = "Salary", incomeActive = true
        )
        val p = BudgetProjection(
            content(
                listOf(record("INCOME_RULE", rule), record("EXPECTED_INCOME", occurrence)),
                commands = listOf(command(update))
            ),
            json,
            date
        )
        assertEquals("MONTHLY", p.incomes.getValue("future").incomeFrequency)
        assertEquals(6L, p.incomes.getValue("future").version)
        assertEquals("400.00", p.incomes.getValue("future").amount)
    }
}
