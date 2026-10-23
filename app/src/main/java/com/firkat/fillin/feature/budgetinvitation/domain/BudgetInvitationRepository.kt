package com.firkat.fillin.feature.budgetinvitation.domain

import kotlinx.coroutines.flow.Flow

data class InvitationTarget(val profileId: String, val uid: String, val budgetId: String, val role: String)
data class BudgetInvitation(val id: String, val budgetId: String, val expiresAt: String)
data class CreatedInvitation(val invitation: BudgetInvitation, val code: String)

interface BudgetInvitationRepository {
    val target: Flow<InvitationTarget?>
    fun isCurrent(target: InvitationTarget): Boolean
    suspend fun current(target: InvitationTarget): BudgetInvitation?
    suspend fun create(target: InvitationTarget): CreatedInvitation
    suspend fun revoke(target: InvitationTarget, invitation: BudgetInvitation)
}
