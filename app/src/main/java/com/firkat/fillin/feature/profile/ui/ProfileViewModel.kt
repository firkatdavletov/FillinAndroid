package com.firkat.fillin.feature.profile.ui

import com.firkat.fillin.feature.budgetprofile.domain.BudgetCurrency

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.auth.domain.AuthRepository
import com.firkat.fillin.feature.auth.domain.AuthSession
import com.firkat.fillin.feature.budgetconnection.domain.BudgetCreationDraft
import com.firkat.fillin.feature.budgetconnection.domain.BudgetCreationRepository
import com.firkat.fillin.feature.budgetsetup.ui.setupError
import com.firkat.fillin.feature.profile.domain.ProfileBudget
import com.firkat.fillin.feature.profile.domain.ProfileBudgetsRepository
import com.firkat.fillin.feature.sync.domain.BudgetConnectionFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class ProfileUiState(
    val budgets: List<ProfileBudget> = emptyList(),
    val expandedBudgetId: String? = null,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
    val creation: BudgetCreationDraft? = null,
    val pendingCreation: Boolean = false,
    val cloudAllowed: Boolean = false,
    val fullAccess: Boolean = true,
    val localLimitReached: Boolean = false,
    val currencyPickerOpen: Boolean = false,
)

class ProfileViewModel(
    private val budgets: ProfileBudgetsRepository,
    private val auth: AuthRepository,
    private val creation: BudgetCreationRepository,
    private val access: com.firkat.fillin.feature.appaccess.domain.AppAccess? = null,
    localCount: kotlinx.coroutines.flow.Flow<Int> = kotlinx.coroutines.flow.flowOf(0),
) : ViewModel() {
    private val mutableState = MutableStateFlow(ProfileUiState(fullAccess = access?.cloudAllowed != false))
    val state = mutableState.asStateFlow()
    private var action: Job? = null
    private var cloudAction = false
    private var refreshJob: Job? = null
    private val reload = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            localCount.collect { count ->
                mutableState.update { it.copy(localLimitReached = count >= com.firkat.fillin.feature.budgetprofile.data.LOCAL_BUDGET_LIMIT) }
            }
        }
        viewModelScope.launch {
            reload.collectLatest {
                budgets.observe().catch { failure ->
                    mutableState.update { it.copy(loading = false, error = setupError(failure as? Exception ?: Exception(failure))) }
                }.collect { rows -> mutableState.update { it.copy(budgets = rows, loading = false, expandedBudgetId = it.expandedBudgetId?.takeIf { id -> rows.any { row -> row.id == id && row.canManage } }) } }
            }
        }
        viewModelScope.launch {
            val mode = access?.mode ?: kotlinx.coroutines.flow.flowOf(com.firkat.fillin.feature.appaccess.domain.AppMode.InternalFull)
            var previousSession: AuthSession? = null
            kotlinx.coroutines.flow.combine(auth.session, mode, ::Pair).collectLatest { (session, currentMode) ->
                val full = currentMode == com.firkat.fillin.feature.appaccess.domain.AppMode.InternalFull
                val signedIn = full && session is AuthSession.Authenticated
                val sessionChanged = previousSession != session
                previousSession = session
                if (sessionChanged || (!full && cloudAction)) action?.cancel()
                mutableState.update {
                    val discardDraft = sessionChanged || (!full && it.pendingCreation)
                    it.copy(
                        expandedBudgetId = null,
                        creation = it.creation.takeUnless { discardDraft },
                        pendingCreation = it.pendingCreation && !discardDraft,
                        currencyPickerOpen = it.currencyPickerOpen && !discardDraft,
                        error = null,
                        cloudAllowed = signedIn,
                        fullAccess = full,
                    )
                }
                if (session != AuthSession.Loading) refresh()
            }
        }
    }

    fun refresh() {
        reload.update { it + 1 }
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            try {
                budgets.refresh()
                mutableState.update { it.copy(error = null) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutableState.update { it.copy(error = setupError(failure)) } }
        }
    }

    fun closeBudgetActions() { mutableState.update { it.copy(expandedBudgetId = null) } }

    fun toggleBudgetActions(id: String) {
        mutableState.update { state ->
            if (state.busy || state.budgets.none { it.id == id && it.canManage }) state
            else state.copy(expandedBudgetId = id.takeUnless { state.expandedBudgetId == id })
        }
    }

    fun selectBudget(id: String) {
        closeBudgetActions()
        if (state.value.budgets.none { it.id == id && !it.selected }) return
        perform(requiresCloud = id.startsWith("cloud:")) { budgets.select(id) }
    }

    fun openCreation() {
        if (state.value.localLimitReached && !state.value.cloudAllowed) return
        val uid = (auth.session.value as? AuthSession.Authenticated)?.user?.uid?.takeIf { state.value.cloudAllowed }
        perform(requiresCloud = uid != null) {
            val pending = uid?.let { creation.pending(it) }
            mutableState.update { it.copy(
                creation = pending?.draft ?: BudgetCreationDraft("", "0", LocalDate.now(ZoneId.of("Asia/Almaty")).toString(), BudgetCurrency.RUB),
                pendingCreation = pending != null,
            ) }
        }
    }

    fun closeCreation() {
        if (!state.value.busy) mutableState.update { it.copy(creation = null, pendingCreation = false, currencyPickerOpen = false, error = null) }
    }

    fun openCurrencyPicker() {
        mutableState.update { if (it.creation != null && !it.busy && !it.pendingCreation) it.copy(currencyPickerOpen = true) else it }
    }

    fun closeCurrencyPicker() {
        mutableState.update { it.copy(currencyPickerOpen = false) }
    }

    fun selectCurrency(currency: BudgetCurrency) {
        mutableState.update {
            if (it.currencyPickerOpen && it.creation != null && !it.busy && !it.pendingCreation) {
                it.copy(creation = it.creation.copy(currency = currency), currencyPickerOpen = false)
            } else it
        }
    }

    fun editCreation(draft: BudgetCreationDraft) {
        if (!state.value.busy && !state.value.pendingCreation) mutableState.update { it.copy(creation = draft, error = null) }
    }

    fun createLocal() {
        if (state.value.localLimitReached) return
        val draft = state.value.creation ?: return
        perform {
            budgets.createLocal(draft.openingBalance.trim().replace(',', '.').toBigDecimal(), LocalDate.parse(draft.accountingStartedOn.trim()), draft.currency)
            mutableState.update { it.copy(creation = null) }
        }
    }

    fun createCloud() {
        if (!state.value.cloudAllowed) return
        val uid = (auth.session.value as? AuthSession.Authenticated)?.user?.uid ?: return
        val draft = state.value.creation ?: return
        perform(requiresCloud = true) {
            try {
                if (state.value.pendingCreation) creation.resume(uid) else creation.create(uid, draft)
                mutableState.update { it.copy(creation = null, pendingCreation = false) }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                val pending = try { creation.pending(uid) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
                mutableState.update { it.copy(creation = pending?.draft ?: draft, pendingCreation = pending != null) }
                throw failure
            }
        }
    }

    private fun perform(requiresCloud: Boolean = false, block: suspend () -> Unit) {
        if (state.value.busy) return
        cloudAction = requiresCloud
        mutableState.update { it.copy(busy = true, error = null) }
        action = viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutableState.update { it.copy(error = setupError(if (failure is IllegalArgumentException || failure is java.time.DateTimeException) BudgetConnectionFailure("invalid_creation") else failure)) } }
            finally {
                cloudAction = false
                mutableState.update { it.copy(busy = false) }
            }
        }
    }
}
