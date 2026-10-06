package com.firkat.fillin.feature.purchasehistory.data.remote

import com.firkat.fillin.feature.purchasehistory.data.remote.dto.PurchasePageDto
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Query

interface PurchaseHistoryApi {
    @Headers("Accept: application/json")
    @GET("api/v1/purchases")
    suspend fun getPurchases(
        @Header("Authorization") authorization: String,
        @Query("limit") limit: Int,
        @Query("cursor") cursor: Long?,
    ): PurchasePageDto
}
