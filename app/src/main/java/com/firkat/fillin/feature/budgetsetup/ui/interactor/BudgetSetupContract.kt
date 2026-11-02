package com.firkat.fillin.feature.budgetsetup.ui.interactor

import com.firkat.fillin.core.mvi.*

data class BudgetSetupState(
    val balance: String,
    val date: String,
    val ready: Boolean? = null,
    val busy: Boolean = false,
    val operationId: Long = 0,
    val error: String? = null,
) : State

sealed interface BudgetSetupAction : Action {
    data class BalanceChanged(val value: String) : BudgetSetupAction
    data class DateChanged(val value: String) : BudgetSetupAction
    data class RestoreForm(val balance: String, val date: String) : BudgetSetupAction
    data object Create : BudgetSetupAction
    data object Back : BudgetSetupAction
    data class OperationCancelled(val operationId: Long) : BudgetSetupAction
}
sealed interface BudgetSetupCommand : Command {
    data object ObserveReadiness : BudgetSetupCommand
    data class Create(val operationId: Long, val balance: String, val date: String) : BudgetSetupCommand
}
sealed interface BudgetSetupEvent : Event {
    data class ReadinessChanged(val ready: Boolean) : BudgetSetupEvent
    data class Created(val operationId: Long, val error: String? = null) : BudgetSetupEvent
}
sealed interface BudgetSetupEffect : Effect {
    data object Back : BudgetSetupEffect
}
