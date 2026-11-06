package com.firkat.fillin.feature.auth.data

import com.firkat.fillin.feature.auth.domain.FirebaseIdTokenProvider
import com.firkat.fillin.feature.auth.domain.ServerAuthError
import com.firkat.fillin.feature.auth.domain.ServerAuthFailure
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.tasks.await

class SdkFirebaseIdTokenProvider(private val auth: FirebaseAuth?, private val repository: com.firkat.fillin.feature.auth.domain.AuthRepository? = null, private val blocked: (String) -> Boolean = { false }, private val invalidate: suspend (String) -> Unit = {}, private val access: com.firkat.fillin.feature.appaccess.domain.AppAccess? = null) : FirebaseIdTokenProvider {
    override fun emailVerificationRequired() { repository?.requireEmailVerification() }
    override fun isCurrentUser(uid: String): Boolean =
        auth?.currentUser?.let { !it.isAnonymous && it.uid == uid && !blocked(uid) } == true

    override suspend fun idToken(uid: String, forceRefresh: Boolean): String {
        if (access?.cloudAllowed == false) throw ServerAuthFailure(ServerAuthError.NotConfigured)
        val firebase = auth ?: throw ServerAuthFailure(ServerAuthError.NotConfigured)
        val user = firebase.currentUser?.takeIf { !it.isAnonymous && it.uid == uid && !blocked(uid) }
            ?: throw ServerAuthFailure(ServerAuthError.SessionChanged)
        if (repository?.session?.value is com.firkat.fillin.feature.auth.domain.AuthSession.EmailVerificationRequired) throw ServerAuthFailure(ServerAuthError.EmailNotVerified)
        val token = try {
            user.getIdToken(forceRefresh).await().token
        } catch (_: FirebaseNetworkException) {
            throw ServerAuthFailure(ServerAuthError.Network)
        } catch (failure: FirebaseAuthException) {
            if (failure.errorCode in setOf("ERROR_USER_DISABLED", "ERROR_USER_NOT_FOUND")) invalidate(uid)
            else if (failure.errorCode in setOf("ERROR_USER_TOKEN_EXPIRED", "ERROR_INVALID_USER_TOKEN") && auth.currentUser?.uid == uid) repository?.signOut()
            throw ServerAuthFailure(ServerAuthError.Unauthorized)
        }
        if (!isCurrentUser(uid)) throw ServerAuthFailure(ServerAuthError.SessionChanged)
        return token?.takeIf(String::isNotBlank) ?: throw ServerAuthFailure(ServerAuthError.Unauthorized)
    }
}
