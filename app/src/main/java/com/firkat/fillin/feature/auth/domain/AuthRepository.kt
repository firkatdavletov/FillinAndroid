package com.firkat.fillin.feature.auth.domain

import kotlinx.coroutines.flow.StateFlow

data class AuthUser(
    val uid: String,
    val displayName: String?,
    val email: String?,
)

sealed interface AuthSession {
    data object Loading : AuthSession
    data object Unavailable : AuthSession
    data object Anonymous : AuthSession
    data class Authenticated(val user: AuthUser) : AuthSession
}

enum class AuthError {
    NotConfigured,
    NoGoogleAccount,
    Network,
    InvalidCredential,
    SignInUnavailable,
    SignInFailed,
    SignOutFailed,
    CredentialResetFailed,
}

class AuthFailure(val error: AuthError) : Exception()

/** Authentication does not read, bind, synchronise or delete a budget. */
interface AuthRepository {
    val session: StateFlow<AuthSession>

    suspend fun signInWithGoogle(idToken: String)

    suspend fun signOut()
}
