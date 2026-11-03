package com.firkat.fillin.feature.auth.data

import com.firkat.fillin.feature.auth.domain.FirebaseIdTokenProvider
import com.firkat.fillin.feature.auth.domain.ServerAuthError
import com.firkat.fillin.feature.auth.domain.ServerAuthFailure
import com.firkat.fillin.feature.auth.domain.ServerAuthRepository
import com.firkat.fillin.feature.auth.domain.ServerUser
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock

class FirebaseServerAuthRepository(
    private val api: FirebaseAuthApi?,
    private val tokens: FirebaseIdTokenProvider,
    private val auth: com.firkat.fillin.feature.auth.domain.AuthRepository? = null,
) : ServerAuthRepository {
    private val mutex = kotlinx.coroutines.sync.Mutex()
    private var cached: Pair<com.firkat.fillin.feature.auth.domain.AuthSession, ServerUser>? = null
    override suspend fun authenticate(uid: String): ServerUser = mutex.withLock {
        val session = auth?.session?.value
        ensureCurrentUser(uid)
        cached?.takeIf { it.first === session && it.second.firebaseUid == uid }?.let { return@withLock it.second }
        val profile = request(uid)
        if (session != null && auth.session.value === session) cached = session to profile
        profile
    }
    private suspend fun request(uid: String): ServerUser {
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
                val code = response.errorBody()?.use { runCatching { kotlinx.serialization.json.Json.parseToJsonElement(it.string()).jsonObject["code"]?.jsonPrimitive?.content }.getOrNull() }
                if (code == "email_not_verified") { auth?.requireEmailVerification(); throw ServerAuthFailure(ServerAuthError.EmailNotVerified) }
                throw ServerAuthFailure(
                    if (code == "access_denied") ServerAuthError.AccessDenied
                    else if (response.code() == 401 || response.code() == 403) ServerAuthError.Unauthorized
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
