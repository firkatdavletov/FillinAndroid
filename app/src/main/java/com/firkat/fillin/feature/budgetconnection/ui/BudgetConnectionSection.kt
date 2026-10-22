package com.firkat.fillin.feature.budgetconnection.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.R
import com.firkat.fillin.feature.auth.domain.*
import com.firkat.fillin.feature.budgetconnection.domain.*
import com.firkat.fillin.feature.budgetsetup.ui.setupError
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.koin.compose.viewmodel.koinViewModel
import java.time.LocalDate
import java.time.ZoneId

data class BudgetConnectionState(
    val session: AuthSession = AuthSession.Loading,
    val budgets: List<AvailableBudget> = emptyList(),
    val loading: Boolean = false,
    val connecting: String? = null,
    val loaded: Boolean = false,
    val error: String? = null,
    val draft: BudgetCreationDraft? = null,
    val pending: PendingBudgetCreation? = null,
    val creating: Boolean = false,
) {
    val busy get() = loading || connecting != null || creating
}

class BudgetConnectionViewModel(
    private val auth: AuthRepository,
    private val repository: BudgetConnectionRepository,
    private val creation: BudgetCreationRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(BudgetConnectionState())
    val state = _state.asStateFlow()
    private var action: Job? = null

    init {
        viewModelScope.launch {
            auth.session.distinctUntilChanged { old, new -> old == new }.collectLatest { session ->
                action?.cancel()
                _state.value = BudgetConnectionState(session)
                (session as? AuthSession.Authenticated)?.let { load(it.user.uid) }
            }
        }
    }

    fun refresh() {
        val uid = uid() ?: return
        if (state.value.busy) return
        action = viewModelScope.launch { load(uid) }
    }

    fun chooseCreate() {
        if (uid() == null || state.value.busy || !state.value.loaded || state.value.budgets.isNotEmpty()) return
        _state.update { it.copy(draft = BudgetCreationDraft("", "0", LocalDate.now(ZoneId.of("Asia/Almaty")).toString()), error = null) }
    }

    fun editDraft(draft: BudgetCreationDraft) {
        if (!state.value.busy && state.value.pending == null) _state.update { it.copy(draft = draft, error = null) }
    }

    fun cancelCreate() {
        if (!state.value.busy && state.value.pending == null) _state.update { it.copy(draft = null, error = null) }
    }

    fun create() {
        val uid = uid() ?: return
        val draft = state.value.draft ?: return
        if (state.value.busy) return
        _state.update { it.copy(creating = true, error = null) }
        action = viewModelScope.launch {
            try {
                if (state.value.pending != null) creation.resume(uid) else creation.create(uid, draft)
                if (current(uid)) {
                    _state.update { it.copy(pending = null, draft = null) }
                    load(uid)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (current(uid)) {
                    val pending = try { creation.pending(uid) } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { state.value.pending }
                    if (current(uid)) _state.update { it.copy(pending = pending, draft = pending?.draft ?: draft, error = creationError(e)) }
                }
            } finally { if (current(uid)) _state.update { it.copy(creating = false) } }
        }
    }

    fun connect(budget: AvailableBudget) {
        val uid = uid() ?: return
        if (state.value.busy || state.value.pending != null || budget !in state.value.budgets) return
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
            val pending = creation.pending(uid)
            if (current(uid)) _state.update { it.copy(pending = pending, draft = pending?.draft ?: it.draft) }
            val budgets = repository.budgets(uid)
            if (current(uid)) _state.update { it.copy(budgets = budgets, loaded = true) }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (current(uid)) _state.update { it.copy(error = setupError(e)) } }
        finally { if (current(uid)) _state.update { it.copy(loading = false) } }
    }

    private fun uid() = (auth.session.value as? AuthSession.Authenticated)?.user?.uid
        ?.takeIf { it == (state.value.session as? AuthSession.Authenticated)?.user?.uid }
    private fun current(uid: String) = uid() == uid
    private fun creationError(error: Exception) = if (error is IllegalArgumentException || error is java.time.DateTimeException)
        "Проверьте название (до 80 символов), баланс (до двух знаков после запятой) и дату начала учёта." else setupError(error)
}

@Composable
fun BudgetConnectionSection(viewModel: BudgetConnectionViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.cloud_budgets_title), style = MaterialTheme.typography.titleMedium)
        when (state.session) {
            is AuthSession.Authenticated -> {
                if (state.busy) CircularProgressIndicator()
                val draft = state.draft
                if (draft != null) {
                    val editable = !state.busy && state.pending == null
                    Text(stringResource(R.string.cloud_budget_create_title), style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(draft.name, { viewModel.editDraft(draft.copy(name = it)) }, label = { Text(stringResource(R.string.cloud_budget_name)) }, enabled = editable, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(draft.openingBalance, { viewModel.editDraft(draft.copy(openingBalance = it)) }, label = { Text(stringResource(R.string.cloud_budget_opening_balance)) }, enabled = editable, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(draft.accountingStartedOn, { viewModel.editDraft(draft.copy(accountingStartedOn = it)) }, label = { Text(stringResource(R.string.cloud_budget_start_date)) }, enabled = editable, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(if (state.pending == null) R.string.cloud_budget_create_explanation else R.string.cloud_budget_create_pending))
                    Button(onClick = viewModel::create, enabled = !state.busy) { Text(stringResource(if (state.pending == null) R.string.cloud_budget_create else R.string.cloud_budget_create_resume)) }
                    if (state.pending == null) TextButton(onClick = viewModel::cancelCreate, enabled = !state.busy) { Text(stringResource(R.string.cloud_budget_create_cancel)) }
                } else {
                    if (state.loaded && state.budgets.isEmpty()) {
                        Text(stringResource(R.string.cloud_budgets_empty))
                        Button(onClick = viewModel::chooseCreate, enabled = !state.busy) { Text(stringResource(R.string.cloud_budget_create)) }
                    }
                    state.budgets.forEach { budget ->
                        OutlinedButton(onClick = { viewModel.connect(budget) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                            Text(budget.name + " · " + stringResource(if (budget.role == "OWNER") R.string.cloud_budget_owner else R.string.cloud_budget_member))
                        }
                    }
                }
                TextButton(onClick = viewModel::refresh, enabled = !state.busy) { Text(stringResource(R.string.cloud_budgets_refresh)) }
            }
            else -> Text(stringResource(R.string.cloud_budgets_sign_in))
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
