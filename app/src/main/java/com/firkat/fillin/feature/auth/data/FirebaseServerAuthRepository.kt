package com.firkat.fillin.feature.auth.data

import com.firkat.fillin.feature.auth.domain.FirebaseIdTokenProvider
import com.firkat.fillin.feature.auth.domain.ServerAuthError
import com.firkat.fillin.feature.auth.domain.ServerAuthFailure
import com.firkat.fillin.feature.auth.domain.ServerAuthRepository
import com.firkat.fillin.feature.auth.domain.ServerUser
import kotlinx.serialization.SerializationException
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class FirebaseServerAuthRepository(
    private val api: FirebaseAuthApi?,
    private val tokens: FirebaseIdTokenProvider,
) : ServerAuthRepository {
    override suspend fun authenticate(uid: String): ServerUser {
        val endpoint = api ?: throw ServerAuthFailure(ServerAuthError.NotConfigured)
        try {
            var response = endpoint.authenticate(authorization(uid, false))
            if (response.code() == 401) {
                response.errorBody()?.close()
                ensureCurrentUser(uid)
                response = endpoint.authenticate(authorization(uid, true))
            }
            currentCoroutineContext().ensureActive()
            ensureCurrentUser(uid)
            if (!response.isSuccessful) {
                response.errorBody()?.close()
                throw ServerAuthFailure(
                    if (response.code() == 401 || response.code() == 403) ServerAuthError.Unauthorized
                    else ServerAuthError.Unavailable,
                )
            }
            val profile = response.body()?.takeIf { it.firebaseUid == uid && it.id.isNotBlank() }
                ?: throw ServerAuthFailure(ServerAuthError.Unauthorized)
            return profile.toDomain()
        } catch (_: IOException) {
            throw ServerAuthFailure(ServerAuthError.Network)
        } catch (_: SerializationException) {
            throw ServerAuthFailure(ServerAuthError.Unavailable)
        }
    }

    private suspend fun authorization(uid: String, refresh: Boolean): String {
        val token = tokens.idToken(uid, forceRefresh = refresh)
        currentCoroutineContext().ensureActive()
        ensureCurrentUser(uid)
        return "Bearer $token"
    }

    private fun ensureCurrentUser(uid: String) {
        if (!tokens.isCurrentUser(uid)) throw ServerAuthFailure(ServerAuthError.SessionChanged)
    }
}
