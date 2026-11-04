package com.firkat.fillin.feature.sync.data

import androidx.room.withTransaction
import com.firkat.fillin.feature.operationhistory.domain.repository.*
import com.firkat.fillin.core.database.*
import com.firkat.fillin.feature.operationhistory.data.local.*
import com.firkat.fillin.feature.operationhistory.data.sync.OperationSyncScheduler
import com.firkat.fillin.feature.plannedoperations.domain.model.*
import com.firkat.fillin.feature.sync.domain.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class BudgetLocalFailure(
    val code: String,
) : Exception(code)

class BudgetStore(
    private val database: OperationDatabase,
    private val scheduler: OperationSyncScheduler,
    private val json: Json,
    private val clock: Clock = Clock.system(ZoneId.of("Asia/Almaty")),
) : BudgetSyncControl, OperationPlanLinkStore {
    private val dao get() = database.budget()

    internal suspend fun accountingStart() = java.time.Instant.parse(requireNotNull(dao.content()).sync.accountingStartedAt).atZone(ZoneId.of("Asia/Almaty")).toLocalDate()

    internal fun currentDate() = today()
    private fun today() = LocalDate.now(clock.withZone(ZoneId.of("Asia/Almaty")))

    internal fun project(content: BudgetContent) = BudgetProjection(content, json, today())

    internal suspend fun snapshot(): BudgetProjection = project(requireNotNull(dao.content()))

    override val context: Flow<BudgetSyncContext> =
        dao.observeContent().map { content ->
            if (content == null) {
                BudgetSyncContext()
            } else {
                val blocked = content.sync.errorCode in setOf("email_not_verified", "scope_changed", "unauthorized", "access_denied")
                BudgetSyncContext(
                    content.sync.localReady || content.sync.initialSyncComplete,
                    (content.sync.localReady || content.sync.initialSyncComplete) && content.sync.role == "OWNER" && (!blocked || content.sync.protocolVersion >= 4),
                    content.sync.isSyncing,
                    content.commands.count {
                        it.status == "PENDING" ||
                            it.status == "ACKNOWLEDGED" &&
                            (it.receiptRevision ?: Long.MAX_VALUE) > content.sync.lastRevision
                    },
                    content.sync.errorCode,
                    content.commands
                        .filter {
                            it.status in setOf("CONFLICT", "REJECTED", "BLOCKED")
                        }.sortedBy { it.createdOrder }
                        .map { issue(content, it) },
                    content.commands
                        .filter {
                            it.status == "PENDING" ||
                                it.status == "ACKNOWLEDGED" &&
                                (it.receiptRevision ?: Long.MAX_VALUE) > content.sync.lastRevision
                        }.sortedBy {
                            it.createdOrder
                        }.map { issue(content, it).let { issue -> "${issue.title}: ${issue.local}" } },
                    cloudEnabled = content.sync.cloudEnabled,
                    lastSyncedAt = content.sync.lastSyncedAt,
                    keepLocalAccess = content.sync.protocolVersion >= 4,
                )
            }
        }

    fun observeCategories() =
        dao.observeContent().map { content ->
            if (content == null ||
                content.sync.protocolVersion < 4 && content.sync.errorCode in setOf("unauthorized", "access_denied", "scope_changed")
            ) {
                emptyList()
            } else {
                project(content).categoryList()
            }
        }

    fun observePlans(filter: PlannedOperationsFilter) =
        dao
            .observeContent()
            .filter {
                (it?.sync?.localReady == true || it?.sync?.initialSyncComplete == true) ||
                    it?.sync?.errorCode in setOf("unauthorized", "access_denied", "scope_changed")
            }.map { content ->
                if (content == null || !(content.sync.localReady || content.sync.initialSyncComplete)) throw BudgetLocalFailure("not_initialized")
                if (content.sync.protocolVersion < 4 && content.sync.errorCode in
                    setOf("unauthorized", "access_denied", "scope_changed")
                ) {
                    throw BudgetLocalFailure("forbidden")
                }
                project(content).plans(filter)
            }.flowOn(kotlinx.coroutines.Dispatchers.Default)

    fun observePlanningRules() =
        dao
            .observeContent()
            .filter {
                (it?.sync?.localReady == true || it?.sync?.initialSyncComplete == true) ||
                    it?.sync?.errorCode in setOf("unauthorized", "access_denied", "scope_changed")
            }.map { content ->
                if (content == null || !(content.sync.localReady || content.sync.initialSyncComplete)) throw BudgetLocalFailure("not_initialized")
                if (content.sync.protocolVersion < 4 && content.sync.errorCode in
                    setOf("unauthorized", "access_denied", "scope_changed")
                ) {
                    throw BudgetLocalFailure("forbidden")
                }
                project(content).planningRules()
            }.flowOn(kotlinx.coroutines.Dispatchers.Default)

    fun observeHistoryContent(): Flow<LocalHistoryContent?> =
        dao.observeContent().map { c ->
            c?.let {
                project(it).let { p ->
                    LocalHistoryContent(it.sync, p.operations.values.toList(), p.categories.values.toList())
                }
            }
        }

    suspend fun historySnapshot(): LocalHistoryContent? =
        dao.content()?.let { c ->
            project(c).let { LocalHistoryContent(c.sync, it.operations.values.toList(), it.categories.values.toList()) }
        }

    override val pendingProposal = dao.observePendingProposal()

    override fun observeLinks(operationId: String): Flow<OperationPlanLinks> = dao.observeContent().map {
        it?.let { content -> project(content).links(operationId) } ?: OperationPlanLinks()
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)

    override suspend fun proposal(operationId: String): SuggestedPlan? {
        val content = dao.content() ?: return null
        return dao.proposal(content.sync.scopeKey, operationId)?.takeIf { it.decision == "PENDING" }?.payload?.let { json.decodeFromString(it) }
    }

    suspend fun suggest(operationId: String): SuggestedPlan? {
        val content = requireNotNull(dao.content())
        val plan = project(content).links(operationId).candidates.firstOrNull()
        dao.putProposal(OperationPlanProposal(content.sync.scopeKey, operationId, plan?.let { json.encodeToString(it) }, if (plan == null) "NONE" else "PENDING"))
        return plan
    }

    override suspend fun selectProposal(operationId: String, plan: SuggestedPlan) = database.withTransaction {
        val content = requireNotNull(dao.content())
        writable(content, allowMember = true)
        val current = project(content).links(operationId).candidates.firstOrNull { it.target.stableKey == plan.target.stableKey && it.target.type == plan.target.type }
            ?: throw BudgetLocalFailure("plan_unavailable")
        dao.putProposal(OperationPlanProposal(content.sync.scopeKey, operationId, json.encodeToString(current), "PENDING"))
    }

    override suspend fun dismissProposal(operationId: String) = database.withTransaction {
        val content = dao.content() ?: return@withTransaction
        val old = dao.proposal(content.sync.scopeKey, operationId)
        dao.putProposal(OperationPlanProposal(content.sync.scopeKey, operationId, old?.payload, "DISMISSED"))
    }

    override suspend fun setLink(operationId: String, plan: SuggestedPlan, operationVersion: Long) {
        database.withTransaction {
            enqueue("SET_OPERATION_PLAN_LINK", operationId, operationVersion) {
                it.copy(planTarget = plan.target, expectedPlanVersion = plan.version)
            }
            val scope = requireNotNull(dao.content()).sync.scopeKey
            dao.putProposal(OperationPlanProposal(scope, operationId, json.encodeToString(plan), "CONFIRMED"))
        }
    }

    override suspend fun removeLink(operationId: String, operationVersion: Long) {
        database.withTransaction {
            enqueue("REMOVE_OPERATION_PLAN_LINK", operationId, operationVersion)
            dismissProposal(operationId)
        }
    }

    override fun requestSync() = scheduler.schedule()

    suspend fun enqueue(
        type: String,
        entityId: String = UUID.randomUUID().toString(),
        expectedVersion: Long? = null,
        observedCategoryRevision: Long? = null,
        edit: (BudgetSyncCommand) -> BudgetSyncCommand = { it },
    ): String {
        database.withTransaction {
            val content = dao.content() ?: throw BudgetLocalFailure("not_initialized")
            writable(content, type in setOf("CREATE_OPERATION", "UPDATE_OPERATION", "REFUND_OPERATION") || type in BudgetProjection.LINK_COMMANDS)
            val projection = project(content)
            var command =
                edit(
                    BudgetSyncCommand(
                        UUID.randomUUID().toString(),
                        content.sync.householdId,
                        content.sync.memberId,
                        type,
                        entityId,
                        today().toString(),
                    ),
                )
            if (content.sync.protocolVersion >= 4) {
                val category = projection.categories[entityId]
                val income = projection.incomeRules[entityId] ?: projection.incomes[entityId]
                if (type == "UPDATE_CATEGORY" && category != null) command = command.copy(name = command.name ?: category.name, icon = command.icon ?: category.iconCode, kind = category.kind)
                if (type in setOf("UPDATE_INCOME", "UPDATE_INCOME_RULE") && income != null) command = command.copy(categoryId = command.categoryId ?: income.categoryId, amount = command.amount ?: income.amount, dueOn = command.dueOn ?: income.dueOn)
                if (type == "UPDATE_RULE") {
                    projection.plans(PlannedOperationsFilter())
                    projection.rules[entityId]?.let { rule -> command = command.copy(categoryId = command.categoryId ?: rule.categoryId, frequency = command.frequency ?: rule.frequency, scheduleValue = if (command.frequency == "ONCE") null else command.scheduleValue ?: rule.scheduleValue, startsOn = command.startsOn ?: rule.startsOn) }
                }
                if (type in setOf("UPDATE_OPERATION", "REFUND_OPERATION")) {
                    projection.operations[entityId]?.let { operation ->
                        command = command.copy(
                            categoryId = if (command.replaceDetails) command.categoryId else operation.categoryId,
                            kind = operation.type, occurredOn = operation.occurredOn,
                            description = if (command.replaceDetails) command.description else operation.description,
                        )
                    }
                }
            }
            val kind = command.entityKind()
            var prior =
                content.commands
                    .filter {
                        it.entityId == entityId && it.kind == kind && it.status != "CANCELLED" &&
                            !(it.status == "ACKNOWLEDGED" && (it.receiptRevision ?: Long.MAX_VALUE) <= content.sync.lastRevision)
                    }.maxByOrNull { it.createdOrder }
            if (prior?.status in setOf("CONFLICT", "REJECTED") && content.sync.protocolVersion >= 4) {
                val failed = requireNotNull(prior)
                dao.update(failed.copy(status = "CANCELLED"))
                content.commands.filter { it.requestId != failed.requestId && (it.predecessor == failed.requestId || it.dependency == failed.requestId || failed.requestId in json.decodeFromString<List<String>>(it.dependencies)) }.forEach { child ->
                    require(child.wirePayload == null)
                    dao.update(child.copy(predecessor = if (child.predecessor == failed.requestId) command.requestId else child.predecessor, dependency = if (child.dependency == failed.requestId) command.requestId else child.dependency, dependencies = rebindDependencies(child, failed.requestId, command.requestId), status = "PENDING", errorCode = null))
                }
                prior = null
            }
            if (prior?.status in setOf("CONFLICT", "REJECTED", "BLOCKED")) throw BudgetLocalFailure("conflict")
            if (expectedVersion != null) {
                val visible =
                    when (kind) {
                        "RECURRING_RULE" -> {
                            projection.plans(PlannedOperationsFilter())
                            projection.rules[entityId]?.version
                        }

                        "INCOME_RULE" -> (projection.incomeRules[entityId] ?: projection.incomes[entityId])?.version
                        "EXPECTED_INCOME" -> {
                            projection.incomes[entityId]?.version
                        }

                        "RECURRING_OCCURRENCE" -> {
                            projection.occurrences[entityId]?.version
                        }

                        "LEDGER" -> {
                            projection.operations[entityId]?.version
                        }

                        else -> {
                            null
                        }
                    }
                if (visible != expectedVersion) throw BudgetLocalFailure("conflict")
            }
            val canonicalToken = observedCategoryRevision ?: token(content, kind, entityId)
                ?: if (type == "UPDATE_INCOME_RULE") token(content, "EXPECTED_INCOME", entityId) else null
            validate(command, projection, content)
            val dependency =
                command.categoryId?.let { category ->
                    content.commands
                        .lastOrNull {
                            it.entityId == category && it.kind == "CATEGORY" && it.status != "CANCELLED" &&
                                !(
                                    it.status == "ACKNOWLEDGED" &&
                                        (it.receiptRevision ?: Long.MAX_VALUE) <= content.sync.lastRevision
                                )
                        }?.requestId
                }
            val order = maxOf(clock.millis(), (content.commands.maxOfOrNull { it.createdOrder } ?: 0) + 1)
            dao.enqueue(
                BudgetOutbox(
                    command.requestId,
                    content.sync.scopeKey,
                    entityId,
                    kind,
                    json.encodeToString(command.copy(expectedToken = canonicalToken)),
                    order,
                    prior?.requestId,
                    dependency,
                    protocolVersion = if (content.sync.protocolVersion >= 4) 4 else 2,
                    dependencies = json.encodeToString(content.commands.filter {
                        it.entityId in setOfNotNull(command.ruleId, command.planTarget?.ruleId, command.planTarget?.planId) && it.status != "CANCELLED"
                    }.map { it.requestId }),
                ),
            )
        }
        runCatching { scheduler.schedule() }
        return entityId
    }

    private fun writable(
        content: BudgetContent,
        allowMember: Boolean = false,
    ) {
        if (!(content.sync.localReady || content.sync.initialSyncComplete)) throw BudgetLocalFailure("not_initialized")
        if ((content.sync.role != "OWNER" && !(allowMember && content.sync.role == "MEMBER")) ||
            content.sync.protocolVersion < 4 && content.sync.errorCode in setOf("unauthorized", "access_denied", "scope_changed")
        ) {
            throw BudgetLocalFailure("forbidden")
        }
    }

    private fun token(
        c: BudgetContent,
        kind: String,
        id: String,
    ): Long? =
        if (kind == "LEDGER") {
            c.operations.find { it.id == id }?.version
        } else if (kind ==
            "CATEGORY"
        ) {
            c.categories.find { it.id == id }?.revision
        } else {
            c.records
                .find {
                    it.kind == kind && it.id == id
                }?.let { json.decodeFromString<SyncPlanRecord>(it.payload).version }
        }

    private fun validate(
        c: BudgetSyncCommand,
        p: BudgetProjection,
        content: BudgetContent,
    ) {
        if (c.type in BudgetProjection.LINK_COMMANDS) {
            val operation = p.operations[c.entityId] ?: throw BudgetLocalFailure("not_found")
            if (operation.status != "POSTED" || operation.syncStatus == "REJECTED" || operation.originalCurrency != "KZT") throw BudgetLocalFailure("invalid")
            if (c.type == "SET_OPERATION_PLAN_LINK") {
                val target = requireNotNull(c.planTarget)
                val plan = p.links(c.entityId).candidates.firstOrNull { it.target.stableKey == target.stableKey && it.target.type == target.type }
                    ?: throw BudgetLocalFailure("plan_unavailable")
                if (plan.version != c.expectedPlanVersion) throw BudgetLocalFailure("plan_changed")
            }
        }
        if (c.type == "UPDATE_OPERATION" && c.categoryId != null && c.categoryId != p.operations[c.entityId]?.categoryId && p.links(c.entityId).current.isNotEmpty()) {
            throw BudgetLocalFailure("unlink_before_category_change")
        }
        if (c.type != "CLOSE_EXPENSE" || !c.entityId.contains("@")) UUID.fromString(c.entityId)
        c.amount?.let { requireMoney(it.toBigDecimal()) }
        c.categoryId?.let { id ->
            val category = p.categories[id] ?: throw BudgetLocalFailure("invalid")
            val expected = if (c.type.endsWith("INCOME") || c.type.endsWith("INCOME_RULE") || c.type in setOf("CREATE_OPERATION", "UPDATE_OPERATION") && c.kind == "INCOME") "INCOME" else "EXPENSE"
            if (category.archived || category.kind != expected) throw BudgetLocalFailure("invalid")
        }
        if (c.type.endsWith("CATEGORY")) {
            val old = p.categories[c.entityId]
            if (c.type != "CREATE_CATEGORY" && (old == null || old.archived)) throw BudgetLocalFailure("not_found")
            c.name?.let { name ->
                if (name.isBlank() || name.length > 50 ||
                    !Regex(
                        "[A-Za-zА-Яа-яЁё]+(?:-[A-Za-zА-Яа-яЁё]+)*(?: [A-Za-zА-Яа-яЁё]+(?:-[A-Za-zА-Яа-яЁё]+)*)*",
                    ).matches(name) ||
                    name.split(' ').any {
                        it.lowercase() in
                            setOf("сегодня", "вчера", "завтра", "купить", "покупать", "покупку", "покупке", "покупки")
                    } ||
                    p.categories.values.any { it.id != c.entityId && it.name.equals(name, true) }
                ) {
                    throw BudgetLocalFailure("invalid")
                }
                if (old?.dictionaryKey == "OTHER" && name != old.name) throw BudgetLocalFailure("invalid")
            }
            c.icon?.let { if (it.isBlank()) throw BudgetLocalFailure("invalid") }
            if (c.type == "CREATE_CATEGORY" &&
                (c.kind !in setOf("EXPENSE", "INCOME") || p.categories.size >= 100)
            ) {
                throw BudgetLocalFailure("invalid")
            }
            if (c.type == "DELETE_CATEGORY" &&
                (
                    old?.dictionaryKey == "OTHER" || old?.kind != "EXPENSE" ||
                        p.categories.values.none { it.dictionaryKey == "OTHER" }
                )
            ) {
                throw BudgetLocalFailure("invalid")
            }
        }
        if (c.replaceDetails && (c.type !in setOf("UPDATE_OPERATION", "CREATE_RULE", "UPDATE_RULE") || c.categoryId == null || c.description.orEmpty().length > 160)) {
            throw BudgetLocalFailure("invalid")
        }
        if (c.type in setOf("UPDATE_OPERATION", "REFUND_OPERATION")) {
            val operation = p.operations[c.entityId] ?: throw BudgetLocalFailure("not_found")
            val allowedTypes = if (c.type == "UPDATE_OPERATION") setOf("EXPENSE", "INCOME") else setOf("EXPENSE")
            if (operation.type !in allowedTypes || operation.originalCurrency != "KZT" || operation.status != "POSTED") throw BudgetLocalFailure("invalid")
            val refunds = p.operations.values.filter { it.refundOf == c.entityId && it.status == "POSTED" && it.syncStatus != "REJECTED" }
            if (c.type == "UPDATE_OPERATION" && refunds.isNotEmpty()) throw BudgetLocalFailure("invalid")
            if (c.type == "REFUND_OPERATION" && operation.originalAmount.toBigDecimal() <= refunds.sumOf { it.originalAmount.toBigDecimal() }) throw BudgetLocalFailure("invalid")
        }
        c.incomeFrequency?.let { if (it !in com.firkat.fillin.feature.dailylimit.domain.IncomeFrequency.entries.map { f -> f.name }) throw BudgetLocalFailure("invalid") }
        c.ruleName?.let { if (it.isBlank() || it.length > 160) throw BudgetLocalFailure("invalid") }
        c.incomeName?.let { if (it.isBlank() || it.length > 160) throw BudgetLocalFailure("invalid") }
        if (c.type == "CONFIRM_REMAINDER_INCOME") {
            val item = p.plans(PlannedOperationsFilter(period = PlannedOperationPeriod.Custom,
                dateFrom = LocalDate.of(1,1,1), dateTo = today().plusYears(5))).items
                .filterIsInstance<PlannedOperation.Income>().firstOrNull { it.id == c.entityId }?.income ?: throw BudgetLocalFailure("not_found")
            val amount = c.remainderAmount?.toBigDecimalOrNull() ?: throw BudgetLocalFailure("invalid")
            if (amount.signum() <= 0 || amount.scale() > 2 || amount > item.remainingAmount ||
                c.remainderReceived?.toBigDecimalOrNull()?.compareTo(item.receivedAmount) != 0 ||
                LocalDate.parse(c.remainderDueOn) < today()) throw BudgetLocalFailure("invalid")
        }
        c.dueOn?.let { if (LocalDate.parse(it) < LocalDate.parse(c.authoredOn) && !(c.type in setOf("UPDATE_INCOME", "UPDATE_INCOME_RULE") && it == (p.incomeRules[c.entityId] ?: p.incomes[c.entityId])?.dueOn)) throw BudgetLocalFailure("invalid") }
        if (c.type == "CREATE_RULE" || c.type == "UPDATE_RULE" && c.replaceDetails) {
            val start = LocalDate.parse(c.startsOn)
            val accountingStart =
                java.time.Instant
                    .parse(
                        content.sync.accountingStartedAt,
                    ).atZone(ZoneId.of("Asia/Almaty"))
                    .toLocalDate()
            if (start < accountingStart && !(c.type == "UPDATE_RULE" && c.startsOn == p.rules[c.entityId]?.startsOn) || !when (c.frequency) {
                    "ONCE" -> c.scheduleValue == null && (start >= LocalDate.parse(c.authoredOn) || c.type == "UPDATE_RULE" && start.toString() == p.rules[c.entityId]?.startsOn)
                    "MONTHLY" -> c.scheduleValue in 1..31
                    "WEEKLY" -> c.scheduleValue in 1..7
                    else -> false
                }
            ) {
                throw BudgetLocalFailure("invalid")
            }
        }
        if (c.type in setOf("UPDATE_INCOME", "DELETE_INCOME", "CLOSE_EXPENSE")) {
            val all =
                p.plans(
                    PlannedOperationsFilter(
                        period = PlannedOperationPeriod.Custom,
                        dateFrom = LocalDate.of(1, 1, 1),
                        dateTo = today().plusYears(5),
                    ),
                )
            val target = all.items.find { it.id == c.entityId }
            if (target == null && (c.type == "CLOSE_EXPENSE" || p.incomes[c.entityId]?.status != "SCHEDULED")) throw BudgetLocalFailure("not_found")
            if (c.type == "CLOSE_EXPENSE" &&
                (target !is PlannedOperation.Expense || !target.expense.canBeClosed)
            ) {
                throw BudgetLocalFailure("invalid")
            }
        }
        if (c.type in setOf("UPDATE_RULE", "DELETE_RULE")) {
            p.plans(PlannedOperationsFilter())
            if (p.rules[c.entityId]?.active != true) throw BudgetLocalFailure("not_found")
        }
    }

    suspend fun prepare(item: BudgetOutbox): BudgetSyncCommand? =
        database.withTransaction {
            val current = dao.command(item.requestId) ?: return@withTransaction null
            if (current.status != "PENDING") return@withTransaction null
            current.wirePayload?.let { return@withTransaction json.decodeFromString<BudgetSyncCommand>(it) }
            val parents = (listOfNotNull(current.predecessor, current.dependency) + json.decodeFromString<List<String>>(current.dependencies)).distinct().mapNotNull { dao.command(it) }
            if (parents.any { it.status in setOf("CONFLICT", "REJECTED", "BLOCKED", "CANCELLED") }) {
                dao.update(current.copy(status = "BLOCKED", errorCode = "dependency_failed"))
                return@withTransaction null
            }
            if (parents.any { it.status != "ACKNOWLEDGED" }) return@withTransaction null
            var intent = json.decodeFromString<BudgetSyncCommand>(current.intent)
            if (current.kind == "LEDGER" && intent.type != "CREATE_OPERATION") {
                val legacyCreate = database.operations().outboxForOperation(intent.entityId)
                if (legacyCreate != null) {
                    if (legacyCreate.errorCode != null) dao.update(current.copy(status = "BLOCKED", errorCode = "dependency_failed"))
                    return@withTransaction null
                }
            }
            if (current.protocolVersion >= 4 && intent.type == "CLOSE_EXPENSE") {
                val projection = project(requireNotNull(dao.content()))
                projection.plans(PlannedOperationsFilter())
                val occurrence = projection.occurrences[intent.entityId]
                intent = intent.copy(ruleId = occurrence?.ruleId, dueOn = occurrence?.dueOn)
            }
            val prior = current.predecessor?.let { dao.command(it) }
            if (intent.type == "SET_OPERATION_PLAN_LINK") {
                val target = requireNotNull(intent.planTarget)
                val targetParent = parents.filter { it.entityId == target.planId || it.entityId == target.ruleId }.maxByOrNull { it.createdOrder }
                // Income versions come from their own receipt. A rule receipt is not an occurrence version.
                if (target.type == "INCOME" && targetParent?.entityId == target.planId && targetParent?.receiptToken != null) intent = intent.copy(expectedPlanVersion = targetParent.receiptToken)
            }
            val wire = if (prior != null) intent.copy(expectedToken = prior.receiptToken) else intent
            dao.update(current.copy(wirePayload = json.encodeToString(wire)))
            wire
        }

    suspend fun promoteLegacyConflict(item: BudgetOutbox) =
        database.withTransaction {
            val current = dao.command(item.requestId) ?: return@withTransaction
            val intent = json.decodeFromString<BudgetSyncCommand>(current.intent)
            val replacement = intent.copy(requestId = UUID.randomUUID().toString())
            dao.update(current.copy(status = "CANCELLED"))
            dao.enqueue(current.copy(requestId = replacement.requestId, intent = json.encodeToString(replacement), wirePayload = null, status = "PENDING", errorCode = null, receiptRevision = null, receiptToken = null, protocolVersion = 4))
            dao.commands(current.scopeKey).filter { it.requestId != current.requestId && (it.predecessor == current.requestId || it.dependency == current.requestId || current.requestId in json.decodeFromString<List<String>>(it.dependencies)) }.forEach { child ->
                require(child.wirePayload == null)
                dao.update(child.copy(predecessor = if (child.predecessor == current.requestId) replacement.requestId else child.predecessor, dependency = if (child.dependency == current.requestId) replacement.requestId else child.dependency, dependencies = rebindDependencies(child, current.requestId, replacement.requestId), status = "PENDING", errorCode = null))
            }
        }

    suspend fun cancelRejectedOperation(id: String) {
        val content = requireNotNull(dao.content())
        content.commands.filter { it.entityId == id && it.status in setOf("REJECTED", "BLOCKED") }.forEach { dao.update(it.copy(status = "CANCELLED")) }
    }

    suspend fun hasFailures(): Boolean =
        dao
            .content()
            ?.commands
            .orEmpty()
            .any { it.status in setOf("CONFLICT", "REJECTED", "BLOCKED") }

    suspend fun pending(): List<BudgetOutbox> =
        dao
            .commands(
                database
                    .operations()
                    .state()
                    ?.scopeKey
                    .orEmpty(),
            ).filter {
                it.status ==
                    "PENDING"
            }

    suspend fun acknowledge(
        item: BudgetOutbox,
        receipt: BudgetCommandReceipt,
    ) = database.withTransaction {
        require(receipt.requestId == item.requestId && receipt.revision >= 0 && receipt.entityToken != null && receipt.entityToken >= 0)
        val current = requireNotNull(dao.command(item.requestId))
        val proposal = dao.proposal(item.scopeKey, item.entityId)
        if (receipt.suggestedPlan != null && proposal?.decision == "NONE") {
            dao.putProposal(OperationPlanProposal(item.scopeKey, item.entityId, json.encodeToString(receipt.suggestedPlan), "PENDING"))
        }
        dao.update(
            current.copy(
                status = "ACKNOWLEDGED",
                receiptRevision = receipt.revision,
                receiptToken = receipt.entityToken,
                errorCode = null,
            ),
        )
    }

    suspend fun fail(
        item: BudgetOutbox,
        status: String,
        code: String,
    ) = database.withTransaction {
        dao.command(item.requestId)?.let { dao.update(it.copy(status = status, errorCode = code)) }
    }

    suspend fun operationCategoryReady(categoryId: String): Boolean {
        val content = dao.content() ?: return false
        return (
            content.categories.any { it.id == categoryId && !it.archived } ||
                content.commands.any {
                    it.entityId == categoryId && it.kind == "CATEGORY" && it.status == "ACKNOWLEDGED"
                }
        ) &&
            content.commands.none {
                it.entityId == categoryId && it.kind == "CATEGORY" && it.status !in
                    setOf(
                        "ACKNOWLEDGED",
                        "CANCELLED",
                    ) &&
                    !(
                        it.status == "PENDING" &&
                            json.decodeFromString<BudgetSyncCommand>(it.intent).type == "DELETE_CATEGORY"
                    )
            }
    }

    override suspend fun resolve(
        id: String,
        reapply: Boolean,
    ) {
        database.withTransaction {
            val content = requireNotNull(dao.content())
            writable(content)
            val failed = dao.command(id) ?: return@withTransaction
            val intent = json.decodeFromString<BudgetSyncCommand>(failed.intent)
            val descendants = mutableSetOf(id)
            var changed: Boolean
            do {
                changed = false
                content.commands.forEach {
                    if (it.predecessor in descendants ||
                        it.dependency in descendants || json.decodeFromString<List<String>>(it.dependencies).any { dependency -> dependency in descendants }
                    ) {
                        changed = descendants.add(it.requestId) || changed
                    }
                }
            } while (changed)
            if (reapply) {
                if (intent.type in BudgetProjection.LINK_COMMANDS) throw BudgetLocalFailure("confirmation_required")
                val canonical = token(content, failed.kind, failed.entityId)
                if (canonical == null && !intent.type.startsWith("CREATE_")) throw BudgetLocalFailure("not_found")
                val replacement = intent.copy(requestId = UUID.randomUUID().toString(), expectedToken = canonical)
                validate(
                    replacement,
                    project(
                        content.copy(
                            commands =
                                content.commands.filter {
                                    it.requestId !in
                                        descendants
                                },
                        ),
                    ),
                    content,
                )
                dao.update(failed.copy(status = "CANCELLED"))
                dao.enqueue(
                    failed.copy(
                        requestId = replacement.requestId,
                        intent = json.encodeToString(replacement),
                        predecessor = null,
                        dependency = null,
                        wirePayload = null,
                        status = "PENDING",
                        errorCode = null,
                        receiptRevision = null,
                        receiptToken = null,
                    ),
                )
                content.commands.filter { it.requestId in descendants && it.requestId != id }.forEach { child ->
                    // Never change an attempted request: only blocked descendants can be rebased safely.
                    require(child.wirePayload == null)
                    dao.update(
                        child.copy(
                            predecessor =
                                if (child.predecessor ==
                                    id
                                ) {
                                    replacement.requestId
                                } else {
                                    child.predecessor
                                },
                            dependency = if (child.dependency == id) replacement.requestId else child.dependency,
                            dependencies = rebindDependencies(child, id, replacement.requestId),
                            status = "PENDING",
                            errorCode = null,
                        ),
                    )
                }
            } else {
                content.commands
                    .filter {
                        it.requestId in descendants
                    }.forEach { dao.update(it.copy(status = "CANCELLED")) }
            }
        }
        runCatching { scheduler.schedule() }
    }

    private fun rebindDependencies(child: BudgetOutbox, oldId: String, newId: String): String =
        json.encodeToString(json.decodeFromString<List<String>>(child.dependencies).map { if (it == oldId) newId else it })

    private fun issue(
        c: BudgetContent,
        item: BudgetOutbox,
    ): BudgetSyncIssue {
        val intent = json.decodeFromString<BudgetSyncCommand>(item.intent)
        val category = c.categories.find { it.id == item.entityId }
        val record =
            c.records
                .find {
                    it.id == item.entityId && it.kind == item.kind
                }?.let { json.decodeFromString<SyncPlanRecord>(it.payload) }
        val title =
            when (item.kind) {
                "LEDGER" -> "Операция"
                "CATEGORY" -> "Категория"
                "RECURRING_RULE" -> "Правило расхода"
                "EXPECTED_INCOME" -> "Плановый доход"
                else -> "Обязательство"
            }
        val action =
            when {
                intent.type == "SET_OPERATION_PLAN_LINK" -> "Связь с планом"
                intent.type == "REMOVE_OPERATION_PLAN_LINK" -> "Снятие связи с планом"
                intent.type.startsWith("CREATE") -> "Создание"

                intent.type.startsWith("DELETE") -> "Удаление"

                intent.type ==
                    "CLOSE_EXPENSE" -> "Закрытие"

                else -> "Изменение"
            }
        val local =
            listOfNotNull(
                action,
                intent.name,
                intent.amount?.let {
                    "$it ₸"
                },
                intent.dueOn,
                intent.startsOn,
            ).joinToString(" · ")
        val server =
            category?.let { it.name + if (it.archived) " (в архиве)" else "" }
                ?: record?.let {
                    listOfNotNull(
                        c.categories
                            .find { category ->
                                category.id == it.categoryId
                            }?.name,
                        it.amount?.let { value -> "$value ₸" },
                        it.dueOn,
                        when (it.status) {
                            "SCHEDULED" -> "Запланировано"
                            "PAID" -> "Закрыто"
                            "CANCELLED" -> "Отменено"
                            else -> null
                        },
                    ).joinToString(" · ")
                }
                ?: "Объект отсутствует"
        val available =
            category?.archived == false ||
                record?.let { it.active != false && it.status !in setOf("CANCELLED", "PAID") } == true
        return BudgetSyncIssue(
            item.requestId,
            title,
            local,
            server,
            item.status,
            item.status != "BLOCKED" && intent.type !in BudgetProjection.LINK_COMMANDS && (available || intent.type.startsWith("CREATE_")),
        )
    }
}
