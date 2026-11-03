package com.firkat.fillin.feature.sync.ui

import androidx.compose.foundation.layout.*
import android.content.res.Configuration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.R
import com.firkat.fillin.feature.budgetsetup.ui.setupError
import com.firkat.fillin.feature.sync.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.firkat.fillin.ui.theme.FillinTheme

class BudgetSyncViewModel(
    private val sync: BudgetSyncControl,
    private val session: CloudSyncSettings? = null,
) : ViewModel() {
    val context = sync.context.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BudgetSyncContext())
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    fun enable() = action { session?.enable() }

    fun disable() = action { session?.disable() }

    fun refresh() = sync.requestSync()

    private fun action(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                block()
                _error.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = setupError(e)
            } finally {
                _busy.value = false
            }
        }
    }
}

@Composable
fun BudgetSyncPanel(viewModel: BudgetSyncViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.context.collectAsStateWithLifecycle()
    BudgetSyncStatus(state.cloudEnabled, state.syncing, state.error != null, state.pending, modifier)
}

@Composable
private fun BudgetSyncStatus(cloudEnabled: Boolean, syncing: Boolean, hasError: Boolean, pending: Int, modifier: Modifier = Modifier) {
    if (cloudEnabled && (syncing || hasError || pending > 0)) {
        Text(
            stringResource(when {
                syncing -> R.string.cloud_sync_running
                hasError -> R.string.cloud_sync_failed
                else -> R.string.cloud_sync_pending
            }, pending),
            modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Preview(name = "Sync status hidden")
@Composable
private fun BudgetSyncStatusHiddenPreview() { FillinTheme { BudgetSyncStatus(false, false, false, 0) } }

@Preview(name = "Sync running")
@Preview(name = "Sync running dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BudgetSyncStatusRunningPreview() { FillinTheme { BudgetSyncStatus(true, true, false, 0) } }

@Preview(name = "Sync error", widthDp = 320, fontScale = 2f)
@Composable
private fun BudgetSyncStatusErrorPreview() { FillinTheme { BudgetSyncStatus(true, false, true, 0) } }

@Preview(name = "Pending sync", widthDp = 840)
@Composable
private fun BudgetSyncStatusPendingPreview() { FillinTheme { BudgetSyncStatus(true, false, false, 3) } }

@Composable
internal fun syncErrorText(code: String): String =
    when (code) {
        "sync_interrupted" -> "Предыдущая синхронизация прервана. Изменения сохранены; повторите синхронизацию."
        "budget_identity_conflict" -> "Бюджет с такими идентификаторами уже существует. Данные сохранены; обратитесь к разработчику."
        "budget_already_exists" -> "Токен уже связан с другим бюджетом. Локальный бюджет сохранён."
        "email_not_verified", "unauthorized", "access_denied", "scope_changed", "missing_api_token", "sign_in_required", "budget_not_found" -> setupError(BudgetConnectionFailure(code))
        "client_upgrade_required" -> stringResource(R.string.plan_link_client_upgrade)
        "backend_update_required" -> stringResource(R.string.plan_link_backend_upgrade)
        "pending_changes" -> "Не все изменения отправлены. Данные сохранены на устройстве."
        else -> "Не удалось синхронизировать. Данные сохранены на устройстве; повторите попытку."
    }
