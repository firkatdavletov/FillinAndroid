package com.firkat.fillin.feature.budgetinvitation.data

import com.firkat.fillin.feature.auth.data.FirebaseAuthenticatedRequests
import com.firkat.fillin.feature.auth.domain.*
import com.firkat.fillin.feature.budgetinvitation.domain.*
import com.firkat.fillin.feature.budgetprofile.data.ActiveBudgetManager
import com.firkat.fillin.feature.budgetprofile.domain.BudgetProfileKind
import com.firkat.fillin.feature.operationhistory.data.local.OperationDatabase
import com.firkat.fillin.feature.sync.domain.BudgetConnectionFailure
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import retrofit2.Response
import java.time.Instant
import java.time.DateTimeException
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseBudgetInvitationRepository(
    private val api: FirebaseBudgetInvitationApi?,
    tokens: FirebaseIdTokenProvider,
    private val auth: AuthRepository,
    private val active: ActiveBudgetManager,
) : BudgetInvitationRepository {
    private val requests = FirebaseAuthenticatedRequests(tokens)
    override val target: Flow<InvitationTarget?> = combine(auth.session, active.active) { session, runtime -> session to runtime }
        .flatMapLatest { (session, runtime) ->
            val uid = (session as? AuthSession.Authenticated)?.user?.uid
            val profile = runtime?.profile
            if (uid == null || profile?.kind != BudgetProfileKind.CLOUD || profile.firebaseUid != uid) flowOf<InvitationTarget?>(null) else flow<InvitationTarget?> {
                val lease = runtime.retain() ?: return@flow
                try {
                    lease.scope.get<OperationDatabase>().operations().observeState().collect { state ->
                        emit(if (state?.cloudBound == true && state.localReady && active.isCurrent(profile))
                            InvitationTarget(profile.id, uid, profile.budgetId!!, state.role) else null)
                    }
                } finally { lease.close() }
            }
        }.distinctUntilChanged().flowOn(Dispatchers.IO)

    override fun isCurrent(target: InvitationTarget): Boolean {
        val profile = active.active.value?.profile ?: return false
        return (auth.session.value as? AuthSession.Authenticated)?.user?.uid == target.uid &&
            profile.id == target.profileId && profile.budgetId == target.budgetId && profile.firebaseUid == target.uid && active.isCurrent(profile)
    }

    override suspend fun current(target: InvitationTarget): BudgetInvitation? {
        val status = call(target) { api, token -> api.current(token, target.budgetId) }.body()
            ?: throw BudgetConnectionFailure("invalid_response")
        if (status.budgetId != target.budgetId) throw BudgetConnectionFailure("invalid_response")
        return status.current?.let { validate(it, target) }
    }

    override suspend fun create(target: InvitationTarget): CreatedInvitation {
        val dto = call(target) { api, token -> api.create(token, target.budgetId) }.body()
            ?: throw BudgetConnectionFailure("invalid_response")
        val invitation = validate(dto, target)
        val code = dto.code ?: throw BudgetConnectionFailure("invalid_response")
        if (!Regex("[0-9A-HJKMNP-TV-Z]{5}(-[0-9A-HJKMNP-TV-Z]{5}){3}").matches(code)) throw BudgetConnectionFailure("invalid_response")
        return CreatedInvitation(invitation, code)
    }

    override suspend fun revoke(target: InvitationTarget, invitation: BudgetInvitation) {
        require(invitation.budgetId == target.budgetId)
        UUID.fromString(invitation.id)
        call(target) { api, token -> api.revoke(token, target.budgetId, invitation.id) }
    }

    private fun validate(dto: InvitationDto, target: InvitationTarget): BudgetInvitation {
        try {
            UUID.fromString(dto.id); UUID.fromString(dto.budgetId); Instant.parse(dto.expiresAt)
            require(dto.budgetId == target.budgetId)
        } catch (_: IllegalArgumentException) { throw BudgetConnectionFailure("invalid_response") }
        catch (_: DateTimeException) { throw BudgetConnectionFailure("invalid_response") }
        return BudgetInvitation(dto.id, dto.budgetId, dto.expiresAt)
    }

    private suspend fun <T> call(target: InvitationTarget, block: suspend (FirebaseBudgetInvitationApi, String) -> Response<T>): Response<T> {
        ensure(target)
        val response = requests.execute(target.uid, api, block)
        ensure(target)
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            throw BudgetConnectionFailure(when (response.code()) {
                401 -> "unauthorized"; 403 -> "access_denied"; 404 -> "invitation_unavailable"; else -> "server_unavailable"
            })
        }
        return response
    }

    private fun ensure(target: InvitationTarget) {
        if (!isCurrent(target)) throw BudgetConnectionFailure("account_changed")
        if (target.role != "OWNER") throw BudgetConnectionFailure("access_denied")
    }
}
