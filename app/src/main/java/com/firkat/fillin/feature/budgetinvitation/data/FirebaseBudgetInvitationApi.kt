package com.firkat.fillin.feature.budgetinvitation.data

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.*

@Serializable
data class InvitationDto(val id: String, val budgetId: String, val expiresAt: String, val code: String? = null)
@Serializable
data class InvitationStatusDto(val budgetId: String, val current: InvitationDto? = null)

interface FirebaseBudgetInvitationApi {
    @GET("api/v4/budgets/{budgetId}/invitations/current")
    suspend fun current(@Header("Authorization") authorization: String, @Path("budgetId") budgetId: String): Response<InvitationStatusDto>
    @POST("api/v4/budgets/{budgetId}/invitations")
    suspend fun create(@Header("Authorization") authorization: String, @Path("budgetId") budgetId: String): Response<InvitationDto>
    @DELETE("api/v4/budgets/{budgetId}/invitations/{invitationId}")
    suspend fun revoke(@Header("Authorization") authorization: String, @Path("budgetId") budgetId: String, @Path("invitationId") invitationId: String): Response<Unit>
}
