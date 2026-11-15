package com.firkat.fillin.feature.budgetsharing.data

import com.firkat.fillin.feature.budgetconnection.data.AvailableBudgetDto
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.*

@Serializable
data class AcceptInvitationDto(val code: String)

@Serializable
data class FamilyMemberDto(
    val id: String,
    val name: String,
    val role: String,
    val joinedAt: String,
    val currentUser: Boolean
)

@Serializable
data class FamilyMembersDto(val budgetId: String, val members: List<FamilyMemberDto>)

@Serializable
internal data class FamilyErrorDto(val code: String = "")

interface FirebaseFamilyBudgetApi {
    @POST("api/v4/budgets/invitations/accept")
    suspend fun join(
        @Header("Authorization") authorization: String,
        @Body request: AcceptInvitationDto
    ): Response<AvailableBudgetDto>

    @GET("api/v4/budgets/{budgetId}/members")
    suspend fun members(
        @Header("Authorization") authorization: String,
        @Path("budgetId") budgetId: String
    ): Response<FamilyMembersDto>

    @DELETE("api/v4/budgets/{budgetId}/members/{memberId}")
    suspend fun revoke(
        @Header("Authorization") authorization: String,
        @Path("budgetId") budgetId: String,
        @Path("memberId") memberId: String
    ): Response<Unit>
}
