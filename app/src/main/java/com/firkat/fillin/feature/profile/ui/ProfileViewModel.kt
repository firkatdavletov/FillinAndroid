package com.firkat.fillin.feature.profile.ui

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
)

class ProfileViewModel(
    private val budgets: ProfileBudgetsRepository,
    private val auth: AuthRepository,
    private val creation: BudgetCreationRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ProfileUiState())
    val state = mutableState.asStateFlow()
    private var action: Job? = null
    private var refreshJob: Job? = null
    private val reload = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            reload.collectLatest {
                budgets.observe().catch { failure ->
                    mutableState.update { it.copy(loading = false, error = setupError(failure as? Exception ?: Exception(failure))) }
                }.collect { rows -> mutableState.update { it.copy(budgets = rows, loading = false, expandedBudgetId = it.expandedBudgetId?.takeIf { id -> rows.any { row -> row.id == id && row.canManage } }) } }
            }
        }
        viewModelScope.launch {
            auth.session.collectLatest { session ->
                action?.cancel()
                mutableState.update { it.copy(expandedBudgetId = null, budgets = emptyList(), loading = true, creation = null, pendingCreation = false, busy = false, error = null, cloudAllowed = session is AuthSession.Authenticated) }
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
        perform { budgets.select(id) }
    }

    fun openCreation() {
        val uid = (auth.session.value as? AuthSession.Authenticated)?.user?.uid
        perform {
            val pending = uid?.let { creation.pending(it) }
            mutableState.update { it.copy(
                creation = pending?.draft ?: BudgetCreationDraft("", "0", LocalDate.now(ZoneId.of("Asia/Almaty")).toString()),
                pendingCreation = pending != null,
            ) }
        }
    }

    fun closeCreation() {
        if (!state.value.busy) mutableState.update { it.copy(creation = null, pendingCreation = false, error = null) }
    }

    fun editCreation(draft: BudgetCreationDraft) {
        if (!state.value.busy && !state.value.pendingCreation) mutableState.update { it.copy(creation = draft, error = null) }
    }

    fun createLocal() {
        val draft = state.value.creation ?: return
        perform {
            budgets.createLocal(draft.openingBalance.trim().replace(',', '.').toBigDecimal(), LocalDate.parse(draft.accountingStartedOn.trim()))
            mutableState.update { it.copy(creation = null) }
        }
    }

    fun createCloud() {
        val uid = (auth.session.value as? AuthSession.Authenticated)?.user?.uid ?: return
        val draft = state.value.creation ?: return
        perform {
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

    private fun perform(block: suspend () -> Unit) {
        if (state.value.busy) return
        mutableState.update { it.copy(busy = true, error = null) }
        action = viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutableState.update { it.copy(error = setupError(if (failure is IllegalArgumentException || failure is java.time.DateTimeException) BudgetConnectionFailure("invalid_creation") else failure)) } }
            finally { mutableState.update { it.copy(busy = false) } }
        }
    }
}
