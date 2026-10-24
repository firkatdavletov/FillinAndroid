package com.firkat.fillin.feature.profile.data

import com.firkat.fillin.feature.auth.domain.AuthRepository
import com.firkat.fillin.feature.auth.domain.AuthSession
import com.firkat.fillin.feature.budgetconnection.domain.BudgetConnectionRepository
import com.firkat.fillin.feature.budgetprofile.data.ActiveBudgetManager
import com.firkat.fillin.feature.budgetprofile.data.BudgetDatabaseFactory
import com.firkat.fillin.feature.budgetprofile.domain.BudgetProfileKind
import com.firkat.fillin.feature.budgetprofile.domain.BudgetProfileRegistry
import com.firkat.fillin.feature.budgetupload.domain.LocalBudgetUploadStore
import com.firkat.fillin.feature.profile.domain.ProfileBudget
import com.firkat.fillin.feature.profile.domain.ProfileBudgetsRepository
import com.firkat.fillin.feature.sync.domain.BudgetConnectionFailure
import com.firkat.fillin.feature.sync.data.CloudBudgetSession
import com.firkat.fillin.feature.sync.data.LocalOnlyCloudApi
import com.firkat.fillin.feature.operationhistory.data.sync.OperationSyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.LocalDate

class RoomProfileBudgetsRepository(
    private val auth: AuthRepository,
    private val profiles: BudgetProfileRegistry,
    private val active: ActiveBudgetManager,
    private val databases: BudgetDatabaseFactory,
    private val connection: BudgetConnectionRepository,
    private val json: Json,
    private val uploads: LocalBudgetUploadStore,
) : ProfileBudgetsRepository {
    private val revision = MutableStateFlow(0)

    override fun observe() = combine(auth.session, active.active, revision) { session, runtime, _ ->
        val uid = (session as? AuthSession.Authenticated)?.user?.uid
        if (session == AuthSession.Loading) return@combine emptyList()
        val available = profiles.available(uid)
        val cloud = uid?.let { connection.cachedBudgets(it) }.orEmpty()
        val localRows = available.filter { it.kind != BudgetProfileKind.CLOUD }.mapNotNull { profile ->
            val database = databases.open(profile)
            val ready = try {
                database.operations().state()?.let { it.localReady || it.initialSyncComplete } == true
            } finally { database.close() }
            if (ready) ProfileBudget(profile.id, null, false, runtime?.profile?.id == profile.id) else null
        }
        val cloudRows = cloud.map { budget ->
            ProfileBudget("cloud:${budget.id}", budget.name, true, runtime?.profile?.budgetId == budget.id)
        }
        // An uploaded local budget is selectable before its first successful server exchange.
        val uploadedRows = available.filter { profile ->
            profile.kind == BudgetProfileKind.CLOUD && cloud.none { it.id == profile.budgetId }
        }.map { profile ->
            ProfileBudget(profile.id, uploads.find(profile.id)?.name, true, runtime?.profile?.id == profile.id)
        }
        // A suspended read from a previous account must never publish that account's catalog.
        if (uid == currentUid()) cloudRows + uploadedRows + localRows else emptyList()
    }.flowOn(Dispatchers.IO)

    override suspend fun refresh() {
        try { currentUid()?.let { connection.budgets(it) } }
        finally { revision.update { it + 1 } }
    }

    override suspend fun select(id: String) {
        val uid = currentUid()
        if (id.startsWith("cloud:")) {
            if (uid == null) throw BudgetConnectionFailure("sign_in_required")
            val budget = connection.cachedBudgets(uid).firstOrNull { "cloud:${it.id}" == id }
                ?: throw BudgetConnectionFailure("budget_not_found")
            connection.connect(uid, budget)
        } else active.select(id, expectedUid = uid)
    }

    override suspend fun createLocal(balance: BigDecimal, startedOn: LocalDate) {
        val uid = currentUid()
        val profile = profiles.createLocal()
        val database = databases.open(profile)
        try {
            CloudBudgetSession(database, LocalOnlyCloudApi, "", json, OperationSyncScheduler {})
                .createLocal(balance, startedOn)
        } finally { database.close() }
        if (uid != currentUid()) throw BudgetConnectionFailure("account_changed")
        active.select(profile.id, expectedUid = uid)
    }

    private fun currentUid() = (auth.session.value as? AuthSession.Authenticated)?.user?.uid
}
