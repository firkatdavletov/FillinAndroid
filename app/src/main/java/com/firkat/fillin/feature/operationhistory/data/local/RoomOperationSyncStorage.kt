package com.firkat.fillin.feature.operationhistory.data.local

import androidx.room.withTransaction
import com.firkat.fillin.core.database.LocalSyncRecord
import com.firkat.fillin.core.database.SyncStagingPage
import com.firkat.fillin.feature.operationhistory.data.remote.CreatedOperationDto
import com.firkat.fillin.feature.operationhistory.data.remote.SyncOperationDto
import com.firkat.fillin.feature.operationhistory.data.remote.SyncPageDto
import com.firkat.fillin.feature.operationhistory.data.sync.OperationSyncStorage
import com.firkat.fillin.feature.operationhistory.data.sync.SyncBlockedException
import com.firkat.fillin.feature.sync.data.SyncBudgetSettings
import com.firkat.fillin.feature.sync.data.SyncSavingsGoal
import com.firkat.fillin.feature.sync.data.SyncVariableSpendingExclusion
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class RoomOperationSyncStorage(
    private val database: OperationDatabase,
    private val json: Json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        },
    private val publishReady: Boolean = true,
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
            require(scope.currency == "KZT" && scope.timeZone == "Asia/Almaty" && scope.role in setOf("OWNER", "MEMBER"))
            Instant.parse(scope.accountingStartedAt)
            if (previous.scopeKey.isNotEmpty() &&
                previous.scopeKey != scope.key
            ) {
                throw SyncBlockedException("scope_changed")
            }
            val staged = database.budget().staged().map { json.decodeFromString<SyncPageDto>(it.payload) }
            require(staged.all { it.scope.key == scope.key && it.snapshotRevision == page.snapshotRevision })
            var revision =
                staged
                    .lastOrNull()
                    ?.changes
                    ?.lastOrNull()
                    ?.revision ?: previous.lastRevision
            page.changes.forEach { change ->
                require(change.revision > revision)
                when (change.kind) {
                    "LEDGER" -> {
                        requireNotNull(change.operation).toLocal(scope.key)
                    }

                    "CATEGORY" -> {
                        val c = requireNotNull(change.category)
                        UUID.fromString(c.id)
                        require(c.kind in setOf("EXPENSE", "INCOME"))
                    }

                    "BUDGET_SETTINGS" -> {
                        val settings = requireNotNull(change.budgetSettings)
                        require(settings.id == scope.householdId)
                        requireDecimal(settings.openingBalance)
                        requireDecimal(settings.protectedReserve, nonNegative = true)
                    }

                    "SAVINGS_GOAL" -> {
                        val goal = requireNotNull(change.savingsGoal)
                        UUID.fromString(goal.id)
                        requireDecimal(goal.allocatedAmount, nonNegative = true)
                    }

                    "VARIABLE_SPENDING_EXCLUSION" -> {
                        UUID.fromString(requireNotNull(change.variableSpendingExclusion).id)
                    }

                    "INCOME_RULE", "RECURRING_RULE", "RECURRING_OCCURRENCE", "EXPECTED_INCOME", "OCCURRENCE_PAYMENT", "INCOME_MATCH" -> {
                        val record = requireNotNull(change.record)
                        UUID.fromString(record.id)
                        record.categoryId?.let(UUID::fromString)
                        record.ruleId?.let(UUID::fromString)
                        record.ledgerId?.let(UUID::fromString)
                        if (record.ledgerId == null) {
                            require(record.version != null && record.version >= 0)
                            require(record.createdAt != null && record.modifiedAt != null)
                        }
                        record.amount?.let { requireMoney(it.toBigDecimal()) }
                        record.incomeFrequency?.let { require(it in setOf("ONCE", "WEEKLY", "BIWEEKLY", "MONTHLY")) }
                        record.incomeName?.let { require(it.isNotBlank() && it.length <= 160) }
                        record.scheduledOn?.let(LocalDate::parse)
                        record.remainderDueOn?.let(LocalDate::parse)
                        record.remainderAmount?.let { requireMoney(it.toBigDecimal()) }
                        record.remainderReceived?.let { requireDecimal(it, nonNegative = true) }
                        record.startsOn?.let(LocalDate::parse)
                        record.dueOn?.let(LocalDate::parse)
                        record.createdAt?.let(Instant::parse)
                        record.modifiedAt?.let(Instant::parse)
                        when (change.kind) {
                            "RECURRING_RULE" -> {
                                require(
                                    record.categoryId != null && record.amount != null && record.startsOn != null &&
                                        record.active != null,
                                )
                                require(record.frequency in setOf("MONTHLY", "WEEKLY"))
                                require(record.scheduleValue in if (record.frequency == "MONTHLY") 1..31 else 1..7)
                            }

                            "INCOME_RULE" -> {
                                require(record.categoryId != null && record.amount != null && record.dueOn != null)
                                require(!record.incomeName.isNullOrBlank() && record.incomeActive != null)
                                require(record.incomeFrequency in setOf("ONCE", "WEEKLY", "BIWEEKLY", "MONTHLY"))
                                require(record.status in setOf("SCHEDULED", "CANCELLED"))
                            }
                            "EXPECTED_INCOME", "RECURRING_OCCURRENCE" -> {
                                require(record.categoryId != null && record.amount != null && record.dueOn != null)
                                require(
                                    record.status in
                                        if (change.kind ==
                                            "EXPECTED_INCOME"
                                        ) {
                                            setOf("SCHEDULED", "CANCELLED")
                                        } else {
                                            setOf("SCHEDULED", "CANCELLED", "PAID")
                                        },
                                )
                                if (change.kind == "RECURRING_OCCURRENCE") require(record.ruleId != null)
                            }

                            else -> {
                                require(record.ledgerId != null)
                            }
                        }
                    }

                    else -> {
                        throw IllegalArgumentException("Unknown sync entity")
                    }
                }
                revision = change.revision
            }
            if (page.hasMore) {
                database.budget().stage(SyncStagingPage(payload = json.encodeToString(page)))
            } else {
                (staged + page).forEach { part ->
                    part.changes.forEach { change ->
                        when (change.kind) {
                            "LEDGER" -> {
                                val operation = requireNotNull(change.operation)
                                if (change.deleted) {
                                    val current = dao.operation(operation.id)
                                    if (current == null ||
                                        current.version <= operation.version
                                    ) {
                                        dao.deleteOperation(operation.id)
                                    }
                                } else {
                                    upsert(operation, scope.key)
                                }
                            }

                            "CATEGORY" -> {
                                val category = requireNotNull(change.category)
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
                                            category.dictionaryKey,
                                        ),
                                    )
                                }
                            }

                            else -> {
                                val (key, payload) =
                                    when (change.kind) {
                                        "BUDGET_SETTINGS" -> {
                                            requireNotNull(change.budgetSettings).let {
                                                it.id to json.encodeToString(it)
                                            }
                                        }

                                        "SAVINGS_GOAL" -> {
                                            requireNotNull(change.savingsGoal).let {
                                                it.id to json.encodeToString(it)
                                            }
                                        }

                                        "VARIABLE_SPENDING_EXCLUSION" -> {
                                            requireNotNull(change.variableSpendingExclusion).let {
                                                it.id to json.encodeToString(it)
                                            }
                                        }

                                        else -> {
                                            requireNotNull(change.record).let { record ->
                                                (record.id + (record.ledgerId?.let { ":$it" } ?: "")) to json.encodeToString(record)
                                            }
                                        }
                                    }
                                if (change.deleted) {
                                    database.budget().deleteRecord(scope.key, change.kind, key)
                                } else {
                                    database.budget().putRecord(
                                        LocalSyncRecord(
                                            scope.key,
                                            change.kind,
                                            key,
                                            payload,
                                            change.revision,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
                database.budget().cleanReceipts(scope.key, maxOf(page.snapshotRevision, revision))
                database.budget().clearStaged()
            }

            if (!page.hasMore && previous.protocolVersion >= 4) require(database.budget().record(scope.key, "BUDGET_SETTINGS", scope.householdId) != null)
            dao.putState(
                previous.copy(
                    scopeKey = scope.key,
                    householdId = scope.householdId,
                    memberId = scope.memberId,
                    accountingStartedAt = scope.accountingStartedAt,
                    cursor = page.nextCursor,
                    lastRevision = if (page.hasMore) previous.lastRevision else maxOf(page.snapshotRevision, revision),
                    initialSyncComplete = previous.initialSyncComplete || (!page.hasMore && publishReady),
                    errorCode = null,
                    role = scope.role,
                    protocolVersion = if (previous.protocolVersion >= 4) 4 else 3,
                    localReady = previous.localReady || (!page.hasMore && publishReady),
                ),
            )
        }

    override suspend fun pending(): List<OperationOutbox> = dao.pending(state().scopeKey)

    override suspend fun acknowledge(response: CreatedOperationDto) =
        database.withTransaction {
            upsert(response.operation, state().scopeKey)
            // Keep the local income-match projection until the atomic pull publishes its canonical link.
            if (response.operation.type == "INCOME") {
                dao.operation(response.operation.id)?.let { dao.putOperation(it.copy(syncStatus = "ACKNOWLEDGED")) }
            }
            dao.complete(response.requestId)
        }

    private suspend fun upsert(
        operation: SyncOperationDto,
        scope: String,
    ) {
        val candidate = operation.toLocal(scope)
        operation.requestId?.let { requestId ->
            dao.byRequest(requestId)?.takeIf { it.id != operation.id }?.let { local ->
                // Legacy v1 assigned server IDs. Rebind only commands that have never been attempted;
                // their immutable HTTP payload is still absent. v4-created objects keep client UUIDs.
                database.budget().commands(scope).filter { it.entityId == local.id && it.status !in setOf("CANCELLED", "ACKNOWLEDGED") }.forEach { command ->
                    require(command.wirePayload == null)
                    val intent = json.decodeFromString<com.firkat.fillin.feature.sync.data.BudgetSyncCommand>(command.intent)
                    database.budget().update(command.copy(entityId = operation.id, intent = json.encodeToString(intent.copy(entityId = operation.id))))
                }
                dao.deleteOperation(local.id)
            }
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
        dao.operation(command.operationId)?.let {
            dao.putOperation(it.copy(syncStatus = "REJECTED", syncError = errorCode))
        }
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
    occurredAt?.let(Instant::parse)
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
        occurredAt = occurredAt,
    )
}

internal fun requireMoney(amount: BigDecimal) {
    require(amount.signum() > 0 && amount.scale() <= 2 && amount <= BigDecimal("99999999999999999.99"))
}

private fun requireDecimal(
    value: String,
    nonNegative: Boolean = false,
) {
    val amount = value.toBigDecimal()
    require(amount.scale() <= 2 && amount.abs() <= BigDecimal("99999999999999999.99"))
    require(!nonNegative || amount.signum() >= 0)
}
