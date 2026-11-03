package com.firkat.fillin.feature.profile.domain

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable
data class DeletionOperation(val uid: String, val secret: String, val accepted: Boolean = false, val cleaned: Boolean = false, val completed: Boolean = false)
interface DeletionStore {
    fun operations(): List<DeletionOperation>
    fun blocked(uid: String): Boolean
    fun save(operation: DeletionOperation)
    fun block(uid: String)
    fun finish(uid: String)
}
enum class DeletionPhase { Idle, Working, Reauthenticate, Pending, Failed, Completed }
data class ProfileDeletionState(val phase: DeletionPhase = DeletionPhase.Idle, val error: String? = null)
interface ProfileDeletion {
    val state: StateFlow<ProfileDeletionState>
    suspend fun begin(uid: String)
    suspend fun resume()
    suspend fun reauthenticate(password: String? = null, googleToken: String? = null)
}
