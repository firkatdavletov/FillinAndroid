package com.firkat.fillin.feature.operationhistory.data.local

import androidx.room.withTransaction
import com.firkat.fillin.feature.operationhistory.data.remote.CreatedOperationDto
import com.firkat.fillin.feature.operationhistory.data.remote.SyncOperationDto
import com.firkat.fillin.feature.operationhistory.data.remote.SyncPageDto
import com.firkat.fillin.feature.operationhistory.data.sync.OperationSyncStorage
import com.firkat.fillin.feature.operationhistory.data.sync.SyncBlockedException
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class RoomOperationSyncStorage(
    private val database: OperationDatabase,
) : OperationSyncStorage {
    private val dao get() = database.operations()

    override suspend fun state(): OperationSyncState = dao.state() ?: OperationSyncState()

    override suspend fun setRunning(running: Boolean) =
        database.withTransaction {
            dao.putState(state().copy(isSyncing = running))
        }

    override suspend fun apply(page: SyncPageDto) =
        database.withTransaction {
            val previous = state()
            val scope = page.scope
            UUID.fromString(scope.householdId)
            UUID.fromString(scope.memberId)
            require(scope.currency == "KZT" && scope.timeZone == "Asia/Almaty")
            Instant.parse(scope.accountingStartedAt)
            if (previous.scopeKey.isNotEmpty() && previous.scopeKey != scope.key) throw SyncBlockedException("scope_changed")
            var revision = previous.lastRevision
            for (change in page.changes) {
                require(change.revision > revision)
                when (change.kind) {
                    "LEDGER" -> {
                        val operation = requireNotNull(change.operation)
                        if (change.deleted) {
                            val current = dao.operation(operation.id)
                            if (current == null || current.version <= operation.version) dao.deleteOperation(operation.id)
                        } else {
                            upsert(operation, scope.key)
                        }
                    }

                    "CATEGORY" -> {
                        val category = requireNotNull(change.category)
                        UUID.fromString(category.id)
                        require(category.kind in setOf("EXPENSE", "INCOME"))
                        if (change.deleted) {
                            dao.deleteCategory(scope.key, category.id)
                        } else {
                            dao.putCategory(
                                LocalCategory(
                                    scope.key,
                                    category.id,
                                    category.name,
                                    category.kind,
                                    category.iconCode,
                                    category.iconEmoji,
                                    category.archived,
                                    change.revision,
                                ),
                            )
                        }
                    }

                    else -> {
                        throw IllegalArgumentException("Unknown sync entity")
                    }
                }
                revision = change.revision
            }
            dao.putState(
                previous.copy(
                    scopeKey = scope.key,
                    householdId = scope.householdId,
                    memberId = scope.memberId,
                    accountingStartedAt = scope.accountingStartedAt,
                    cursor = page.nextCursor,
                    lastRevision = revision,
                    initialSyncComplete = previous.initialSyncComplete || !page.hasMore,
                    errorCode = null,
                ),
            )
        }

    override suspend fun pending(): List<OperationOutbox> = dao.pending(state().scopeKey)

    override suspend fun acknowledge(response: CreatedOperationDto) =
        database.withTransaction {
            upsert(response.operation, state().scopeKey)
            dao.complete(response.requestId)
        }

    private suspend fun upsert(
        operation: SyncOperationDto,
        scope: String,
    ) {
        val candidate = operation.toLocal(scope)
        operation.requestId?.let { requestId ->
            dao.byRequest(requestId)?.takeIf { it.id != operation.id }?.let { dao.deleteOperation(it.id) }
            dao.complete(requestId)
        }
        val current = dao.operation(operation.id)
        require(current == null || current.scopeKey == scope)
        if (current == null || current.version <= operation.version) dao.putOperation(candidate)
    }

    override suspend fun reject(
        command: OperationOutbox,
        errorCode: String,
    ) = database.withTransaction {
        dao.operation(command.operationId)?.let { dao.putOperation(it.copy(syncStatus = "REJECTED", syncError = errorCode)) }
        dao.commandError(command.requestId, errorCode)
    }

    override suspend fun finish(
        errorCode: String?,
        syncedAt: String?,
    ) = database.withTransaction {
        val current = state()
        dao.putState(current.copy(errorCode = errorCode, lastSyncedAt = syncedAt ?: current.lastSyncedAt))
    }
}

internal fun SyncOperationDto.toLocal(scope: String): LocalOperation {
    UUID.fromString(id)
    UUID.fromString(categoryId)
    requestId?.let(UUID::fromString)
    refundOf?.let(UUID::fromString)
    require(type in setOf("EXPENSE", "INCOME", "EXPENSE_REFUND"))
    require(status in setOf("POSTED", "VOIDED") && version >= 0 && sequence > 0)
    require(originalCurrency in setOf("KZT", "RUB"))
    requireMoney(originalAmount.toBigDecimal())
    requireMoney(amountKzt.toBigDecimal())
    if (originalCurrency == "KZT") require(originalAmount.toBigDecimal().compareTo(amountKzt.toBigDecimal()) == 0)
    LocalDate.parse(occurredOn)
    Instant.parse(createdAt)
    return LocalOperation(
        id,
        scope,
        categoryId,
        type,
        originalAmount,
        originalCurrency,
        amountKzt,
        occurredOn,
        createdAt,
        status,
        version,
        sequence,
        description,
        refundOf,
        requestId,
    )
}

internal fun requireMoney(amount: BigDecimal) {
    require(amount.signum() > 0 && amount.scale() <= 2 && amount <= BigDecimal("99999999999999999.99"))
}
