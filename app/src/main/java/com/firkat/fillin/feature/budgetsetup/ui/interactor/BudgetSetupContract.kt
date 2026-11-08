package com.firkat.fillin.feature.budgetsetup.ui.interactor

import com.firkat.fillin.feature.budgetprofile.domain.BudgetCurrency

import com.firkat.fillin.core.mvi.*
import com.firkat.fillin.feature.auth.domain.AuthSession
import com.firkat.fillin.feature.budgetconnection.domain.*
import com.firkat.fillin.feature.budgetsetup.domain.BudgetSetupMode

enum class SetupPage { Create, List, Join }
enum class SetupAuthTarget { Create, Join }

data class BudgetSetupState(
    val name: String = "Личный бюджет",
    val balance: String = "0",
    val currency: BudgetCurrency = BudgetCurrency.RUB,
    val currencyPickerOpen: Boolean = false,
    val mode: BudgetSetupMode = BudgetSetupMode.Local,
    val page: SetupPage = SetupPage.Create,
    val session: AuthSession = AuthSession.Loading,
    val ownerUid: String? = null,
    val cloudAllowed: Boolean = true,
    val localFirst: Boolean = false,
    val initialized: Boolean = false,
    val formOpened: Boolean = false,
    val authTarget: SetupAuthTarget? = null,
    val authLaunched: Boolean = false,
    val budgets: List<AvailableBudget> = emptyList(),
    val pending: PendingBudgetCreation? = null,
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val showingSaved: Boolean = false,
    val busy: Boolean = false,
    val completed: Boolean = false,
    val generation: Long = 0,
    val operationId: Long = 0,
    val nameInvalid: Boolean = false,
    val balanceInvalid: Boolean = false,
    val code: String = "",
    val codeInvalid: Boolean = false,
    val error: String? = null,
) : State {
    val uid get() = (session as? AuthSession.Authenticated)?.user?.uid
    val canCreate get() = initialized && session != AuthSession.Loading && !busy && !completed &&
        (mode == BudgetSetupMode.Local || uid == null || pending != null || loaded && !loading)
    val editable get() = initialized && session != AuthSession.Loading && !busy && pending == null && !completed
}
sealed interface BudgetSetupAction : Action {
    data object Start : BudgetSetupAction
    data class NameChanged(val value: String) : BudgetSetupAction
    data class BalanceChanged(val value: String) : BudgetSetupAction
    data object OpenCurrencyPicker : BudgetSetupAction
    data object CloseCurrencyPicker : BudgetSetupAction
    data class CurrencyChanged(val currency: BudgetCurrency) : BudgetSetupAction
    data class StorageChanged(val mode: BudgetSetupMode) : BudgetSetupAction
    data class CodeChanged(val value: String) : BudgetSetupAction
    data object Create : BudgetSetupAction
    data object OpenList : BudgetSetupAction
    data object OpenCreate : BudgetSetupAction
    data object OpenJoin : BudgetSetupAction
    data object Join : BudgetSetupAction
    data object Refresh : BudgetSetupAction
    data class Connect(val budget: AvailableBudget) : BudgetSetupAction
    data object Back : BudgetSetupAction
    data object AuthLaunched : BudgetSetupAction
    data object AuthReturned : BudgetSetupAction
    data object ErrorShown : BudgetSetupAction
    data object Reset : BudgetSetupAction
    data class Cancelled(val generation: Long, val operationId: Long) : BudgetSetupAction
}
sealed interface BudgetSetupCommand : Command {
    data object ObserveSession : BudgetSetupCommand
    data class Load(val uid: String, val generation: Long) : BudgetSetupCommand
    data class Submit(val state: BudgetSetupState, val kind: SubmitKind, val budget: AvailableBudget? = null) : BudgetSetupCommand
}
enum class SubmitKind { Local, Cloud, Connect, Join }
sealed interface BudgetSetupEvent : Event {
    data class Session(val session: AuthSession, val cloudAllowed: Boolean = true, val localFirst: Boolean = false) : BudgetSetupEvent
    data class Catalog(val generation: Long, val budgets: List<AvailableBudget>, val pending: PendingBudgetCreation?, val cached: Boolean) : BudgetSetupEvent
    data class LoadFinished(val generation: Long, val error: String? = null) : BudgetSetupEvent
    data class Finished(val generation: Long, val operationId: Long, val error: String? = null, val pending: PendingBudgetCreation? = null) : BudgetSetupEvent
}
sealed interface BudgetSetupEffect : Effect { data object Exit : BudgetSetupEffect }
