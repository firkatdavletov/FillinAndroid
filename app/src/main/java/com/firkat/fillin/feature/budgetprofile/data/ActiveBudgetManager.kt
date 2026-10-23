package com.firkat.fillin.feature.budgetprofile.data

import com.firkat.fillin.feature.auth.domain.AuthRepository
import com.firkat.fillin.feature.auth.domain.AuthSession
import com.firkat.fillin.feature.budgetprofile.di.BUDGET_SCOPE
import com.firkat.fillin.feature.budgetprofile.domain.*
import com.firkat.fillin.feature.operationhistory.data.sync.OperationSyncRequests
import com.firkat.fillin.feature.operationhistory.data.sync.OperationsSynchronizer
import com.firkat.fillin.feature.operationhistory.data.local.OperationDatabase
import com.firkat.fillin.feature.sync.domain.BudgetConnectionFailure
import androidx.room.withTransaction
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.Koin
import org.koin.core.scope.Scope
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentHashMap

/** A scope remains open while its UI or an in-flight worker still holds a lease. */
class BudgetRuntime(val profile: BudgetProfile, val scope: Scope, private val onClosed: (BudgetRuntime) -> Unit = {}) {
    private var references = 1

    @Synchronized
    fun retain(): BudgetRuntimeLease? {
        if (!retainOwner()) return null
        return BudgetRuntimeLease(this)
    }

    @Synchronized
    internal fun retainOwner(): Boolean {
        if (references == 0) return false
        references++
        return true
    }

    internal fun release() {
        val close = synchronized(this) {
            check(references > 0)
            --references == 0
        }
        if (close) {
            scope.close()
            onClosed(this)
        }
    }
}

class BudgetRuntimeLease internal constructor(val runtime: BudgetRuntime) : AutoCloseable {
    private val closed = AtomicBoolean()
    val scope: Scope get() = runtime.scope
    override fun close() {
        if (closed.compareAndSet(false, true)) runtime.release()
    }
}

class ActiveBudgetManager(
    private val registry: BudgetProfileRegistry,
    private val auth: AuthRepository,
    private val koin: Koin,
    private val cancelWork: (BudgetSyncIdentity) -> Unit,
) : AutoCloseable {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val runtimes = ConcurrentHashMap<BudgetProfile, BudgetRuntime>()
    private val _active = MutableStateFlow<BudgetRuntime?>(null)
    val active: StateFlow<BudgetRuntime?> = _active.asStateFlow()
    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed.asStateFlow()
    private var observer: Job? = null
    private val initialized = CompletableDeferred<Unit>()

    fun start() {
        if (observer != null) return
        observer = applicationScope.launch {
            auth.session.map { (it as? AuthSession.Authenticated)?.user?.uid }.distinctUntilChanged().collect {
                restore()
            }
        }
    }

    suspend fun restore() = mutex.withLock {
        try {
            var uid: String?
            var profile: BudgetProfile
            do {
                uid = currentUid()
                profile = registry.selected(uid)
            } while (uid != currentUid())
            activate(profile)
            _failed.value = false
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _failed.value = true
        } finally {
            initialized.complete(Unit)
        }
    }

    suspend fun select(profileId: String, expectedUid: String? = null) = mutex.withLock {
        val uid = currentUid()
        if (expectedUid != null && uid != expectedUid) throw BudgetConnectionFailure("account_changed")
        val profile = registry.select(profileId, uid)
        check(uid == currentUid()) { "Account changed during profile selection" }
        activate(profile)
    }

    /** Never resolve an old job through the active profile's dependencies. */
    suspend fun acquire(identity: BudgetSyncIdentity): BudgetRuntimeLease? {
        initialized.await()
        return mutex.withLock {
            _active.value?.takeIf { it.profile.syncIdentity == identity && isCurrent(it.profile) }?.retain()
        }
    }

    fun isCurrent(profile: BudgetProfile): Boolean = _active.value?.profile == profile && profile.accessibleTo(currentUid())

    /** Publish ownership only after the old exchange has stopped; never move the database file. */
    suspend fun attachLegacy(uid: String, budgetId: String, memberId: String, role: String) = mutex.withLock {
        val previous = requireNotNull(_active.value)
        check(previous.profile.kind == BudgetProfileKind.LEGACY && currentUid() == uid)
        withContext(NonCancellable) {
            _active.value = null
            try {
                cancelWork(previous.profile.syncIdentity)
                previous.scope.get<OperationSyncRequests>().cancelForeground()
                previous.scope.get<OperationsSynchronizer>().awaitIdle()
                if (currentUid() != uid) throw BudgetConnectionFailure("account_changed")
                val database = previous.scope.get<OperationDatabase>()
                val state = requireNotNull(database.operations().state())
                if (state.householdId != budgetId || state.memberId != memberId) throw BudgetConnectionFailure("scope_changed")
                if (state.protocolVersion != 4 || database.operations().pending(state.scopeKey).isNotEmpty() || database.budget().commands(state.scopeKey).any { it.protocolVersion != 4 && it.status == "PENDING" }) {
                    throw BudgetConnectionFailure("unsupported_legacy_queue")
                }
                registry.attachLegacy(previous.profile.id, uid, budgetId, role)
                database.withTransaction {
                    val latest = requireNotNull(database.operations().state())
                    database.operations().putState(latest.copy(cloudEnabled = true, cloudBound = true, errorCode = null))
                }
            } finally {
                previous.release()
                activate(registry.selected(currentUid()))
            }
        }
    }

    /** Reserve one local file for one UID; no network is allowed under this switch lock. */
    suspend fun attachLocal(uid: String, profileId: String, bind: suspend (BudgetProfile, OperationDatabase) -> Unit) = mutex.withLock {
        if (currentUid() != uid) throw BudgetConnectionFailure("account_changed")
        val profile = registry.find(profileId) ?: throw BudgetConnectionFailure("scope_changed")
        require(profile.kind != BudgetProfileKind.CLOUD)
        activate(profile)
        val previous = requireNotNull(_active.value)
        withContext(NonCancellable) {
            _active.value = null
            try {
                cancelWork(previous.profile.syncIdentity)
                previous.scope.get<OperationSyncRequests>().cancelForeground()
                previous.scope.get<OperationsSynchronizer>().awaitIdle()
                if (currentUid() != uid) throw BudgetConnectionFailure("account_changed")
                bind(previous.profile, previous.scope.get())
            } finally {
                previous.release()
                activate(registry.selected(currentUid()))
            }
        }
    }

    private fun currentUid(): String? = (auth.session.value as? AuthSession.Authenticated)?.user?.uid

    private fun activate(profile: BudgetProfile) {
        val previous = _active.value
        if (previous?.profile == profile) return
        // Returning while an old request finishes must reuse that profile's mutex and DB handle.
        val runtime = runtimes[profile]?.takeIf { it.retainOwner() } ?: BudgetRuntime(
            profile, koin.createScope("budget-${UUID.randomUUID()}", BUDGET_SCOPE, profile),
            onClosed = { runtimes.remove(profile, it) },
        ).also { runtimes[profile] = it }
        _active.value = runtime
        previous?.let {
            cancelWork(it.profile.syncIdentity)
            it.scope.get<OperationSyncRequests>().cancelForeground()
            it.release()
        }
    }

    override fun close() {
        applicationScope.cancel()
        _active.value?.let {
            _active.value = null
            it.scope.get<OperationSyncRequests>().cancelForeground()
            it.release()
        }
    }
}
