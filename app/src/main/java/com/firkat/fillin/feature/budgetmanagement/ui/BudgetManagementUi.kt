@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.firkat.fillin.feature.budgetmanagement.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.R
import com.firkat.fillin.feature.auth.domain.AuthRepository
import com.firkat.fillin.feature.auth.domain.AuthSession
import com.firkat.fillin.feature.budgetmanagement.data.BudgetManagement
import com.firkat.fillin.feature.budgetmanagement.domain.validBudgetName
import com.firkat.fillin.ui.component.SaldomiBottomSheet
import com.firkat.fillin.ui.theme.FillinTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class BudgetManagementState(val id: String, val name: String, val deleting: Boolean, val value: String, val busy: Boolean = false, val error: Boolean = false)
class BudgetManagementViewModel(private val management: BudgetManagement, auth: AuthRepository) : ViewModel() {
    private val mutableState = MutableStateFlow<BudgetManagementState?>(null)
    val state = mutableState.asStateFlow()
    val pending = management.entries.combine(auth.session) { entries, session ->
        val uid = (session as? AuthSession.Authenticated)?.user?.uid
        entries.filter { (it.deleting || it.error == "access_denied") && (it.uid == null || it.uid == uid) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    init { viewModelScope.launch { auth.session.collect { mutableState.value = null } } }
    fun open(id: String, name: String, deleting: Boolean) { mutableState.value = BudgetManagementState(id, name, deleting, if (deleting) "" else name) }
    fun change(value: String) { mutableState.value?.takeUnless { it.busy }?.let { mutableState.value = it.copy(value = value, error = false) } }
    fun close() { if (mutableState.value?.busy != true) mutableState.value = null }
    fun retry() = management.retry()
    fun confirm() {
        val current = mutableState.value?.takeUnless { it.busy } ?: return
        if (current.deleting && current.value.trim() != current.name.trim() || !current.deleting && !validBudgetName(current.value)) return
        mutableState.value = current.copy(busy = true)
        viewModelScope.launch {
            try { management.submit(current.id, if (current.deleting) current.name else current.value, current.deleting); mutableState.value = null }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (mutableState.value?.id == current.id) mutableState.value = current.copy(error = true) }
        }
    }
}

@Composable
internal fun BudgetManagementSheet(state: BudgetManagementState, onChange: (String) -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    SaldomiBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(stringResource(if (state.deleting) R.string.budget_manage_delete else R.string.budget_manage_rename), style = MaterialTheme.typography.headlineSmall)
            if (state.deleting) {
                Text(state.name, style = MaterialTheme.typography.titleLarge)
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                    Text(stringResource(R.string.budget_manage_warning), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            OutlinedTextField(value = state.value, onValueChange = onChange, enabled = !state.busy, singleLine = true,
                label = { Text(stringResource(if (state.deleting) R.string.budget_manage_confirm_name else R.string.cloud_budget_name)) }, modifier = Modifier.fillMaxWidth().testTag("budget_management_input"))
            if (state.error) Text(stringResource(R.string.budget_manage_error), color = MaterialTheme.colorScheme.error)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = onDismiss, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.profile_delete_cancel)) }
                Button(onClick = onConfirm, modifier = Modifier.weight(1f).testTag("budget_management_confirm"),
                    enabled = !state.busy && if (state.deleting) state.value.trim() == state.name.trim() else validBudgetName(state.value),
                    colors = if (state.deleting) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()) {
                    Text(stringResource(if (state.deleting) R.string.budget_manage_delete else R.string.budget_manage_save))
                }
            }
        }
    }
}

@Composable
internal fun PendingBudgetDeletion(name: String, failed: Boolean, onRetry: () -> Unit, modifier: Modifier = Modifier, sending: Boolean = false, denied: Boolean = false) {
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(name, style = MaterialTheme.typography.titleMedium)
        Text(stringResource(if (denied) R.string.budget_manage_denied else if (sending) R.string.budget_manage_sending else if (failed) R.string.budget_manage_error else R.string.budget_manage_pending_delete))
        if (failed) TextButton(onClick = onRetry) { Text(stringResource(R.string.budget_profile_retry)) }
    }
}

@Preview(name = "Rename") @Preview(name = "Rename dark", uiMode = UI_MODE_NIGHT_YES)
@Preview(name = "Rename large", widthDp = 320, fontScale = 2f)
@Composable private fun RenamePreview() { FillinTheme { BudgetManagementSheet(BudgetManagementState("1", "Семейный", false, "Семейный"), {}, {}, {}) } }
@Preview(name = "Delete") @Preview(name = "Delete dark", uiMode = UI_MODE_NIGHT_YES)
@Composable private fun DeletePreview() { FillinTheme { BudgetManagementSheet(BudgetManagementState("1", "Семейный", true, ""), {}, {}, {}) } }
@Preview(name = "Delete confirmed", widthDp = 840)
@Composable private fun DeleteConfirmedPreview() { FillinTheme { BudgetManagementSheet(BudgetManagementState("1", "Семейный", true, "Семейный"), {}, {}, {}) } }
@Preview(name = "Saving")
@Composable private fun SavingPreview() { FillinTheme { BudgetManagementSheet(BudgetManagementState("1", "Семейный", false, "Дом", busy = true), {}, {}, {}) } }
@Preview(name = "Error")
@Composable private fun ErrorPreview() { FillinTheme { BudgetManagementSheet(BudgetManagementState("1", "Семейный", false, "", error = true), {}, {}, {}) } }
@Preview(name = "Pending deletion") @Preview(name = "Pending deletion dark", uiMode = UI_MODE_NIGHT_YES)
@Composable private fun PendingPreview() { FillinTheme { PendingBudgetDeletion("Семейный", false, {}) } }
@Preview(name = "Failed deletion") @Preview(name = "Failed deletion dark", uiMode = UI_MODE_NIGHT_YES)
@Composable private fun FailedPreview() { FillinTheme { PendingBudgetDeletion("Семейный", true, {}) } }
