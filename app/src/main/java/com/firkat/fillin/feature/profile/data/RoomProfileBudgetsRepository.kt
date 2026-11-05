package com.firkat.fillin.feature.profile.data

import com.firkat.fillin.feature.budgetprofile.domain.BudgetCurrency

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
import com.firkat.fillin.feature.sync.data.CreateCloudBudget
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
    private val localCreator: com.firkat.fillin.feature.budgetsetup.domain.LocalBudgetCreator = com.firkat.fillin.feature.budgetsetup.data.RoomLocalBudgetCreator(auth, profiles, active, databases, json),
    private val management: com.firkat.fillin.feature.budgetmanagement.data.BudgetManagement? = null,
) : ProfileBudgetsRepository {
    private val revision = MutableStateFlow(0)

    override fun observe() = combine(auth.session, active.active, revision, management?.entries ?: kotlinx.coroutines.flow.flowOf(emptyList()), management?.catalogChanges ?: kotlinx.coroutines.flow.flowOf(emptyList())) { session, runtime, _, pending, _ ->
        val uid = (session as? AuthSession.Authenticated)?.user?.uid
        if (session == AuthSession.Loading) return@combine emptyList()
        val available = profiles.available(uid)
        val cloud = uid?.let { connection.cachedBudgets(it) }.orEmpty()
        val localRows = available.filter { it.kind != BudgetProfileKind.CLOUD }.mapNotNull { profile ->
            val database = databases.open(profile)
            val state = try { database.operations().state() } finally { database.close() }
            if (state != null && (state.localReady || state.initialSyncComplete)) {
                ProfileBudget(profile.id, profile.name, false, runtime?.profile?.id == profile.id,
                    currency = BudgetCurrency.valueOf(state.currency))
            } else null
        }
        val cloudRows = cloud.map { budget ->
            ProfileBudget("cloud:${budget.id}", budget.name, true, runtime?.profile?.budgetId == budget.id, budget.role == "OWNER", currency = BudgetCurrency.valueOf(budget.currency))
        }
        // An uploaded local budget is selectable before its first successful server exchange.
        val uploadedRows = available.filter { profile ->
            profile.kind == BudgetProfileKind.CLOUD && cloud.none { it.id == profile.budgetId }
        }.map { profile ->
            val upload = uploads.find(profile.id)
            val currency = upload?.let { json.decodeFromString<CreateCloudBudget>(it.bootstrapPayload).currency }
            ProfileBudget(profile.id, upload?.name, true, runtime?.profile?.id == profile.id, profile.role == "OWNER",
                currency = currency?.let(BudgetCurrency::valueOf) ?: BudgetCurrency.KZT)
        }
        // A suspended read from a previous account must never publish that account's catalog.
        if (uid == currentUid()) (cloudRows + uploadedRows + localRows).mapNotNull { row ->
            val changes = pending.filter { it.error != "access_denied" && (it.uid == null || it.uid == uid) && (it.profileId == row.id || "cloud:${it.budgetId}" == row.id) }
            if (changes.any { it.deleting }) null else row.copy(
                name = changes.lastOrNull { it.rename }?.name ?: row.name,
                pendingRename = changes.isNotEmpty(),
                currency = changes.asReversed().firstNotNullOfOrNull { it.currency }?.let(BudgetCurrency::valueOf) ?: row.currency,
            )
        } else emptyList()
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
        createLocalInternal(null, balance, startedOn)
    }

    override suspend fun createLocal(balance: BigDecimal, startedOn: LocalDate, currency: BudgetCurrency) {
        localCreator.createLocal(null, balance, startedOn, currency)
    }

    override suspend fun createLocal(name: String, balance: BigDecimal, startedOn: LocalDate) {
        createLocalInternal(name, balance, startedOn)
    }

    private suspend fun createLocalInternal(name: String?, balance: BigDecimal, startedOn: LocalDate) {
        localCreator.createLocal(name, balance, startedOn)
    }

    private fun currentUid() = (auth.session.value as? AuthSession.Authenticated)?.user?.uid
}
