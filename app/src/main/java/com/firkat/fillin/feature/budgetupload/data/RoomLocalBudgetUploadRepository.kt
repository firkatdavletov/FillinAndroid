package com.firkat.fillin.feature.budgetupload.data

import androidx.room.withTransaction
import com.firkat.fillin.feature.auth.domain.*
import com.firkat.fillin.feature.budgetprofile.data.*
import com.firkat.fillin.feature.budgetprofile.domain.*
import com.firkat.fillin.feature.budgetupload.domain.*
import com.firkat.fillin.feature.operationhistory.data.local.*
import com.firkat.fillin.feature.operationhistory.data.sync.OperationSyncScheduler
import com.firkat.fillin.feature.sync.data.CreateCloudBudget
import com.firkat.fillin.feature.sync.domain.BudgetConnectionFailure
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json

@OptIn(ExperimentalCoroutinesApi::class)
class RoomLocalBudgetUploadRepository(
    private val auth: AuthRepository,
    private val profiles: BudgetProfileRegistry,
    private val store: LocalBudgetUploadStore,
    private val databases: BudgetDatabaseFactory,
    private val active: ActiveBudgetManager,
    private val scheduler: OperationSyncScheduler,
    private val json: Json,
) : LocalBudgetUploadRepository {
    override val candidates: Flow<List<LocalBudgetCandidate>> = combine(auth.session, active.active) { session, runtime -> session to runtime }
        .flatMapLatest { (session, runtime) ->
            val uid = (session as? AuthSession.Authenticated)?.user?.uid
            if (runtime == null || !runtime.profile.accessibleTo(uid)) flowOf(emptyList()) else flow {
                val lease = runtime.retain() ?: return@flow
                try {
                    lease.scope.get<OperationDatabase>().operations().observeContent().collect {
                        val rows = candidates(uid)
                        if ((auth.session.value as? AuthSession.Authenticated)?.user?.uid == uid) emit(rows)
                    }
                } finally { lease.close() }
            }
        }.flowOn(Dispatchers.IO)

    private suspend fun candidates(uid: String?): List<LocalBudgetCandidate> = profiles.available(uid).mapNotNull { profile ->
        val upload = store.find(profile.id)
        if (profile.kind == BudgetProfileKind.CLOUD && (upload == null || upload.firebaseUid != uid)) return@mapNotNull null
        val db = databases.open(profile)
        try {
            val state = db.operations().state() ?: return@mapNotNull null
            if (!state.localReady) return@mapNotNull null
            if (upload != null) {
                val pending = db.budget().commands(state.scopeKey).any { it.status in setOf("PENDING", "BLOCKED", "REJECTED", "CONFLICT") }
                if (state.cloudBound && !pending) null else LocalBudgetCandidate(profile.id, upload.name, true, state.isSyncing, upload.firebaseUid)
            } else if (!state.cloudBound && !state.initialSyncComplete && state.bootstrapPayload != null) {
                LocalBudgetCandidate(profile.id, "Мой бюджет", false)
            } else null
        } finally { db.close() }
    }

    override suspend fun connect(uid: String, profileId: String, name: String) {
        ensureCurrent(uid)
        withContext(NonCancellable) {
            val profile = profiles.find(profileId) ?: throw BudgetConnectionFailure("scope_changed")
            if (profile.kind == BudgetProfileKind.CLOUD) {
                val saved = store.find(profileId) ?: throw BudgetConnectionFailure("scope_changed")
                if (saved.firebaseUid != uid) throw BudgetConnectionFailure("account_changed")
                active.select(profileId)
                val lease = active.acquire(profile.syncIdentity) ?: throw BudgetConnectionFailure("account_changed")
                try {
                    lease.scope.get<OperationDatabase>().withTransaction {
                        val db = lease.scope.get<OperationDatabase>()
                        val state = requireNotNull(db.operations().state())
                        db.operations().putState(state.copy(cloudEnabled = true, errorCode = null))
                    }
                } finally { lease.close() }
            } else {
                val title = name.trim()
                require(title.length in 1..80 && title.none(Char::isISOControl))
                active.attachLocal(uid, profileId) { source, db ->
                    val state = requireNotNull(db.operations().state())
                    if (state.protocolVersion != 4 || db.operations().pending(state.scopeKey).isNotEmpty() ||
                        db.budget().commands(state.scopeKey).any { it.protocolVersion != 4 || it.status == "ACKNOWLEDGED" }) {
                        throw BudgetConnectionFailure("unsupported_legacy_queue")
                    }
                    if (!state.localReady || state.cloudBound || state.initialSyncComplete || state.cursor != null || state.lastRevision != 0L) throw BudgetConnectionFailure("not_local_budget")
                    val payload = state.bootstrapPayload ?: throw BudgetConnectionFailure("not_local_budget")
                    val bootstrap = json.decodeFromString<CreateCloudBudget>(payload)
                    require(bootstrap.householdId == state.householdId && bootstrap.memberId == state.memberId)
                    ensureCurrent(uid)
                    store.bind(source, uid, state.householdId, title, payload)
                    db.withTransaction {
                        val latest = requireNotNull(db.operations().state())
                        db.operations().putState(latest.copy(cloudEnabled = true, errorCode = null))
                    }
                }
            }
            // Publishing a CLOUD runtime replaces the old budget ViewModelStore and cancels its caller.
            if (currentUid() == uid) scheduler.schedule()
        }
    }

    private fun currentUid() = (auth.session.value as? AuthSession.Authenticated)?.user?.uid
    private fun ensureCurrent(uid: String) { if (currentUid() != uid) throw BudgetConnectionFailure("account_changed") }
}
