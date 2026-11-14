package com.firkat.fillin.feature.budgetmanagement.data

import android.content.Context
import androidx.room.withTransaction
import androidx.work.*
import com.firkat.fillin.feature.auth.domain.*
import com.firkat.fillin.feature.budgetconnection.data.*
import com.firkat.fillin.feature.budgetprofile.data.*
import com.firkat.fillin.feature.budgetprofile.domain.*
import com.firkat.fillin.feature.budgetupload.domain.LocalBudgetUploadStore
import com.firkat.fillin.feature.sync.data.CreateCloudBudget
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/** One account-level outbox, independent of the currently selected financial database. */
class BudgetManagement(
    private val context: Context,
    private val db: BudgetProfileDatabase,
    private val registry: BudgetProfileRegistry,
    private val active: ActiveBudgetManager,
    private val auth: AuthRepository,
    private val requests: FirebaseBudgetRequests,
    private val uploads: LocalBudgetUploadStore,
    private val json: Json,
    private val enqueue: (() -> Unit)? = null,
    private val access: com.firkat.fillin.feature.appaccess.domain.AppAccess? = null,
    private val refreshCatalog: suspend (String) -> Unit = {}
) {
    private val mutex = Mutex()
    private val paused = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    suspend fun pause(
        uid: String
    ) {
        paused.add(uid)
        mutex.withLock { }
    }
    val entries = db.management().observe()
    val catalogChanges = db.catalog().observe()
    private fun uid() = (auth.session.value as? AuthSession.Authenticated)?.user?.uid

    fun start() {
        val connectivity = context.getSystemService(android.net.ConnectivityManager::class.java)
        connectivity.registerDefaultNetworkCallback(object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(
                network: android.net.Network
            ) {
                schedule()
            }
        })
        active.applicationScope.launch {
            db.management().all().filter {
                it.error == "sending"
            }.forEach { db.management().update(it.copy(error = null)) }
            mutex.withLock {
                db.management().all().filter { it.uid == null && it.deleting }.forEach { cleanup(it.profileId) }
            }
            auth.session.collect { if (it != AuthSession.Loading) schedule() }
        }
    }
    fun retry() {
        if (access?.cloudAllowed == false) return
        active.applicationScope.launch {
            db.management().all().filter {
                it.uid == uid() && it.error == "access_denied"
            }.forEach { db.management().remove(it.requestId) }
            schedule()
        }
    }
    fun schedule() {
        if (access?.cloudAllowed == false) return
        if (enqueue != null) {
            enqueue.invoke()
            return
        }
        WorkManager.getInstance(context).enqueueUniqueWork(
            "budget-management",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<BudgetManagementWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        )
    }

    suspend fun submit(
        id: String,
        name: String,
        deleting: Boolean,
        currency: BudgetCurrency? = null,
        rename: Boolean = true
    ) = mutex.withLock {
        val title = name.trim()
        require(title.length in 1..80 && title.none(Char::isISOControl))
        val currentUid = uid()
        val cloudId = id.takeIf { it.startsWith("cloud:") }?.removePrefix("cloud:")
        val profile = if (cloudId ==
            null
        ) {
            registry.find(id)
        } else {
            registry.available(currentUid).firstOrNull { it.budgetId == cloudId }
        }
        val catalog = cloudId?.let {
            db.catalog().budgets(requireNotNull(currentUid)).firstOrNull { row ->
                row.budgetId ==
                    it
            }
        }
        require(profile != null || catalog != null)
        require(profile?.accessibleTo(currentUid) != false)
        require(
            if (catalog !=
                null
            ) {
                catalog.role == "OWNER"
            } else {
                profile?.kind != BudgetProfileKind.CLOUD || profile.role == "OWNER"
            }
        )
        val profileId = profile?.id ?: id
        require(db.management().all().none { it.profileId == profileId && it.deleting })
        val cloud = catalog != null || profile?.kind == BudgetProfileKind.CLOUD
        if (cloud) access?.requireCloud()
        val budgetId = catalog?.budgetId ?: profile?.budgetId
        val original =
            db.management().all().lastOrNull {
                it.profileId == profileId &&
                    !it.deleting &&
                    it.rename &&
                    it.error != "access_denied"
            }?.name
                ?: catalog?.name
                ?: profile?.name
        val changeName = rename && title != original
        if (!deleting && !changeName && currency == null) return@withLock
        if (!cloud && !deleting && currency != null) {
            active.withLocalDatabase(profileId) { database ->
                database.withTransaction {
                    val state = requireNotNull(database.operations().state())
                    val bootstrap = state.bootstrapPayload?.let {
                        json.decodeFromString<CreateCloudBudget>(it).copy(currency = currency.name)
                    }
                    database.operations().putState(
                        state.copy(
                            currency = currency.name,
                            bootstrapPayload = bootstrap?.let { json.encodeToString(it) }
                        )
                    )
                }
            }
        }
        db.withTransaction {
            if (deleting) db.profiles().markSelectionRequired(profileId)
            if (deleting) {
                db.management().all().filter {
                    it.profileId == profileId
                }.forEach { db.management().remove(it.requestId) }
            }
            db.management().insert(
                BudgetManagementEntry(
                    UUID.randomUUID().toString(), profileId,
                    if (cloud) currentUid else null, budgetId, title, deleting,
                    maxOf(
                        System.currentTimeMillis(),
                        (
                            db.management().all().maxOfOrNull {
                                it.createdAt
                            } ?: 0L
                            ) + 1
                    ),
                    currency = currency?.name, rename = changeName
                )
            )
            if (!deleting &&
                !cloud &&
                profile != null &&
                changeName
            ) {
                db.profiles().find(profile.id)?.let { db.profiles().update(it.copy(name = title)) }
            }
            if (deleting && active.active.value?.profile?.id == profileId) {
                (registry as RoomBudgetProfileRegistry).deselect(currentUid)
                if (!cloud) registry.deselect(null)
            }
        }
        schedule()
        withContext(NonCancellable) {
            if (deleting) active.detach(profileId)
            if (!cloud) {
                if (deleting) {
                    cleanup(profileId)
                } else {
                    db.management().all().filter {
                        it.profileId == profileId
                    }.forEach { db.management().remove(it.requestId) }
                }
            }
        }
    }

    private suspend fun cleanup(
        profileId: String
    ) {
        if (active.active.value?.profile?.id == profileId) (registry as RoomBudgetProfileRegistry).deselect(uid())
        active.detach(profileId)
        val row = db.profiles().find(profileId)
        if (row !=
            null
        ) {
            check(!context.getDatabasePath(row.databaseName).exists() || context.deleteDatabase(row.databaseName))
        }
        db.withTransaction {
            db.profiles().deleteUpload(profileId)
            db.profiles().markSelectionRequired(profileId)
            db.profiles().deleteProfile(profileId)
            db.management().removeProfile(profileId)
        }
        active.revive(profileId)
        if (active.selectionRequired.value) active.restore()
    }

    suspend fun run(): Boolean = mutex.withLock {
        var successful = true
        val failedBudgets = mutableSetOf<String>()
        for (entry in db.management().all()) {
            val budgetKey = entry.budgetId ?: entry.profileId
            if (entry.uid != null && access?.cloudAllowed == false) continue
            if (budgetKey in failedBudgets) continue
            if (entry.error == "access_denied" ||
                (entry.uid != null && entry.uid in paused) ||
                entry.uid != null &&
                entry.uid != uid()
            ) {
                continue
            }
            try {
                if (entry.uid ==
                    null
                ) {
                    if (entry.deleting) cleanup(entry.profileId) else db.management().remove(entry.requestId)
                    continue
                }
                db.management().update(entry.copy(error = "sending"))
                requests.ensureCurrent(entry.uid)
                // Resolve an uncertain import before changing its name or deleting it.
                uploads.find(entry.profileId)?.let { upload ->
                    val imported = requests.importLocal(
                        entry.uid,
                        ImportFirebaseBudgetDto(
                            upload.name,
                            json.decodeFromString<CreateCloudBudget>(upload.bootstrapPayload)
                        )
                    )
                    if (entry.deleting &&
                        imported.code() == 409
                    ) {
                        imported.errorBody()?.close()
                    } else {
                        imported.budgetBody()
                    }
                }
                val response = if (entry.deleting) {
                    requests.delete(entry.uid, requireNotNull(entry.budgetId), entry.requestId)
                } else {
                    requests.rename(
                        entry.uid,
                        requireNotNull(entry.budgetId),
                        RenameBudgetDto(
                            entry.requestId,
                            entry.name.takeIf {
                                entry.rename
                            },
                            entry.currency
                        )
                    )
                }
                requests.ensureCurrent(entry.uid)
                if (!response.isSuccessful) {
                    val code = response.code()
                    response.errorBody()?.close()
                    access?.requireCloud()
                    if (code in setOf(403, 404)) {
                        val rows = requests.budgets(entry.uid).budgetBody()
                        val remote = rows.firstOrNull { it.id == entry.budgetId }
                        if (remote ==
                            null
                        ) {
                            db.catalog().remove(entry.uid, requireNotNull(entry.budgetId))
                            cleanup(entry.profileId)
                            continue
                        }
                        if (remote.role != "OWNER") {
                            db.withTransaction {
                                db.management().update(
                                    entry.copy(deleting = false, name = remote.name, error = "access_denied")
                                )
                                db.catalog().remember(
                                    CachedCloudBudget(
                                        entry.uid,
                                        remote.id,
                                        remote.name,
                                        remote.memberId,
                                        remote.role,
                                        remote.currency,
                                        remote.currencyVersion
                                    )
                                )
                            }
                            active.revive(entry.profileId)
                            continue
                        }
                    }
                    error("HTTP $code")
                }
                if (entry.deleting) {
                    db.catalog().remove(entry.uid, requireNotNull(entry.budgetId))
                    cleanup(entry.profileId)
                } else {
                    val rows = requests.budgets(entry.uid).budgetBody()
                    requests.ensureCurrent(entry.uid)
                    db.withTransaction {
                        rows.forEach {
                            db.catalog().remember(
                                CachedCloudBudget(
                                    entry.uid,
                                    it.id,
                                    it.name,
                                    it.memberId,
                                    it.role,
                                    it.currency,
                                    it.currencyVersion
                                )
                            )
                        }
                        db.management().remove(entry.requestId)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                db.management().update(entry.copy(error = "retry"))
                successful = false
                failedBudgets.add(budgetKey) // Other budgets can proceed; this budget retains request order.
            }
        }
        uid()?.let { account ->
            try {
                refreshCatalog(account)
            } catch (
                cancelled: CancellationException
            ) {
                throw cancelled
            } catch (_: Exception) {
                successful = false
            }
        }
        successful
    }
}

class BudgetManagementWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val service = org.koin.core.context.GlobalContext.get().get<BudgetManagement>()
        return if (service.run()) Result.success() else Result.retry()
    }
}
