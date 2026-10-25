package com.firkat.fillin.feature.sync.data

import com.firkat.fillin.core.database.BudgetContent
import com.firkat.fillin.feature.categories.domain.model.*
import com.firkat.fillin.feature.dashboard.domain.calculation.*
import com.firkat.fillin.feature.operationhistory.data.local.LocalCategory
import com.firkat.fillin.feature.operationhistory.data.local.LocalOperation
import com.firkat.fillin.feature.plannedexpenses.domain.model.*
import com.firkat.fillin.feature.plannedincomes.domain.model.*
import com.firkat.fillin.feature.plannedoperations.domain.model.*
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** A server snapshot plus an ordered overlay. Import never rewrites a user's queued intent. */
internal class BudgetProjection(
    private val content: BudgetContent,
    private val json: Json,
    private val today: LocalDate,
) {
    val categories = content.categories.associateBy { it.id }.toMutableMap()
    val operations = content.operations.associateBy { it.id }.toMutableMap()
    val rules = linkedMapOf<String, SyncPlanRecord>()
    val occurrences = linkedMapOf<String, SyncPlanRecord>()
    val incomes = linkedMapOf<String, SyncPlanRecord>()
    private val expenseLinks = mutableListOf<SyncPlanRecord>()
    private val incomeLinks = mutableListOf<SyncPlanRecord>()
    private val overlays =
        content.commands.sortedBy { it.createdOrder }.filter {
            it.status != "CANCELLED" && (it.status != "REJECTED" || content.sync.protocolVersion >= 4 && it.kind != "LEDGER") &&
                !(it.status == "ACKNOWLEDGED" && (it.receiptRevision ?: Long.MAX_VALUE) <= content.sync.lastRevision)
        }
    private val scope = content.sync.scopeKey
    private var rulesApplied = false

    init {
        content.records.filter { it.kind in PLAN_KINDS }.forEach {
            val record = json.decodeFromString<SyncPlanRecord>(it.payload)
            when (it.kind) {
                "RECURRING_RULE" -> rules[record.id] = record
                "RECURRING_OCCURRENCE" -> occurrences[record.id] = record
                "EXPECTED_INCOME" -> incomes[record.id] = record
                "OCCURRENCE_PAYMENT" -> expenseLinks += record
                "INCOME_MATCH" -> incomeLinks += record
            }
        }
        overlays.filter { it.kind == "CATEGORY" }.forEach { overlayCategory(json.decodeFromString(it.intent)) }
        overlays.filter { it.kind == "EXPECTED_INCOME" }.forEach { overlayIncome(json.decodeFromString(it.intent)) }
        overlays.filter { it.kind == "LEDGER" }.forEach { item ->
            val command = json.decodeFromString<BudgetSyncCommand>(item.intent)
            val operation = operations[command.entityId]
            if (operation != null && command.type == "UPDATE_OPERATION") {
                operations[operation.id] =
                    operation.copy(
                        originalAmount = requireNotNull(command.amount),
                        amountKzt = command.amount,
                        version = operation.version + 1,
                        status = "POSTED",
                        syncStatus = "PENDING",
                    )
            }
            if (operation != null && command.type == "REFUND_OPERATION") {
                val date = LocalDate.parse(command.authoredOn)
                val at = date.atStartOfDay(ZoneId.of("Asia/Almaty")).toInstant().toString()
                val refundId = requireNotNull(command.refundId)
                // The canonical snapshot may already contain a committed refund after a lost response.
                if (refundId !in operations) {
                    operations[refundId] =
                        operation.copy(
                            id = refundId,
                            type = "EXPENSE_REFUND",
                            originalAmount = requireNotNull(command.amount),
                            amountKzt = command.amount,
                            occurredOn = command.authoredOn,
                            occurredAt = at,
                            createdAt = at,
                            refundOf = operation.id,
                            requestId = command.requestId,
                            version = 0,
                            sequence = 0,
                            syncStatus = "PENDING",
                        )
                    operations[operation.id] = operation.copy(version = operation.version + 1)
                }
            }
        }
    }

    fun categoryList(): List<Category> =
        categories.values.filterNot { it.archived }.sortedBy { it.name }.map {
            Category(
                it.id,
                it.name,
                if (it.kind == "INCOME") CategoryKind.Income else CategoryKind.Expense,
                CategoryIcon(it.iconCode, it.iconEmoji),
                it.dictionaryKey,
                it.revision,
            )
        }

    private fun overlayCategory(c: BudgetSyncCommand) {
        val old = categories[c.entityId] ?: if (c.type == "UPDATE_CATEGORY" && c.name != null && c.kind != null && c.icon != null) LocalCategory(scope, c.entityId, c.name, c.kind, c.icon, CategoryIconType.fromCode(c.icon)?.emoji ?: "📦", false, 0) else null
        when (c.type) {
            "CREATE_CATEGORY" -> {
                if (old == null) {
                    categories[c.entityId] =
                        LocalCategory(
                            scope,
                            c.entityId,
                            requireNotNull(c.name),
                            requireNotNull(c.kind),
                            requireNotNull(c.icon),
                            CategoryIconType.fromCode(c.icon)?.emoji ?: "📦",
                            false,
                            0,
                        )
                }
            }

            "UPDATE_CATEGORY" -> {
                if (old != null) {
                    categories[c.entityId] =
                        old.copy(
                            name = c.name ?: old.name,
                            archived = if (content.sync.protocolVersion >= 4) false else old.archived,
                            iconCode = c.icon ?: old.iconCode,
                            iconEmoji =
                                c.icon?.let { CategoryIconType.fromCode(it)?.emoji } ?: old.iconEmoji,
                        )
                }
            }

            "DELETE_CATEGORY" -> {
                if (old != null) {
                    categories[c.entityId] = old.copy(archived = true)
                    categories.values.firstOrNull { it.dictionaryKey == "OTHER" }?.let { fallback ->
                        operations.replaceAll { _, operation ->
                            if (operation.categoryId ==
                                c.entityId
                            ) {
                                operation.copy(categoryId = fallback.id)
                            } else {
                                operation
                            }
                        }
                    }
                }
            }
        }
    }

    private fun overlayIncome(c: BudgetSyncCommand) {
        val old = incomes[c.entityId] ?: if (c.type == "UPDATE_INCOME" && c.categoryId != null && c.amount != null && c.dueOn != null) SyncPlanRecord(c.entityId, categoryId = c.categoryId, amount = c.amount, dueOn = c.dueOn, status = "SCHEDULED", version = 0) else null
        val at = "${c.authoredOn}T00:00:00Z"
        when (c.type) {
            "CREATE_INCOME" -> {
                if (old == null) {
                    incomes[c.entityId] =
                        SyncPlanRecord(
                            c.entityId,
                            categoryId = c.categoryId,
                            amount = c.amount,
                            dueOn = c.dueOn,
                            status = "SCHEDULED",
                            version = 0,
                            createdAt = at,
                            modifiedAt = at,
                        )
                }
            }

            "UPDATE_INCOME" -> {
                if (old !=
                    null
                ) {
                    incomes[c.entityId] =
                        old.copy(
                            categoryId = c.categoryId ?: old.categoryId,
                            amount = c.amount ?: old.amount,
                            dueOn = c.dueOn ?: old.dueOn,
                            status = if (content.sync.protocolVersion >= 4) "SCHEDULED" else old.status,
                            version = (old.version ?: 0) + 1,
                            modifiedAt = at,
                        )
                }
            }

            "DELETE_INCOME" -> {
                if (old !=
                    null
                ) {
                    incomes[c.entityId] = old.copy(status = "CANCELLED", version = (old.version ?: 0) + 1)
                }
            }
        }
    }

    fun plans(filter: PlannedOperationsFilter): PlannedOperations {
        val from = filter.dateFrom ?: today
        val to =
            filter.dateTo
                ?: if (filter.period ==
                    PlannedOperationPeriod.EndOfYear
                ) {
                    today.withDayOfYear(today.lengthOfYear())
                } else {
                    YearMonth.from(today).atEndOfMonth()
                }
        require(from <= to && to <= today.plusYears(5))
        // Preserve old periods before changing a rule, just as the server's materialized occurrences do.
        if (!rulesApplied) {
            overlays.filter { it.kind == "RECURRING_RULE" }.forEach { item ->
                val c = json.decodeFromString<BudgetSyncCommand>(item.intent)
                val old = rules[c.entityId] ?: if (c.type == "UPDATE_RULE" && c.categoryId != null && c.amount != null && c.startsOn != null) SyncPlanRecord(c.entityId, categoryId = c.categoryId, amount = c.amount, frequency = c.frequency, scheduleValue = c.scheduleValue, startsOn = c.startsOn, active = true, version = 0, createdAt = "${c.authoredOn}T00:00:00Z", modifiedAt = "${c.authoredOn}T00:00:00Z") else null
                val effective = LocalDate.parse(c.authoredOn)
                when (c.type) {
                    "CREATE_RULE" -> {
                        if (old == null) {
                            rules[c.entityId] =
                                SyncPlanRecord(
                                    c.entityId,
                                    categoryId = c.categoryId,
                                    amount = c.amount,
                                    frequency = c.frequency,
                                    scheduleValue = c.scheduleValue,
                                    startsOn = c.startsOn,
                                    active = true,
                                    version = 0,
                                    createdAt = "${c.authoredOn}T00:00:00Z",
                                    modifiedAt = "${c.authoredOn}T00:00:00Z",
                                )
                        }
                    }

                    "UPDATE_RULE", "DELETE_RULE" -> {
                        if (old != null) {
                            val restoring = c.type == "UPDATE_RULE" && content.sync.protocolVersion >= 4 && old.active == false
                            val baseRule = if (restoring) old.copy(active = true) else old
                            if (restoring) {
                                occurrences.replaceAll { _, occurrence ->
                                    if (occurrence.ruleId == c.entityId && occurrence.status == "CANCELLED" && LocalDate.parse(occurrence.dueOn) >= effective) occurrence.copy(status = "SCHEDULED") else occurrence
                                }
                            }
                            rules[c.entityId] = baseRule
                            materialize(baseRule, to)
                            if (c.type == "UPDATE_RULE") {
                                occurrences.replaceAll { _, o ->
                                    if (o.ruleId == c.entityId && LocalDate.parse(o.dueOn) >= effective &&
                                        o.status == "SCHEDULED" &&
                                        paid(o.id).signum() == 0
                                    ) {
                                        o.copy(amount = c.amount, version = (o.version ?: 0) + 1)
                                    } else {
                                        o
                                    }
                                }
                                rules[c.entityId] = baseRule.copy(amount = c.amount, version = (old.version ?: 0) + 1)
                            } else {
                                occurrences.replaceAll { _, o ->
                                    if (o.ruleId == c.entityId &&
                                        LocalDate.parse(o.dueOn) >= effective &&
                                        o.status == "SCHEDULED"
                                    ) {
                                        o.copy(status = "CANCELLED", version = (o.version ?: 0) + 1)
                                    } else {
                                        o
                                    }
                                }
                                rules[c.entityId] = old.copy(active = false, version = (old.version ?: 0) + 1)
                            }
                        }
                    }
                }
            }
        }
        rules.values.forEach { materialize(it, to) }
        overlays.filter { it.kind == "RECURRING_OCCURRENCE" }.forEach { item ->
            val c = json.decodeFromString<BudgetSyncCommand>(item.intent)
            occurrences[c.entityId]?.let {
                occurrences[c.entityId] =
                    it.copy(status = "PAID", version = (it.version ?: 0) + 1)
            }
        }
        rulesApplied = true
        val expenseItems =
            occurrences.values.mapNotNull { o ->
                val due = LocalDate.parse(o.dueOn)
                val amount = requireNotNull(o.amount).toBigDecimal()
                val paid = paid(o.id)
                val remaining = (amount - paid).max(BigDecimal.ZERO)
                if (o.status != "SCHEDULED" || remaining.signum() == 0 || due < from || due > to || categories[o.categoryId]?.archived == true) return@mapNotNull null
                val rule = rules[o.ruleId] ?: return@mapNotNull null
                PlannedOperation.Expense(
                    PlannedExpense(
                        o.id,
                        requireNotNull(o.categoryId),
                        categories[o.categoryId]?.name.orEmpty(),
                        amount,
                        paid,
                        remaining,
                        "KZT",
                        due,
                        due < today,
                        PlannedExpenseStatus.Scheduled,
                        o.version ?: 0,
                        instant(o.createdAt),
                        instant(o.modifiedAt),
                        requireNotNull(o.ruleId),
                        rule.version ?: 0,
                    ),
                )
            }
        val incomeItems =
            incomes.values.mapNotNull { i ->
                val due = LocalDate.parse(i.dueOn)
                val fulfilled = incomeLinks.any { it.id == i.id && operations[it.ledgerId]?.status == "POSTED" }
                if (i.status != "SCHEDULED" || fulfilled || due < from || due > to) return@mapNotNull null
                PlannedOperation.Income(
                    PlannedIncome(
                        i.id,
                        requireNotNull(i.categoryId),
                        categories[i.categoryId]?.name.orEmpty(),
                        requireNotNull(i.amount).toBigDecimal(),
                        "KZT",
                        due,
                        due < today,
                        PlannedIncomeStatus.Scheduled,
                        i.version ?: 0,
                        instant(i.createdAt),
                        instant(i.modifiedAt),
                    ),
                )
            }
        val totals =
            PlannedOperationTotals(
                expenseItems.sumOf {
                    it.expense.remainingAmount
                },
                incomeItems.sumOf { it.income.plannedAmount },
            )
        return PlannedOperations(
            today,
            from,
            to,
            (expenseItems + incomeItems)
                .filter {
                    filter.type == null ||
                        it.type == filter.type
                }.sortedWith(compareBy({ it.dueOn }, { it.id })),
            totals,
        )
    }

    private fun paid(id: String): BigDecimal =
        expenseLinks.filter { it.id == id }.sumOf { link ->
            operations[link.ledgerId]?.takeIf { it.status == "POSTED" && it.syncStatus != "REJECTED" }?.originalAmount?.toBigDecimal() ?: BigDecimal.ZERO
        }

    private fun materialize(
        rule: SyncPlanRecord,
        through: LocalDate,
    ) {
        if (rule.active != true) return
        val start = LocalDate.parse(rule.startsOn)
        val value = requireNotNull(rule.scheduleValue)
        var due =
            if (rule.frequency ==
                "MONTHLY"
            ) {
                YearMonth.from(start).let { it.atDay(value.coerceAtMost(it.lengthOfMonth())) }
            } else {
                start.plusDays(((value - start.dayOfWeek.value + 7) % 7).toLong())
            }
        val existing =
            occurrences.values
                .filter { it.ruleId == rule.id }
                .map { it.dueOn }
                .toHashSet()
        while (due <= through) {
            if (due >= start && due.toString() !in existing) {
                val id = "${rule.id}@$due"
                occurrences[id] =
                    SyncPlanRecord(
                        id,
                        rule.categoryId,
                        rule.id,
                        amount = rule.amount,
                        dueOn = due.toString(),
                        status = "SCHEDULED",
                        version = 0,
                        createdAt = rule.createdAt,
                        modifiedAt = rule.modifiedAt,
                    )
            }
            due =
                if (rule.frequency ==
                    "MONTHLY"
                ) {
                    YearMonth.from(due).plusMonths(1).let { it.atDay(value.coerceAtMost(it.lengthOfMonth())) }
                } else {
                    due.plusWeeks(1)
                }
        }
    }

    fun dashboardInput(asOf: Instant): DashboardCalculationInput {
        val settings =
            content.records
                .single { it.kind == "BUDGET_SETTINGS" }
                .let { json.decodeFromString<SyncBudgetSettings>(it.payload) }
        val horizon = YearMonth.from(today.plusMonths(2)).atEndOfMonth()
        val plans =
            plans(
                PlannedOperationsFilter(
                    period = PlannedOperationPeriod.Custom,
                    dateFrom = LocalDate.of(1, 1, 1),
                    dateTo = horizon,
                ),
            )
        val eligible = operations.values.filter { it.status == "POSTED" && it.syncStatus != "REJECTED" }
        val fulfilled =
            incomeLinks
                .filter { link ->
                    eligible.any { it.id == link.ledgerId }
                }.mapTo(hashSetOf()) { it.id }
        // The server matches queued KZT incomes when they are posted, in queue order.
        val matchedLedgerIds = incomeLinks.mapTo(hashSetOf()) { it.ledgerId }
        eligible
            .filter {
                it.syncStatus in setOf("PENDING", "ACKNOWLEDGED") && it.id !in matchedLedgerIds &&
                    it.type == "INCOME" && it.originalCurrency == "KZT"
            }.sortedWith(
                compareBy<LocalOperation>(
                    { if (it.syncStatus == "ACKNOWLEDGED") 0 else 1 },
                    { if (it.syncStatus == "ACKNOWLEDGED") it.sequence else 0 },
                    { Instant.parse(it.createdAt) },
                ),
            ).forEach { operation ->
                val date = LocalDate.parse(operation.occurredOn)
                incomes.values
                    .filter {
                        it.status == "SCHEDULED" && it.id !in fulfilled &&
                            it.categoryId == operation.categoryId
                    }.minWithOrNull(
                        compareBy<SyncPlanRecord>(
                            { kotlin.math.abs(ChronoUnit.DAYS.between(LocalDate.parse(it.dueOn), date)) },
                            { it.dueOn },
                            { it.id },
                        ),
                    )?.let { fulfilled += it.id }
            }
        val excluded = expenseLinks.mapNotNullTo(hashSetOf()) { it.ledgerId }
        content.records.filter { it.kind == "VARIABLE_SPENDING_EXCLUSION" }.forEach {
            excluded += json.decodeFromString<SyncVariableSpendingExclusion>(it.payload).id
        }
        return DashboardCalculationInput(
            asOf,
            Instant.parse(requireNotNull(content.sync.accountingStartedAt)),
            settings.openingBalance.toBigDecimal(),
            settings.protectedReserve.toBigDecimal(),
            content.records.filter { it.kind == "SAVINGS_GOAL" }.sumOf {
                json.decodeFromString<SyncSavingsGoal>(it.payload).allocatedAmount.toBigDecimal()
            },
            eligible.map {
                DashboardOperation(
                    it.id,
                    it.categoryId,
                    when (it.type) {
                        "INCOME" -> DashboardOperationKind.Income
                        "EXPENSE" -> DashboardOperationKind.Expense
                        "EXPENSE_REFUND" -> DashboardOperationKind.Refund
                        else -> error("Unknown operation type")
                    },
                    it.amountKzt.toBigDecimal(),
                    LocalDate.parse(it.occurredOn),
                    it.occurredAt?.let(Instant::parse)
                        ?: LocalDate.parse(it.occurredOn).atStartOfDay(ZoneId.of("Asia/Almaty")).toInstant(),
                    it.refundOf,
                )
            },
            categories.values.map { DashboardCategory(it.id, it.name, it.kind == "EXPENSE") },
            plans.items.filterIsInstance<PlannedOperation.Expense>().map {
                DashboardPayment(it.id, it.expense.categoryName, it.dueOn, it.expense.remainingAmount)
            },
            incomes.values.filter { it.status == "SCHEDULED" && it.id !in fulfilled }.map {
                DashboardExpectedIncome(LocalDate.parse(it.dueOn), requireNotNull(it.amount).toBigDecimal())
            },
            excluded,
            operations.values.filter { it.type == "EXPENSE" }.mapTo(hashSetOf()) { it.id },
        )
    }

    companion object {
        private val PLAN_KINDS =
            setOf("RECURRING_RULE", "RECURRING_OCCURRENCE", "EXPECTED_INCOME", "OCCURRENCE_PAYMENT", "INCOME_MATCH")
    }

    private fun instant(value: String?): Instant = value?.let(Instant::parse) ?: Instant.EPOCH
}
