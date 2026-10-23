package com.firkat.fillin.feature.budgetsharing.data

import com.firkat.fillin.feature.auth.data.FirebaseAuthenticatedRequests
import com.firkat.fillin.feature.auth.domain.*
import com.firkat.fillin.feature.budgetconnection.domain.*
import com.firkat.fillin.feature.budgetinvitation.domain.*
import com.firkat.fillin.feature.budgetsharing.domain.*
import com.firkat.fillin.feature.sync.domain.BudgetConnectionFailure
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.time.Instant
import java.time.DateTimeException
import java.util.UUID

class FirebaseFamilyBudgetRepository(
    private val api: FirebaseFamilyBudgetApi?,
    tokens: FirebaseIdTokenProvider,
    private val auth: AuthRepository,
    private val context: BudgetInvitationRepository,
    private val catalog: BudgetCatalogStore,
    private val connection: BudgetConnectionRepository,
    private val json: Json,
) : FamilyBudgetRepository {
    private val requests = FirebaseAuthenticatedRequests(tokens)
    override val target = context.target
    override fun isCurrent(target: InvitationTarget) = context.isCurrent(target)

    override suspend fun join(uid: String, code: String): AvailableBudget {
        val request = AcceptInvitationDto(FamilyInvitationCode.normalize(code))
        requests.ensureCurrent(uid)
        val dto = body(requests.execute(uid, api) { endpoint, token -> endpoint.join(token, request) })
        valid { UUID.fromString(dto.id); UUID.fromString(dto.memberId); require(dto.name.isNotBlank() && dto.role in setOf("OWNER", "MEMBER")) }
        val budget = AvailableBudget(dto.id, dto.name, dto.memberId, dto.role)
        requests.ensureCurrent(uid)
        catalog.remember(uid, budget)
        requests.ensureCurrent(uid)
        connection.connect(uid, budget)
        return budget
    }

    override suspend fun members(target: InvitationTarget): List<FamilyBudgetMember> {
        ensure(target)
        val dto = body(requests.execute(target.uid, api) { endpoint, token -> endpoint.members(token, target.budgetId) })
        ensure(target)
        valid {
            require(dto.budgetId == target.budgetId && dto.members.map { it.id }.distinct().size == dto.members.size)
            require(dto.members.count { it.currentUser } == 1)
            dto.members.forEach { UUID.fromString(it.id); Instant.parse(it.joinedAt); require(it.name.isNotBlank() && it.role in setOf("OWNER", "MEMBER")) }
        }
        return dto.members.map { FamilyBudgetMember(it.id, it.name, it.role, it.joinedAt, it.currentUser) }
    }
    override suspend fun revoke(target: InvitationTarget, member: FamilyBudgetMember) {
        ensure(target)
        if (target.role != "OWNER" || member.role != "MEMBER" || member.currentUser) throw BudgetConnectionFailure("access_denied")
        UUID.fromString(member.id)
        checked(requests.execute(target.uid, api) { endpoint, token -> endpoint.revoke(token, target.budgetId, member.id) })
        ensure(target)
    }
    private fun ensure(target: InvitationTarget) { if (!isCurrent(target)) throw BudgetConnectionFailure("account_changed") }
    private fun <T> body(response: Response<T>): T = checked(response).body() ?: throw BudgetConnectionFailure("invalid_response")
    private fun <T> checked(response: Response<T>): Response<T> {
        if (!response.isSuccessful) {
            val error = response.errorBody()?.use { runCatching { json.decodeFromString<FamilyErrorDto>(it.string()).code }.getOrNull() }
            throw BudgetConnectionFailure(when (response.code()) {
                401 -> "unauthorized"; 403 -> "access_denied"; 404 -> "family_unavailable"
                400, 409 -> error?.takeIf { it in setOf("invalid_invitation", "already_member") } ?: "invalid_invitation"
                else -> "server_unavailable"
            })
        }
        return response
    }
    private inline fun valid(block: () -> Unit) {
        try { block() } catch (_: IllegalArgumentException) { throw BudgetConnectionFailure("invalid_response") }
        catch (_: DateTimeException) { throw BudgetConnectionFailure("invalid_response") }
    }
}
