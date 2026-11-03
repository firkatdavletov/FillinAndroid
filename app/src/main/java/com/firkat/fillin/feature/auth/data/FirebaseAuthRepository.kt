package com.firkat.fillin.feature.auth.data

import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.ClearCredentialException
import com.firkat.fillin.feature.auth.domain.*
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

class FirebaseAuthRepository(
    private val gateway: FirebaseAuthGateway?,
    private val clearCredentials: suspend () -> Unit,
) : AuthRepository, AutoCloseable {
    constructor(auth: FirebaseAuth?, credentialManager: CredentialManager) : this(auth?.let(::SdkFirebaseAuthGateway), credentialManager)
    constructor(gateway: FirebaseAuthGateway?, credentialManager: CredentialManager) : this(gateway, { credentialManager.clearCredentialState(ClearCredentialStateRequest()) })
    private val mutableSession = MutableStateFlow<AuthSession>(if (gateway == null) AuthSession.Unavailable else AuthSession.Loading)
    override val session = mutableSession.asStateFlow()
    private val mutex = Mutex()
    @Volatile private var updating = false
    @Volatile private var verificationRequired = false
    private val observation = gateway?.observe { if (!updating) publish() }

    private fun publish() {
        val user = gateway?.user
        mutableSession.value = if (user == null || user.anonymous) AuthSession.Anonymous else {
            val value = AuthUser(user.uid, user.name, user.email, user.providers, user.verified)
            if ((!user.verified && (user.signInProvider == "password" || user.signInProvider == null && "password" in user.providers)) || verificationRequired) AuthSession.EmailVerificationRequired(value)
            else AuthSession.Authenticated(value)
        }
    }
    private suspend fun operation(block: suspend (FirebaseAuthGateway) -> Unit) = mutex.withLock {
        val sdk = gateway ?: throw AuthFailure(AuthError.NotConfigured)
        updating = true
        try { block(sdk) }
        catch (e: CancellationException) { throw e }
        catch (e: AuthFailure) { throw e }
        catch (_: FirebaseNetworkException) { throw AuthFailure(AuthError.Network) }
        catch (_: FirebaseTooManyRequestsException) { throw AuthFailure(AuthError.TooManyRequests) }
        catch (e: FirebaseAuthException) { throw AuthFailure(firebaseError(e.errorCode)) }
        finally { updating = false; publish() }
    }
    override suspend fun signInWithGoogle(idToken: String) = operation { sdk ->
        if (idToken.isBlank()) throw AuthFailure(AuthError.InvalidCredential)
        sdk.google(idToken); verificationRequired = false
    }
    override suspend fun signInWithEmail(email: String, password: String) = operation { sdk ->
        sdk.signIn(email.trim(), password); verificationRequired = sdk.user?.verified != true
    }
    // Sending the email is a separate operation: failure must never repeat account creation.
    override suspend fun register(email: String, password: String) = operation { sdk ->
        if (password.length < 12) throw AuthFailure(AuthError.WeakPassword)
        sdk.create(email.trim(), password); verificationRequired = true
    }
    override suspend fun sendVerification() = operation { it.verification() }
    override suspend fun resetPassword(email: String) = operation { sdk ->
        try { sdk.reset(email.trim()) } catch (e: FirebaseAuthException) {
            if (e.errorCode !in setOf("ERROR_USER_NOT_FOUND", "ERROR_USER_DISABLED", "ERROR_INVALID_CREDENTIAL", "ERROR_INVALID_LOGIN_CREDENTIALS")) throw e
        }
    }
    override suspend fun reloadUser() = operation { sdk ->
        val uid = sdk.user?.uid
        verificationRequired = session.value is AuthSession.EmailVerificationRequired
        sdk.reload()
        if (sdk.user?.uid != uid) throw AuthFailure(AuthError.InvalidCredential)
        sdk.refresh()
        verificationRequired = sdk.user?.verified != true
    }
    override suspend fun linkPassword(password: String) = operation { sdk ->
        if (password.length < 12) throw AuthFailure(AuthError.WeakPassword)
        val uid = sdk.user?.uid
        sdk.linkPassword(password); sdk.reload(); sdk.refresh()
        if (sdk.user?.uid != uid) throw AuthFailure(AuthError.InvalidCredential)
    }
    override suspend fun linkGoogle(idToken: String) = operation { sdk ->
        val email = runCatching {
            val payload = java.util.Base64.getUrlDecoder().decode(idToken.split('.')[1])
            Json.parseToJsonElement(String(payload, Charsets.UTF_8)).jsonObject["email"]?.jsonPrimitive?.content
        }.getOrNull()
        if (email == null || !email.equals(sdk.user?.email, ignoreCase = true)) throw AuthFailure(AuthError.EmailMismatch)
        val uid = sdk.user?.uid
        sdk.linkGoogle(idToken); sdk.reload(); sdk.refresh()
        if (sdk.user?.uid != uid) throw AuthFailure(AuthError.InvalidCredential)
        verificationRequired = sdk.user?.verified != true
    }
    override suspend fun reauthenticatePassword(password: String) = operation { it.reauthenticatePassword(password) }
    override suspend fun reauthenticateGoogle(idToken: String) = operation { it.reauthenticateGoogle(idToken) }
    override fun requireEmailVerification() { verificationRequired = true; publish() }
    override suspend fun signOut() {
        operation { it.signOut(); verificationRequired = false }
        try { clearCredentials() }
        catch (_: ClearCredentialException) { throw AuthFailure(AuthError.CredentialResetFailed) }
    }
    override fun close() { observation?.close() }
}

internal fun firebaseError(code: String): AuthError = when (code) {
    "ERROR_INVALID_EMAIL" -> AuthError.InvalidEmail
    "ERROR_WEAK_PASSWORD" -> AuthError.WeakPassword
    "ERROR_EMAIL_ALREADY_IN_USE" -> AuthError.EmailInUse
    "ERROR_CREDENTIAL_ALREADY_IN_USE", "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" -> AuthError.CredentialInUse
    "ERROR_REQUIRES_RECENT_LOGIN" -> AuthError.RecentLoginRequired
    "ERROR_USER_DISABLED" -> AuthError.Disabled
    "ERROR_OPERATION_NOT_ALLOWED" -> AuthError.SignInUnavailable
    "ERROR_INVALID_CREDENTIAL", "ERROR_INVALID_LOGIN_CREDENTIALS", "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND" -> AuthError.InvalidCredential
    else -> AuthError.SignInFailed
}
