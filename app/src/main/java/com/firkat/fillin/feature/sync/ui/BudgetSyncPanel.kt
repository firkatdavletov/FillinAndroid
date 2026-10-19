package com.firkat.fillin.feature.sync.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.R
import com.firkat.fillin.feature.budgetsetup.ui.setupError
import com.firkat.fillin.feature.sync.domain.*
import com.firkat.fillin.ui.component.FeatureScaffold
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class BudgetSyncViewModel(
    private val sync: BudgetSyncControl,
    private val session: CloudSyncSettings? = null,
) : ViewModel() {
    val context = sync.context.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BudgetSyncContext())
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun enable() = action { session?.enable() }

    fun disable() = action { session?.disable() }

    fun refresh() = sync.requestSync()

    private fun action(block: suspend () -> Unit) =
        viewModelScope.launch {
            try {
                block()
                _error.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = setupError(e)
            }
        }
}

@Composable
fun BudgetSyncPanel(viewModel: BudgetSyncViewModel) {
    val state by viewModel.context.collectAsStateWithLifecycle()
    if (state.cloudEnabled && (state.syncing || state.error != null || state.pending > 0)) {
        Text(
            when {
                state.syncing -> "Синхронизация…"
                state.error != null -> "Ошибка синхронизации. Данные сохранены на устройстве."
                else -> "Ожидают синхронизации: ${state.pending}"
            },
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
fun BudgetSyncScreen(viewModel: BudgetSyncViewModel = koinViewModel()) {
    val state by viewModel.context.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    FeatureScaffold(R.string.sync_title) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                when {
                    state.syncing -> "Синхронизация…"
                    state.cloudEnabled -> "Облачная синхронизация включена"
                    else -> "Облачная синхронизация выключена"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Text("Ожидают синхронизации: ${state.pending}")
            Text(
                "Последняя успешная синхронизация: " + (
                    state.lastSyncedAt?.let {
                        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.of("Asia/Almaty")).format(Instant.parse(it))
                    } ?: "Ещё не выполнялась"
                ),
            )
            Button(onClick = { viewModel.enable() }, enabled = state.ready && !state.cloudEnabled && !state.syncing) { Text("Включить облачную синхронизацию") }
            OutlinedButton(onClick = { viewModel.disable() }, enabled = state.cloudEnabled) { Text("Отключить облачную синхронизацию") }
            OutlinedButton(onClick = viewModel::refresh, enabled = state.cloudEnabled && !state.syncing) { Text("Синхронизировать") }
            state.error?.let { code -> Text(syncErrorText(code), color = MaterialTheme.colorScheme.error) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.issues.forEach { Text("${it.title}: ${it.local}\nОшибка отправки. Проверьте данные записи.", color = MaterialTheme.colorScheme.error) }
        }
    }
}

private fun syncErrorText(code: String): String =
    when (code) {
        "budget_already_exists" -> "Токен уже связан с другим бюджетом. Локальный бюджет сохранён."
        "unauthorized", "access_denied", "scope_changed", "missing_api_token", "budget_not_found" -> setupError(BudgetConnectionFailure(code))
        "pending_changes" -> "Не все изменения отправлены. Данные сохранены на устройстве."
        else -> "Не удалось синхронизировать. Данные сохранены на устройстве; повторите попытку."
    }
