package com.firkat.fillin.feature.auth.data

import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.ClearCredentialException
import com.firkat.fillin.feature.auth.domain.AuthError
import com.firkat.fillin.feature.auth.domain.AuthFailure
import com.firkat.fillin.feature.auth.domain.AuthRepository
import com.firkat.fillin.feature.auth.domain.AuthSession
import com.firkat.fillin.feature.auth.domain.AuthUser
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

class FirebaseAuthRepository(
    private val auth: FirebaseAuth?,
    private val credentialManager: CredentialManager,
) : AuthRepository, AutoCloseable {
    private val mutableSession = MutableStateFlow<AuthSession>(
        if (auth == null) AuthSession.Unavailable else AuthSession.Loading,
    )
    override val session = mutableSession.asStateFlow()
    private val listener = FirebaseAuth.AuthStateListener { firebase ->
        mutableSession.value = firebase.currentUser.toSession()
    }

    init {
        // Firebase restores the SDK-owned persisted user; no app-owned copy of tokens is kept.
        auth?.addAuthStateListener(listener)
    }

    override suspend fun signInWithGoogle(idToken: String) {
        val firebase = auth ?: throw AuthFailure(AuthError.NotConfigured)
        if (idToken.isBlank()) throw AuthFailure(AuthError.InvalidCredential)
        try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = firebase.signInWithCredential(credential).await()
            mutableSession.value = result.user.toSession()
        } catch (error: CancellationException) {
            throw error
        } catch (_: FirebaseNetworkException) {
            throw AuthFailure(AuthError.Network)
        } catch (_: FirebaseAuthInvalidCredentialsException) {
            throw AuthFailure(AuthError.InvalidCredential)
        } catch (error: FirebaseAuthException) {
            throw AuthFailure(
                if (error.errorCode == "ERROR_OPERATION_NOT_ALLOWED") {
                    AuthError.SignInUnavailable
                } else {
                    AuthError.SignInFailed
                },
            )
        }
    }

    override suspend fun signOut() {
        val firebase = auth ?: throw AuthFailure(AuthError.NotConfigured)
        firebase.signOut()
        mutableSession.value = AuthSession.Anonymous
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (_: ClearCredentialException) {
            // Firebase is already signed out; a provider reset failure must not undo that.
            throw AuthFailure(AuthError.CredentialResetFailed)
        }
    }

    override fun close() {
        auth?.removeAuthStateListener(listener)
    }

    private fun FirebaseUser?.toSession(): AuthSession =
        if (this == null || isAnonymous) {
            AuthSession.Anonymous
        } else {
            AuthSession.Authenticated(AuthUser(uid, displayName, email))
        }
}
