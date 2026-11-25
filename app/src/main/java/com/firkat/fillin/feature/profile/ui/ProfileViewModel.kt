package com.firkat.fillin.feature.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.auth.domain.AuthRepository
import com.firkat.fillin.feature.auth.domain.AuthSession
import com.firkat.fillin.feature.budgetsetup.ui.setupError
import com.firkat.fillin.feature.profile.domain.ProfileBudget
import com.firkat.fillin.feature.profile.domain.ProfileBudgetsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val budgets: List<ProfileBudget> = emptyList(),
    val expandedBudgetId: String? = null,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
    val cloudAllowed: Boolean = false,
    val fullAccess: Boolean = true
)

class ProfileViewModel(
    private val budgets: ProfileBudgetsRepository,
    private val auth: AuthRepository,
    private val access: com.firkat.fillin.feature.appaccess.domain.AppAccess? = null,
    private val installationId: () -> String = { "" }
) : ViewModel() {
    private val mutableState = MutableStateFlow(ProfileUiState(fullAccess = access?.cloudAllowed != false))
    val state = mutableState.asStateFlow()
    private val aboutId = MutableStateFlow<String?>(null)
    val aboutInstallationId = aboutId.asStateFlow()
    private var aboutJob: Job? = null
    private var action: Job? = null
    private var cloudAction = false
    private var refreshJob: Job? = null
    private val reload = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            reload.collectLatest {
                budgets.observe().catch { failure ->
                    mutableState.update {
                        it.copy(
                            loading = false,
                            error = setupError(
                                failure as? Exception ?: Exception(failure)
                            )
                        )
                    }
                }.collect { rows ->
                    mutableState.update {
                        it.copy(
                            budgets = rows,
                            loading = false,
                            expandedBudgetId = it.expandedBudgetId?.takeIf { id ->
                                rows.any { row ->
                                    row.id ==
                                        id &&
                                        row.canManage
                                }
                            }
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            val mode =
                access?.mode
                    ?: kotlinx.coroutines.flow.flowOf(com.firkat.fillin.feature.appaccess.domain.AppMode.InternalFull)
            var previousSession: AuthSession? = null
            kotlinx.coroutines.flow.combine(auth.session, mode, ::Pair).collectLatest { (session, currentMode) ->
                val full = currentMode == com.firkat.fillin.feature.appaccess.domain.AppMode.InternalFull
                val signedIn = full && session is AuthSession.Authenticated
                if (session == AuthSession.Loading) return@collectLatest
                val sessionChanged = previousSession != null && previousSession != session
                previousSession = session
                if (sessionChanged || (!full && cloudAction)) action?.cancel()
                mutableState.update {
                    it.copy(expandedBudgetId = null, error = null, cloudAllowed = signedIn, fullAccess = full)
                }
                refresh()
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
            } catch (
                cancelled: CancellationException
            ) {
                throw cancelled
            } catch (
                failure: Exception
            ) {
                mutableState.update { it.copy(error = setupError(failure)) }
            }
        }
    }

    fun closeBudgetActions() {
        mutableState.update { it.copy(expandedBudgetId = null) }
    }

    fun openAbout() {
        aboutJob?.cancel()
        aboutJob = viewModelScope.launch {
            aboutId.value = kotlinx.coroutines.withContext(Dispatchers.IO) {
                runCatching(installationId).getOrDefault("")
            }
        }
    }

    fun closeAbout() {
        aboutJob?.cancel()
        aboutId.value = null
    }

    fun toggleBudgetActions(
        id: String
    ) {
        mutableState.update { state ->
            if (state.busy || state.budgets.none { it.id == id && it.canManage }) {
                state
            } else {
                state.copy(expandedBudgetId = id.takeUnless { state.expandedBudgetId == id })
            }
        }
    }

    fun selectBudget(
        id: String
    ) {
        closeBudgetActions()
        if (state.value.budgets.none { it.id == id && !it.selected }) return
        perform(requiresCloud = id.startsWith("cloud:")) { budgets.select(id) }
    }

    private fun perform(
        requiresCloud: Boolean = false,
        block: suspend () -> Unit
    ) {
        if (state.value.busy) return
        cloudAction = requiresCloud
        mutableState.update { it.copy(busy = true, error = null) }
        action = viewModelScope.launch {
            try {
                block()
            } catch (
                cancelled: CancellationException
            ) {
                throw cancelled
            } catch (
                failure: Exception
            ) {
                mutableState.update { it.copy(error = setupError(failure)) }
            } finally {
                cloudAction = false
                mutableState.update { it.copy(busy = false) }
            }
        }
    }
}
