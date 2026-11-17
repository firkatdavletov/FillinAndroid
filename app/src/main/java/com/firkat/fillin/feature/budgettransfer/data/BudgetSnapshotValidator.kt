package com.firkat.fillin.feature.budgettransfer.data

import com.firkat.fillin.core.database.BudgetContent
import com.firkat.fillin.core.database.BudgetOutbox
import com.firkat.fillin.core.database.LocalSyncRecord
import com.firkat.fillin.feature.budgetprofile.domain.BudgetCurrency
import com.firkat.fillin.feature.operationhistory.data.local.LocalOperation
import com.firkat.fillin.feature.operationhistory.data.local.OperationSyncState
import com.firkat.fillin.feature.operationhistory.data.local.requireMoney
import com.firkat.fillin.feature.operationhistory.data.remote.CreateOperationDto
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationPlanOffer
import com.firkat.fillin.feature.operationhistory.domain.repository.SuggestedPlan
import com.firkat.fillin.feature.sync.data.BudgetProjection
import com.firkat.fillin.feature.sync.data.BudgetSyncCommand
import com.firkat.fillin.feature.sync.data.SyncBudgetSettings
import com.firkat.fillin.feature.sync.data.SyncPlanRecord
import com.firkat.fillin.feature.sync.data.SyncSavingsGoal
import com.firkat.fillin.feature.sync.data.SyncVariableSpendingExclusion
import com.firkat.fillin.feature.sync.data.entityKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

internal class BudgetSnapshotValidator(private val json: Json) {
    fun validate(
        snapshot: BudgetSnapshot,
        invalid: (SnapshotRow) -> Nothing
    ) {
        fun checkRow(
            type: String,
            id: String,
            block: () -> Unit
        ) {
            try {
                require(id.isNotBlank())
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: RuntimeException) {
                invalid(type to id)
            }
        }
        val meta = snapshot.metadata
        checkRow("budget", "1") {
            require(meta.protocolVersion in 2..4 && meta.lastRevision >= 0)
            require(meta.name == null || meta.name.length in 1..80 && meta.name.none(Char::isISOControl))
            Instant.parse(meta.accountingStartedAt)
            require(snapshot.records.count { it.kind == "BUDGET_SETTINGS" } == 1)
            requireUnique(snapshot.operations.mapNotNull { it.requestId })
            requireUnique(snapshot.usageEvents.map { it.operationId })
        }

        val ids = SnapshotReferences(
            operations = snapshot.operations.associateBy { it.id },
            categories = snapshot.categories.map { it.id }.toSet() +
                snapshot.commands.filter { it.kind == "CATEGORY" }.map { it.entityId },
            records = snapshot.records.mapTo(hashSetOf()) { it.kind to it.id },
            commandOrder = snapshot.commands.associate { it.requestId to it.createdOrder }
        )
        snapshot.categories.forEach { category ->
            checkRow("category", category.id) {
                require(category.kind in setOf("INCOME", "EXPENSE") && category.revision >= 0)
                require(category.name.isNotBlank())
            }
        }
        snapshot.operations.forEach { operation ->
            checkRow("operation", operation.id) { validateOperation(operation, ids) }
        }
        snapshot.records.forEach { record ->
            checkRow("record:${record.kind}", record.id) { validateRecord(record, ids) }
        }
        snapshot.commands.forEach { command ->
            checkRow("command", command.requestId) { validateCommand(command, ids) }
        }
        snapshot.legacyCommands.forEach { command ->
            checkRow("legacy_command", command.requestId) {
                val request = json.decodeFromString<CreateOperationDto>(command.payload)
                require(request.requestId == command.requestId && command.operationId in ids.operations)
                require(request.categoryId in ids.categories)
                requireMoney(request.amount.toBigDecimal())
                LocalDate.parse(request.occurredOn)
            }
        }
        snapshot.proposals.forEach { proposal ->
            checkRow("proposal", proposal.operationId) {
                require(proposal.decision in setOf("PENDING", "NONE", "CONFIRMED", "DISMISSED", "DECLINED", "ACCEPTED"))
                proposal.payload?.let { payload ->
                    val wrapper = json.parseToJsonElement(payload).jsonObject
                    val offer = wrapper["offer"]
                    if (offer == null) {
                        json.decodeFromString<SuggestedPlan>(payload)
                    } else {
                        json.decodeFromJsonElement(OperationPlanOffer.serializer(), offer)
                    }
                }
            }
        }
        snapshot.usageEvents.forEach {
            checkRow("category_usage", it.id) {
                // Historical usage can outlive its operation or category.
                require(it.operationId.isNotBlank() && it.categoryId.isNotBlank())
            }
        }
        checkRow("budget", "1") { verifyProjection(snapshot) }
    }

    private fun validateOperation(
        operation: LocalOperation,
        ids: SnapshotReferences
    ) {
        require(operation.categoryId in ids.categories)
        require(operation.type in setOf("INCOME", "EXPENSE", "EXPENSE_REFUND"))
        require(operation.status in setOf("POSTED", "VOIDED"))
        require(operation.syncStatus in setOf("SYNCED", "PENDING", "REJECTED"))
        require(operation.version >= 0 && operation.sequence >= 0)
        require(BudgetCurrency.entries.any { it.name == operation.originalCurrency })
        requireMoney(operation.originalAmount.toBigDecimal())
        requireMoney(operation.accountingAmount.toBigDecimal())
        LocalDate.parse(operation.occurredOn)
        Instant.parse(operation.createdAt)
        operation.occurredAt?.let(Instant::parse)
        operation.rubKztRate?.let { require(it.toBigDecimal().signum() > 0) }
        if (operation.type == "EXPENSE_REFUND") {
            require(ids.operations[operation.refundOf]?.type == "EXPENSE")
        } else {
            require(operation.refundOf == null)
        }
    }

    private fun validateRecord(
        record: LocalSyncRecord,
        ids: SnapshotReferences
    ) {
        require(record.revision >= 0)
        when (record.kind) {
            "BUDGET_SETTINGS" -> {
                val settings = json.decodeFromString<SyncBudgetSettings>(record.payload)
                require(record.id == settings.id)
                validateDecimal(settings.openingBalance)
                validateDecimal(settings.protectedReserve, nonNegative = true)
            }
            "SAVINGS_GOAL" -> {
                val goal = json.decodeFromString<SyncSavingsGoal>(record.payload)
                require(record.id == goal.id)
                validateDecimal(goal.allocatedAmount, nonNegative = true)
            }
            "VARIABLE_SPENDING_EXCLUSION" -> {
                require(record.id == json.decodeFromString<SyncVariableSpendingExclusion>(record.payload).id)
            }
            in PLAN_TYPES -> validatePlan(record, ids)
            else -> error("Unknown record type")
        }
    }

    private fun validatePlan(
        record: LocalSyncRecord,
        ids: SnapshotReferences
    ) {
        val plan = json.decodeFromString<SyncPlanRecord>(record.payload)
        if (record.kind == "OCCURRENCE_PAYMENT" || record.kind == "INCOME_MATCH") {
            require(plan.ledgerId in ids.operations && record.id == "${plan.id}:${plan.ledgerId}")
            val targetKind = if (record.kind == "OCCURRENCE_PAYMENT") "RECURRING_OCCURRENCE" else "EXPECTED_INCOME"
            require(targetKind to plan.id in ids.records)
            return
        }
        require(record.id == plan.id && plan.ledgerId == null)
        require(plan.categoryId in ids.categories && plan.version != null && plan.version >= 0)
        requireMoney(requireNotNull(plan.amount).toBigDecimal())
        listOfNotNull(plan.startsOn, plan.dueOn, plan.scheduledOn, plan.remainderDueOn).forEach(LocalDate::parse)
        listOfNotNull(plan.createdAt, plan.modifiedAt).forEach(Instant::parse)
        plan.remainderAmount?.let { requireMoney(it.toBigDecimal()) }
        plan.remainderReceived?.let { validateDecimal(it, nonNegative = true) }
        if (record.kind == "RECURRING_RULE") {
            require(plan.startsOn != null && plan.active != null)
            require(
                when (plan.frequency) {
                    "ONCE" -> plan.scheduleValue == null
                    "WEEKLY" -> plan.scheduleValue in 1..7
                    "MONTHLY" -> plan.scheduleValue in 1..31
                    else -> false
                }
            )
        } else {
            require(plan.dueOn != null && plan.status in setOf("SCHEDULED", "CANCELLED", "PAID"))
        }
        if (record.kind == "RECURRING_OCCURRENCE") require("RECURRING_RULE" to plan.ruleId in ids.records)
    }

    private fun validateCommand(
        command: BudgetOutbox,
        ids: SnapshotReferences
    ) {
        val intent = json.decodeFromString<BudgetSyncCommand>(command.intent)
        require(intent.requestId == command.requestId && intent.entityId == command.entityId)
        require(intent.entityKind() == command.kind)
        require(command.status in setOf("PENDING", "ACKNOWLEDGED", "CONFLICT", "REJECTED", "BLOCKED", "CANCELLED"))
        require(command.protocolVersion in 2..4 && command.createdOrder in 0 until Long.MAX_VALUE)
        LocalDate.parse(intent.authoredOn)
        listOfNotNull(intent.startsOn, intent.dueOn, intent.occurredOn, intent.scheduledOn).forEach(LocalDate::parse)
        intent.amount?.let { validateDecimal(it, nonNegative = true) }
        intent.planTarget?.let { target ->
            require(target.type in setOf("INCOME", "EXPENSE"))
            if (target.type == "EXPENSE") require(!target.ruleId.isNullOrBlank())
            if (target.type == "INCOME") require(!target.planId.isNullOrBlank())
            // The planner expands linked expenses through the target date.
            require(LocalDate.parse(target.dueOn) <= LocalDate.now(ACCOUNTING_ZONE).plusYears(PLAN_HORIZON_YEARS))
            target.scheduledOn?.let(LocalDate::parse)
        }
        if (command.status == "ACKNOWLEDGED") require(command.receiptRevision != null)
        val predecessors = json.decodeFromString<List<String>>(command.dependencies) +
            listOfNotNull(command.dependency, command.predecessor)
        // Receipt cleanup and historical mutations can leave references to commands no longer present.
        require(predecessors.none { it == command.requestId })
        require(predecessors.all { (ids.commandOrder[it] ?: -1) < command.createdOrder })
    }

    private fun verifyProjection(
        snapshot: BudgetSnapshot
    ) {
        val state = OperationSyncState(
            accountingStartedAt = snapshot.metadata.accountingStartedAt,
            protocolVersion = snapshot.metadata.protocolVersion,
            lastRevision = snapshot.metadata.lastRevision,
            currency = snapshot.metadata.currency.name
        )
        val content =
            BudgetContent(state, snapshot.categories, snapshot.operations, snapshot.records, snapshot.commands)
        val today = LocalDate.now(ACCOUNTING_ZONE)
        val projection = BudgetProjection(content, json, today)
        projection.dashboardInput(Instant.now())
        projection.planningRules(includeSettled = true)
    }

    private fun validateDecimal(
        value: String,
        nonNegative: Boolean = false
    ) {
        val amount = value.toBigDecimal()
        require(amount.scale() <= 2 && amount.abs() <= MAX_AMOUNT)
        require(!nonNegative || amount.signum() >= 0)
    }

    private fun requireUnique(
        ids: List<String>
    ) {
        require(ids.toSet().size == ids.size)
    }

    private data class SnapshotReferences(
        val operations: Map<String, LocalOperation>,
        val categories: Set<String>,
        val records: Set<SnapshotRow>,
        val commandOrder: Map<String, Long>
    )

    private companion object {
        const val PLAN_HORIZON_YEARS = 5L
        val ACCOUNTING_ZONE: ZoneId = ZoneId.of("Asia/Almaty")
        val MAX_AMOUNT = "99999999999999999.99".toBigDecimal()
        val PLAN_TYPES = setOf(
            "RECURRING_RULE",
            "RECURRING_OCCURRENCE",
            "OCCURRENCE_PAYMENT",
            "INCOME_RULE",
            "EXPECTED_INCOME",
            "INCOME_MATCH"
        )
    }
}
