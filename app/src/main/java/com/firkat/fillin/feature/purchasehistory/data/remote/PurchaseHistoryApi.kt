package com.firkat.fillin.feature.purchasehistory.data.remote

import com.firkat.fillin.feature.purchasehistory.data.remote.dto.PurchaseDto
import com.firkat.fillin.feature.purchasehistory.data.remote.dto.PurchasePageDto
import com.firkat.fillin.feature.purchasehistory.data.remote.dto.UpdatePurchaseRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query

interface PurchaseHistoryApi {
    @Headers("Accept: application/json")
    @GET("api/v1/purchases")
    suspend fun getPurchases(
        @Header("Authorization") authorization: String,
        @Query("limit") limit: Int,
        @Query("cursor") cursor: Long?,
    ): PurchasePageDto

    @Headers("Accept: application/json")
    @PATCH("api/v1/purchases/{purchaseId}")
    suspend fun updatePurchase(
        @Header("Authorization") authorization: String,
        @Path("purchaseId") purchaseId: String,
        @Body request: UpdatePurchaseRequestDto,
    ): Response<PurchaseDto>

    @DELETE("api/v1/purchases/{purchaseId}")
    suspend fun refundPurchase(
        @Header("Authorization") authorization: String,
        @Path("purchaseId") purchaseId: String,
        @Query("version") version: Long,
    ): Response<Unit>
}
