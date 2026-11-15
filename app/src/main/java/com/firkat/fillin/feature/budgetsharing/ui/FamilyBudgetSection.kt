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
import com.firkat.fillin.ui.component.SaldomiButton
import com.firkat.fillin.ui.component.SaldomiProgressIndicator
import com.firkat.fillin.ui.component.SaldomiTextField
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.koin.compose.viewmodel.koinViewModel

data class FamilyMembersState(
    val target: InvitationTarget? = null,
    val members: List<FamilyBudgetMember> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null
)
class FamilyMembersViewModel(private val repository: FamilyBudgetRepository) : ViewModel() {
    private val _state = MutableStateFlow(FamilyMembersState())
    val state = _state.asStateFlow()
    private var action: Job? = null
    init {
        viewModelScope.launch {
            repository.target.collect { target ->
                action?.cancel()
                _state.value =
                    FamilyMembersState(target)
                if (target != null) refresh()
            }
        }
    }
    fun refresh() = perform { target ->
        val members = repository.members(target)
        if (current(target)) _state.update { it.copy(members = members) }
    }
    fun revoke(
        member: FamilyBudgetMember
    ) {
        if (state.value.target?.role != "OWNER" ||
            member.role != "MEMBER" ||
            member.currentUser ||
            member !in state.value.members
        ) {
            return
        }
        perform { target ->
            repository.revoke(target, member)
            val members = repository.members(target)
            if (current(target)) _state.update { it.copy(members = members) }
        }
    }
    private fun perform(
        block: suspend (InvitationTarget) -> Unit
    ) {
        val target = state.value.target ?: return
        if (state.value.busy || !current(target)) return
        _state.update { it.copy(busy = true, error = null) }
        action = viewModelScope.launch {
            try {
                block(target)
            } catch (
                e: CancellationException
            ) {
                throw e
            } catch (e: Exception) {
                if (current(target)) {
                    _state.update {
                        it.copy(
                            error = familyError(e),
                            members = if ((e as? BudgetConnectionFailure)?.code ==
                                "access_denied"
                            ) {
                                emptyList()
                            } else {
                                it.members
                            }
                        )
                    }
                }
            } finally {
                if (current(target)) _state.update { it.copy(busy = false) }
            }
        }
    }
    private fun current(
        target: InvitationTarget
    ) = state.value.target == target && repository.isCurrent(target)
}

@Composable
fun FamilyBudgetJoinSection(
    viewModel: FamilyBudgetJoinViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    FamilyBudgetJoinContent(
        state.session,
        state.code,
        state.busy,
        state.error,
        state.joinedName,
        onCodeChange = { viewModel.onAction(FamilyJoinAction.EditCode(it)) },
        onJoinClick = { viewModel.onAction(FamilyJoinAction.Join) }
    )
}

@Composable
internal fun FamilyBudgetJoinContent(
    session: AuthSession,
    code: String,
    busy: Boolean,
    error: String?,
    joinedName: String?,
    onCodeChange: (String) -> Unit,
    onJoinClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (session !is AuthSession.Authenticated) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.family_join_title), style = MaterialTheme.typography.titleMedium)
        SaldomiTextField(
            code,
            onCodeChange,
            enabled = !busy,
            label = stringResource(R.string.family_join_code),
            modifier = Modifier.fillMaxWidth()
        )
        SaldomiButton(onClick = onJoinClick, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.family_join))
        }
        if (busy) SaldomiProgressIndicator()
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        joinedName?.let { Text(stringResource(R.string.family_joined, it)) }
    }
}

@Composable
fun FamilyMembersSection(
    viewModel: FamilyMembersViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val target = state.target ?: return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.family_members_title), style = MaterialTheme.typography.titleMedium)
        if (state.busy) CircularProgressIndicator()
        state.members.forEach { member ->
            Text(
                member.name + " · " +
                    stringResource(
                        if (member.role ==
                            "OWNER"
                        ) {
                            R.string.cloud_budget_owner
                        } else {
                            R.string.cloud_budget_member
                        }
                    ) +
                    if (member.currentUser) " · " + stringResource(R.string.family_you) else ""
            )
            if (target.role == "OWNER" && member.role == "MEMBER" && !member.currentUser) {
                OutlinedButton(onClick = {
                    viewModel.revoke(member)
                }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.family_revoke, member.name))
                }
            }
        }
        TextButton(onClick = viewModel::refresh, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.family_members_refresh))
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

internal fun familyError(
    error: Exception
): String = when ((error as? BudgetConnectionFailure)?.code) {
    "invalid_invitation" -> "Код недействителен, истёк, отозван или уже использован другим аккаунтом. Попросите владельца создать новый."
    "already_member" -> "Вы уже владелец этого бюджета. Выберите его в списке бюджетов."
    "access_denied" -> "Доступ к бюджету отозван или у аккаунта недостаточно прав."
    "unauthorized", "account_changed" -> "Войдите в нужный Google-аккаунт ещё раз."
    "family_unavailable", "server_not_configured" -> "Сервис семейного доступа пока недоступен. Повторите позже."
    "invalid_response" -> "Не удалось проверить ответ сервера. Повторите запрос."
    else -> "Не удалось связаться с сервером. Данные сохранены; повторите запрос при подключении к интернету."
}
