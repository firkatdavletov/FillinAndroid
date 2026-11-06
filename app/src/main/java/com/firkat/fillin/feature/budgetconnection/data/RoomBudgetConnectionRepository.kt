package com.firkat.fillin.feature.budgetconnection.data

import com.firkat.fillin.feature.budgetprofile.domain.BudgetCurrency

import androidx.room.withTransaction
import com.firkat.fillin.feature.budgetconnection.domain.*
import com.firkat.fillin.feature.budgetprofile.data.*
import com.firkat.fillin.feature.budgetprofile.domain.*
import com.firkat.fillin.feature.operationhistory.data.local.*
import com.firkat.fillin.feature.operationhistory.data.remote.SyncScopeDto
import com.firkat.fillin.feature.operationhistory.data.sync.OperationSyncScheduler
import com.firkat.fillin.feature.sync.domain.BudgetConnectionFailure
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.time.Instant
import java.util.UUID

class RoomBudgetConnectionRepository(
    private val requests: FirebaseBudgetRequests,
    private val profiles: BudgetProfileRegistry,
    private val databases: BudgetDatabaseFactory,
    private val active: ActiveBudgetManager,
    private val scheduler: OperationSyncScheduler,
    private val json: Json,
    private val catalog: BudgetCatalogStore,
    private val access: com.firkat.fillin.feature.appaccess.domain.AppAccess? = null,
    private val eraseMissing: suspend (String, Set<String>) -> Unit = { _, _ -> },
) : BudgetConnectionRepository {
    private val mutex = Mutex()

    override val activeBudgetId = active.active.map { runtime ->
        runtime?.takeIf { active.isCurrent(it.profile) }?.profile?.budgetId
    }.distinctUntilChanged()

    override suspend fun cachedBudgets(uid: String): List<AvailableBudget> {
        requests.ensureCurrent(uid)
        return catalog.budgets(uid).also { requests.ensureCurrent(uid) }
    }

    override suspend fun budgets(uid: String): List<AvailableBudget> = mutex.withLock { mapped {
        access?.requireCloud()
        requests.budgets(uid).budgetBody().map {
            UUID.fromString(it.id); UUID.fromString(it.memberId)
            require(it.name.isNotBlank() && it.role in setOf("OWNER", "MEMBER"))
            AvailableBudget(it.id, it.name, it.memberId, it.role, it.currency, it.currencyVersion)
        }.also { rows ->
            require(rows.map { it.id }.distinct().size == rows.size)
            requests.ensureCurrent(uid)
            access?.requireCloud()
            eraseMissing(uid, catalog.budgets(uid).map { it.id }.toSet() - rows.map { it.id }.toSet())
            requests.ensureCurrent(uid)
            catalog.replace(uid, rows)
        }.let { catalog.budgets(uid) }
    } }

    suspend fun awaitIdle() = mutex.withLock { }

    override suspend fun connect(uid: String, budget: AvailableBudget) = mutex.withLock {
        mapped {
            access?.requireCloud()
            requests.ensureCurrent(uid)
            // A downloaded budget is immediately selectable without a network exchange.
            // The catalog is UID scoped; unknown/partial profiles still require a full download.
            val known = catalog.budgets(uid).firstOrNull { it.id == budget.id }
            val existing = profiles.available(uid).firstOrNull { it.firebaseUid == uid && it.budgetId == budget.id }
            if (known == budget && existing != null && selectDownloaded(uid, existing, budget)) return@mapped
            val remote = requests.budget(uid, budget.id).budgetBody().scope
            validate(remote, budget)
            val current = active.active.value
            if (current?.profile?.kind == BudgetProfileKind.LEGACY) {
                val lease = current.retain() ?: throw BudgetConnectionFailure("account_changed")
                try {
                    val state = lease.scope.get<OperationDatabase>().operations().state()
                    if (state?.householdId == budget.id && state.memberId == remote.memberId) {
                        active.attachLegacy(uid, budget.id, remote.memberId, remote.role)
                        catalog.remember(uid, budget.copy(role = remote.role, currency = remote.currency, currencyVersion = remote.currencyVersion))
                        scheduler.schedule()
                        return@mapped
                    }
                } finally { lease.close() }
            }
            var profile = profiles.registerCloud(uid, budget.id, remote.role)
            val previous = databases.open(profile)
            val replaced = try {
                val state = previous.operations().state()
                val changed = state?.let { (it.localReady || it.initialSyncComplete) && it.householdId == remote.householdId && it.memberId != remote.memberId } == true
                if (changed && (state?.role != "MEMBER" || remote.role != "MEMBER")) throw BudgetConnectionFailure("scope_changed")
                changed
            } finally { previous.close() }
            val originalProfile = profile.takeIf { replaced }
            if (replaced) {
                val id = UUID.randomUUID().toString()
                profile = BudgetProfile(id, "budget-$id.db", BudgetProfileKind.CLOUD, uid, budget.id, remote.role)
            }
            val database = databases.open(profile)
            var loaded = false
            try {
                val original = database.operations().state()
                if (original?.let { it.localReady || it.initialSyncComplete } != true) {
                    try {
                        database.withTransaction {
                            database.budget().clearStaged()
                            database.operations().putState(OperationSyncState(scopeKey = remote.key, householdId = remote.householdId,
                                memberId = remote.memberId, accountingStartedAt = remote.accountingStartedAt, role = remote.role,
                                protocolVersion = 4, cloudEnabled = true, cloudBound = true))
                        }
                        val storage = RoomOperationSyncStorage(database, json, publishReady = false)
                        do {
                            val cursor = storage.state().cursor
                            val page = requests.changes(uid, budget.id, cursor).budgetBody()
                            require(page.scope.copy(currency = remote.currency, currencyVersion = remote.currencyVersion) == remote && page.nextCursor.isNotBlank() && (!page.hasMore || page.nextCursor != cursor))
                            storage.apply(page)
                        } while (page.hasMore)
                        requests.ensureCurrent(uid)
                        withContext(NonCancellable) {
                            database.withTransaction {
                                val complete = requireNotNull(database.operations().state())
                                database.operations().putState(complete.copy(localReady = true, initialSyncComplete = true, lastSyncedAt = Instant.now().toString()))
                            }
                        }
                    } catch (e: Exception) {
                        withContext(NonCancellable) {
                            database.withTransaction {
                                database.budget().clearStaged()
                                database.operations().putState(original ?: OperationSyncState())
                            }
                        }
                        throw e
                    }
                } else {
                    if (original.householdId != remote.householdId || original.memberId != remote.memberId) throw BudgetConnectionFailure("scope_changed")
                    if (original.protocolVersion != 4) throw BudgetConnectionFailure("unsupported_legacy_queue")
                    database.withTransaction {
                        val latest = requireNotNull(database.operations().state())
                        database.operations().putState(latest.copy(cloudEnabled = true, cloudBound = true, role = remote.role, errorCode = null))
                    }
                }
                loaded = true
            } finally {
                database.close()
                if (originalProfile != null && !loaded) databases.discardUnregisteredCache(profile)
            }
            try {
                currentCoroutineContext().ensureActive()
                withContext(NonCancellable) {
                    requests.ensureCurrent(uid)
                    if (originalProfile != null) profiles.replaceCloudMembership(originalProfile.id, profile)
                    catalog.remember(uid, budget.copy(role = remote.role, currency = remote.currency, currencyVersion = remote.currencyVersion))
                    active.select(profile.id, expectedUid = uid)
                    scheduler.schedule()
                }
            } finally {
                if (originalProfile != null) withContext(NonCancellable) {
                    if (profiles.find(profile.id) == null) databases.discardUnregisteredCache(profile)
                }
            }
        }
    }

    private suspend fun selectDownloaded(uid: String, profile: BudgetProfile, budget: AvailableBudget): Boolean {
        val database = databases.open(profile)
        try {
            val original = database.operations().state() ?: return false
            if (!original.localReady && !original.initialSyncComplete) return false
            if (original.householdId != budget.id) throw BudgetConnectionFailure("scope_changed")
            if (original.memberId != budget.memberId) return false
            if (original.protocolVersion != 4) throw BudgetConnectionFailure("unsupported_legacy_queue")
            database.withTransaction {
                val latest = requireNotNull(database.operations().state())
                database.operations().putState(latest.copy(cloudEnabled = true, role = budget.role, errorCode = null))
            }
        } finally { database.close() }
        requests.ensureCurrent(uid)
        active.select(profile.id, expectedUid = uid)
        scheduler.schedule()
        return true
    }

    private fun validate(scope: SyncScopeDto, budget: AvailableBudget) {
        UUID.fromString(scope.householdId); UUID.fromString(scope.memberId)
        require(scope.householdId == budget.id && scope.memberId == budget.memberId && scope.role in setOf("OWNER", "MEMBER"))
        require(BudgetCurrency.entries.any { it.name == scope.currency } && scope.timeZone == "Asia/Almaty")
        Instant.parse(scope.accountingStartedAt)
    }

    private suspend fun <T> mapped(block: suspend () -> T): T = try { block() } catch (e: HttpException) {
        e.response()?.errorBody()?.close()
        throw BudgetConnectionFailure(when (e.code()) { 401 -> "unauthorized"; 403, 404 -> "access_denied"; else -> "server_unavailable" })
    } catch (_: IllegalArgumentException) {
        throw BudgetConnectionFailure("invalid_response")
    }
}
