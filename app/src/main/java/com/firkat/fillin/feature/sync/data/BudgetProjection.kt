package com.firkat.fillin.feature.sync.data

import com.firkat.fillin.core.database.BudgetContent
import com.firkat.fillin.feature.categories.domain.model.*
import com.firkat.fillin.feature.dashboard.domain.calculation.*
import com.firkat.fillin.feature.operationhistory.data.local.LocalCategory
import com.firkat.fillin.feature.operationhistory.data.local.LocalOperation
import com.firkat.fillin.feature.operationhistory.domain.repository.*
import com.firkat.fillin.feature.plannedexpenses.domain.model.*
import com.firkat.fillin.feature.plannedincomes.domain.model.*
import com.firkat.fillin.feature.plannedoperations.domain.model.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.serialization.json.Json

/** A server snapshot plus an ordered overlay. Import never rewrites a user's queued intent. */
internal class BudgetProjection(
    private val content: BudgetContent,
    private val json: Json,
    private val today: LocalDate
) {
    val categories = content.categories.associateBy { it.id }.toMutableMap()
    val operations = content.operations.associateBy { it.id }.toMutableMap()
    val rules = linkedMapOf<String, SyncPlanRecord>()
    val occurrences = linkedMapOf<String, SyncPlanRecord>()
    val incomeRules = linkedMapOf<String, SyncPlanRecord>()
    val incomes = linkedMapOf<String, SyncPlanRecord>()
    private val expenseLinks = mutableListOf<SyncPlanRecord>()
    private val incomeLinks = mutableListOf<SyncPlanRecord>()
    private val overlays =
        content.commands.sortedBy { it.createdOrder }.filter {
            it.status != "CANCELLED" &&
                (it.status != "REJECTED" || content.sync.protocolVersion >= 4 && it.kind != "LEDGER") &&
                !(it.status == "ACKNOWLEDGED" && (it.receiptRevision ?: Long.MAX_VALUE) <= content.sync.lastRevision)
        }
    private val scope = content.sync.scopeKey
    private val ruleEffectiveDates = mutableMapOf<String, LocalDate>()
    private val changedIncomeRules = mutableSetOf<String>()
    private var rulesApplied = false
    private var linksApplied = false

    init {
        content.records.filter { it.kind in PLAN_KINDS }.forEach {
            val record = json.decodeFromString<SyncPlanRecord>(it.payload)
            when (it.kind) {
                "RECURRING_RULE" -> rules[record.id] = record
                "RECURRING_OCCURRENCE" -> occurrences[record.id] = record
                "INCOME_RULE" -> incomeRules[record.id] = record
                "EXPECTED_INCOME" -> incomes[record.id] = record
                "OCCURRENCE_PAYMENT" -> expenseLinks += record
                "INCOME_MATCH" -> incomeLinks += record
            }
        }
        overlays.filter { it.kind == "CATEGORY" }.forEach { overlayCategory(json.decodeFromString(it.intent)) }
        overlays.filter {
            it.kind == "EXPECTED_INCOME" &&
                json.decodeFromString<BudgetSyncCommand>(it.intent).type != "CONFIRM_REMAINDER_INCOME"
        }.forEach { overlayIncome(json.decodeFromString(it.intent)) }
        overlays.filter { it.kind == "INCOME_RULE" }.forEach {
            val command = json.decodeFromString<BudgetSyncCommand>(it.intent)
            if (command.entityId !in incomeRules && command.type == "UPDATE_INCOME_RULE") {
                incomes[command.entityId]?.let { legacy ->
                    incomeRules[command.entityId] = legacy
                    incomes[command.entityId] = legacy.copy(ruleId = command.entityId, scheduledOn = legacy.dueOn)
                }
            }
            expandIncomes(today.plusYears(5))
            overlayIncome(command, incomeRules)
            changedIncomeRules += command.entityId
        }
        expandIncomes(today.plusYears(5))
        overlays.filter {
            it.kind == "EXPECTED_INCOME" &&
                json.decodeFromString<BudgetSyncCommand>(it.intent).type == "CONFIRM_REMAINDER_INCOME"
        }
            .forEach { overlayIncome(json.decodeFromString(it.intent)) }
        overlays.filter { it.kind == "LEDGER" }.forEach { item ->
            val command = json.decodeFromString<BudgetSyncCommand>(item.intent)
            val operation = operations[command.entityId]
            if (operation != null && command.type in LINK_COMMANDS && linkEligible(item)) {
                operations[operation.id] = operation.copy(version = operation.version + 1, syncStatus = "PENDING")
            }
            if (operation != null && command.type == "UPDATE_OPERATION") {
                operations[operation.id] =
                    operation.copy(
                        categoryId = if (command.replaceDetails) {
                            requireNotNull(
                                command.categoryId
                            )
                        } else {
                            operation.categoryId
                        },
                        description = if (command.replaceDetails) command.description else operation.description,
                        originalAmount = requireNotNull(command.amount),
                        amountKzt = command.amount,
                        version = operation.version + 1,
                        status = "POSTED",
                        syncStatus = "PENDING"
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
                            syncStatus = "PENDING"
                        )
                    operations[operation.id] = operation.copy(version = operation.version + 1)
                }
            }
        }
    }

    private fun linkEligible(
        item: com.firkat.fillin.core.database.BudgetOutbox,
        visiting: Set<String> = emptySet()
    ): Boolean {
        if (item.status !in setOf("PENDING", "ACKNOWLEDGED") || item.requestId in visiting) return false
        val ids =
            listOfNotNull(item.predecessor, item.dependency) + json.decodeFromString<List<String>>(item.dependencies)
        return ids.all { id ->
            content.commands.firstOrNull { it.requestId == id }?.let {
                linkEligible(
                    it,
                    visiting + item.requestId
                )
            } !=
                false
        }
    }

    fun categoryList(): List<Category> = categories.values.filterNot { it.archived }.sortedBy { it.name }.map {
        Category(
            it.id,
            it.name,
            if (it.kind == "INCOME") CategoryKind.Income else CategoryKind.Expense,
            CategoryIcon(it.iconCode, it.iconEmoji),
            it.dictionaryKey,
            it.revision
        )
    }

    private fun overlayCategory(
        c: BudgetSyncCommand
    ) {
        val old =
            categories[c.entityId]
                ?: if (c.type == "UPDATE_CATEGORY" &&
                    c.name != null &&
                    c.kind != null &&
                    c.icon != null
                ) {
                    LocalCategory(scope, c.entityId, c.name, c.kind, c.icon, "📦", false, 0)
                } else {
                    null
                }
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
                            "📦",
                            false,
                            0
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
                            if (c.icon != null) "📦" else old.iconEmoji
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

    private fun overlayIncome(
        c: BudgetSyncCommand,
        target: MutableMap<String, SyncPlanRecord> = incomes
    ) {
        val old =
            target[c.entityId]
                ?: if (c.type.removeSuffix("_RULE") == "UPDATE_INCOME" &&
                    c.categoryId != null &&
                    c.amount != null &&
                    c.dueOn != null
                ) {
                    SyncPlanRecord(
                        c.entityId,
                        categoryId = c.categoryId,
                        amount = c.amount,
                        dueOn = c.dueOn,
                        status = "SCHEDULED",
                        version = 0
                    )
                } else {
                    null
                }
        val at = "${c.authoredOn}T00:00:00Z"
        when (c.type.removeSuffix("_RULE")) {
            "CREATE_INCOME" -> {
                if (old == null) {
                    target[c.entityId] =
                        SyncPlanRecord(
                            c.entityId,
                            incomeName = c.incomeName, incomeFrequency = c.incomeFrequency, incomeActive = c.incomeActive,
                            categoryId = c.categoryId,
                            amount = c.amount,
                            dueOn = c.dueOn,
                            status = "SCHEDULED",
                            version = 0,
                            createdAt = at,
                            modifiedAt = at
                        )
                }
            }

            "UPDATE_INCOME" -> {
                if (old !=
                    null
                ) {
                    target[c.entityId] =
                        old.copy(
                            incomeName = c.incomeName ?: old.incomeName,
                            incomeFrequency =
                            c.incomeFrequency ?: old.incomeFrequency,
                            incomeActive = c.incomeActive ?: old.incomeActive,
                            categoryId = c.categoryId ?: old.categoryId,
                            amount = c.amount ?: old.amount,
                            dueOn = c.dueOn ?: old.dueOn,
                            status = if (content.sync.protocolVersion >= 4) "SCHEDULED" else old.status,
                            version = (old.version ?: 0) + 1,
                            modifiedAt = at
                        )
                }
            }

            "CONFIRM_REMAINDER_INCOME" -> if (old != null) {
                target[c.entityId] = old.copy(
                    remainderAmount = c.remainderAmount,
                    remainderDueOn = c.remainderDueOn,
                    remainderReceived = c.remainderReceived,
                    version = (old.version ?: 0) + 1
                )
            }
            "DELETE_INCOME" -> {
                if (old !=
                    null
                ) {
                    target[c.entityId] = old.copy(status = "CANCELLED", version = (old.version ?: 0) + 1)
                }
            }
        }
    }

    private fun expandIncomes(
        through: LocalDate
    ) {
        val pendingLinked = overlays.filter { it.kind == "LEDGER" && linkEligible(it) }.mapNotNull {
            val command = json.decodeFromString<BudgetSyncCommand>(it.intent)
            command.planTarget?.planId?.takeIf { command.type == "SET_OPERATION_PLAN_LINK" }
        }.toSet()
        incomeRules.values.forEach { rule ->
            if (rule.incomeActive == false || rule.status == "CANCELLED") {
                deactivateIncomeOccurrences(rule, pendingLinked)
                return@forEach
            }
            val anchor = LocalDate.parse(rule.dueOn)
            val frequency = com.firkat.fillin.feature.dailylimit.domain.IncomeFrequency.valueOf(
                rule.incomeFrequency ?: "ONCE"
            )
            val dates = com.firkat.fillin.feature.dailylimit.domain.ForecastEventGenerator.dates(
                anchor,
                frequency,
                through
            ).toSet()
            // Materialized/linked history is immutable when a future schedule changes.
            incomes.replaceAll { id, old ->
                if (old.ruleId != rule.id ||
                    rule.id !in changedIncomeRules ||
                    old.status != "SCHEDULED" ||
                    LocalDate.parse(old.scheduledOn ?: old.dueOn) < today ||
                    (received(id).signum() > 0 || id in pendingLinked)
                ) {
                    old
                } else {
                    val updated = old.copy(
                        status = if (LocalDate.parse(old.scheduledOn ?: old.dueOn) in
                            dates
                        ) {
                            rule.status
                        } else {
                            "CANCELLED"
                        },
                        amount = rule.amount,
                        categoryId = rule.categoryId,
                        incomeName = rule.incomeName,
                        incomeActive = rule.incomeActive,
                        incomeFrequency = rule.incomeFrequency
                    )
                    if (updated != old && content.records.any { it.kind == "EXPECTED_INCOME" && it.id == id }) {
                        updated.copy(version = (old.version ?: 0) + 1)
                    } else {
                        updated
                    }
                }
            }
            dates.filter { rule.id !in changedIncomeRules || it >= today }.forEach { date ->
                val id =
                    incomes.values.firstOrNull {
                        it.ruleId == rule.id && (it.scheduledOn ?: it.dueOn) == date.toString()
                    }?.id
                        ?: java.util.UUID.nameUUIDFromBytes(
                            "income:${rule.id}:$date".toByteArray(Charsets.UTF_8)
                        ).toString()
                if (id !in
                    incomes
                ) {
                    incomes[id] = rule.copy(
                        id = id,
                        ruleId = rule.id,
                        dueOn = date.toString(),
                        version = 0,
                        scheduledOn = date.toString(),
                        remainderAmount = null,
                        remainderDueOn = null,
                        remainderReceived = null
                    )
                }
            }
        }
    }

    private fun deactivateIncomeOccurrences(
        rule: SyncPlanRecord,
        pendingLinked: Set<String>
    ) {
        incomes.replaceAll { id, old ->
            if (old.ruleId != rule.id) {
                old
            } else {
                val deletedLocally = rule.id in changedIncomeRules && rule.status == "CANCELLED"
                val toCancel = old.status == "SCHEDULED" &&
                    LocalDate.parse(old.scheduledOn ?: old.dueOn) >= today &&
                    received(id).signum() == 0 &&
                    id !in pendingLinked
                if (deletedLocally && toCancel) {
                    old.copy(incomeActive = false, status = "CANCELLED", version = (old.version ?: 0) + 1)
                } else {
                    old.copy(incomeActive = false)
                }
            }
        }
    }

    fun plans(
        filter: PlannedOperationsFilter
    ): PlannedOperations {
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
        applyPlanCommands(to)
        val expenseItems =
            occurrences.values.mapNotNull { o ->
                val due = LocalDate.parse(o.dueOn)
                val amount = requireNotNull(o.amount).toBigDecimal()
                val paid = paid(o.id)
                val remaining = (amount - paid).max(BigDecimal.ZERO)
                if (o.status != "SCHEDULED" || remaining.signum() == 0 || due < from || due > to) return@mapNotNull null
                val rule = rules[o.ruleId] ?: return@mapNotNull null
                PlannedOperation.Expense(
                    PlannedExpense(
                        o.id,
                        requireNotNull(o.categoryId),
                        categories[o.categoryId]?.name.orEmpty(),
                        amount,
                        paid,
                        remaining,
                        currency,
                        due,
                        due < today,
                        PlannedExpenseStatus.Scheduled,
                        o.version ?: 0,
                        instant(o.createdAt),
                        instant(o.modifiedAt),
                        requireNotNull(o.ruleId),
                        rule.version ?: 0
                    )
                )
            }
        val incomeItems =
            incomes.values.mapNotNull { i ->
                val due = LocalDate.parse(i.dueOn)
                val received = received(i.id)
                val remaining = (requireNotNull(i.amount).toBigDecimal() - received).max(BigDecimal.ZERO)
                if (i.status != "SCHEDULED" ||
                    remaining.signum() == 0 ||
                    due < from ||
                    due > to ||
                    categories[i.categoryId]?.archived == true
                ) {
                    return@mapNotNull null
                }
                PlannedOperation.Income(
                    PlannedIncome(
                        i.id,
                        requireNotNull(i.categoryId),
                        categories[i.categoryId]?.name.orEmpty(),
                        requireNotNull(i.amount).toBigDecimal(),
                        currency,
                        due,
                        due < today,
                        PlannedIncomeStatus.Scheduled,
                        i.version ?: 0,
                        instant(i.createdAt),
                        instant(i.modifiedAt),
                        name = i.incomeName ?: categories[i.categoryId]?.name.orEmpty(),
                        frequency = com.firkat.fillin.feature.dailylimit.domain.IncomeFrequency.valueOf(
                            i.incomeFrequency ?: "ONCE"
                        ),
                        active = i.incomeActive != false, ruleId = i.ruleId,
                        scheduledOn = LocalDate.parse(
                            i.scheduledOn ?: i.dueOn
                        ),
                        remainderConfirmed = i.remainderReceived?.toBigDecimal()?.compareTo(received) == 0,
                        receivedAmount = received, remainingAmount = remaining
                    )
                )
            }
        val totals =
            PlannedOperationTotals(
                expenseItems.sumOf {
                    it.expense.remainingAmount
                },
                incomeItems.sumOf { it.income.remainingAmount }
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
            totals
        )
    }

    private fun contribution(
        ledgerId: String?
    ): BigDecimal {
        val operation =
            operations[ledgerId]?.takeIf { it.status == "POSTED" && it.syncStatus != "REJECTED" }
                ?: return BigDecimal.ZERO
        val refunds = operations.values.filter {
            it.refundOf == ledgerId &&
                it.status == "POSTED" &&
                it.syncStatus != "REJECTED"
        }.sumOf { it.accountingAmount.toBigDecimal() }
        return (operation.accountingAmount.toBigDecimal() - refunds).max(BigDecimal.ZERO)
    }

    private fun paidBefore(
        id: String,
        order: Long
    ): BigDecimal {
        val links = expenseLinks.toMutableList()
        overlays.filter { it.createdOrder < order && linkEligible(it) }.forEach { item ->
            val c = json.decodeFromString<BudgetSyncCommand>(item.intent)
            if (c.type in LINK_COMMANDS) {
                links.removeAll { it.ledgerId == c.entityId }
                val target = c.planTarget
                if (c.type == "SET_OPERATION_PLAN_LINK" && target?.type == "EXPENSE") {
                    occurrences.values.firstOrNull { it.ruleId == target.ruleId && it.dueOn == target.dueOn }?.let {
                        links += SyncPlanRecord(it.id, ledgerId = c.entityId)
                    }
                }
            }
        }
        return links.filter { it.id == id }.sumOf { contribution(it.ledgerId) }
    }

    private fun paid(
        id: String
    ): BigDecimal = expenseLinks.filter { it.id == id }.sumOf { contribution(it.ledgerId) }
    private fun received(
        id: String
    ): BigDecimal = incomeLinks.filter { it.id == id }.sumOf { contribution(it.ledgerId) }

    fun links(
        operationId: String
    ): OperationPlanLinks = planLinkContext(operationId).links

    fun planLinkContext(
        operationId: String
    ): OperationPlanLinkContext {
        // Matching is independent of the Plans tab's period and includes historic open occurrences.
        applyPlanCommands(today.plusYears(LINK_HORIZON_YEARS))
        val operation = operations[operationId] ?: return OperationPlanLinkContext()
        fun describe(
            record: SyncPlanRecord,
            type: String
        ): SuggestedPlan {
            val rule = if (type == "EXPENSE") rules[record.ruleId] else incomeRules[record.ruleId]
            val frequency = if (type == "EXPENSE") rule?.frequency else rule?.incomeFrequency ?: record.incomeFrequency
            return SuggestedPlan(
                PlanLinkTarget(
                    type,
                    record.id.takeUnless {
                        it.contains("@")
                    },
                    record.ruleId,
                    requireNotNull(record.dueOn),
                    record.scheduledOn
                ),
                requireNotNull(record.categoryId),
                categories[record.categoryId]?.name.orEmpty(),
                requireNotNull(record.amount),
                (
                    record.amount.toBigDecimal() - if (type ==
                        "EXPENSE"
                    ) {
                        paid(record.id)
                    } else {
                        received(record.id)
                    }
                    ).max(BigDecimal.ZERO).toPlainString(),
                record.version ?: 0,
                name = if (type == "EXPENSE") rule?.ruleName else rule?.incomeName ?: record.incomeName,
                frequency = when (frequency) {
                    "WEEKLY" -> PlanningFrequency.Weekly
                    "BIWEEKLY" -> PlanningFrequency.Biweekly
                    "MONTHLY" -> PlanningFrequency.Monthly
                    else -> PlanningFrequency.Once
                },
                currency = currency
            )
        }
        val isLinked = expenseLinks.any { it.ledgerId == operationId } ||
            incomeLinks.any { it.ledgerId == operationId }
        val current =
            expenseLinks.filter {
                it.ledgerId == operationId
            }.mapNotNull { occurrences[it.id]?.let { record -> describe(record, "EXPENSE") } } +
                incomeLinks.filter {
                    it.ledgerId == operationId
                }.mapNotNull { incomes[it.id]?.let { record -> describe(record, "INCOME") } }
        val amount = contribution(operationId)
        val category = categories[operation.categoryId]
        val validAmountAndCategory = category?.archived == false && amount.signum() > 0
        if (!validAmountAndCategory || !isLinkable(operation)) {
            return OperationPlanLinkContext(links = OperationPlanLinks(current = current), isLinked = isLinked)
        }
        requireNotNull(category)
        val records = if (operation.type == "EXPENSE") occurrences.values else incomes.values
        val candidates = records.filter {
            it.status == "SCHEDULED" &&
                it.categoryId == operation.categoryId &&
                categories[it.categoryId]?.archived == false
        }.map { describe(it, operation.type) }.filter { candidate ->
            candidate.remainingAmount.toBigDecimal().signum() > 0 &&
                current.none {
                    it.target.type == candidate.target.type && it.target.stableKey == candidate.target.stableKey
                }
        }
            .sortedWith(
                compareBy<SuggestedPlan>(
                    {
                        if (it.target.dueOn <
                            operation.occurredOn
                        ) {
                            0
                        } else if (it.target.dueOn == operation.occurredOn) {
                            1
                        } else {
                            2
                        }
                    },
                    { it.target.dueOn },
                    { it.target.stableKey }
                )
            )
        return OperationPlanLinkContext(
            PlanLinkOperation(
                operation.version, operation.type, category.id, category.name, category.iconCode, currency,
                operation.occurredOn, operation.accountingAmount, amount.toPlainString()
            ),
            OperationPlanLinks(candidates, current),
            isLinked
        )
    }

    private fun isLinkable(
        operation: LocalOperation
    ): Boolean {
        val postedInBudget = operation.scopeKey == scope &&
            operation.status == "POSTED" &&
            operation.syncStatus != "REJECTED"
        val supportedType = operation.type in setOf("EXPENSE", "INCOME") && operation.refundOf == null
        return postedInBudget && supportedType && !operation.hasOriginalValuation
    }

    private fun applyPlanCommands(
        through: LocalDate
    ) {
        // Explicit links must be projected even when their target lies outside the visible period.
        val linkedThrough = overlays.filter {
            linkEligible(it)
        }.map { json.decodeFromString<BudgetSyncCommand>(it.intent) }
            .filter { it.type == "SET_OPERATION_PLAN_LINK" && it.planTarget?.type == "EXPENSE" }
            .map { LocalDate.parse(requireNotNull(it.planTarget).dueOn) }.maxOrNull()
        val materializationThrough = maxOf(through, linkedThrough ?: through)
        // Preserve old periods before changing a rule, just as the server's materialized occurrences do.
        if (!rulesApplied) {
            overlays.filter { it.kind == "RECURRING_RULE" }.forEach { item ->
                val c = json.decodeFromString<BudgetSyncCommand>(item.intent)
                val old =
                    rules[c.entityId]
                        ?: if (c.type == "UPDATE_RULE" &&
                            c.categoryId != null &&
                            c.amount != null &&
                            c.startsOn != null
                        ) {
                            SyncPlanRecord(c.entityId, categoryId = c.categoryId, amount = c.amount, frequency = c.frequency, scheduleValue = c.scheduleValue, startsOn = c.startsOn, active = true, version = 0, createdAt = "${c.authoredOn}T00:00:00Z", modifiedAt = "${c.authoredOn}T00:00:00Z")
                        } else {
                            null
                        }
                val effective = LocalDate.parse(c.authoredOn)
                when (c.type) {
                    "CREATE_RULE" -> {
                        if (old == null) {
                            rules[c.entityId] =
                                SyncPlanRecord(
                                    c.entityId,
                                    categoryId = c.categoryId,
                                    ruleName = c.ruleName,
                                    amount = c.amount,
                                    frequency = c.frequency,
                                    scheduleValue = c.scheduleValue,
                                    startsOn = c.startsOn,
                                    active = true,
                                    version = 0,
                                    createdAt = "${c.authoredOn}T00:00:00Z",
                                    modifiedAt = "${c.authoredOn}T00:00:00Z"
                                )
                        }
                    }

                    "UPDATE_RULE", "DELETE_RULE" -> {
                        if (old != null) {
                            val restoring =
                                c.type == "UPDATE_RULE" && content.sync.protocolVersion >= 4 && old.active == false
                            val baseRule = if (restoring) old.copy(active = true) else old
                            if (restoring) {
                                occurrences.replaceAll { _, occurrence ->
                                    if (occurrence.ruleId == c.entityId &&
                                        occurrence.status == "CANCELLED" &&
                                        LocalDate.parse(occurrence.dueOn) >= effective
                                    ) {
                                        occurrence.copy(status = "SCHEDULED")
                                    } else {
                                        occurrence
                                    }
                                }
                            }
                            rules[c.entityId] = baseRule
                            materialize(baseRule, materializationThrough)
                            if (c.type == "UPDATE_RULE") {
                                val updated = baseRule.copy(
                                    amount = c.amount,
                                    ruleName =
                                    c.ruleName ?: baseRule.ruleName,
                                    categoryId = c.categoryId ?: baseRule.categoryId,
                                    frequency = c.frequency ?: baseRule.frequency,
                                    startsOn = c.startsOn ?: baseRule.startsOn,
                                    scheduleValue = if (c.frequency ==
                                        "ONCE"
                                    ) {
                                        null
                                    } else {
                                        c.scheduleValue ?: baseRule.scheduleValue
                                    },
                                    version = (old.version ?: 0) + 1
                                )
                                fun matches(
                                    date: LocalDate
                                ): Boolean {
                                    val start = LocalDate.parse(updated.startsOn)
                                    if (date < start) return false
                                    return when (updated.frequency) {
                                        "ONCE" -> date == start
                                        "WEEKLY" -> date.dayOfWeek.value == updated.scheduleValue
                                        else ->
                                            date.dayOfMonth ==
                                                minOf(requireNotNull(updated.scheduleValue), date.lengthOfMonth())
                                    }
                                }
                                occurrences.replaceAll { _, o ->
                                    if (o.ruleId == c.entityId &&
                                        LocalDate.parse(o.dueOn) >= effective &&
                                        (o.status == "SCHEDULED" || c.replaceDetails && o.status == "CANCELLED") &&
                                        paidBefore(o.id, item.createdOrder).signum() == 0
                                    ) {
                                        o.copy(
                                            amount = c.amount,
                                            categoryId = updated.categoryId,
                                            status = if (matches(
                                                    LocalDate.parse(o.dueOn)
                                                )
                                            ) {
                                                "SCHEDULED"
                                            } else {
                                                "CANCELLED"
                                            },
                                            version =
                                            (o.version ?: 0) + 1
                                        )
                                    } else {
                                        o
                                    }
                                }
                                rules[c.entityId] = updated
                                if (c.replaceDetails) ruleEffectiveDates[c.entityId] = effective
                                // Generate only future dates; old and settled occurrences remain untouched.
                                val generationStart = maxOf(effective, LocalDate.parse(updated.startsOn))
                                materialize(
                                    updated.copy(
                                        startsOn = if (updated.frequency ==
                                            "ONCE"
                                        ) {
                                            updated.startsOn
                                        } else {
                                            generationStart.toString()
                                        }
                                    ),
                                    materializationThrough
                                )
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
        rules.values.forEach { materialize(it, materializationThrough) }
        overlays.filter { it.kind == "RECURRING_OCCURRENCE" }.forEach { item ->
            val c = json.decodeFromString<BudgetSyncCommand>(item.intent)
            occurrences[c.entityId]?.let {
                occurrences[c.entityId] =
                    it.copy(status = "PAID", version = (it.version ?: 0) + 1)
            }
        }
        rulesApplied = true
        if (!linksApplied) {
            overlays.filter { linkEligible(it) }.forEach { item ->
                val c = json.decodeFromString<BudgetSyncCommand>(item.intent)
                if (c.type in LINK_COMMANDS) {
                    expenseLinks.removeAll { it.ledgerId == c.entityId }
                    incomeLinks.removeAll { it.ledgerId == c.entityId }
                    c.planTarget?.takeIf { c.type == "SET_OPERATION_PLAN_LINK" }?.let { target ->
                        val id = if (target.type == "EXPENSE") {
                            occurrences.values.firstOrNull {
                                it.ruleId == target.ruleId && it.dueOn == target.dueOn
                            }?.id
                        } else {
                            target.planId
                        }
                        if (id != null) {
                            val link = SyncPlanRecord(id, ledgerId = c.entityId)
                            if (target.type == "EXPENSE") expenseLinks += link else incomeLinks += link
                        }
                    }
                }
            }
            linksApplied = true
        }
    }

    fun planningRules(
        includeSettled: Boolean = false
    ): List<PlanningRule> {
        // Apply the same overlay as payment projections, without selecting a payment period.
        applyPlanCommands(today)
        val result = mutableListOf<PlanningRule>()
        rules.values.filter { it.active == true }.forEach { r ->
            val frequency = when (r.frequency) {
                "ONCE" -> PlanningFrequency.Once
                "WEEKLY" -> PlanningFrequency.Weekly
                "MONTHLY" -> PlanningFrequency.Monthly
                else -> return@forEach
            }
            if (!includeSettled && frequency == PlanningFrequency.Once) {
                val occurrence = occurrences.values.firstOrNull { it.ruleId == r.id }
                if (occurrence != null &&
                    (
                        occurrence.status != "SCHEDULED" ||
                            paid(occurrence.id) >= requireNotNull(occurrence.amount).toBigDecimal()
                        )
                ) {
                    return@forEach
                }
            }
            val category = categories[r.categoryId]
            result += PlanningRule(
                r.id, r.version ?: 0, PlannedOperationType.Expense,
                requireNotNull(r.categoryId), category?.name.orEmpty(), category?.iconCode.orEmpty(),
                r.ruleName ?: category?.name.orEmpty(),
                requireNotNull(
                    r.amount
                ).toBigDecimal(),
                currency = currency, frequency = frequency,
                startsOn = LocalDate.parse(r.startsOn), scheduleValue = r.scheduleValue
            )
        }
        fun addIncome(
            r: SyncPlanRecord,
            rule: Boolean
        ) {
            if (r.incomeActive == false || !includeSettled && r.status != "SCHEDULED") return
            val frequency = when (r.incomeFrequency ?: "ONCE") {
                "ONCE" -> PlanningFrequency.Once
                "WEEKLY" -> PlanningFrequency.Weekly
                "BIWEEKLY" -> PlanningFrequency.Biweekly
                "MONTHLY" -> PlanningFrequency.Monthly
                else -> return
            }
            if (!includeSettled && frequency == PlanningFrequency.Once) {
                val item = if (rule) incomes.values.firstOrNull { it.ruleId == r.id } ?: r else r
                if (item.status != "SCHEDULED" ||
                    received(item.id) >= requireNotNull(item.amount).toBigDecimal()
                ) {
                    return
                }
            }
            val category = categories[r.categoryId]
            result += PlanningRule(
                r.id, r.version ?: 0, PlannedOperationType.Income,
                requireNotNull(r.categoryId), category?.name.orEmpty(), category?.iconCode.orEmpty(),
                r.incomeName?.takeIf { it.isNotBlank() } ?: category?.name.orEmpty(),
                requireNotNull(r.amount).toBigDecimal(), currency = currency, frequency = frequency,
                startsOn = LocalDate.parse(r.dueOn), isIncomeRule = rule
            )
        }
        incomeRules.values.forEach { addIncome(it, true) }
        incomes.values.filter { it.ruleId == null && it.id !in incomeRules }.forEach { addIncome(it, false) }
        return result
    }

    private fun materialize(
        rule: SyncPlanRecord,
        through: LocalDate
    ) {
        if (rule.active != true) return
        val originalStart = LocalDate.parse(rule.startsOn)
        val start = maxOf(originalStart, ruleEffectiveDates[rule.id] ?: originalStart)
        if (rule.frequency == "ONCE") {
            if (originalStart >= start &&
                start <= through &&
                occurrences.values.none { it.ruleId == rule.id && (it.scheduledOn ?: it.dueOn) == start.toString() }
            ) {
                val id = "${rule.id}@$start"
                occurrences[id] = SyncPlanRecord(
                    id, rule.categoryId, rule.id, amount = rule.amount,
                    dueOn = start.toString(), status = "SCHEDULED", version = 0,
                    createdAt = rule.createdAt, modifiedAt = rule.modifiedAt
                )
            }
            return
        }
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
                .flatMap { listOfNotNull(it.dueOn, it.scheduledOn) }
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
                        modifiedAt = rule.modifiedAt
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

    val currency: String get() = content.sync.currency

    fun dashboardInput(
        asOf: Instant
    ): DashboardCalculationInput {
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
                    dateTo = horizon
                )
            )
        val eligible = operations.values.filter { it.status == "POSTED" && it.syncStatus != "REJECTED" }
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
                    it.accountingAmount.toBigDecimal(),
                    LocalDate.parse(it.occurredOn),
                    it.occurredAt?.let(Instant::parse)
                        ?: LocalDate.parse(it.occurredOn).atStartOfDay(ZoneId.of("Asia/Almaty")).toInstant(),
                    it.refundOf
                )
            },
            categories.values.map { DashboardCategory(it.id, it.name, it.kind == "EXPENSE") },
            plans.items.filterIsInstance<PlannedOperation.Expense>().map {
                DashboardPayment(it.id, it.expense.categoryName, it.dueOn, it.expense.remainingAmount)
            },
            incomes.values.filter {
                it.status == "SCHEDULED" &&
                    it.incomeActive != false &&
                    categories[it.categoryId]?.archived != true
            }.mapNotNull {
                val received = received(it.id)
                val remaining = (requireNotNull(it.amount).toBigDecimal() - received).max(BigDecimal.ZERO)
                if (remaining.signum() == 0) {
                    null
                } else if (received.signum() > 0 && it.remainderReceived?.toBigDecimal()?.compareTo(received) != 0) {
                    null
                } else {
                    DashboardExpectedIncome(
                        LocalDate.parse(
                            if (received.signum() >
                                0
                            ) {
                                it.remainderDueOn ?: it.dueOn
                            } else {
                                it.dueOn
                            }
                        ),
                        if (received.signum() >
                            0
                        ) {
                            minOf(remaining, it.remainderAmount?.toBigDecimal() ?: BigDecimal.ZERO)
                        } else {
                            remaining
                        },
                        it.id,
                        it.incomeName ?: categories[it.categoryId]?.name.orEmpty()
                    )
                }
            },
            excluded,
            operations.values.filter { it.type == "EXPENSE" }.mapTo(hashSetOf()) { it.id },
            if (incomes.values.any {
                    it.status == "SCHEDULED" &&
                        it.incomeActive != false &&
                        received(it.id).signum() > 0 &&
                        received(it.id) < requireNotNull(it.amount).toBigDecimal() &&
                        it.remainderReceived?.toBigDecimal()?.compareTo(received(it.id)) != 0
                }
            ) {
                setOf(com.firkat.fillin.feature.dailylimit.domain.ForecastWarning.UNCONFIRMED_REMAINDER)
            } else {
                emptySet()
            },
            currency = currency
        )
    }

    companion object {
        private const val LINK_HORIZON_YEARS = 5L
        val LINK_COMMANDS = setOf("SET_OPERATION_PLAN_LINK", "REMOVE_OPERATION_PLAN_LINK")
        private val PLAN_KINDS =
            setOf(
                "INCOME_RULE",
                "RECURRING_RULE",
                "RECURRING_OCCURRENCE",
                "EXPECTED_INCOME",
                "OCCURRENCE_PAYMENT",
                "INCOME_MATCH"
            )
    }

    private fun instant(
        value: String?
    ): Instant = value?.let(Instant::parse) ?: Instant.EPOCH
}
