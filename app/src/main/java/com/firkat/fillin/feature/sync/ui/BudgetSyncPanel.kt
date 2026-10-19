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
import com.firkat.fillin.feature.sync.domain.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class BudgetSyncViewModel(
    private val sync: BudgetSyncControl,
) : ViewModel() {
    val context = sync.context.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BudgetSyncContext())
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun resolve(
        id: String,
        reapply: Boolean,
    ) {
        viewModelScope.launch {
            try {
                sync.resolve(id, reapply)
                _error.value = null
            } catch (
                e: kotlinx.coroutines.CancellationException,
            ) {
                throw e
            } catch (e: Exception) {
                _error.value =
                    "Не удалось применить решение. Проверьте, что объект доступен и значения допустимы."
            }
        }
    }

    fun refresh() = sync.requestSync()
}

@Composable
fun BudgetSyncPanel(viewModel: BudgetSyncViewModel) {
    val state by viewModel.context.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(
                max = 220.dp,
            ).verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        val text =
            when {
                !state.ready -> "Для работы офлайн нужна первая полная синхронизация"

                state.syncing -> "Синхронизация…"

                state.error in
                    setOf(
                        "unauthorized",
                        "access_denied",
                        "scope_changed",
                    )
                -> "Доступ к бюджету изменился. Синхронизация приостановлена"

                state.error != null -> "Не удалось синхронизировать. Изменения сохранены на устройстве"

                state.pending > 0 -> "Ожидают синхронизации: ${state.pending}"

                else -> null
            }
        text?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (!state.ready ||
            state.error != null
        ) {
            TextButton(onClick = viewModel::refresh) { Text("Повторить синхронизацию") }
        }
        if (state.pendingActions.isNotEmpty()) {
            TextButton(onClick = {
                expanded = !expanded
            }) { Text(if (expanded) "Скрыть изменения" else "Показать ожидающие изменения") }
            if (expanded) state.pendingActions.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        state.issues.forEach { issue ->
            TextButton(onClick = { selected = issue.requestId }) {
                Text(
                    "${issue.title}: ${if (issue.status == "CONFLICT") {
                        "конфликт"
                    } else if (issue.status == "BLOCKED") {
                        "ожидает связанное изменение"
                    } else {
                        "ошибка отправки"
                    }}",
                )
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
    state.issues.firstOrNull { it.requestId == selected }?.let { issue ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(issue.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("На устройстве: ${issue.local}")
                    Text("На сервере: ${issue.server}")
                    if (!issue.canReapply) {
                        Text(
                            "Изменение нельзя повторить автоматически. Можно принять серверное состояние и создать новую запись.",
                        )
                    }
                }
            },
            confirmButton = {
                if (issue.canReapply) {
                    TextButton(onClick = {
                        viewModel.resolve(issue.requestId, true)
                        selected =
                            null
                    }) { Text("Применить мою правку") }
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        viewModel.resolve(issue.requestId, false)
                        selected = null
                    }) { Text("Принять серверную") }
                    TextButton(onClick = { selected = null }) { Text("Позже") }
                }
            },
        )
    }
}
