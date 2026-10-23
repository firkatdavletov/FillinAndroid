package com.firkat.fillin.feature.budgetsharing.ui

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
import com.firkat.fillin.feature.budgetinvitation.domain.InvitationTarget
import com.firkat.fillin.feature.budgetsharing.domain.*
import com.firkat.fillin.feature.sync.domain.BudgetConnectionFailure
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.koin.compose.viewmodel.koinViewModel

data class FamilyJoinState(val session: AuthSession = AuthSession.Loading, val code: String = "", val busy: Boolean = false, val error: String? = null, val joinedName: String? = null)
class FamilyBudgetJoinViewModel(private val auth: AuthRepository, private val repository: FamilyBudgetRepository) : ViewModel() {
    private val _state = MutableStateFlow(FamilyJoinState())
    val state = _state.asStateFlow()
    private var action: Job? = null
    init { viewModelScope.launch { auth.session.collect { action?.cancel(); _state.value = FamilyJoinState(it) } } }
    fun editCode(code: String) { if (!state.value.busy && code.length <= 80) _state.update { it.copy(code = code, error = null, joinedName = null) } }
    fun join() {
        val uid = (state.value.session as? AuthSession.Authenticated)?.user?.uid ?: return
        if (state.value.busy || !current(uid)) return
        val code = try { FamilyInvitationCode.normalize(state.value.code) } catch (_: IllegalArgumentException) {
            _state.update { it.copy(error = "Введите код приглашения полностью: 20 букв и цифр.") }; return
        }
        _state.update { it.copy(busy = true, error = null, joinedName = null) }
        action = viewModelScope.launch {
            try {
                val budget = repository.join(uid, code)
                if (current(uid)) _state.update { it.copy(code = "", joinedName = budget.name) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (current(uid)) _state.update { it.copy(error = familyError(e)) } }
            finally { if (current(uid)) _state.update { it.copy(busy = false) } }
        }
    }
    private fun current(uid: String) = (auth.session.value as? AuthSession.Authenticated)?.user?.uid == uid &&
        (state.value.session as? AuthSession.Authenticated)?.user?.uid == uid
}

data class FamilyMembersState(val target: InvitationTarget? = null, val members: List<FamilyBudgetMember> = emptyList(), val busy: Boolean = false, val error: String? = null)
class FamilyMembersViewModel(private val repository: FamilyBudgetRepository) : ViewModel() {
    private val _state = MutableStateFlow(FamilyMembersState())
    val state = _state.asStateFlow()
    private var action: Job? = null
    init { viewModelScope.launch { repository.target.collect { target -> action?.cancel(); _state.value = FamilyMembersState(target); if (target != null) refresh() } } }
    fun refresh() = perform { target ->
        val members = repository.members(target)
        if (current(target)) _state.update { it.copy(members = members) }
    }
    fun revoke(member: FamilyBudgetMember) {
        if (state.value.target?.role != "OWNER" || member.role != "MEMBER" || member.currentUser || member !in state.value.members) return
        perform { target -> repository.revoke(target, member)
            val members = repository.members(target)
            if (current(target)) _state.update { it.copy(members = members) }
        }
    }
    private fun perform(block: suspend (InvitationTarget) -> Unit) {
        val target = state.value.target ?: return
        if (state.value.busy || !current(target)) return
        _state.update { it.copy(busy = true, error = null) }
        action = viewModelScope.launch {
            try { block(target) } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (current(target)) _state.update { it.copy(error = familyError(e), members = if ((e as? BudgetConnectionFailure)?.code == "access_denied") emptyList() else it.members) } }
            finally { if (current(target)) _state.update { it.copy(busy = false) } }
        }
    }
    private fun current(target: InvitationTarget) = state.value.target == target && repository.isCurrent(target)
}

@Composable
fun FamilyBudgetJoinSection(viewModel: FamilyBudgetJoinViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.session !is AuthSession.Authenticated) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.family_join_title), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(state.code, viewModel::editCode, enabled = !state.busy, singleLine = true,
            label = { Text(stringResource(R.string.family_join_code)) }, modifier = Modifier.fillMaxWidth())
        Button(onClick = viewModel::join, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.family_join)) }
        if (state.busy) CircularProgressIndicator()
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.joinedName?.let { Text(stringResource(R.string.family_joined, it)) }
    }
}

@Composable
fun FamilyMembersSection(viewModel: FamilyMembersViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val target = state.target ?: return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.family_members_title), style = MaterialTheme.typography.titleMedium)
        if (state.busy) CircularProgressIndicator()
        state.members.forEach { member ->
            Text(member.name + " · " + stringResource(if (member.role == "OWNER") R.string.cloud_budget_owner else R.string.cloud_budget_member) + if (member.currentUser) " · " + stringResource(R.string.family_you) else "")
            if (target.role == "OWNER" && member.role == "MEMBER" && !member.currentUser) {
                OutlinedButton(onClick = { viewModel.revoke(member) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.family_revoke, member.name))
                }
            }
        }
        TextButton(onClick = viewModel::refresh, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.family_members_refresh)) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

private fun familyError(error: Exception): String = when ((error as? BudgetConnectionFailure)?.code) {
    "invalid_invitation" -> "Код недействителен, истёк, отозван или уже использован другим аккаунтом. Попросите владельца создать новый."
    "already_member" -> "Вы уже владелец этого бюджета. Выберите его в списке бюджетов."
    "access_denied" -> "Доступ к бюджету отозван или у аккаунта недостаточно прав."
    "unauthorized", "account_changed" -> "Войдите в нужный Google-аккаунт ещё раз."
    "family_unavailable", "server_not_configured" -> "Сервис семейного доступа пока недоступен. Повторите позже."
    "invalid_response" -> "Не удалось проверить ответ сервера. Повторите запрос."
    else -> "Не удалось связаться с сервером. Данные сохранены; повторите запрос при подключении к интернету."
}
