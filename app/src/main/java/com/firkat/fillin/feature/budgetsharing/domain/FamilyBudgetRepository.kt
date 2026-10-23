package com.firkat.fillin.feature.budgetsharing.domain

import com.firkat.fillin.feature.budgetconnection.domain.AvailableBudget
import com.firkat.fillin.feature.budgetinvitation.domain.InvitationTarget
import kotlinx.coroutines.flow.Flow
import java.util.Locale

data class FamilyBudgetMember(val id: String, val name: String, val role: String, val joinedAt: String, val currentUser: Boolean)

interface FamilyBudgetRepository {
    val target: Flow<InvitationTarget?>
    fun isCurrent(target: InvitationTarget): Boolean
    suspend fun join(uid: String, code: String): AvailableBudget
    suspend fun members(target: InvitationTarget): List<FamilyBudgetMember>
    suspend fun revoke(target: InvitationTarget, member: FamilyBudgetMember)
}

object FamilyInvitationCode {
    fun normalize(value: String): String {
        require(value.length <= 80)
        val compact = value.uppercase(Locale.ROOT).filterNot { it == '-' || it.isWhitespace() }
        require(Regex("[0-9A-HJKMNP-TV-Z]{20}").matches(compact))
        return compact.chunked(5).joinToString("-")
    }
}
