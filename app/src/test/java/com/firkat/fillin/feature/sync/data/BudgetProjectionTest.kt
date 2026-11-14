package com.firkat.fillin.feature.sync.data

import com.firkat.fillin.core.database.*
import com.firkat.fillin.feature.operationhistory.data.local.*
import com.firkat.fillin.feature.plannedoperations.domain.model.*
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class BudgetProjectionTest {
    @Test fun `catalog reads rules once and keeps paid regular rules`() {
        val source = content().copy(
            records = content().records.map { row ->
                if (row.kind == "RECURRING_OCCURRENCE") record(row.kind, occurrence.copy(status = "PAID")) else row
            }
        )
        val projection = BudgetProjection(source, json, today)
        val catalog = projection.planningRules()
        assertEquals(1, catalog.count { it.id == "r" })
        assertEquals(BigDecimal("100.00"), catalog.first { it.id == "r" }.amount)
        assertEquals(catalog, projection.planningRules())
        projection.plans(filter)
        assertEquals(catalog, projection.planningRules())
    }

    @Test fun `catalog hides completed once but keeps full amount of partial once`() {
        val once = rule.copy(frequency = "ONCE", scheduleValue = null, startsOn = "2026-02-28")
        val source = content().copy(
            records = content().records.map {
                if (it.kind == "RECURRING_RULE") record(it.kind, once) else it
            }
        )
        assertEquals(
            BigDecimal("100.00"),
            BudgetProjection(source, json, today).planningRules().first {
                it.id == "r"
            }.amount
        )
        val paid = source.copy(operations = listOf(operation("payment", "100.00")))
        assertFalse(BudgetProjection(paid, json, today).planningRules().any { it.id == "r" })
        assertEquals(
            BigDecimal("100.00"),
            BudgetProjection(paid, json, today).planningRules(includeSettled = true).first {
                it.id ==
                    "r"
            }.amount
        )
        val disabled = source.copy(
            records = source.records.map {
                if (it.kind ==
                    "RECURRING_RULE"
                ) {
                    record(it.kind, once.copy(active = false))
                } else {
                    it
                }
            }
        )
        assertFalse(BudgetProjection(disabled, json, today).planningRules().any { it.id == "r" })
    }

    @Test fun `income rule catalog never duplicates expanded occurrences and preserves anchor`() {
        val income =
            SyncPlanRecord(
                "ir", categoryId = "income", amount = "200", dueOn = "2025-01-31", status = "SCHEDULED",
                incomeName = "Salary", incomeFrequency = "MONTHLY", incomeActive = true, version = 7
            )
        val source = content().copy(records = listOf(record("INCOME_RULE", income)))
        val catalog = BudgetProjection(source, json, today).planningRules()
        assertEquals(1, catalog.size)
        assertEquals(LocalDate.of(2025, 1, 31), catalog.single().startsOn)
        assertEquals(7L, catalog.single().version)
        assertTrue(catalog.single().isIncomeRule)
    }

    @Test fun `catalog overlays local creates updates and deletes before sync`() {
        val created = BudgetSyncCommand(
            "create", "h", "m", "CREATE_RULE", "new", today.toString(),
            categoryId = "expense", amount = "75", frequency = "WEEKLY", scheduleValue = 1, startsOn = today.toString()
        )
        val createOutbox =
            BudgetOutbox(
                created.requestId,
                "scope",
                created.entityId,
                created.entityKind(),
                json.encodeToString(created),
                1
            )
        val source = content(commands = listOf(createOutbox, command("UPDATE_RULE", "125", order = 2)))
        val catalog = BudgetProjection(source, json, today).planningRules()
        assertEquals(BigDecimal("125"), catalog.first { it.id == "r" }.amount)
        assertEquals(PlanningFrequency.Weekly, catalog.first { it.id == "new" }.frequency)
        val deleted = source.copy(commands = source.commands + command("DELETE_RULE", id = "new", order = 3))
        assertFalse(BudgetProjection(deleted, json, today).planningRules().any { it.id == "new" })
    }

    @Test fun `catalog excludes fully received or cancelled standalone incomes`() {
        val income =
            SyncPlanRecord(
                "salary",
                categoryId = "income",
                amount = "200.00",
                dueOn = "2026-02-28",
                status = "SCHEDULED"
            )
        val source = content().copy(
            records = listOf(
                record("EXPECTED_INCOME", income),
                record("INCOME_MATCH", SyncPlanRecord("salary", ledgerId = "payment"))
            ),
            operations = listOf(operation("payment", "100.00"))
        )
        assertEquals(BigDecimal("200.00"), BudgetProjection(source, json, today).planningRules().single().amount)
        assertTrue(
            BudgetProjection(
                source.copy(operations = listOf(operation("payment", "200.00"))),
                json,
                today
            ).planningRules().isEmpty()
        )
        assertTrue(
            BudgetProjection(
                source.copy(records = listOf(record("EXPECTED_INCOME", income.copy(status = "CANCELLED")))),
                json,
                today
            ).planningRules().isEmpty()
        )
    }

    private val json = Json

    @Test fun `full rule edit updates metadata and future schedule while preserving partial payment`() {
        val change = BudgetSyncCommand(
            "edit", "h", "m", "UPDATE_RULE", "r", today.toString(),
            categoryId = "expense", amount = "300", frequency = "MONTHLY", scheduleValue = 20,
            startsOn = "2026-02-20", ruleName = "Rent", replaceDetails = true
        )
        val source =
            content(
                commands = listOf(BudgetOutbox("edit", "scope", "r", "RECURRING_RULE", json.encodeToString(change), 1))
            )
        val projection = BudgetProjection(source, json, today)
        val catalog = projection.planningRules().first { it.id == "r" }
        assertEquals("Rent", catalog.name)
        assertEquals(20, catalog.scheduleValue)
        projection.plans(filter)
        assertEquals("100.00", projection.occurrences.getValue("occurrence").amount)
        assertEquals("300", projection.occurrences.values.first { it.ruleId == "r" && it.dueOn == "2026-02-20" }.amount)
        assertEquals(1, projection.occurrences.values.count { it.ruleId == "r" && it.dueOn == "2026-02-20" })
    }

    @Test fun `returning to earlier schedule restores matching unpaid dates without duplicates`() {
        val first = BudgetSyncCommand(
            "first", "h", "m", "UPDATE_RULE", "r", today.toString(),
            amount = "200", frequency = "MONTHLY", scheduleValue = 20, startsOn = "2026-02-20", replaceDetails = true
        )
        val second = first.copy(requestId = "second", scheduleValue = 28, startsOn = "2026-02-28")
        val commands = listOf(first, second).mapIndexed { index, c ->
            BudgetOutbox(c.requestId, "scope", "r", "RECURRING_RULE", json.encodeToString(c), index.toLong())
        }
        val source = content(commands).copy(records = content().records.filter { it.kind != "OCCURRENCE_PAYMENT" })
        val p = BudgetProjection(source, json, today)
        p.plans(filter)
        assertEquals("SCHEDULED", p.occurrences.getValue("occurrence").status)
        assertEquals("200", p.occurrences.getValue("occurrence").amount)
        assertEquals(1, p.occurrences.values.count { it.ruleId == "r" && it.dueOn == "2026-02-28" })
    }

    @Test fun `editing standalone partially received income separates rule without changing payment`() {
        val change = BudgetSyncCommand(
            "income-edit", "h", "m", "UPDATE_INCOME_RULE", "salary", today.toString(),
            categoryId = "income", amount = "500", dueOn = "2026-02-28", incomeName = "New name", incomeFrequency = "ONCE", incomeActive = true
        )
        val p =
            BudgetProjection(
                content(
                    listOf(
                        BudgetOutbox(change.requestId, "scope", "salary", "INCOME_RULE", json.encodeToString(change), 1)
                    )
                ),
                json,
                today
            )
        val catalog = p.planningRules().single { it.id == "salary" }
        assertEquals("New name", catalog.name)
        assertEquals("500".toBigDecimal(), catalog.amount)
        assertEquals("200.00", p.incomes.getValue("salary").amount)
        assertEquals("salary", p.incomes.getValue("salary").ruleId)
    }

    private val today = LocalDate.parse("2026-02-15")
    private val filter =
        PlannedOperationsFilter(
            period = PlannedOperationPeriod.Custom,
            dateFrom = LocalDate.parse("2026-01-01"),
            dateTo = LocalDate.parse("2026-03-31")
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
            modifiedAt = AT
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
            modifiedAt = AT
        )

    private fun record(
        kind: String,
        record: SyncPlanRecord
    ) = LocalSyncRecord("scope", kind, record.id, json.encodeToString(record), 1)

    private fun operation(
        id: String,
        amount: String,
        status: String = "POSTED"
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
        null
    )

    private fun content(
        commands: List<BudgetOutbox> = emptyList(),
        voided: Boolean = false
    ): BudgetContent = BudgetContent(
        OperationSyncState(scopeKey = "scope", initialSyncComplete = true, role = "OWNER"),
        listOf(
            LocalCategory("scope", "expense", "Расход", "EXPENSE", "HOME", "🏠", false, 1),
            LocalCategory("scope", "income", "Доход", "INCOME", "INCOME", "💰", false, 2)
        ),
        listOf(
            operation("payment", "20.00", if (voided) "VOIDED" else "POSTED"),
            operation("received", "1.00", if (voided) "VOIDED" else "POSTED")
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
                    modifiedAt = AT
                )
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
                    modifiedAt = AT
                )
            ),
            record("INCOME_MATCH", SyncPlanRecord("salary", ledgerId = "received"))
        ),
        commands
    )

    private fun command(
        type: String,
        amount: String? = null,
        id: String = "r",
        order: Long = 1
    ): BudgetOutbox {
        val c = BudgetSyncCommand("request$order", "h", "m", type, id, today.toString(), amount = amount)
        return BudgetOutbox(c.requestId, "scope", id, c.entityKind(), json.encodeToString(c), order)
    }

    @Test fun `metadata replacement clears descriptions and survives earlier acknowledgements`() {
        val first = BudgetSyncCommand(
            "first", "h", "m", "UPDATE_OPERATION", "payment", today.toString(),
            categoryId = "new-category", amount = "20.00", description = "новое", replaceDetails = true
        )
        val second = first.copy(requestId = "second", amount = "25.00", description = null)
        val commands = listOf(first, second).mapIndexed { i, c ->
            BudgetOutbox(
                c.requestId,
                "scope",
                "payment",
                "LEDGER",
                json.encodeToString(c),
                i.toLong(),
                status = if (i == 0) "ACKNOWLEDGED" else "PENDING",
                receiptRevision = if (i == 0) 2L else null
            )
        }
        val before = content(commands).let { it.copy(sync = it.sync.copy(protocolVersion = 4, lastRevision = 1)) }
        val projected = BudgetProjection(before, json, today).operations.getValue("payment")
        assertEquals("new-category", projected.categoryId)
        assertNull(projected.description)
        assertEquals("25.00", projected.originalAmount)
        val after = before.copy(
            sync = before.sync.copy(lastRevision = 2),
            operations = before.operations.map {
                if (it.id == "payment") it.copy(categoryId = "new-category", description = "новое", version = 1) else it
            }
        )
        assertEquals(projected, BudgetProjection(after, json, today).operations.getValue("payment"))
    }

    @Test fun `legacy amount commands retain category and description and omit the new flag`() {
        val c = BudgetSyncCommand(
            "legacy", "h", "m", "UPDATE_OPERATION", "payment", today.toString(),
            categoryId = "ignored", amount = "25.00", description = "ignored"
        )
        val encoded = json.encodeToString(c)
        assertFalse(encoded.contains("replaceDetails"))
        val command = BudgetOutbox(c.requestId, "scope", "payment", "LEDGER", encoded, 1)
        val source = content(listOf(command)).let { data ->
            data.copy(
                operations = data.operations.map {
                    if (it.id == "payment") it.copy(description = "старое") else it
                }
            )
        }
        val operation = BudgetProjection(source, json, today).operations.getValue("payment")
        assertEquals("expense", operation.categoryId)
        assertEquals("старое", operation.description)
        assertEquals("25.00", operation.originalAmount)
        val wireJson = kotlinx.serialization.json.Json { explicitNulls = false }
        val clear = wireJson.decodeFromString<BudgetSyncCommand>(
            wireJson.encodeToString(c.copy(description = null, replaceDetails = true))
        )
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
                today
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
                .expense.ruleVersion
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
                records = listOf(record("RECURRING_RULE", rule.copy(startsOn = "2028-01-31")))
            )
        val f = filter.copy(dateFrom = LocalDate.parse("2028-02-01"), dateTo = LocalDate.parse("2028-02-29"))
        assertEquals(
            LocalDate.parse("2028-02-29"),
            BudgetProjection(c, json, LocalDate.parse("2028-02-01"))
                .plans(f)
                .items
                .single()
                .dueOn
        )
        val weekly =
            c.copy(
                records =
                listOf(
                    record(
                        "RECURRING_RULE",
                        rule.copy(frequency = "WEEKLY", scheduleValue = 1, startsOn = "2028-02-01")
                    )
                )
            )
        assertEquals(
            listOf(7, 14, 21, 28),
            BudgetProjection(weekly, json, LocalDate.parse("2028-02-01")).plans(f).items.map {
                it.dueOn.dayOfMonth
            }
        )
    }

    @Test fun `v4 pending local changes remain visible over remotely archived and cancelled records`() {
        val categoryIntent =
            BudgetSyncCommand("category-change", "h", "m", "UPDATE_CATEGORY", "expense", today.toString(), name = "Мой расход", kind = "EXPENSE", icon = "HOME")
        val base =
            content(
                listOf(
                    BudgetOutbox(
                        categoryIntent.requestId,
                        "scope",
                        "expense",
                        "CATEGORY",
                        json.encodeToString(categoryIntent),
                        1,
                        protocolVersion = 4
                    ),
                    command("UPDATE_RULE", "150.00", order = 2).copy(protocolVersion = 4),
                    command("UPDATE_INCOME", "60.00", id = "other", order = 3).copy(protocolVersion = 4)
                )
            )
        val remote =
            base.copy(
                sync = base.sync.copy(protocolVersion = 4),
                categories = base.categories.map { if (it.id == "expense") it.copy(archived = true) else it },
                records =
                base.records.map { row ->
                    when {
                        row.kind == "RECURRING_RULE" -> record(row.kind, rule.copy(active = false))
                        row.kind == "RECURRING_OCCURRENCE" -> record(
                            row.kind,
                            occurrence.copy(status = "CANCELLED")
                        )
                        row.kind == "EXPECTED_INCOME" && row.id == "other" -> record(
                            row.kind,
                            json.decodeFromString<SyncPlanRecord>(row.payload).copy(status = "CANCELLED")
                        )
                        else -> row
                    }
                }
            )
        val projection = BudgetProjection(remote, json, today)
        assertEquals("Мой расход", projection.categoryList().first { it.id == "expense" }.name)
        val plans = projection.plans(filter)
        assertTrue(plans.items.any { it.id == "occurrence" })
        assertTrue(projection.rules.getValue("r").active == true)
        assertEquals(BigDecimal("259.00"), plans.totals.incomes)
        assertEquals(plans, projection.plans(filter))
    }

    @Test fun `once restored from sync stays singular overdue and keeps canonical payment`() {
        val once = rule.copy(frequency = "ONCE", scheduleValue = null, startsOn = "2026-02-01")
        val canonical = occurrence.copy(dueOn = "2026-02-01")
        val source = content().copy(
            records = listOf(
                record("RECURRING_RULE", once),
                record("RECURRING_OCCURRENCE", canonical),
                record("OCCURRENCE_PAYMENT", SyncPlanRecord("occurrence", ledgerId = "payment"))
            )
        )
        val projection = BudgetProjection(source, json, today)
        val extended = filter.copy(dateTo = today.plusYears(2))
        val first = projection.plans(extended).items.filterIsInstance<PlannedOperation.Expense>().single().expense
        assertEquals("occurrence", first.id)
        assertTrue(first.overdue)
        assertEquals(BigDecimal("20.00"), first.paidAmount)
        assertEquals(
            first,
            projection.plans(extended).items.filterIsInstance<PlannedOperation.Expense>().single().expense
        )
        val paid = source.copy(operations = listOf(operation("payment", "100.00")))
        assertEquals(
            BigDecimal.ZERO,
            BudgetProjection(paid, json, today).plans(extended).totals.expenses.stripTrailingZeros()
        )
    }

    @Test fun `once without canonical occurrence materializes only within horizon and survives reconstruction`() {
        val source = content().copy(
            records = listOf(
                record(
                    "RECURRING_RULE",
                    rule.copy(
                        frequency = "ONCE",
                        scheduleValue = null,
                        startsOn = "2026-02-20"
                    )
                )
            )
        )
        val projection = BudgetProjection(source, json, today)
        assertTrue(projection.plans(filter.copy(dateTo = today)).items.isEmpty())
        val first = projection.plans(filter).items.single()
        assertEquals("r@2026-02-20", first.id)
        assertEquals(listOf(first), projection.plans(filter.copy(dateTo = today.plusYears(2))).items)
        assertEquals(listOf(first), BudgetProjection(source, json, today).plans(filter).items)
    }

    companion object {
        private const val AT = "2026-01-01T00:00:00Z"
    }
}
