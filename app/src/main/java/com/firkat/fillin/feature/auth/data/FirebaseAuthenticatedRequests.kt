package com.firkat.fillin.feature.auth.data

import com.firkat.fillin.feature.auth.domain.*
import com.firkat.fillin.feature.sync.domain.BudgetConnectionFailure
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Acquires a current token for each call and retries an unauthorized request only once. */
class FirebaseAuthenticatedRequests(private val tokens: FirebaseIdTokenProvider) {
    fun ensureCurrent(uid: String) {
        if (!tokens.isCurrentUser(uid)) throw BudgetConnectionFailure("account_changed")
    }

    suspend fun <A, T> execute(uid: String, api: A?, action: suspend (A, String) -> Response<T>): Response<T> {
        val endpoint = api ?: throw BudgetConnectionFailure("server_not_configured")
        try {
            ensureCurrent(uid)
            var response = action(endpoint, authorization(uid, false))
            if (response.code() == 401) {
                response.errorBody()?.close()
                ensureCurrent(uid)
                response = action(endpoint, authorization(uid, true))
            }
            currentCoroutineContext().ensureActive()
            if (!tokens.isCurrentUser(uid)) {
                response.errorBody()?.close()
                throw BudgetConnectionFailure("account_changed")
            }
            if (response.code() == 403) {
                val body = response.errorBody()
                val type = body?.contentType()
                val raw = body?.use { it.string() }.orEmpty()
                val code = runCatching { Json.parseToJsonElement(raw).jsonObject["code"]?.jsonPrimitive?.content }.getOrNull()
                if (code == "email_not_verified") { tokens.emailVerificationRequired(); throw BudgetConnectionFailure("email_not_verified") }
                return Response.error(403, raw.toResponseBody(type))
            }
            return response
        } catch (_: com.firkat.fillin.feature.appaccess.data.DeviceAccessDeniedIOException) {
            throw BudgetConnectionFailure("device_access_denied")
        } catch (_: IOException) {
            throw BudgetConnectionFailure("network")
        } catch (_: SerializationException) {
            throw BudgetConnectionFailure("invalid_response")
        } catch (failure: ServerAuthFailure) {
            throw BudgetConnectionFailure(if (!tokens.isCurrentUser(uid)) "account_changed" else when (failure.reason) {
                ServerAuthError.EmailNotVerified -> "email_not_verified"
                ServerAuthError.Network -> "network"
                ServerAuthError.NotConfigured -> "server_not_configured"
                ServerAuthError.Unavailable -> "server_unavailable"
                else -> "unauthorized"
            })
        }
    }

    private suspend fun authorization(uid: String, refresh: Boolean): String {
        val token = tokens.idToken(uid, refresh)
        currentCoroutineContext().ensureActive()
        ensureCurrent(uid)
        return "Bearer $token"
    }
}
