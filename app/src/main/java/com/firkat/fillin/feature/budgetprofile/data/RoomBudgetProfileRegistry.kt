package com.firkat.fillin.feature.budgetprofile.data

import androidx.room.withTransaction
import com.firkat.fillin.feature.budgetprofile.domain.*
import java.security.MessageDigest
import java.util.UUID

class RoomBudgetProfileRegistry(
    private val database: BudgetProfileDatabase,
    backendUrl: String,
) : BudgetProfileRegistry {
    private val backend = MessageDigest.getInstance("SHA-256").digest(backendUrl.toByteArray(Charsets.UTF_8))
        .take(8).joinToString("") { "%02x".format(it) }
    // Register the original file in place. Never rename, copy or recreate its contents.
    private val legacy = BudgetProfile("legacy-$backend", "operations-$backend.db", BudgetProfileKind.LEGACY)
    private val dao get() = database.profiles()

    override suspend fun initialize(): BudgetProfile = database.withTransaction {
        dao.insert(legacy.entity())
        val original = requireNotNull(dao.find(legacy.id)).profile()
        if (original.kind != BudgetProfileKind.CLOUD) original else {
            val fallback = BudgetProfile("local-$backend", "budget-local-$backend.db", BudgetProfileKind.LOCAL)
            dao.insert(fallback.entity())
            requireNotNull(dao.find(fallback.id)).profile()
        }
    }

    override suspend fun find(id: String): BudgetProfile? = dao.find(id)?.profile()

    override suspend fun available(uid: String?): List<BudgetProfile> = dao.available(uid).map { it.profile() }

    override suspend fun createLocal(): BudgetProfile {
        val id = UUID.randomUUID().toString()
        return BudgetProfile(id, "budget-$id.db", BudgetProfileKind.LOCAL).also { dao.insert(it.entity()) }
    }

    override suspend fun registerCloud(uid: String, budgetId: String, role: String): BudgetProfile = database.withTransaction {
        require(uid.isNotBlank() && budgetId.isNotBlank() && role in setOf("OWNER", "MEMBER"))
        dao.cloud(uid, budgetId)?.profile() ?: run {
            val id = UUID.randomUUID().toString()
            val profile = BudgetProfile(id, "budget-$id.db", BudgetProfileKind.CLOUD, uid, budgetId, role)
            dao.insert(profile.entity())
            requireNotNull(dao.cloud(uid, budgetId)).profile()
        }
    }

    override suspend fun selected(uid: String?): BudgetProfile = database.withTransaction {
        val fallback = initialize()
        dao.selection(accountKey(uid))?.let { dao.find(it)?.profile() }
            ?.takeIf { it.accessibleTo(uid) } ?: fallback
    }

    override suspend fun attachLegacy(id: String, uid: String, budgetId: String, role: String): BudgetProfile = database.withTransaction {
        val original = requireNotNull(find(id))
        require(original.kind == BudgetProfileKind.LEGACY && role in setOf("OWNER", "MEMBER"))
        check(dao.cloud(uid, budgetId) == null) { "Budget is already registered" }
        val profile = original.copy(kind = BudgetProfileKind.CLOUD, firebaseUid = uid, budgetId = budgetId, role = role)
        dao.update(profile.entity())
        dao.select(BudgetProfileSelection(accountKey(uid), id))
        profile
    }

    override suspend fun select(id: String, uid: String?): BudgetProfile = database.withTransaction {
        val profile = requireNotNull(find(id))
        require(profile.accessibleTo(uid)) { "Budget profile is unavailable for this account" }
        dao.select(BudgetProfileSelection(accountKey(uid), id))
        profile
    }

    private fun accountKey(uid: String?) = uid?.let { "uid:$it" } ?: "local"
    private fun BudgetProfile.entity() = BudgetProfileEntity(id, databaseName, kind.name, firebaseUid, budgetId, role)
    private fun BudgetProfileEntity.profile() = BudgetProfile(id, databaseName, BudgetProfileKind.valueOf(kind), firebaseUid, budgetId, role)
}
