package com.firkat.fillin.feature.budgetconnection.ui.interactor

import com.firkat.fillin.core.mvi.*
import com.firkat.fillin.feature.auth.domain.AuthSession
import com.firkat.fillin.feature.budgetconnection.domain.*

internal val AuthSession.uid: String? get() = (this as? AuthSession.Authenticated)?.user?.uid

data class ConnectionRequest(val uid: String, val generation: Long, val id: Long)

data class BudgetConnectionState(
    val session: AuthSession = AuthSession.Loading,
    val budgets: List<AvailableBudget> = emptyList(),
    val loading: Boolean = false,
    val connecting: String? = null,
    val loaded: Boolean = false,
    val error: String? = null,
    val draft: BudgetCreationDraft? = null,
    val pending: PendingBudgetCreation? = null,
    val creating: Boolean = false,
    val activeBudgetId: String? = null,
    val showingSaved: Boolean = false,
    val generation: Long = 0,
    val operationId: Long = 0,
    val joiningFamily: Boolean = false
) : State {
    val busy get() = loading || connecting != null || creating
}

sealed interface BudgetConnectionAction : Action {
    data object Refresh : BudgetConnectionAction
    data class ChooseCreate(val date: String = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Almaty")).toString()) :
        BudgetConnectionAction
    data class EditDraft(val draft: BudgetCreationDraft) : BudgetConnectionAction
    data object CancelCreate : BudgetConnectionAction
    data object Create : BudgetConnectionAction
    data class Connect(val budget: AvailableBudget) : BudgetConnectionAction
    data class ShowJoin(val show: Boolean) : BudgetConnectionAction
    data class Restore(val uid: String?, val draft: BudgetCreationDraft?) : BudgetConnectionAction
    data class RestoreSection(val uid: String?, val joining: Boolean) : BudgetConnectionAction
    data object Back : BudgetConnectionAction
    data class Cancelled(val request: ConnectionRequest) : BudgetConnectionAction
}
sealed interface BudgetConnectionCommand : Command {
    data object ObserveSession : BudgetConnectionCommand
    data object ObserveActiveBudget : BudgetConnectionCommand
    data class Load(val request: ConnectionRequest) : BudgetConnectionCommand
    data class Create(
        val request: ConnectionRequest,
        val draft: BudgetCreationDraft,
        val pending: PendingBudgetCreation?
    ) : BudgetConnectionCommand
    data class Connect(val request: ConnectionRequest, val budget: AvailableBudget) : BudgetConnectionCommand
}
sealed interface BudgetConnectionEvent : Event {
    data class SessionChanged(val session: AuthSession) : BudgetConnectionEvent
    data class ActiveBudgetChanged(val id: String?) : BudgetConnectionEvent
    data class Cached(val request: ConnectionRequest, val budgets: List<AvailableBudget>) : BudgetConnectionEvent
    data class Pending(val request: ConnectionRequest, val pending: PendingBudgetCreation?) : BudgetConnectionEvent
    data class Loaded(val request: ConnectionRequest, val budgets: List<AvailableBudget>, val defaultDate: String) :
        BudgetConnectionEvent
    data class Created(val request: ConnectionRequest) : BudgetConnectionEvent
    data class CreationFailed(
        val request: ConnectionRequest,
        val draft: BudgetCreationDraft,
        val pending: PendingBudgetCreation?,
        val error: String
    ) : BudgetConnectionEvent
    data class Finished(val request: ConnectionRequest, val error: String? = null) : BudgetConnectionEvent
}
sealed interface BudgetConnectionEffect : Effect {
    data object Back : BudgetConnectionEffect
}
internal fun BudgetConnectionState.request() = session.uid?.let { ConnectionRequest(it, generation, operationId) }
internal fun BudgetConnectionState.accepts(
    request: ConnectionRequest
) = busy && request() == request
