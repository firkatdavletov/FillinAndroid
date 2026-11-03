package com.firkat.fillin.feature.auth.domain

data class ServerUser(
    val id: String,
    val firebaseUid: String,
    val displayName: String?,
    val email: String?,
)

enum class ServerAuthError { EmailNotVerified, AccessDenied, NotConfigured, Network, Unavailable, Unauthorized, SessionChanged }

class ServerAuthFailure(val reason: ServerAuthError) : Exception()

interface ServerAuthRepository {
    suspend fun authenticate(uid: String): ServerUser
}

/** Tokens are obtained from Firebase immediately before a request, never stored by the app. */
interface FirebaseIdTokenProvider {
    fun emailVerificationRequired() {}
    fun isCurrentUser(uid: String): Boolean
    suspend fun idToken(uid: String, forceRefresh: Boolean): String
}

sealed interface ServerProfileState {
    data object Idle : ServerProfileState
    data object Loading : ServerProfileState
    data class Ready(val user: ServerUser) : ServerProfileState
    data class Failed(val reason: ServerAuthError) : ServerProfileState
}
