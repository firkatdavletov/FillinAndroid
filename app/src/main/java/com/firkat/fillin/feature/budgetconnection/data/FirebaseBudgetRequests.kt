package com.firkat.fillin.feature.budgetconnection.data

import com.firkat.fillin.feature.auth.domain.FirebaseIdTokenProvider
import com.firkat.fillin.feature.auth.domain.ServerAuthFailure
import com.firkat.fillin.feature.auth.domain.ServerAuthError
import com.firkat.fillin.feature.sync.data.BudgetSyncCommand
import com.firkat.fillin.feature.sync.domain.BudgetConnectionFailure
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/** Tokens are acquired per request and are never persisted in profile or work data. */
class FirebaseBudgetRequests(private val api: FirebaseBudgetApi?, private val tokens: FirebaseIdTokenProvider) {
    suspend fun budgets(uid: String) = execute(uid) { api, auth -> api.budgets(auth) }
    suspend fun budget(uid: String, id: String) = execute(uid) { api, auth -> api.budget(auth, id) }
    suspend fun changes(uid: String, id: String, cursor: String?) = execute(uid) { api, auth -> api.changes(auth, id, cursor) }
    suspend fun command(uid: String, id: String, command: BudgetSyncCommand) = execute(uid) { api, auth -> api.command(auth, id, command) }

    fun ensureCurrent(uid: String) {
        if (!tokens.isCurrentUser(uid)) throw BudgetConnectionFailure("account_changed")
    }

    private suspend fun <T> execute(uid: String, action: suspend (FirebaseBudgetApi, String) -> Response<T>): Response<T> {
        val endpoint = api ?: throw BudgetConnectionFailure("server_not_configured")
        try {
            ensureCurrent(uid)
            var response = action(endpoint, "Bearer ${tokens.idToken(uid, false)}")
            if (response.code() == 401) {
                response.errorBody()?.close()
                ensureCurrent(uid)
                response = action(endpoint, "Bearer ${tokens.idToken(uid, true)}")
            }
            if (!tokens.isCurrentUser(uid)) {
                response.errorBody()?.close()
                throw BudgetConnectionFailure("account_changed")
            }
            return response
        } catch (_: IOException) {
            throw BudgetConnectionFailure("network")
        } catch (_: SerializationException) {
            throw BudgetConnectionFailure("invalid_response")
        } catch (failure: ServerAuthFailure) {
            throw BudgetConnectionFailure(if (!tokens.isCurrentUser(uid)) "account_changed" else when (failure.reason) {
                ServerAuthError.Network -> "network"
                ServerAuthError.NotConfigured -> "server_not_configured"
                else -> "unauthorized"
            })
        }
    }
}

internal fun <T> Response<T>.budgetBody(): T {
    if (!isSuccessful) throw HttpException(this)
    return body() ?: throw BudgetConnectionFailure("invalid_response")
}
