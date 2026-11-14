package com.firkat.fillin.feature.budgetsetup.ui.interactor

import com.firkat.fillin.core.mvi.Reducer
import com.firkat.fillin.feature.auth.domain.AuthSession
import com.firkat.fillin.feature.budgetconnection.domain.budgetCreationFieldErrors
import com.firkat.fillin.feature.budgetsetup.domain.BudgetSetupMode
import com.firkat.fillin.feature.budgetsharing.domain.FamilyInvitationCode

class BudgetSetupReducer : Reducer<BudgetSetupState, BudgetSetupAction, BudgetSetupEvent> {
    override fun reduceAction(
        state: BudgetSetupState,
        action: BudgetSetupAction
    ): BudgetSetupState = with(state) {
        when (action) {
            BudgetSetupAction.Start -> this
            is BudgetSetupAction.LocalLimitChanged -> copy(localLimitReached = action.reached)
            is BudgetSetupAction.NameChanged -> if (editable) {
                copy(
                    name = action.value,
                    nameInvalid = false,
                    formOpened = true
                )
            } else {
                this
            }
            is BudgetSetupAction.BalanceChanged -> if (editable) {
                copy(
                    balance = action.value,
                    balanceInvalid = false,
                    formOpened = true
                )
            } else {
                this
            }
            BudgetSetupAction.OpenCurrencyPicker -> if (editable) copy(currencyPickerOpen = true) else this
            BudgetSetupAction.CloseCurrencyPicker -> copy(currencyPickerOpen = false)
            is BudgetSetupAction.CurrencyChanged -> if (editable && currencyPickerOpen) {
                copy(currency = action.currency, currencyPickerOpen = false, formOpened = true)
            } else {
                this
            }
            is BudgetSetupAction.StorageChanged -> if (editable &&
                (cloudAllowed || action.mode == BudgetSetupMode.Local)
            ) {
                copy(mode = action.mode, formOpened = true)
            } else {
                this
            }
            is BudgetSetupAction.CodeChanged -> if (!busy &&
                action.value.length <= 80
            ) {
                copy(code = action.value, codeInvalid = false)
            } else {
                this
            }
            BudgetSetupAction.Create -> if (!canCreate || page != SetupPage.Create) {
                this
            } else {
                val errors = budgetCreationFieldErrors(name, balance)
                when {
                    pending != null -> copy(busy = true, operationId = operationId + 1, error = null)
                    !errors.isEmpty -> copy(nameInvalid = errors.name, balanceInvalid = errors.balance)
                    mode == BudgetSetupMode.Cloud && uid == null -> copy(
                        authTarget = SetupAuthTarget.Create,
                        authLaunched = false,
                        formOpened = true
                    )
                    else -> copy(busy = true, operationId = operationId + 1, error = null)
                }
            }
            BudgetSetupAction.OpenList -> if (cloudAllowed &&
                !busy &&
                pending == null &&
                budgets.isNotEmpty()
            ) {
                copy(page = SetupPage.List)
            } else {
                this
            }
            BudgetSetupAction.OpenCreate -> if (!busy &&
                pending == null
            ) {
                copy(page = SetupPage.Create, formOpened = true)
            } else {
                this
            }
            BudgetSetupAction.OpenJoin -> if (cloudAllowed && !busy && pending == null) {
                if (uid == null) {
                    copy(authTarget = SetupAuthTarget.Join, authLaunched = false, formOpened = true)
                } else {
                    copy(page = SetupPage.Join, formOpened = true)
                }
            } else {
                this
            }
            BudgetSetupAction.Join -> if (busy || uid == null || pending != null) {
                this
            } else {
                if (runCatching { FamilyInvitationCode.normalize(code) }.isFailure) {
                    copy(codeInvalid = true)
                } else {
                    copy(busy = true, operationId = operationId + 1, error = null)
                }
            }
            is BudgetSetupAction.Connect -> if (!busy &&
                uid != null &&
                pending == null &&
                action.budget in budgets
            ) {
                copy(busy = true, operationId = operationId + 1, error = null)
            } else {
                this
            }
            BudgetSetupAction.Refresh -> if (!busy &&
                !loading &&
                uid != null
            ) {
                copy(loading = true, error = null)
            } else {
                this
            }
            BudgetSetupAction.Back -> if (busy) {
                this
            } else {
                when {
                    page == SetupPage.Join || (localFirst && page == SetupPage.List) -> copy(page = SetupPage.Create)
                    !localFirst && page == SetupPage.Create && budgets.isNotEmpty() && pending == null -> copy(
                        page = SetupPage.List
                    )
                    else -> this
                }
            }
            BudgetSetupAction.AuthLaunched -> copy(authLaunched = true)
            BudgetSetupAction.AuthReturned -> copy(authTarget = null, authLaunched = false)
            BudgetSetupAction.ErrorShown -> copy(error = null)
            BudgetSetupAction.Reset -> BudgetSetupState(
                cloudAllowed = cloudAllowed,
                localFirst = localFirst,
                localLimitReached = localLimitReached,
                generation = generation + 1
            )
            is BudgetSetupAction.Cancelled -> if (generation == action.generation &&
                operationId == action.operationId
            ) {
                copy(busy = false)
            } else {
                this
            }
        }
    }
    override fun reduceEvent(
        state: BudgetSetupState,
        event: BudgetSetupEvent
    ): BudgetSetupState = with(state) {
        when (event) {
            is BudgetSetupEvent.Session -> {
                if (event.localFirst && !event.cloudAllowed) {
                    if (initialized && !cloudAllowed && session == event.session) {
                        this
                    } else {
                        val nextUid = (event.session as? AuthSession.Authenticated)?.user?.uid
                        val accountChanged = initialized && ownerUid != nextUid
                        val keepSubmitting = !accountChanged && busy && mode == BudgetSetupMode.Local
                        val base = if (accountChanged) BudgetSetupState(localLimitReached = localLimitReached) else this
                        base.copy(
                            session = if (event.session ==
                                AuthSession.Loading
                            ) {
                                AuthSession.Anonymous
                            } else {
                                event.session
                            },
                            ownerUid = nextUid,
                            cloudAllowed = false, localFirst = true, initialized = true, formOpened = true,
                            mode = BudgetSetupMode.Local, page = SetupPage.Create, authTarget = null, authLaunched = false,
                            pending = null, pendingChecked = false, budgets = emptyList(), loading = false, loaded = false,
                            generation = if (accountChanged ||
                                cloudAllowed &&
                                !keepSubmitting
                            ) {
                                generation + 1
                            } else {
                                generation
                            },
                            operationId = operationId,
                            busy = keepSubmitting
                        )
                    }
                } else if (event.localFirst && busy && mode == BudgetSetupMode.Local && event.session == session) {
                    copy(cloudAllowed = event.cloudAllowed)
                } else if (event.session == AuthSession.Loading ||
                    (event.session == session && cloudAllowed)
                ) {
                    this
                } else {
                    val nextUid = (event.session as? AuthSession.Authenticated)?.user?.uid
                    if (initialized &&
                        cloudAllowed &&
                        session != AuthSession.Loading &&
                        uid == nextUid
                    ) {
                        copy(session = event.session)
                    } else {
                        val preserve = initialized && (ownerUid == nextUid || (ownerUid == null && authTarget != null))
                        val base = if (preserve) this else BudgetSetupState()
                        base.copy(
                            session = event.session, ownerUid = nextUid, initialized = true, cloudAllowed = true, localFirst = event.localFirst,
                            formOpened = event.localFirst || base.formOpened,
                            mode = if (!preserve) BudgetSetupMode.Local else base.mode,
                            page = if (preserve &&
                                authTarget == SetupAuthTarget.Join &&
                                nextUid != null
                            ) {
                                SetupPage.Join
                            } else {
                                base.page
                            },
                            generation = generation + 1, operationId = operationId + 1,
                            budgets = emptyList(), pending = null, pendingChecked = false, localLimitReached = localLimitReached,
                            loading = nextUid != null, loaded = false, busy = false, error = null
                        )
                    }
                }
            }
            is BudgetSetupEvent.Catalog -> if (!cloudAllowed || event.generation != generation) {
                this
            } else {
                val request = event.pending
                copy(
                    budgets = event.budgets, pending = request, pendingChecked = true, showingSaved = event.cached,
                    name = request?.draft?.name ?: name, balance = request?.draft?.openingBalance ?: balance,
                    currency = request?.draft?.currency ?: currency,
                    currencyPickerOpen =
                    currencyPickerOpen && request == null,
                    mode = if (request != null) BudgetSetupMode.Cloud else mode,
                    page = when {
                        request != null -> SetupPage.Create
                        !formOpened && event.budgets.isNotEmpty() -> SetupPage.List
                        else -> page
                    }
                )
            }
            is BudgetSetupEvent.LoadFinished -> if (cloudAllowed &&
                event.generation == generation
            ) {
                copy(loading = false, loaded = event.error == null, error = event.error)
            } else {
                this
            }
            is BudgetSetupEvent.Finished -> if (generation != event.generation ||
                operationId != event.operationId ||
                !busy
            ) {
                this
            } else {
                copy(
                    busy = false,
                    completed = event.error == null,
                    error = event.error,
                    pending = event.pending,
                    name = event.pending?.draft?.name ?: name,
                    balance = event.pending?.draft?.openingBalance ?: balance,
                    currency = event.pending?.draft?.currency ?: currency
                )
            }
        }
    }
}
