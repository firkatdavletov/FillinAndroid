package com.firkat.fillin.feature.budgetconnection.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.R
import com.firkat.fillin.feature.auth.domain.*
import com.firkat.fillin.feature.budgetconnection.domain.*
import com.firkat.fillin.feature.budgetsetup.ui.setupError
import com.firkat.fillin.feature.sync.domain.BudgetConnectionFailure
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.koin.compose.viewmodel.koinViewModel

data class BudgetConnectionState(
    val session: AuthSession = AuthSession.Loading,
    val budgets: List<AvailableBudget> = emptyList(),
    val loading: Boolean = false,
    val connecting: String? = null,
    val loaded: Boolean = false,
    val error: String? = null,
)

class BudgetConnectionViewModel(private val auth: AuthRepository, private val repository: BudgetConnectionRepository) : ViewModel() {
    private val _state = MutableStateFlow(BudgetConnectionState())
    val state = _state.asStateFlow()
    private var action: Job? = null

    init {
        viewModelScope.launch {
            auth.session.map { it }.distinctUntilChanged().collectLatest { session ->
                action?.cancel()
                _state.value = BudgetConnectionState(session)
                (session as? AuthSession.Authenticated)?.let { load(it.user.uid) }
            }
        }
    }

    fun refresh() {
        val uid = (_state.value.session as? AuthSession.Authenticated)?.user?.uid ?: return
        if (_state.value.loading || _state.value.connecting != null) return
        action = viewModelScope.launch { load(uid) }
    }

    fun connect(budget: AvailableBudget) {
        val uid = (_state.value.session as? AuthSession.Authenticated)?.user?.uid ?: return
        if (_state.value.loading || _state.value.connecting != null || budget !in _state.value.budgets) return
        _state.update { it.copy(connecting = budget.id, error = null) }
        action = viewModelScope.launch {
            try { repository.connect(uid, budget) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (current(uid)) _state.update { it.copy(error = setupError(e)) } }
            finally { if (current(uid)) _state.update { it.copy(connecting = null) } }
        }
    }

    private suspend fun load(uid: String) {
        _state.update { it.copy(loading = true, error = null) }
        try {
            val budgets = repository.budgets(uid)
            if (current(uid)) _state.update { it.copy(budgets = budgets, loaded = true) }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (current(uid)) _state.update { it.copy(error = setupError(e)) } }
        finally { if (current(uid)) _state.update { it.copy(loading = false) } }
    }

    private fun current(uid: String) = (auth.session.value as? AuthSession.Authenticated)?.user?.uid == uid
}

@Composable
fun BudgetConnectionSection(viewModel: BudgetConnectionViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.cloud_budgets_title), style = MaterialTheme.typography.titleMedium)
        when (state.session) {
            is AuthSession.Authenticated -> {
                if (state.loading || state.connecting != null) CircularProgressIndicator()
                if (state.loaded && state.budgets.isEmpty()) Text(stringResource(R.string.cloud_budgets_empty))
                state.budgets.forEach { budget ->
                    OutlinedButton(onClick = { viewModel.connect(budget) }, enabled = !state.loading && state.connecting == null, modifier = Modifier.fillMaxWidth()) {
                        Text(budget.name + " · " + stringResource(if (budget.role == "OWNER") R.string.cloud_budget_owner else R.string.cloud_budget_member))
                    }
                }
                TextButton(onClick = viewModel::refresh, enabled = !state.loading && state.connecting == null) { Text(stringResource(R.string.cloud_budgets_refresh)) }
            }
            else -> Text(stringResource(R.string.cloud_budgets_sign_in))
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
