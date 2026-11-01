package com.firkat.fillin.feature.sync.data

import com.firkat.fillin.core.database.*
import com.firkat.fillin.feature.operationhistory.data.local.*
import com.firkat.fillin.feature.plannedoperations.domain.model.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class BudgetProjectionTest {
    private val json = Json
    private val today = LocalDate.parse("2026-02-15")
    private val filter =
        PlannedOperationsFilter(
            period = PlannedOperationPeriod.Custom,
            dateFrom = LocalDate.parse("2026-01-01"),
            dateTo = LocalDate.parse("2026-03-31"),
        )
    private val rule =
        SyncPlanRecord(
            "r",
            "expense",
            amount = "100.00",
            frequency = "MONTHLY",
            scheduleValue = 31,
            startsOn = "2026-01-31",
            active = true,
            version = 0,
            createdAt = AT,
            modifiedAt = AT,
        )
    private val occurrence =
        SyncPlanRecord(
            "occurrence",
            "expense",
            "r",
            amount = "100.00",
            dueOn = "2026-02-28",
            status = "SCHEDULED",
            version = 0,
            createdAt = AT,
            modifiedAt = AT,
        )

    private fun record(
        kind: String,
        record: SyncPlanRecord,
    ) = LocalSyncRecord("scope", kind, record.id, json.encodeToString(record), 1)

    private fun operation(
        id: String,
        amount: String,
        status: String = "POSTED",
    ) = LocalOperation(
        id,
        "scope",
        "expense",
        "EXPENSE",
        amount,
        "KZT",
        amount,
        "2026-02-01",
        AT,
        status,
        0,
        1,
        null,
        null,
        null,
    )

    private fun content(
        commands: List<BudgetOutbox> = emptyList(),
        voided: Boolean = false,
    ): BudgetContent =
        BudgetContent(
            OperationSyncState(scopeKey = "scope", initialSyncComplete = true, role = "OWNER"),
            listOf(
                LocalCategory("scope", "expense", "Расход", "EXPENSE", "HOME", "🏠", false, 1),
                LocalCategory("scope", "income", "Доход", "INCOME", "INCOME", "💰", false, 2),
            ),
            listOf(
                operation("payment", "20.00", if (voided) "VOIDED" else "POSTED"),
                operation("received", "1.00", if (voided) "VOIDED" else "POSTED"),
            ),
            listOf(
                record("RECURRING_RULE", rule),
                record("RECURRING_OCCURRENCE", occurrence),
                record("OCCURRENCE_PAYMENT", SyncPlanRecord("occurrence", ledgerId = "payment")),
                record(
                    "EXPECTED_INCOME",
                    SyncPlanRecord(
                        "salary",
                        "income",
                        amount = "200.00",
                        dueOn = "2026-02-28",
                        status = "SCHEDULED",
                        version = 0,
                        createdAt = AT,
                        modifiedAt = AT,
                    ),
                ),
                record(
                    "EXPECTED_INCOME",
                    SyncPlanRecord(
                        "other",
                        "income",
                        amount = "50.00",
                        dueOn = "2026-03-01",
                        status = "SCHEDULED",
                        version = 0,
                        createdAt = AT,
                        modifiedAt = AT,
                    ),
                ),
                record("INCOME_MATCH", SyncPlanRecord("salary", ledgerId = "received")),
            ),
            commands,
        )

    private fun command(
        type: String,
        amount: String? = null,
        id: String = "r",
        order: Long = 1,
    ): BudgetOutbox {
        val c = BudgetSyncCommand("request$order", "h", "m", type, id, today.toString(), amount = amount)
        return BudgetOutbox(c.requestId, "scope", id, c.entityKind(), json.encodeToString(c), order)
    }

    @Test fun `metadata replacement clears descriptions and survives earlier acknowledgements`() {
        val first = BudgetSyncCommand("first", "h", "m", "UPDATE_OPERATION", "payment", today.toString(),
            categoryId = "new-category", amount = "20.00", description = "новое", replaceDetails = true)
        val second = first.copy(requestId = "second", amount = "25.00", description = null)
        val commands = listOf(first, second).mapIndexed { i, c ->
            BudgetOutbox(c.requestId, "scope", "payment", "LEDGER", json.encodeToString(c), i.toLong(),
                status = if (i == 0) "ACKNOWLEDGED" else "PENDING", receiptRevision = if (i == 0) 2L else null)
        }
        val before = content(commands).let { it.copy(sync = it.sync.copy(protocolVersion = 4, lastRevision = 1)) }
        val projected = BudgetProjection(before, json, today).operations.getValue("payment")
        assertEquals("new-category", projected.categoryId)
        assertNull(projected.description)
        assertEquals("25.00", projected.originalAmount)
        val after = before.copy(sync = before.sync.copy(lastRevision = 2), operations = before.operations.map {
            if (it.id == "payment") it.copy(categoryId = "new-category", description = "новое", version = 1) else it
        })
        assertEquals(projected, BudgetProjection(after, json, today).operations.getValue("payment"))
    }

    @Test fun `legacy amount commands retain category and description and omit the new flag`() {
        val c = BudgetSyncCommand("legacy", "h", "m", "UPDATE_OPERATION", "payment", today.toString(),
            categoryId = "ignored", amount = "25.00", description = "ignored")
        val encoded = json.encodeToString(c)
        assertFalse(encoded.contains("replaceDetails"))
        val command = BudgetOutbox(c.requestId, "scope", "payment", "LEDGER", encoded, 1)
        val source = content(listOf(command)).let { data -> data.copy(operations = data.operations.map {
            if (it.id == "payment") it.copy(description = "старое") else it
        }) }
        val operation = BudgetProjection(source, json, today).operations.getValue("payment")
        assertEquals("expense", operation.categoryId)
        assertEquals("старое", operation.description)
        assertEquals("25.00", operation.originalAmount)
        val wireJson = kotlinx.serialization.json.Json { explicitNulls = false }
        val clear = wireJson.decodeFromString<BudgetSyncCommand>(wireJson.encodeToString(c.copy(description = null, replaceDetails = true)))
        assertTrue(clear.replaceDetails)
        assertNull(clear.description)
    }

    @Test fun `calendar projection uses month end and merges materialized occurrences`() {
        val plans = BudgetProjection(content(), json, today).plans(filter)
        assertEquals(BigDecimal("280.00"), plans.totals.expenses)
        assertEquals(BigDecimal("249.00"), plans.totals.incomes)
        val expenses = plans.items.filterIsInstance<PlannedOperation.Expense>()
        assertEquals(listOf("2026-01-31", "2026-02-28", "2026-03-31"), expenses.map { it.dueOn.toString() })
        assertEquals("occurrence", expenses[1].id)
        val incomesOnly =
            BudgetProjection(
                content(),
                json,
                today,
            ).plans(filter.copy(type = PlannedOperationType.Income))
        assertEquals(plans.totals, incomesOnly.totals)
        assertEquals(2, incomesOnly.items.size)
    }

    @Test fun `voiding linked facts restores obligation and expected income`() {
        val plans = BudgetProjection(content(voided = true), json, today).plans(filter)
        assertEquals(BigDecimal("300.00"), plans.totals.expenses)
        assertEquals(BigDecimal("250.00"), plans.totals.incomes)
    }

    @Test fun `rule update preserves prior and partially paid periods and is repeatable`() {
        val p = BudgetProjection(content(listOf(command("UPDATE_RULE", "150.00"))), json, today)
        val first = p.plans(filter)
        assertEquals(BigDecimal("330.00"), first.totals.expenses)
        assertEquals(first, p.plans(filter))
        assertEquals(
            1L,
            first.items
                .filterIsInstance<PlannedOperation.Expense>()
                .first()
                .expense.ruleVersion,
        )
    }

    @Test fun `deleting rule cancels future obligations without making a financial operation`() {
        val c = content(listOf(command("DELETE_RULE")))
        val p = BudgetProjection(c, json, today)
        assertEquals(BigDecimal("100.00"), p.plans(filter).totals.expenses)
        assertEquals(c.operations.size, p.operations.size)
    }

    @Test fun `closing partially paid expense removes remainder without adding a payment`() {
        val c = content(listOf(command("CLOSE_EXPENSE", id = "occurrence")))
        val p = BudgetProjection(c, json, today)
        assertEquals(BigDecimal("200.00"), p.plans(filter).totals.expenses)
        assertEquals(c.operations.size, p.operations.size)
    }

    @Test fun `leap February and weekly ISO days are generated correctly`() {
        val c =
            content().copy(
                operations = emptyList(),
                commands = emptyList(),
                records = listOf(record("RECURRING_RULE", rule.copy(startsOn = "2028-01-31"))),
            )
        val f = filter.copy(dateFrom = LocalDate.parse("2028-02-01"), dateTo = LocalDate.parse("2028-02-29"))
        assertEquals(
            LocalDate.parse("2028-02-29"),
            BudgetProjection(c, json, LocalDate.parse("2028-02-01"))
                .plans(f)
                .items
                .single()
                .dueOn,
        )
        val weekly =
            c.copy(
                records =
                    listOf(
                        record("RECURRING_RULE", rule.copy(frequency = "WEEKLY", scheduleValue = 1, startsOn = "2028-02-01")),
                    ),
            )
        assertEquals(
            listOf(7, 14, 21, 28),
            BudgetProjection(weekly, json, LocalDate.parse("2028-02-01")).plans(f).items.map {
                it.dueOn.dayOfMonth
            },
        )
    }

    @Test fun `v4 pending local changes remain visible over remotely archived and cancelled records`() {
        val categoryIntent = BudgetSyncCommand("category-change", "h", "m", "UPDATE_CATEGORY", "expense", today.toString(), name = "Мой расход", kind = "EXPENSE", icon = "HOME")
        val base =
            content(
                listOf(
                    BudgetOutbox(categoryIntent.requestId, "scope", "expense", "CATEGORY", json.encodeToString(categoryIntent), 1, protocolVersion = 4),
                    command("UPDATE_RULE", "150.00", order = 2).copy(protocolVersion = 4),
                    command("UPDATE_INCOME", "60.00", id = "other", order = 3).copy(protocolVersion = 4),
                ),
            )
        val remote =
            base.copy(
                sync = base.sync.copy(protocolVersion = 4),
                categories = base.categories.map { if (it.id == "expense") it.copy(archived = true) else it },
                records =
                    base.records.map { row ->
                        when {
                            row.kind == "RECURRING_RULE" -> record(row.kind, rule.copy(active = false))
                            row.kind == "RECURRING_OCCURRENCE" -> record(row.kind, occurrence.copy(status = "CANCELLED"))
                            row.kind == "EXPECTED_INCOME" && row.id == "other" -> record(row.kind, json.decodeFromString<SyncPlanRecord>(row.payload).copy(status = "CANCELLED"))
                            else -> row
                        }
                    },
            )
        val projection = BudgetProjection(remote, json, today)
        assertEquals("Мой расход", projection.categoryList().first { it.id == "expense" }.name)
        val plans = projection.plans(filter)
        assertTrue(plans.items.any { it.id == "occurrence" })
        assertTrue(projection.rules.getValue("r").active == true)
        assertEquals(BigDecimal("259.00"), plans.totals.incomes)
        assertEquals(plans, projection.plans(filter))
    }

    companion object {
        private const val AT = "2026-01-01T00:00:00Z"
    }
}
