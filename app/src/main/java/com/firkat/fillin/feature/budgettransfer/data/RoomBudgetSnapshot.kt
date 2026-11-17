package com.firkat.fillin.feature.budgettransfer.data

import androidx.room.withTransaction
import com.firkat.fillin.feature.budgetprofile.domain.BudgetCurrency
import com.firkat.fillin.feature.budgettransfer.domain.BudgetTransferException
import com.firkat.fillin.feature.budgettransfer.domain.TransferFailure
import com.firkat.fillin.feature.operationhistory.data.local.OperationDatabase
import com.firkat.fillin.feature.operationhistory.data.local.OperationSyncState
import com.firkat.fillin.feature.operationhistory.data.remote.CreateOperationDto
import com.firkat.fillin.feature.sync.data.BudgetSyncCommand
import com.firkat.fillin.feature.sync.data.SyncBudgetSettings
import java.util.UUID
import kotlinx.serialization.json.Json

internal class RoomBudgetSnapshot(private val json: Json) {
    suspend fun read(
        database: OperationDatabase,
        name: String?
    ): BudgetSnapshot = database.withTransaction {
        val content = database.budget().content()
        if (content == null || (!content.sync.localReady && !content.sync.initialSyncComplete)) {
            throw BudgetTransferException(TransferFailure.SourceUnavailable)
        }
        val state = content.sync
        val scope = state.scopeKey
        BudgetSnapshot(
            SnapshotMetadata(
                BUDGET_CSV_VERSION,
                name,
                BudgetCurrency.valueOf(state.currency),
                state.accountingStartedAt ?: throw BudgetTransferException(TransferFailure.SourceUnavailable),
                state.lastRevision,
                state.protocolVersion
            ),
            content.operations.sortedBy { it.id },
            content.categories.sortedBy { it.id },
            content.records.sortedWith(compareBy({ it.kind }, { it.id })),
            content.commands.sortedWith(compareBy({ it.createdOrder }, { it.requestId })),
            database.operations().allQueued(scope),
            database.budget().proposals(scope),
            database.categoryUsage().events(scope)
        ).withIdentity("", "budget", "")
    }

    suspend fun restore(
        database: OperationDatabase,
        source: BudgetSnapshot
    ) {
        val household = UUID.randomUUID().toString()
        val member = UUID.randomUUID().toString()
        val scope = "$household:$member"
        val snapshot = source.withIdentity(scope, household, member)
        database.withTransaction {
            check(database.operations().state() == null)
            val budget = database.budget()
            val operations = database.operations()
            snapshot.categories.forEach { operations.putCategory(it) }
            snapshot.operations.forEach { operations.putOperation(it) }
            snapshot.records.forEach { budget.putRecord(it) }
            snapshot.commands.forEach { budget.enqueue(it) }
            snapshot.legacyCommands.forEach { operations.enqueue(it) }
            snapshot.proposals.forEach { budget.putProposal(it) }
            snapshot.usageEvents.forEach { database.categoryUsage().record(it) }
            operations.putState(
                OperationSyncState(
                    scopeKey = scope,
                    householdId = household,
                    memberId = member,
                    accountingStartedAt = snapshot.metadata.accountingStartedAt,
                    lastRevision = snapshot.metadata.lastRevision,
                    role = "OWNER",
                    protocolVersion = snapshot.metadata.protocolVersion,
                    localReady = true,
                    currency = snapshot.metadata.currency.name,
                    importedLocalOnly = true
                )
            )
        }
    }

    private fun BudgetSnapshot.withIdentity(
        scope: String,
        household: String,
        member: String
    ): BudgetSnapshot = copy(
        operations = operations.map { it.copy(scopeKey = scope) },
        categories = categories.map { it.copy(scopeKey = scope) },
        records = records.map { record ->
            if (record.kind == "BUDGET_SETTINGS") {
                val settings = json.decodeFromString<SyncBudgetSettings>(record.payload)
                record.copy(
                    scopeKey = scope,
                    id = household,
                    payload = json.encodeToString(settings.copy(id = household))
                )
            } else {
                record.copy(scopeKey = scope)
            }
        },
        commands = commands.map { command ->
            val intent = json.decodeFromString<BudgetSyncCommand>(command.intent)
            command.copy(
                scopeKey = scope,
                intent = json.encodeToString(intent.copy(householdId = household, memberId = member)),
                // A prepared request is tied to the original server. The local projection uses intent.
                wirePayload = null
            )
        },
        legacyCommands = legacyCommands.map { command ->
            val request = json.decodeFromString<CreateOperationDto>(command.payload)
            command.copy(
                scopeKey = scope,
                payload = json.encodeToString(request.copy(householdId = household, memberId = member))
            )
        },
        proposals = proposals.map { it.copy(scopeKey = scope) },
        usageEvents = usageEvents.map { it.copy(scopeKey = scope, delivered = true) }
    )
}
