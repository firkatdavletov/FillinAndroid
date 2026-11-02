package com.firkat.fillin.feature.budgetsetup.ui.interactor

import com.firkat.fillin.core.mvi.Reducer
import com.firkat.fillin.feature.auth.domain.AuthSession
import com.firkat.fillin.feature.budgetsetup.domain.BudgetSetupMode
import com.firkat.fillin.feature.budgetsharing.domain.FamilyInvitationCode
import java.math.BigDecimal

class BudgetSetupReducer : Reducer<BudgetSetupState, BudgetSetupAction, BudgetSetupEvent> {
    override fun reduceAction(state: BudgetSetupState, action: BudgetSetupAction): BudgetSetupState = with(state) {
        when (action) {
            BudgetSetupAction.Start -> this
            is BudgetSetupAction.NameChanged -> if (editable) copy(name = action.value, nameInvalid = false, formOpened = true) else this
            is BudgetSetupAction.BalanceChanged -> if (editable) copy(balance = action.value, balanceInvalid = false, formOpened = true) else this
            is BudgetSetupAction.StorageChanged -> if (editable) copy(mode = action.mode, formOpened = true) else this
            is BudgetSetupAction.CodeChanged -> if (!busy && action.value.length <= 80) copy(code = action.value, codeInvalid = false) else this
            BudgetSetupAction.Create -> if (busy || completed || session == AuthSession.Loading) this else {
                val invalidName = name.trim().let { it.length !in 1..80 || it.any(Char::isISOControl) }
                val invalidBalance = !validBalance(balance)
                when {
                    pending != null -> copy(busy = true, operationId = operationId + 1, error = null)
                    invalidName || invalidBalance -> copy(nameInvalid = invalidName, balanceInvalid = invalidBalance)
                    mode == BudgetSetupMode.Cloud && uid == null -> copy(authTarget = SetupAuthTarget.Create, authLaunched = false, formOpened = true)
                    mode == BudgetSetupMode.Cloud && (!loaded || loading) -> this
                    else -> copy(busy = true, operationId = operationId + 1, error = null)
                }
            }
            BudgetSetupAction.OpenCreate -> if (!busy && pending == null) copy(page = SetupPage.Create, formOpened = true) else this
            BudgetSetupAction.OpenJoin -> if (!busy && pending == null) {
                if (uid == null) copy(authTarget = SetupAuthTarget.Join, authLaunched = false, formOpened = true)
                else copy(page = SetupPage.Join, formOpened = true)
            } else this
            BudgetSetupAction.Join -> if (busy || uid == null || pending != null) this else {
                if (runCatching { FamilyInvitationCode.normalize(code) }.isFailure) copy(codeInvalid = true)
                else copy(busy = true, operationId = operationId + 1, error = null)
            }
            is BudgetSetupAction.Connect -> if (!busy && uid != null && pending == null && action.budget in budgets) copy(busy = true, operationId = operationId + 1, error = null) else this
            BudgetSetupAction.Refresh -> if (!busy && !loading && uid != null) copy(loading = true, error = null) else this
            BudgetSetupAction.Back -> if (busy) this else when {
                page == SetupPage.Join -> copy(page = SetupPage.Create)
                page == SetupPage.Create && budgets.isNotEmpty() && pending == null -> copy(page = SetupPage.List)
                else -> this
            }
            BudgetSetupAction.AuthLaunched -> copy(authLaunched = true)
            BudgetSetupAction.AuthReturned -> copy(authTarget = null, authLaunched = false)
            BudgetSetupAction.ErrorShown -> copy(error = null)
            BudgetSetupAction.Reset -> BudgetSetupState(generation = generation + 1)
            is BudgetSetupAction.Cancelled -> if (generation == action.generation && operationId == action.operationId) copy(busy = false) else this
        }
    }
    override fun reduceEvent(state: BudgetSetupState, event: BudgetSetupEvent): BudgetSetupState = with(state) {
        when (event) {
            is BudgetSetupEvent.Session -> {
                if (event.session == AuthSession.Loading || event.session == session) this else {
                    val nextUid = (event.session as? AuthSession.Authenticated)?.user?.uid
                    if (initialized && session != AuthSession.Loading && uid == nextUid) copy(session = event.session) else {
                        val preserve = initialized && (ownerUid == nextUid || (ownerUid == null && authTarget != null))
                        val base = if (preserve) this else BudgetSetupState()
                        base.copy(session = event.session, ownerUid = nextUid, initialized = true,
                            mode = if (!preserve) { if (nextUid == null) BudgetSetupMode.Local else BudgetSetupMode.Cloud } else base.mode,
                            page = if (preserve && authTarget == SetupAuthTarget.Join && nextUid != null) SetupPage.Join else base.page,
                            generation = generation + 1, operationId = operationId + 1,
                            budgets = emptyList(), pending = null, loading = nextUid != null, loaded = false, busy = false, error = null)
                    }
                }
            }
            is BudgetSetupEvent.Catalog -> if (event.generation != generation) this else {
                val request = event.pending
                copy(budgets = event.budgets, pending = request, showingSaved = event.cached,
                    name = request?.draft?.name ?: name, balance = request?.draft?.openingBalance ?: balance,
                    mode = if (request != null) BudgetSetupMode.Cloud else mode,
                    page = when { request != null -> SetupPage.Create; !formOpened && event.budgets.isNotEmpty() -> SetupPage.List; else -> page })
            }
            is BudgetSetupEvent.LoadFinished -> if (event.generation == generation) copy(loading = false, loaded = event.error == null, error = event.error) else this
            is BudgetSetupEvent.Finished -> if (generation != event.generation || operationId != event.operationId || !busy) this else copy(
                busy = false, completed = event.error == null, error = event.error, pending = event.pending,
                name = event.pending?.draft?.name ?: name, balance = event.pending?.draft?.openingBalance ?: balance)
        }
    }
}
internal fun validBalance(value: String): Boolean = value.length <= 64 && runCatching {
    val amount = value.trim().replace(',', '.').toBigDecimal()
    amount.scale() <= 2 && amount.abs() <= BigDecimal("99999999999999999.99")
}.getOrDefault(false)
