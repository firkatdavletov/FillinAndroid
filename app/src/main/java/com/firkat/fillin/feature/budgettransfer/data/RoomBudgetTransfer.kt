package com.firkat.fillin.feature.budgettransfer.data

import com.firkat.fillin.feature.auth.domain.AuthRepository
import com.firkat.fillin.feature.auth.domain.AuthSession
import com.firkat.fillin.feature.budgetprofile.data.ActiveBudgetManager
import com.firkat.fillin.feature.budgetprofile.data.BudgetDatabaseFactory
import com.firkat.fillin.feature.budgetprofile.data.RoomBudgetProfileRegistry
import com.firkat.fillin.feature.budgetprofile.domain.BudgetSyncIdentity
import com.firkat.fillin.feature.budgettransfer.domain.BudgetTransfer
import com.firkat.fillin.feature.budgettransfer.domain.BudgetTransferException
import com.firkat.fillin.feature.budgettransfer.domain.TransferFailure
import com.firkat.fillin.feature.operationhistory.data.local.OperationDatabase
import com.firkat.fillin.feature.sync.domain.BudgetConnectionFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal class RoomBudgetTransfer(
    private val active: ActiveBudgetManager,
    private val registry: RoomBudgetProfileRegistry,
    private val databases: BudgetDatabaseFactory,
    private val auth: AuthRepository,
    private val snapshots: RoomBudgetSnapshot,
    private val documents: BudgetDocuments
) : BudgetTransfer {
    @OptIn(ExperimentalCoroutinesApi::class)
    override val exportSource = active.active.flatMapLatest { runtime ->
        if (runtime == null) return@flatMapLatest flowOf(null)
        flow<BudgetSyncIdentity?> {
            val lease = runtime.retain() ?: return@flow emit(null)
            lease.use {
                lease.scope.get<OperationDatabase>().operations().observeState().map { state ->
                    runtime.profile.syncIdentity.takeIf {
                        state?.let { it.localReady || it.initialSyncComplete } == true
                    }
                }.collect { emit(it) }
            }
        }.catch { emit(null) }
    }.distinctUntilChanged()

    override suspend fun export(
        source: BudgetSyncIdentity,
        document: String
    ) = withContext(Dispatchers.IO) {
        var completed = false
        try {
            val lease = active.acquire(source) ?: throw BudgetTransferException(TransferFailure.SourceUnavailable)
            val snapshot = lease.use {
                val profile = it.runtime.profile
                val data = snapshots.read(it.scope.get(), profile.name)
                if (!active.isCurrent(profile)) throw BudgetTransferException(TransferFailure.SourceUnavailable)
                data
            }
            documents.write(document, snapshot)
            completed = true
        } finally {
            if (!completed) documents.discard(document)
        }
    }

    override suspend fun import(
        document: String,
        fallbackName: String,
        nameSuffix: String
    ): Boolean = withContext(Dispatchers.IO) {
        val uid = currentUid()
        val snapshot = documents.read(document)
        val name = (snapshot.metadata.name ?: fallbackName).take(MAX_NAME_LENGTH - nameSuffix.length)
            .trimEnd() + nameSuffix
        val profile = try {
            registry.reserveLocal(name, databases)
        } catch (failure: BudgetConnectionFailure) {
            if (failure.code != "local_budget_limit") throw failure
            throw BudgetTransferException(TransferFailure.LocalLimit)
        }
        try {
            val database = databases.open(profile)
            try {
                snapshots.restore(database, snapshot)
            } finally {
                database.close()
            }
        } finally {
            withContext(NonCancellable) { registry.settleLocal(profile, databases) }
        }
        selectRestoredBudget(profile.id, uid)
    }

    private suspend fun selectRestoredBudget(
        profileId: String,
        uid: String?
    ): Boolean {
        if (currentUid() != uid) return false
        return try {
            active.select(profileId, expectedUid = uid, requireExpectedAccount = true)
            true
        } catch (failure: BudgetConnectionFailure) {
            if (failure.code != "account_changed") throw failure
            false
        }
    }

    private fun currentUid() = (auth.session.value as? AuthSession.Authenticated)?.user?.uid

    private companion object {
        const val MAX_NAME_LENGTH = 80
    }
}
