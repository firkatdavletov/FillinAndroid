package com.firkat.fillin.feature.auth.domain

import kotlinx.coroutines.flow.StateFlow

data class AuthUser(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val providers: Set<String> = emptySet(),
    val emailVerified: Boolean = true,
)

sealed interface AuthSession {
    data object Loading : AuthSession

    data object Unavailable : AuthSession

    data object Anonymous : AuthSession

    data class Authenticated(
        val user: AuthUser,
    ) : AuthSession

    data class EmailVerificationRequired(
        val user: AuthUser,
    ) : AuthSession
}

enum class AuthError {
    InvalidEmail,
    WeakPassword,
    EmailInUse,
    CredentialInUse,
    RecentLoginRequired,
    Disabled,
    TooManyRequests,
    EmailMismatch,
    NotConfigured,
    NoGoogleAccount,
    Network,
    InvalidCredential,
    SignInUnavailable,
    SignInFailed,
    SignOutFailed,
    CredentialResetFailed,
}

class AuthFailure(
    val error: AuthError,
) : Exception()

/** Authentication does not read, bind, synchronise or delete a budget. */
interface AuthRepository {
    val session: StateFlow<AuthSession>

    suspend fun signInWithGoogle(
        idToken: String,
    )

    suspend fun signInWithEmail(
        email: String,
        password: String,
    ): Unit = throw AuthFailure(AuthError.SignInUnavailable)

    suspend fun register(
        email: String,
        password: String,
    ): Unit = throw AuthFailure(AuthError.SignInUnavailable)

    suspend fun sendVerification(): Unit = throw AuthFailure(AuthError.SignInUnavailable)

    suspend fun reloadUser(): Unit = throw AuthFailure(AuthError.SignInUnavailable)

    suspend fun resetPassword(
        email: String,
    ): Unit = throw AuthFailure(AuthError.SignInUnavailable)

    suspend fun linkPassword(
        password: String,
    ): Unit = throw AuthFailure(AuthError.SignInUnavailable)

    suspend fun linkGoogle(
        idToken: String,
    ): Unit = throw AuthFailure(AuthError.SignInUnavailable)

    suspend fun reauthenticatePassword(
        password: String,
    ): Unit = throw AuthFailure(AuthError.SignInUnavailable)

    suspend fun reauthenticateGoogle(
        idToken: String,
    ): Unit = throw AuthFailure(AuthError.SignInUnavailable)

    fun requireEmailVerification() {}

    suspend fun signOut()
}

val AuthSession.userOrNull: AuthUser? get() =
    when (this) {
        is AuthSession.Authenticated -> user
        is AuthSession.EmailVerificationRequired -> user
        else -> null
    }
