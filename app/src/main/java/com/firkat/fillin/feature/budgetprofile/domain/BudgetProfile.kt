package com.firkat.fillin.feature.budgetprofile.domain

enum class BudgetProfileKind { LOCAL, LEGACY, CLOUD }

/** Device profile identity is independent of the server's household/member IDs. */
data class BudgetProfile(
    val id: String,
    val databaseName: String,
    val kind: BudgetProfileKind,
    val firebaseUid: String? = null,
    val budgetId: String? = null,
    val role: String? = null,
) {
    init {
        require(id.isNotBlank())
        require(databaseName.matches(Regex("[a-zA-Z0-9-]+\\.db")))
        require(if (kind == BudgetProfileKind.CLOUD) !firebaseUid.isNullOrBlank() && !budgetId.isNullOrBlank() else firebaseUid == null && budgetId == null)
    }

    fun accessibleTo(uid: String?): Boolean = kind != BudgetProfileKind.CLOUD || firebaseUid == uid

    val syncIdentity: BudgetSyncIdentity get() = BudgetSyncIdentity(id, firebaseUid)
}

data class BudgetSyncIdentity(val profileId: String, val firebaseUid: String?)

interface BudgetProfileRegistry {
    suspend fun initialize(): BudgetProfile
    suspend fun find(id: String): BudgetProfile?
    suspend fun available(uid: String?): List<BudgetProfile>
    suspend fun createLocal(): BudgetProfile
    suspend fun registerCloud(uid: String, budgetId: String, role: String): BudgetProfile
    suspend fun attachLegacy(id: String, uid: String, budgetId: String, role: String): BudgetProfile
    suspend fun selected(uid: String?): BudgetProfile
    suspend fun select(id: String, uid: String?): BudgetProfile
}
