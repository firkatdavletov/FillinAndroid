package com.firkat.fillin.feature.budgetinvitation.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.PersistableBundle
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.R
import com.firkat.fillin.feature.budgetinvitation.domain.*
import com.firkat.fillin.feature.sync.domain.BudgetConnectionFailure
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.koin.compose.viewmodel.koinViewModel
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class BudgetInvitationState(
    val target: InvitationTarget? = null,
    val current: BudgetInvitation? = null,
    val code: String? = null,
    val busy: Boolean = false,
    val copied: Boolean = false,
    val expired: Boolean = false,
    val error: String? = null,
)

class BudgetInvitationViewModel(
    private val repository: BudgetInvitationRepository,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {
    private val _state = MutableStateFlow(BudgetInvitationState())
    val state = _state.asStateFlow()
    private var action: Job? = null
    private var expiry: Job? = null

    init {
        viewModelScope.launch {
            repository.target.collect { target ->
                action?.cancel(); expiry?.cancel()
                _state.value = BudgetInvitationState(target)
                if (target?.role == "OWNER") refresh()
            }
        }
    }

    fun refresh() = perform { target ->
        val current = repository.current(target)
        publish(target, current, state.value.code.takeIf { current?.id == state.value.current?.id })
    }

    fun create() = perform { target ->
        // The old invitation may already have been replaced if the response is lost.
        expiry?.cancel()
        _state.update { it.copy(code = null, current = null, copied = false, expired = false) }
        val created = repository.create(target)
        publish(target, created.invitation, created.code)
    }

    fun revoke() {
        val invitation = state.value.current ?: return
        perform { target ->
            _state.update { it.copy(code = null, copied = false) }
            repository.revoke(target, invitation)
            publish(target, null, null)
        }
    }

    fun copyCode(copy: (String) -> Unit) {
        val target = state.value.target ?: return
        val invitation = state.value.current ?: return
        val code = state.value.code ?: return
        if (state.value.busy || !repository.isCurrent(target) || target.role != "OWNER") return
        if (!Instant.parse(invitation.expiresAt).isAfter(clock.instant())) { expire(target); return }
        copy(code)
        _state.update { it.copy(copied = true) }
    }

    private fun perform(block: suspend (InvitationTarget) -> Unit) {
        val target = state.value.target ?: return
        if (target.role != "OWNER" || state.value.busy || !repository.isCurrent(target)) return
        _state.update { it.copy(busy = true, error = null, copied = false) }
        action = viewModelScope.launch {
            try { block(target) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (current(target)) _state.update { it.copy(error = invitationError(e)) }
            } finally { if (current(target)) _state.update { it.copy(busy = false) } }
        }
    }

    private fun publish(target: InvitationTarget, invitation: BudgetInvitation?, code: String?) {
        if (!current(target)) return
        expiry?.cancel()
        val remaining = invitation?.let { Duration.between(clock.instant(), Instant.parse(it.expiresAt)).toMillis() }
        if (remaining != null && remaining <= 0) { expire(target); return }
        _state.update { it.copy(current = invitation, code = code, expired = false, copied = false) }
        if (remaining != null) expiry = viewModelScope.launch { delay(remaining); expire(target) }
    }

    private fun expire(target: InvitationTarget) {
        if (current(target)) _state.update { it.copy(current = null, code = null, copied = false, expired = true) }
    }
    private fun current(target: InvitationTarget) = state.value.target == target && repository.isCurrent(target)
    private fun invitationError(error: Exception) = when ((error as? BudgetConnectionFailure)?.code) {
        "access_denied" -> "Приглашениями может управлять только владелец бюджета. Обновите список бюджетов."
        "unauthorized", "account_changed" -> "Войдите в Google-аккаунт этого бюджета ещё раз."
        "invitation_unavailable", "server_not_configured" -> "Сервис приглашений пока недоступен. Повторите позже."
        "invalid_response" -> "Не удалось проверить приглашение. Обновите статус или создайте новый код."
        else -> "Не удалось получить ответ сервера. Повторите действие при подключении к интернету."
    }
}

@Composable
fun BudgetInvitationSection(viewModel: BudgetInvitationViewModel = koinViewModel(), copy: ((String) -> Unit)? = null) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val target = state.target ?: return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.invitation_title), style = MaterialTheme.typography.titleMedium)
        if (target.role != "OWNER") {
            Text(stringResource(R.string.invitation_owner_only))
        } else {
            Text(stringResource(R.string.invitation_explanation))
            if (state.busy) CircularProgressIndicator()
            val current = state.current
            if (current != null) {
                val deadline = remember(current.expiresAt) { DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(current.expiresAt)) }
                Text(stringResource(R.string.invitation_expires, deadline))
                if (state.code != null) {
                    Text(state.code!!, style = MaterialTheme.typography.titleLarge)
                    Button(onClick = { viewModel.copyCode { code ->
                        if (copy != null) copy(code) else {
                            val clip = ClipData.newPlainText("Fillin family invitation", code)
                            clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
                            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
                        }
                    } }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.invitation_copy)) }
                } else Text(stringResource(R.string.invitation_code_hidden))
                OutlinedButton(onClick = viewModel::revoke, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.invitation_revoke)) }
            }
            if (state.expired) Text(stringResource(R.string.invitation_expired))
            if (state.copied) Text(stringResource(R.string.invitation_copied))
            Button(onClick = viewModel::create, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.invitation_create)) }
            TextButton(onClick = viewModel::refresh, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.invitation_refresh)) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
