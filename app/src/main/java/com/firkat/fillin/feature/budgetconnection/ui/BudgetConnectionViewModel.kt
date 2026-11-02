package com.firkat.fillin.feature.budgetconnection.ui

import androidx.lifecycle.viewModelScope
import com.firkat.fillin.core.mvi.*
import com.firkat.fillin.feature.auth.domain.AuthRepository
import com.firkat.fillin.feature.budgetconnection.domain.*
import com.firkat.fillin.feature.budgetconnection.ui.interactor.*
import kotlinx.coroutines.*

class BudgetConnectionViewModel(
    auth: AuthRepository,
    repository: BudgetConnectionRepository,
    creation: BudgetCreationRepository,
    autoStartCreation: Boolean = false,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    reducer: Reducer<BudgetConnectionState, BudgetConnectionAction, BudgetConnectionEvent> = BudgetConnectionReducer(autoStartCreation),
    interactor: Interactor<BudgetConnectionCommand, BudgetConnectionEvent> = BudgetConnectionInteractor(auth, repository, creation, dispatcher),
) : BaseViewModel<BudgetConnectionState, BudgetConnectionAction, BudgetConnectionCommand, BudgetConnectionEvent, BudgetConnectionEffect>(
    BudgetConnectionState(), reducer, interactor,
) {
    private var operation: Job? = null
    private var startedRequest: ConnectionRequest? = null
    private var generation: Long = 0
    init {
        execute(BudgetConnectionCommand.ObserveActiveBudget)
        execute(BudgetConnectionCommand.ObserveSession)
    }
    override fun handleAction(action: BudgetConnectionAction) {
        val current = state.value
        if (action == BudgetConnectionAction.Back) {
            if (!current.busy) emitEffect(BudgetConnectionEffect.Back)
            return
        }
        val request = current.request() ?: return
        when (action) {
            BudgetConnectionAction.Refresh -> if (current.loading) start(request, BudgetConnectionCommand.Load(request))
            BudgetConnectionAction.Create -> if (current.creating) current.draft?.let {
                start(request, BudgetConnectionCommand.Create(request, it, current.pending))
            }
            is BudgetConnectionAction.Connect -> if (current.connecting == action.budget.id) start(request, BudgetConnectionCommand.Connect(request, action.budget))
            else -> Unit
        }
    }
    override fun handleEvent(event: BudgetConnectionEvent) {
        val current = state.value
        if (generation == current.generation) return
        generation = current.generation
        // Only account-scoped work is cancelled; both observations remain active.
        operation?.cancel()
        current.request()?.let { start(it, BudgetConnectionCommand.Load(it)) }
    }
    private fun start(request: ConnectionRequest, command: BudgetConnectionCommand) {
        if (startedRequest == request) return
        startedRequest = request
        operation = execute(command).also { job ->
            job.invokeOnCompletion { cause ->
                if (cause is CancellationException) viewModelScope.launch { onAction(BudgetConnectionAction.Cancelled(request)) }
            }
        }
    }
}
