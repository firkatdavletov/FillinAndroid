package com.firkat.fillin.feature.budgetconnection.ui.interactor

import com.firkat.fillin.core.mvi.Reducer
import com.firkat.fillin.feature.budgetconnection.domain.BudgetCreationDraft

class BudgetConnectionReducer(private val autoStartCreation: Boolean) : Reducer<BudgetConnectionState, BudgetConnectionAction, BudgetConnectionEvent> {
    override fun reduceAction(state: BudgetConnectionState, action: BudgetConnectionAction): BudgetConnectionState = with(state) {
        when (action) {
            BudgetConnectionAction.Refresh -> if (!busy && session.uid != null) copy(loading = true, error = null, operationId = operationId + 1) else this
            is BudgetConnectionAction.ChooseCreate -> if (session.uid != null && !busy && loaded && pending == null) copy(draft = BudgetCreationDraft("", "0", action.date), error = null) else this
            is BudgetConnectionAction.EditDraft -> if (!busy && pending == null) copy(draft = action.draft, error = null) else this
            BudgetConnectionAction.CancelCreate -> if (!busy && pending == null) copy(draft = null, error = null) else this
            BudgetConnectionAction.Create -> if (!busy && session.uid != null && draft != null) copy(creating = true, error = null, operationId = operationId + 1) else this
            is BudgetConnectionAction.Connect -> if (!busy && session.uid != null && pending == null && action.budget in budgets) copy(connecting = action.budget.id, error = null, operationId = operationId + 1) else this
            is BudgetConnectionAction.ShowJoin -> if (!action.show || (!busy && pending == null)) copy(joiningFamily = action.show) else this
            is BudgetConnectionAction.Restore -> if (loaded && !busy && action.uid == session.uid) copy(
                draft = if (pending == null && draft != null) action.draft ?: draft else draft,
            ) else this
            is BudgetConnectionAction.RestoreSection -> if (action.uid == session.uid) copy(joiningFamily = action.joining) else this
            is BudgetConnectionAction.Cancelled -> if (accepts(action.request)) copy(loading = false, creating = false, connecting = null) else this
            BudgetConnectionAction.Back -> this
        }
    }

    override fun reduceEvent(state: BudgetConnectionState, event: BudgetConnectionEvent): BudgetConnectionState = with(state) {
        when (event) {
            is BudgetConnectionEvent.SessionChanged -> if (session == event.session) this else BudgetConnectionState(
                session = event.session, activeBudgetId = activeBudgetId,
                generation = generation + 1, operationId = operationId + 1, loading = event.session.uid != null,
            )
            is BudgetConnectionEvent.ActiveBudgetChanged -> copy(activeBudgetId = event.id)
            is BudgetConnectionEvent.Cached -> if (accepts(event.request) && event.budgets.isNotEmpty()) copy(budgets = event.budgets, loaded = true, showingSaved = true) else this
            is BudgetConnectionEvent.Pending -> if (accepts(event.request)) copy(pending = event.pending, draft = event.pending?.draft ?: draft) else this
            is BudgetConnectionEvent.Loaded -> if (accepts(event.request)) copy(
                budgets = event.budgets, loaded = true, showingSaved = false,
                draft = draft ?: if (autoStartCreation && event.budgets.isEmpty()) BudgetCreationDraft("", "0", event.defaultDate) else null,
            ) else this
            is BudgetConnectionEvent.Created -> if (accepts(event.request)) copy(pending = null, draft = null, loading = true) else this
            is BudgetConnectionEvent.CreationFailed -> if (accepts(event.request)) copy(
                pending = event.pending, draft = event.pending?.draft ?: event.draft, error = event.error,
            ) else this
            is BudgetConnectionEvent.Finished -> if (accepts(event.request)) copy(
                loading = false, creating = false, connecting = null, error = event.error ?: error,
            ) else this
        }
    }
}
