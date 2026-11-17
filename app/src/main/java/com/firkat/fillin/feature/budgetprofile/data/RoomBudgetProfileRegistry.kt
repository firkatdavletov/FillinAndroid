package com.firkat.fillin.feature.budgetprofile.data

import androidx.room.withTransaction
import com.firkat.fillin.feature.budgetprofile.domain.*
import com.firkat.fillin.feature.sync.domain.BudgetConnectionFailure
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

const val LOCAL_BUDGET_LIMIT = 2

private const val LOCAL_RESERVED = 1
private const val LOCAL_READY = 2
private const val LOCAL_EMPTY = 3

class RoomBudgetProfileRegistry(private val database: BudgetProfileDatabase, backendUrl: String) :
    BudgetProfileRegistry {
    private val backend = MessageDigest.getInstance("SHA-256").digest(backendUrl.toByteArray(Charsets.UTF_8))
        .take(8).joinToString("") { "%02x".format(it) }

    // Register the original file in place. Never rename, copy or recreate its contents.
    private val legacy = BudgetProfile("legacy-$backend", "operations-$backend.db", BudgetProfileKind.LEGACY)
    private val dao get() = database.profiles()

    private val recoveryLock = Mutex()
    private var recovered = false

    fun observeLocalCount() = dao.observeLocalCount()

    internal fun observeReadyBudgetExists(
        uid: String?,
        databases: BudgetDatabaseFactory
    ) = database.invalidationTracker.createFlow("budget_profiles", "budget_management", "budget_profile_selection")
        .map { available(uid).any { isReady(it, databases) } }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    suspend fun recoverLocalCreations(
        databases: BudgetDatabaseFactory
    ) = recoveryLock.withLock {
        if (recovered) return@withLock
        initialize()
        for (row in dao.localRecovery()) {
            val profile = row.profile()
            val ready = isReady(profile, databases)
            if (!ready && row.localCreationState == LOCAL_RESERVED) {
                databases.discardUnregisteredCache(profile)
                dao.deleteProfile(row.id)
            } else {
                dao.setLocalCreationState(row.id, if (ready) LOCAL_READY else LOCAL_EMPTY)
            }
        }
        recovered = true
    }

    suspend fun reserveLocal(
        name: String?,
        databases: BudgetDatabaseFactory
    ): BudgetProfile {
        recoverLocalCreations(databases)
        return database.withTransaction {
            if (dao.localCount() >= LOCAL_BUDGET_LIMIT) throw BudgetConnectionFailure("local_budget_limit")
            val id = UUID.randomUUID().toString()
            BudgetProfile(id, "budget-$id.db", BudgetProfileKind.LOCAL, name = name).also {
                dao.insert(it.entity().copy(localCreationState = LOCAL_RESERVED))
            }
        }
    }

    suspend fun settleLocal(
        profile: BudgetProfile,
        databases: BudgetDatabaseFactory
    ) {
        if (isReady(profile, databases)) {
            dao.setLocalCreationState(profile.id, LOCAL_READY)
        } else {
            databases.discardUnregisteredCache(profile)
            dao.deleteProfile(profile.id)
        }
    }

    suspend fun isReady(
        profile: BudgetProfile,
        databases: BudgetDatabaseFactory
    ): Boolean {
        if (!databases.exists(profile)) return false
        val database = databases.open(profile)
        return try {
            database.operations().state()?.let { it.localReady || it.initialSyncComplete } == true
        } finally {
            database.close()
        }
    }

    override suspend fun initialize(): BudgetProfile = database.withTransaction {
        dao.insert(legacy.entity())
        val original = requireNotNull(dao.find(legacy.id)).profile()
        if (original.kind != BudgetProfileKind.CLOUD) {
            original
        } else {
            val fallback = BudgetProfile("local-$backend", "budget-local-$backend.db", BudgetProfileKind.LOCAL)
            dao.insert(fallback.entity())
            requireNotNull(dao.find(fallback.id)).profile()
        }
    }

    suspend fun requiresSelection(
        uid: String?
    ): Boolean = dao.selection(accountKey(uid)) == "choose-budget"
    suspend fun deselect(
        uid: String?
    ) {
        dao.select(BudgetProfileSelection(accountKey(uid), "choose-budget"))
    }

    override suspend fun find(
        id: String
    ): BudgetProfile? = dao.find(id)?.profile()

    override suspend fun available(
        uid: String?
    ): List<BudgetProfile> {
        val hidden = database.management().all().filter { it.deleting }.map { it.profileId }.toSet()
        return dao.available(uid).filter {
            it.id !in hidden && it.localCreationState != LOCAL_RESERVED
        }.map { it.profile() }
    }

    override suspend fun createLocal(): BudgetProfile {
        val id = UUID.randomUUID().toString()
        return BudgetProfile(id, "budget-$id.db", BudgetProfileKind.LOCAL).also { dao.insert(it.entity()) }
    }

    override suspend fun createLocal(
        name: String
    ): BudgetProfile {
        require(name.isNotBlank() && name.length <= 80 && name.none(Char::isISOControl))
        val id = UUID.randomUUID().toString()
        return BudgetProfile(id, "budget-$id.db", BudgetProfileKind.LOCAL, name = name)
            .also { dao.insert(it.entity()) }
    }

    override suspend fun registerCloud(
        uid: String,
        budgetId: String,
        role: String
    ): BudgetProfile = database.withTransaction {
        require(uid.isNotBlank() && budgetId.isNotBlank() && role in setOf("OWNER", "MEMBER"))
        dao.cloud(uid, budgetId)?.profile() ?: run {
            val id = UUID.randomUUID().toString()
            val profile = BudgetProfile(id, "budget-$id.db", BudgetProfileKind.CLOUD, uid, budgetId, role)
            dao.insert(profile.entity())
            requireNotNull(dao.cloud(uid, budgetId)).profile()
        }
    }

    override suspend fun replaceCloudMembership(
        originalId: String,
        replacement: BudgetProfile
    ) = database.withTransaction {
        val original = requireNotNull(dao.find(originalId))
        require(original.kind == "CLOUD" && original.role == "MEMBER" && original.activeCloudKey != null)
        require(replacement.kind == BudgetProfileKind.CLOUD && replacement.role == "MEMBER" && !replacement.archived)
        require(
            replacement.firebaseUid == original.firebaseUid &&
                replacement.budgetId == original.budgetId &&
                replacement.id != original.id
        )
        dao.update(original.copy(activeCloudKey = null))
        dao.insert(replacement.entity())
        check(
            dao.cloud(requireNotNull(replacement.firebaseUid), requireNotNull(replacement.budgetId))?.id ==
                replacement.id
        )
        dao.select(BudgetProfileSelection(accountKey(replacement.firebaseUid), replacement.id))
    }

    override suspend fun selected(
        uid: String?
    ): BudgetProfile = database.withTransaction {
        val fallback = initialize()
        (dao.selection(accountKey(uid)) ?: dao.selection("local"))
            ?.let { dao.find(it)?.profile() }
            ?.takeIf { it.accessibleTo(uid) } ?: fallback
    }

    override suspend fun attachLegacy(
        id: String,
        uid: String,
        budgetId: String,
        role: String
    ): BudgetProfile = database.withTransaction {
        val original = requireNotNull(find(id))
        require(original.kind == BudgetProfileKind.LEGACY && role in setOf("OWNER", "MEMBER"))
        check(dao.cloud(uid, budgetId) == null) { "Budget is already registered" }
        val profile = original.copy(kind = BudgetProfileKind.CLOUD, firebaseUid = uid, budgetId = budgetId, role = role)
        dao.update(profile.entity())
        dao.select(BudgetProfileSelection(accountKey(uid), id))
        profile
    }

    override suspend fun select(
        id: String,
        uid: String?
    ): BudgetProfile = database.withTransaction {
        val profile = requireNotNull(find(id))
        require(database.management().all().none { it.profileId == id && it.deleting })
        require(profile.accessibleTo(uid)) { "Budget profile is unavailable for this account" }
        dao.select(BudgetProfileSelection(accountKey(uid), id))
        if (profile.kind != BudgetProfileKind.CLOUD && uid != null) {
            dao.select(BudgetProfileSelection("local", id))
        }
        profile
    }

    private fun accountKey(
        uid: String?
    ) = uid?.let { "uid:$it" } ?: "local"
    private fun BudgetProfile.entity() = BudgetProfileEntity(
        id,
        databaseName,
        kind.name,
        firebaseUid,
        budgetId,
        role,
        if (archived) null else budgetId,
        name
    )
    private fun BudgetProfileEntity.profile() = BudgetProfile(
        id,
        databaseName,
        BudgetProfileKind.valueOf(kind),
        firebaseUid,
        budgetId,
        role,
        kind == "CLOUD" && activeCloudKey == null,
        name
    )
}
