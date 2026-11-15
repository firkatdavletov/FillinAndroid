package com.firkat.fillin.feature.budgetsharing.ui

import com.firkat.fillin.core.mvi.*
import com.firkat.fillin.feature.auth.domain.AuthSession

internal val AuthSession.joinUid: String? get() = (this as? AuthSession.Authenticated)?.user?.uid

data class FamilyJoinState(
    val session: AuthSession = AuthSession.Loading,
    val code: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val joinedName: String? = null,
    val generation: Long = 0,
    val operationId: Long = 0
) : State

data class JoinRequest(val uid: String, val generation: Long, val id: Long)
internal fun FamilyJoinState.request() = session.joinUid?.let { JoinRequest(it, generation, operationId) }
sealed interface FamilyJoinAction : Action {
    data class EditCode(val code: String) : FamilyJoinAction
    data object Join : FamilyJoinAction
    data class Cancelled(val request: JoinRequest) : FamilyJoinAction
}
sealed interface FamilyJoinCommand : Command {
    data object ObserveSession : FamilyJoinCommand
    data class Join(val request: JoinRequest, val code: String) : FamilyJoinCommand
}
sealed interface FamilyJoinEvent : Event {
    data class SessionChanged(val session: AuthSession) : FamilyJoinEvent
    data class Finished(val request: JoinRequest, val name: String? = null, val error: String? = null) : FamilyJoinEvent
}
sealed interface FamilyJoinEffect : Effect
