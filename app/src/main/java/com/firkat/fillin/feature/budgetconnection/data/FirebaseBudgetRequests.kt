package com.firkat.fillin.feature.budgetconnection.data

import com.firkat.fillin.feature.auth.domain.FirebaseIdTokenProvider
import com.firkat.fillin.feature.sync.data.BudgetSyncCommand
import com.firkat.fillin.feature.sync.domain.BudgetConnectionFailure
import retrofit2.HttpException
import retrofit2.Response

/** Tokens are acquired per request and are never persisted in profile or work data. */
class FirebaseBudgetRequests(private val api: FirebaseBudgetApi?, tokens: FirebaseIdTokenProvider, private val serverAuth: com.firkat.fillin.feature.auth.domain.ServerAuthRepository? = null) {
    suspend fun importLocal(uid: String, request: ImportFirebaseBudgetDto) = execute(uid) { api, auth -> api.importLocal(auth, request) }
    suspend fun create(uid: String, request: CreateFirebaseBudgetDto) = execute(uid) { api, auth -> api.create(auth, request) }
    suspend fun budgets(uid: String) = execute(uid) { api, auth ->
        serverAuth?.authenticate(uid)
        api.budgets(auth)
    }
    suspend fun budget(uid: String, id: String) = execute(uid) { api, auth -> api.budget(auth, id) }
    suspend fun changes(uid: String, id: String, cursor: String?) = execute(uid) { api, auth -> api.changes(auth, id, cursor) }
    suspend fun command(uid: String, id: String, command: BudgetSyncCommand) = execute(uid) { api, auth -> api.command(auth, id, command) }

    fun ensureCurrent(uid: String) = authenticated.ensureCurrent(uid)

    private val authenticated = com.firkat.fillin.feature.auth.data.FirebaseAuthenticatedRequests(tokens)
    private suspend fun <T> execute(uid: String, action: suspend (FirebaseBudgetApi, String) -> Response<T>): Response<T> =
        authenticated.execute(uid, api, action)
}

internal fun <T> Response<T>.budgetBody(): T {
    if (!isSuccessful) throw HttpException(this)
    return body() ?: throw BudgetConnectionFailure("invalid_response")
}
