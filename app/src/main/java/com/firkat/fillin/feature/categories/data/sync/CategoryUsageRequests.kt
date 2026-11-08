package com.firkat.fillin.feature.categories.data.sync

import com.firkat.fillin.feature.auth.data.FirebaseAuthenticatedRequests
import com.firkat.fillin.feature.auth.domain.FirebaseIdTokenProvider
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface CategoryUsageApi {
    @POST("api/v4/category-usage/events")
    suspend fun push(
        @Header("Authorization") authorization: String,
        @Query("budgetId") budgetId: String,
        @Body batch: CategoryUsageBatch,
    ): Response<CategoryUsageReceipt>

    @GET("api/v4/category-usage/events")
    suspend fun pull(
        @Header("Authorization") authorization: String,
        @Query("budgetId") budgetId: String,
        @Query("cursor") cursor: String?,
    ): Response<CategoryUsagePage>
}

class CategoryUsageRequests(private val api: CategoryUsageApi?, tokens: FirebaseIdTokenProvider) {
    private val authenticated = FirebaseAuthenticatedRequests(tokens)

    suspend fun push(uid: String, budgetId: String, batch: CategoryUsageBatch) =
        authenticated.execute(uid, api) { service, auth -> service.push(auth, budgetId, batch) }

    suspend fun pull(uid: String, budgetId: String, cursor: String?) =
        authenticated.execute(uid, api) { service, auth -> service.pull(auth, budgetId, cursor) }
}
