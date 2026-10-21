package com.firkat.fillin.feature.auth.data

import com.firkat.fillin.feature.auth.domain.FirebaseIdTokenProvider
import com.firkat.fillin.feature.auth.domain.ServerAuthError
import com.firkat.fillin.feature.auth.domain.ServerAuthFailure
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.tasks.await

class SdkFirebaseIdTokenProvider(private val auth: FirebaseAuth?) : FirebaseIdTokenProvider {
    override fun isCurrentUser(uid: String): Boolean =
        auth?.currentUser?.let { !it.isAnonymous && it.uid == uid } == true

    override suspend fun idToken(uid: String, forceRefresh: Boolean): String {
        val firebase = auth ?: throw ServerAuthFailure(ServerAuthError.NotConfigured)
        val user = firebase.currentUser?.takeIf { !it.isAnonymous && it.uid == uid }
            ?: throw ServerAuthFailure(ServerAuthError.SessionChanged)
        val token = try {
            user.getIdToken(forceRefresh).await().token
        } catch (_: FirebaseNetworkException) {
            throw ServerAuthFailure(ServerAuthError.Network)
        } catch (_: FirebaseAuthException) {
            throw ServerAuthFailure(ServerAuthError.Unauthorized)
        }
        if (!isCurrentUser(uid)) throw ServerAuthFailure(ServerAuthError.SessionChanged)
        return token?.takeIf(String::isNotBlank) ?: throw ServerAuthFailure(ServerAuthError.Unauthorized)
    }
}
