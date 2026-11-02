package com.firkat.fillin.feature.budgetsharing.ui

import androidx.lifecycle.viewModelScope
import com.firkat.fillin.core.mvi.*
import com.firkat.fillin.feature.auth.domain.AuthRepository
import com.firkat.fillin.feature.budgetsharing.domain.FamilyBudgetRepository
import kotlinx.coroutines.*

class FamilyBudgetJoinViewModel(
    auth: AuthRepository,
    repository: FamilyBudgetRepository,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    reducer: Reducer<FamilyJoinState, FamilyJoinAction, FamilyJoinEvent> = FamilyJoinReducer(),
    interactor: Interactor<FamilyJoinCommand, FamilyJoinEvent> = FamilyJoinInteractor(auth, repository, dispatcher),
) : BaseViewModel<FamilyJoinState, FamilyJoinAction, FamilyJoinCommand, FamilyJoinEvent, FamilyJoinEffect>(FamilyJoinState(), reducer, interactor) {
    private var operation: Job? = null
    private var startedRequest: JoinRequest? = null
    private var generation: Long = 0
    init { execute(FamilyJoinCommand.ObserveSession) }
    override fun handleAction(action: FamilyJoinAction) {
        val current = state.value
        val request = current.request() ?: return
        if (action == FamilyJoinAction.Join && current.busy && startedRequest != request) {
            startedRequest = request
            operation = execute(FamilyJoinCommand.Join(request, current.code)).also { job ->
                job.invokeOnCompletion { cause ->
                    if (cause is CancellationException) viewModelScope.launch { onAction(FamilyJoinAction.Cancelled(request)) }
                }
            }
        }
    }
    override fun handleEvent(event: FamilyJoinEvent) {
        if (generation != state.value.generation) {
            generation = state.value.generation
            operation?.cancel()
        }
    }
}
