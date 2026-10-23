package com.firkat.fillin.feature.budgetupload.domain

import com.firkat.fillin.feature.budgetprofile.domain.BudgetProfile
import kotlinx.coroutines.flow.Flow

data class LocalBudgetUpload(val profileId: String, val firebaseUid: String, val name: String, val bootstrapPayload: String)
data class LocalBudgetCandidate(val profileId: String, val name: String, val connecting: Boolean, val syncing: Boolean = false, val firebaseUid: String? = null)

interface LocalBudgetUploadStore {
    suspend fun find(profileId: String): LocalBudgetUpload?
    suspend fun bind(profile: BudgetProfile, uid: String, budgetId: String, name: String, bootstrapPayload: String)
}

interface LocalBudgetUploadRepository {
    val candidates: Flow<List<LocalBudgetCandidate>>
    suspend fun connect(uid: String, profileId: String, name: String)
}
