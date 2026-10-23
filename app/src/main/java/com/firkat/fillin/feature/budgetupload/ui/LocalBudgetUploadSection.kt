package com.firkat.fillin.feature.budgetupload.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.R
import com.firkat.fillin.feature.auth.domain.*
import com.firkat.fillin.feature.budgetupload.domain.*
import com.firkat.fillin.feature.budgetsetup.ui.setupError
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.koin.compose.viewmodel.koinViewModel

data class LocalBudgetUploadState(val session: AuthSession = AuthSession.Loading, val budgets: List<LocalBudgetCandidate> = emptyList(), val busy: Boolean = false, val error: String? = null)

class LocalBudgetUploadViewModel(private val auth: AuthRepository, private val repository: LocalBudgetUploadRepository) : ViewModel() {
    private val local = MutableStateFlow(LocalBudgetUploadState())
    val state = combine(auth.session, local) { session, state -> state.copy(session = session) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LocalBudgetUploadState(auth.session.value))
    init {
        viewModelScope.launch {
            repository.candidates.catch { error -> local.update { it.copy(error = message(error)) } }
                .collect { rows -> local.update { it.copy(budgets = rows) } }
        }
    }
    fun connect(profileId: String, name: String) {
        val uid = (auth.session.value as? AuthSession.Authenticated)?.user?.uid ?: return
        if (local.value.busy || state.value.budgets.none { it.profileId == profileId && (it.firebaseUid == null || it.firebaseUid == uid) }) return
        local.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try { repository.connect(uid, profileId, name) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { local.update { it.copy(error = message(e)) } }
            finally { local.update { it.copy(busy = false) } }
        }
    }
    private fun message(error: Throwable) = when (error) {
        is IllegalArgumentException -> "Укажите название бюджета от 1 до 80 символов."
        is Exception -> setupError(error)
        else -> "Не удалось подключить бюджет. Данные сохранены на устройстве."
    }
}

@Composable
fun LocalBudgetUploadSection(onSignIn: () -> Unit, viewModel: LocalBudgetUploadViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.budgets.filter { it.firebaseUid == null || it.firebaseUid == (state.session as? AuthSession.Authenticated)?.user?.uid }.forEach { budget ->
            key(budget.profileId) {
                var name by rememberSaveable { mutableStateOf(budget.name) }
                Text(stringResource(R.string.local_budget_upload_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(if (budget.connecting) R.string.local_budget_upload_pending else R.string.local_budget_upload_hint))
                if (!budget.connecting) OutlinedTextField(name, { name = it }, enabled = !state.busy, label = { Text(stringResource(R.string.cloud_budget_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (budget.syncing || state.busy) CircularProgressIndicator()
                Button(onClick = {
                    if (state.session is AuthSession.Authenticated) viewModel.connect(budget.profileId, name) else onSignIn()
                }, enabled = !state.busy && !budget.syncing && state.session != AuthSession.Loading && state.session != AuthSession.Unavailable, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (budget.connecting) R.string.local_budget_upload_resume else R.string.local_budget_upload_connect))
                }
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
