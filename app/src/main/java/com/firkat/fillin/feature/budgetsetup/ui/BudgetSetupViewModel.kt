package com.firkat.fillin.feature.budgetsetup.ui

import androidx.lifecycle.viewModelScope
import com.firkat.fillin.core.mvi.*
import com.firkat.fillin.feature.budgetsetup.domain.BudgetSetupRepository
import com.firkat.fillin.feature.budgetsetup.domain.CreateLocalBudget
import com.firkat.fillin.feature.budgetsetup.ui.interactor.*
import kotlinx.coroutines.*

class BudgetSetupViewModel(
    session: BudgetSetupRepository,
    createLocal: CreateLocalBudget = CreateLocalBudget(session),
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    reducer: Reducer<BudgetSetupState, BudgetSetupAction, BudgetSetupEvent> = BudgetSetupReducer(),
    interactor: Interactor<BudgetSetupCommand, BudgetSetupEvent> = BudgetSetupInteractor(session, createLocal, dispatcher),
) : BaseViewModel<BudgetSetupState, BudgetSetupAction, BudgetSetupCommand, BudgetSetupEvent, BudgetSetupEffect>(
    BudgetSetupState("0", session.today.toString()), reducer, interactor,
) {
    private var startedOperationId: Long? = null
    init { execute(BudgetSetupCommand.ObserveReadiness) }

    override fun handleAction(action: BudgetSetupAction) {
        val current = state.value
        when (action) {
            BudgetSetupAction.Create -> if (current.busy && startedOperationId != current.operationId) {
                startedOperationId = current.operationId
                execute(BudgetSetupCommand.Create(current.operationId, current.balance, current.date)).invokeOnCompletion { cause ->
                    if (cause is CancellationException) viewModelScope.launch {
                        onAction(BudgetSetupAction.OperationCancelled(current.operationId))
                    }
                }
            }
            BudgetSetupAction.Back -> if (!current.busy) emitEffect(BudgetSetupEffect.Back)
            else -> Unit
        }
    }
}
