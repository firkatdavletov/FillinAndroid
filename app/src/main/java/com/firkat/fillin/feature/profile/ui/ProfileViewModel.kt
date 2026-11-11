package com.firkat.fillin.feature.profile.ui

import com.firkat.fillin.feature.budgetprofile.domain.BudgetCurrency

import androidx.lifecycle.SavedStateHandle
import com.firkat.fillin.feature.budgetconnection.domain.BudgetCreationErrors
import com.firkat.fillin.feature.budgetconnection.domain.validationErrors
import com.firkat.fillin.feature.budgetsetup.domain.BudgetSetupMode
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
    val creationMode: BudgetSetupMode = BudgetSetupMode.Local,
    val creationErrors: BudgetCreationErrors = BudgetCreationErrors(),
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
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val mutableState = MutableStateFlow(ProfileUiState(fullAccess = access?.cloudAllowed != false))
    val state = mutableState.asStateFlow()
    private var action: Job? = null
    private var cloudAction = false
    private var refreshJob: Job? = null
    private val reload = MutableStateFlow(0)

    init {
        val snapshot = savedState.get<ArrayList<String>>("profile_creation")
        restoreCreation(snapshot)
        viewModelScope.launch {
            state.collect { value -> saveCreation(value, snapshot?.getOrNull(5)) }
        }
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
                val firstSession = previousSession == null
                if (session == AuthSession.Loading) return@collectLatest
                val restoredOwnerChanged = previousSession == null && snapshot != null &&
                    snapshot.getOrNull(5).orEmpty() != (session as? AuthSession.Authenticated)?.user?.uid.orEmpty()
                val sessionChanged = restoredOwnerChanged || (previousSession != null && previousSession != session)
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
                        creationMode = if (signedIn) it.creationMode else BudgetSetupMode.Local,
                        cloudAllowed = signedIn,
                        fullAccess = full,
                    )
                }
                if (firstSession && signedIn && state.value.creation != null) {
                    try {
                        val uid = (session as AuthSession.Authenticated).user.uid
                        val pending = creation.pending(uid)
                        if (pending != null) mutableState.update {
                            it.copy(creation = pending.draft, pendingCreation = true,
                                creationMode = BudgetSetupMode.Cloud, currencyPickerOpen = false)
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { mutableState.update { it.copy(error = setupError(failure)) } }
                }
                refresh()
            }
        }
    }

    private fun restoreCreation(snapshot: ArrayList<String>?) {
        if (snapshot == null) return
        runCatching {
            val draft = BudgetCreationDraft(snapshot[0], snapshot[1], snapshot[2], BudgetCurrency.valueOf(snapshot[3]))
            mutableState.update {
                it.copy(
                    creation = draft,
                    creationMode = BudgetSetupMode.valueOf(snapshot[4]),
                    pendingCreation = snapshot.getOrNull(6).toBoolean(),
                    currencyPickerOpen = snapshot.getOrNull(7).toBoolean(),
                )
            }
        }
    }

    private fun saveCreation(value: ProfileUiState, restoredUid: String?) {
        val owner = if (auth.session.value == AuthSession.Loading) restoredUid
            else (auth.session.value as? AuthSession.Authenticated)?.user?.uid
        savedState["profile_creation"] = value.creation?.let { draft ->
            arrayListOf(
                draft.name, draft.openingBalance, draft.accountingStartedOn, draft.currency.name,
                value.creationMode.name, owner.orEmpty(), value.pendingCreation.toString(), value.currencyPickerOpen.toString(),
            )
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
                creation = pending?.draft ?: BudgetCreationDraft("Личный бюджет", "0", LocalDate.now(ZoneId.of("Asia/Almaty")).toString(), BudgetCurrency.RUB),
                pendingCreation = pending != null,
                creationMode = if (pending != null) BudgetSetupMode.Cloud else BudgetSetupMode.Local,
                creationErrors = BudgetCreationErrors(),
            ) }
        }
    }

    fun closeCreation() {
        if (!state.value.busy) mutableState.update { it.copy(creation = null, pendingCreation = false, currencyPickerOpen = false, error = null, creationErrors = BudgetCreationErrors()) }
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
        if (state.value.creation != null && !state.value.busy && !state.value.pendingCreation) mutableState.update {
            it.copy(creation = draft, error = null, creationErrors = it.creationErrors.copy(
                name = it.creationErrors.name && it.creation?.name == draft.name,
                balance = it.creationErrors.balance && it.creation?.openingBalance == draft.openingBalance,
                date = it.creationErrors.date && it.creation?.accountingStartedOn == draft.accountingStartedOn,
            ))
        }
    }

    fun selectCreationMode(mode: BudgetSetupMode) {
        if (!state.value.busy && !state.value.pendingCreation && (mode == BudgetSetupMode.Local || state.value.cloudAllowed)) {
            mutableState.update { it.copy(creationMode = mode) }
        }
    }

    private fun validCreation(): Boolean {
        val draft = state.value.creation ?: return false
        val errors = draft.validationErrors(LocalDate.now(ZoneId.of("Asia/Almaty")))
        mutableState.update { it.copy(creationErrors = errors) }
        return errors.isEmpty
    }

    fun createLocal() {
        if (state.value.busy || state.value.localLimitReached || state.value.pendingCreation || !validCreation()) return
        val draft = state.value.creation?.validated(LocalDate.now(ZoneId.of("Asia/Almaty"))) ?: return
        perform {
            budgets.createLocal(draft.name, draft.openingBalance.toBigDecimal(), LocalDate.parse(draft.accountingStartedOn), draft.currency)
            mutableState.update { it.copy(creation = null) }
        }
    }

    fun createCloud() {
        if (state.value.busy || !state.value.cloudAllowed || (!state.value.pendingCreation && !validCreation())) return
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
