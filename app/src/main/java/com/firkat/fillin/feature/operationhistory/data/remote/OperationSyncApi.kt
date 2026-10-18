package com.firkat.fillin.feature.operationhistory.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface OperationSyncApi {
    @GET("api/v1/sync/changes")
    suspend fun changes(
        @Header("Authorization") authorization: String,
        @Query("cursor") cursor: String?,
    ): SyncPageDto

    @POST("api/v1/operations")
    suspend fun create(
        @Header("Authorization") authorization: String,
        @Body request: CreateOperationDto,
    ): Response<CreatedOperationDto>
}

@Serializable
data class SyncScopeDto(
    val householdId: String,
    val memberId: String,
    val role: String,
    val accountingStartedAt: String,
    val timeZone: String,
    val currency: String,
) {
    val key: String get() = "$householdId:$memberId"
}

@Serializable
data class SyncPageDto(
    val scope: SyncScopeDto,
    val changes: List<SyncChangeDto>,
    val nextCursor: String,
    val hasMore: Boolean,
)

@Serializable
data class SyncChangeDto(
    val revision: Long,
    val kind: String,
    val deleted: Boolean,
    val operation: SyncOperationDto? = null,
    val category: SyncCategoryDto? = null,
)

@Serializable
data class SyncOperationDto(
    val id: String,
    val categoryId: String,
    val type: String,
    val originalAmount: String,
    val originalCurrency: String,
    val amountKzt: String,
    val occurredOn: String,
    val createdAt: String,
    val status: String,
    val version: Long,
    val sequence: Long,
    val description: String? = null,
    val refundOf: String? = null,
    val requestId: String? = null,
)

@Serializable
data class SyncCategoryDto(
    val id: String,
    val name: String,
    val kind: String,
    val iconCode: String,
    val iconEmoji: String,
    val archived: Boolean,
)

@Serializable
data class CreateOperationDto(
    val requestId: String,
    val householdId: String,
    val memberId: String,
    val type: String,
    val categoryId: String,
    val amount: String,
    val occurredOn: String,
    val description: String? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    val currency: String = "KZT",
)

@Serializable
data class CreatedOperationDto(
    val requestId: String,
    val operation: SyncOperationDto,
)

@Serializable
data class SyncApiError(
    val code: String,
    val reason: String? = null,
)
