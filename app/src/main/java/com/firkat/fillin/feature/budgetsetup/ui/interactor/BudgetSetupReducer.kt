package com.firkat.fillin.feature.budgetsetup.ui.interactor

import com.firkat.fillin.core.mvi.Reducer

class BudgetSetupReducer : Reducer<BudgetSetupState, BudgetSetupAction, BudgetSetupEvent> {
    override fun reduceAction(state: BudgetSetupState, action: BudgetSetupAction): BudgetSetupState = with(state) {
        when (action) {
            is BudgetSetupAction.BalanceChanged -> if (!busy) copy(balance = action.value) else this
            is BudgetSetupAction.DateChanged -> if (!busy) copy(date = action.value) else this
            is BudgetSetupAction.RestoreForm -> if (!busy && ready != true) copy(balance = action.balance, date = action.date) else this
            BudgetSetupAction.Create -> if (!busy && ready != true) copy(busy = true, operationId = operationId + 1, error = null) else this
            BudgetSetupAction.Back -> this
            is BudgetSetupAction.OperationCancelled -> if (operationId == action.operationId) copy(busy = false) else this
        }
    }
    override fun reduceEvent(state: BudgetSetupState, event: BudgetSetupEvent): BudgetSetupState = when (event) {
        is BudgetSetupEvent.ReadinessChanged -> state.copy(ready = event.ready)
        is BudgetSetupEvent.Created -> if (state.busy && state.operationId == event.operationId) {
            state.copy(busy = false, error = event.error)
        } else state
    }
}
