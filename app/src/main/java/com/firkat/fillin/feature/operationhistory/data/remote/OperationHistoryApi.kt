package com.firkat.fillin.feature.operationhistory.data.remote

import com.firkat.fillin.feature.operationhistory.data.remote.dto.OperationDto
import com.firkat.fillin.feature.operationhistory.data.remote.dto.OperationPageDto
import com.firkat.fillin.feature.operationhistory.data.remote.dto.UpdatePurchaseRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query

interface OperationHistoryApi {
    @Headers("Accept: application/json")
    @GET("api/v1/operations")
    suspend fun getOperations(
        @Header("Authorization") authorization: String,
        @Query("limit") limit: Int,
        @Query("cursor") cursor: Long?,
        @Query("type") type: String?,
        @Query("categoryId") categoryId: String?,
        @Query("dateFrom") dateFrom: String,
        @Query("dateTo") dateTo: String,
    ): OperationPageDto

    @Headers("Accept: application/json")
    @PATCH("api/v1/purchases/{purchaseId}")
    suspend fun updatePurchase(
        @Header("Authorization") authorization: String,
        @Path("purchaseId") purchaseId: String,
        @Body request: UpdatePurchaseRequestDto,
    ): Response<OperationDto>

    @DELETE("api/v1/purchases/{purchaseId}")
    suspend fun refundPurchase(
        @Header("Authorization") authorization: String,
        @Path("purchaseId") purchaseId: String,
        @Query("version") version: Long,
    ): Response<Unit>
}
