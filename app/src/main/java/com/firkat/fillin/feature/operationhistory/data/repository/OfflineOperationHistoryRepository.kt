package com.firkat.fillin.feature.operationhistory.data.repository

import androidx.room.withTransaction
import com.firkat.fillin.feature.categories.data.local.CategoryUsageEvent
import com.firkat.fillin.feature.categories.domain.model.Category
import com.firkat.fillin.feature.categories.domain.model.CategoryIcon
import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import com.firkat.fillin.feature.operationhistory.data.local.LocalOperation
import com.firkat.fillin.feature.operationhistory.data.local.OperationDatabase
import com.firkat.fillin.feature.operationhistory.data.local.OperationOutbox
import com.firkat.fillin.feature.operationhistory.data.local.localHistory
import com.firkat.fillin.feature.operationhistory.data.local.localOperation
import com.firkat.fillin.feature.operationhistory.data.local.requireMoney
import com.firkat.fillin.feature.operationhistory.data.remote.CreateOperationDto
import com.firkat.fillin.feature.operationhistory.data.sync.OperationConnectivity
import com.firkat.fillin.feature.operationhistory.data.sync.OperationSyncScheduler
import com.firkat.fillin.feature.operationhistory.data.sync.OperationsSynchronizer
import com.firkat.fillin.feature.operationhistory.data.sync.SyncResult
import com.firkat.fillin.feature.operationhistory.domain.model.Operation
import com.firkat.fillin.feature.operationhistory.domain.model.OperationHistoryFilter
import com.firkat.fillin.feature.operationhistory.domain.model.OperationPage
import com.firkat.fillin.feature.operationhistory.domain.repository.CreateOperationResult
import com.firkat.fillin.feature.operationhistory.domain.repository.NewOperation
import com.firkat.fillin.feature.operationhistory.domain.repository.OfflineHistoryContext
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryException
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryRepository
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryStore
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationUpdate
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

class OfflineOperationHistoryRepository(
    private val database: OperationDatabase,
    private val remote: OperationHistoryRepository,
    private val sync: OperationsSynchronizer,
    private val scheduler: OperationSyncScheduler,
    private val connectivity: OperationConnectivity,
    private val json: Json,
    private val clock: Clock = Clock.system(ZoneId.of("Asia/Almaty")),
    private val budgetStore: com.firkat.fillin.feature.sync.data.BudgetStore? = null
) : OperationHistoryStore,
    OperationHistoryRepository {
    private val dao get() = database.operations()

    private val localContent = budgetStore?.observeHistoryContent() ?: dao.observeContent()

    override val context: Flow<OfflineHistoryContext> =
        combine(localContent, connectivity.online, sync.running) { content, online, running ->
            OfflineHistoryContext(
                categories =
                content?.categories.orEmpty().filterNot { it.archived }.sortedBy { it.name }.map {
                    Category(
                        it.id,
                        it.name,
                        if (it.kind == "EXPENSE") CategoryKind.Expense else CategoryKind.Income,
                        CategoryIcon(it.iconCode, it.iconEmoji)
                    )
                },
                initialSyncComplete = content?.sync?.let { it.localReady || it.initialSyncComplete } == true,
                isSyncing = running,
                isOnline = online,
                lastSyncedAt = content?.sync?.lastSyncedAt?.let(Instant::parse),
                accountingStartedOn =
                content
                    ?.sync
                    ?.accountingStartedAt
                    ?.let(Instant::parse)
                    ?.let { start ->
                        val date = start.atZone(ZoneId.of("Asia/Almaty")).toLocalDate()
                        if (date.atStartOfDay(ZoneId.of("Asia/Almaty")).toInstant() <
                            start
                        ) {
                            date.plusDays(1)
                        } else {
                            date
                        }
                    },
                syncError = content?.sync?.takeIf { it.protocolVersion < 4 }?.errorCode,
                pendingCount = content?.operations.orEmpty().count { it.syncStatus == "PENDING" },
                rejectedCount = content?.operations.orEmpty().count { it.syncStatus == "REJECTED" }
            )
        }.flowOn(Dispatchers.Default)

    override fun observeOperation(
        id: String
    ): Flow<Operation?> = localContent.map { localOperation(it, id) }.flowOn(Dispatchers.Default)

    override fun observeHistory(
        filter: OperationHistoryFilter,
        limit: Int
    ): Flow<OperationPage> = localContent.map { localHistory(it, filter, limit) }.flowOn(Dispatchers.Default)

    override fun requestSync() = scheduler.schedule()

    override suspend fun create(
        operation: NewOperation,
        replacesRejectedId: String?
    ): CreateOperationResult {
        val result = database.withTransaction {
            localCommand { budgetStore?.checkWritable() }
            val state =
                dao.state()?.takeIf { it.localReady || it.initialSyncComplete }
                    ?: throw OperationHistoryException.InvalidRequest
            if (state.protocolVersion < 4 &&
                state.errorCode == "scope_changed"
            ) {
                throw OperationHistoryException.Forbidden
            }
            try {
                requireMoney(operation.amount)
            } catch (_: IllegalArgumentException) {
                throw OperationHistoryException.InvalidRequest
            }
            val category =
                budgetStore?.historySnapshot()?.categories?.find { it.id == operation.categoryId }
                    ?: dao.category(state.scopeKey, operation.categoryId)
            if (category == null ||
                category.archived ||
                category.kind != operation.type.apiValue
            ) {
                throw OperationHistoryException.InvalidRequest
            }
            val at = operation.occurredOn.atStartOfDay(ZoneId.of("Asia/Almaty")).toInstant()
            if (at < Instant.parse(state.accountingStartedAt) ||
                operation.occurredOn > LocalDate.now(clock)
            ) {
                throw OperationHistoryException.InvalidRequest
            }
            if (operation.description.orEmpty().length > 160) throw OperationHistoryException.InvalidRequest
            replacesRejectedId?.let { id ->
                val previous =
                    dao.operation(id)?.takeIf { it.scopeKey == state.scopeKey && it.syncStatus == "REJECTED" }
                        ?: throw OperationHistoryException.InvalidRequest
                budgetStore?.cancelRejectedOperation(previous.id)
                dao.deleteOperation(previous.id)
                previous.requestId?.let { dao.complete(it) }
            }
            val id = UUID.randomUUID().toString()
            val savedAt = clock.instant()
            val amount = operation.amount.setScale(2).toPlainString()
            val request =
                CreateOperationDto(
                    id,
                    state.householdId,
                    state.memberId,
                    operation.type.apiValue,
                    operation.categoryId,
                    amount,
                    operation.occurredOn.toString(),
                    operation.description?.trim()?.takeIf { it.isNotEmpty() }
                )
            dao.putOperation(
                LocalOperation(
                    id,
                    state.scopeKey,
                    operation.categoryId,
                    operation.type.apiValue,
                    amount,
                    "KZT",
                    amount,
                    operation.occurredOn.toString(),
                    savedAt.toString(),
                    "POSTED",
                    0,
                    0,
                    request.description,
                    null,
                    id,
                    "PENDING",
                    occurredAt = at.toString()
                )
            )
            if (state.protocolVersion >= 4 && budgetStore != null) {
                budgetStore.enqueue("CREATE_OPERATION", id) {
                    it.copy(
                        categoryId = request.categoryId,
                        amount = request.amount,
                        kind = request.type,
                        occurredOn = request.occurredOn,
                        description = request.description,
                        planMatchingMode = "CONFIRM_REQUIRED"
                    )
                }
            } else {
                dao.enqueue(OperationOutbox(id, state.scopeKey, id, json.encodeToString(request)))
            }
            if (replacesRejectedId == null) {
                database.categoryUsage().record(
                    CategoryUsageEvent(state.scopeKey, id, id, operation.categoryId, savedAt.toEpochMilli())
                )
            }
            CreateOperationResult(id, budgetStore?.suggest(id))
        }
        // A scheduler failure must not turn a committed local save into an apparent failure.
        // Startup will discover the durable outbox again.
        runCatching { scheduler.schedule() }
        return result
    }

    override suspend fun getOperations(
        limit: Int,
        cursor: Long?,
        filter: OperationHistoryFilter
    ): OperationPage {
        val offset = cursor ?: 0L
        require(limit > 0 && offset >= 0 && offset <= Int.MAX_VALUE - limit)
        val page = observeHistory(filter, limit + offset.toInt()).first()
        return page.copy(items = page.items.drop(offset.toInt()))
    }

    override suspend fun updateOperation(
        id: String,
        version: Long,
        update: OperationUpdate
    ): Operation {
        if (dao.state()?.protocolVersion != 4 || budgetStore == null) throw OperationHistoryException.InvalidRequest
        localCommand {
            budgetStore.enqueue("UPDATE_OPERATION", id, version) {
                it.copy(
                    amount = update.amount.toPlainString(),
                    categoryId = update.categoryId,
                    description = update.description?.trim()?.takeIf(String::isNotEmpty),
                    replaceDetails = true
                )
            }
        }
        return observeOperation(id).first() ?: throw OperationHistoryException.NotFound
    }

    override suspend fun updateOperationAmount(
        id: String,
        version: Long,
        amount: BigDecimal
    ): Operation {
        if (dao.state()?.protocolVersion == 4 && budgetStore != null) {
            localCommand {
                budgetStore.enqueue("UPDATE_OPERATION", id, version) { it.copy(amount = amount.toPlainString()) }
            }
            return observeHistory(
                OperationHistoryFilter.currentMonth(LocalDate.now(clock)),
                Int.MAX_VALUE
            ).first().items.firstOrNull {
                it.id ==
                    id
            }
                ?: budgetStore.historySnapshot()!!.let {
                    localHistory(
                        it,
                        OperationHistoryFilter(dateFrom = LocalDate.of(1, 1, 1), dateTo = LocalDate.now(clock)),
                        Int.MAX_VALUE
                    ).items.first {
                        it.id ==
                            id
                    }
                }
        }
        // The retired /purchases endpoint cannot update an income.
        if (dao.operation(id)?.type != "EXPENSE") throw OperationHistoryException.InvalidRequest
        requireOnlineOperation(id)
        val result = remote.updateOperationAmount(id, version, amount)
        database.withTransaction {
            val current = dao.operation(id)
            if (current != null && current.version <= result.version) {
                dao.putOperation(
                    current.copy(
                        originalAmount = result.originalAmount.toPlainString(),
                        amountKzt = result.amount.toPlainString(),
                        categoryId = result.categoryId,
                        description = result.description,
                        version = result.version,
                        status =
                        if (result.status ==
                            com.firkat.fillin.feature.operationhistory.domain.model.OperationStatus.Posted
                        ) {
                            "POSTED"
                        } else {
                            "VOIDED"
                        }
                    )
                )
            }
        }
        // Only canonical server snapshots enter Room. The network response is never a second UI source.
        refreshAfterMutation()
        return result
    }

    override suspend fun refundPurchase(
        id: String,
        version: Long
    ) {
        if (dao.state()?.protocolVersion == 4 && budgetStore != null) {
            val content = requireNotNull(budgetStore.historySnapshot())
            val operation = content.operations.firstOrNull { it.id == id } ?: throw OperationHistoryException.NotFound
            val remaining =
                operation.originalAmount.toBigDecimal() -
                    content.operations.filter {
                        it.refundOf == id &&
                            it.status == "POSTED" &&
                            it.syncStatus != "REJECTED"
                    }.sumOf { it.originalAmount.toBigDecimal() }
            localCommand {
                budgetStore.enqueue("REFUND_OPERATION", id, version) {
                    it.copy(amount = remaining.toPlainString(), refundId = UUID.randomUUID().toString())
                }
            }
            return
        }
        requireOnlineOperation(id)
        remote.refundPurchase(id, version)
        refreshAfterMutation()
    }

    private suspend fun requireOnlineOperation(
        id: String
    ) {
        localCommand { budgetStore?.checkWritable() }
        if (!connectivity.online.value) throw OperationHistoryException.Network
        val state = dao.state() ?: throw OperationHistoryException.InvalidRequest
        val operation = dao.operation(id)
        if (operation == null || operation.scopeKey != state.scopeKey || operation.syncStatus != "SYNCED") {
            throw OperationHistoryException.InvalidRequest
        }
        // Confirm the token still belongs to the locally cached household/member before mutating.
        if (sync.synchronize() != SyncResult.Completed) throw OperationHistoryException.Network
    }

    private suspend fun refreshAfterMutation() {
        if (sync.synchronize() != SyncResult.Completed) scheduler.schedule()
    }
}

private inline fun <T> localCommand(
    action: () -> T
): T = try {
    action()
} catch (failure: com.firkat.fillin.feature.sync.data.BudgetLocalFailure) {
    throw when (failure.code) {
        "unlink_before_category_change" -> OperationHistoryException.UnlinkBeforeCategoryChange
        "not_found" -> OperationHistoryException.NotFound
        "conflict" -> OperationHistoryException.Conflict
        "forbidden" -> OperationHistoryException.Forbidden
        "invalid", "not_initialized" -> OperationHistoryException.InvalidRequest
        else -> OperationHistoryException.Unknown
    }
}
