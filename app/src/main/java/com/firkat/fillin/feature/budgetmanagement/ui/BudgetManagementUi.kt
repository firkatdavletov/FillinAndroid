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
import com.firkat.fillin.feature.budgetprofile.domain.BudgetCurrency
import com.firkat.fillin.feature.budgetprofile.ui.BudgetCurrencyRow
import com.firkat.fillin.feature.auth.domain.AuthRepository
import com.firkat.fillin.feature.auth.domain.AuthSession
import com.firkat.fillin.feature.budgetmanagement.data.BudgetManagement
import com.firkat.fillin.feature.budgetmanagement.domain.validBudgetName
import com.firkat.fillin.ui.component.*
import com.firkat.fillin.ui.icon.FillinIcons
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import com.firkat.fillin.ui.theme.FillinTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class BudgetManagementState(val id: String, val name: String, val deleting: Boolean, val value: String, val busy: Boolean = false, val error: Boolean = false, val family: Boolean = false,
    val originalCurrency: BudgetCurrency = BudgetCurrency.KZT,
    val currency: BudgetCurrency = originalCurrency,
    val pickerOpen: Boolean = false,
    val confirmCurrency: Boolean = false,
)
class BudgetManagementViewModel(private val management: BudgetManagement, auth: AuthRepository) : ViewModel() {
    private val mutableState = MutableStateFlow<BudgetManagementState?>(null)
    val state = mutableState.asStateFlow()
    val pending = management.entries.combine(auth.session) { entries, session ->
        val uid = (session as? AuthSession.Authenticated)?.user?.uid
        entries.filter { (it.deleting || it.error == "access_denied") && (it.uid == null || it.uid == uid) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    init { viewModelScope.launch { auth.session.collect { mutableState.value = null } } }
    fun open(id: String, name: String, deleting: Boolean, family: Boolean = false, currency: BudgetCurrency = BudgetCurrency.KZT) {
        mutableState.value = BudgetManagementState(id, name, deleting, if (deleting) "" else name, family = family, originalCurrency = currency)
    }
    fun openCurrencyPicker() = mutableState.update { it?.let { state -> if (state.busy || state.deleting) state else state.copy(pickerOpen = true) } }
    fun closeCurrencyPicker() = mutableState.update { it?.copy(pickerOpen = false) }
    fun selectCurrency(currency: BudgetCurrency) = mutableState.update { it?.copy(currency = currency, pickerOpen = false, error = false) }
    fun dismissCurrencyConfirmation() = mutableState.update { it?.copy(confirmCurrency = false) }
    fun confirmCurrencyChange() = confirm(allowCurrencyChange = true)
    fun change(value: String) { mutableState.value?.takeUnless { it.busy }?.let { mutableState.value = it.copy(value = value, error = false) } }
    fun close() { if (mutableState.value?.busy != true) mutableState.value = null }
    fun retry() = management.retry()
    fun confirm() = confirm(allowCurrencyChange = false)

    private fun confirm(allowCurrencyChange: Boolean) {
        val current = mutableState.value?.takeUnless { it.busy } ?: return
        if (current.deleting && current.value.trim() != current.name.trim() || !current.deleting && !validBudgetName(current.value)) return
        val newCurrency = current.currency.takeUnless { current.deleting || it == current.originalCurrency }
        if (newCurrency != null && !allowCurrencyChange) {
            mutableState.value = current.copy(confirmCurrency = true)
            return
        }
        mutableState.value = current.copy(busy = true, confirmCurrency = false)
        viewModelScope.launch {
            try {
                management.submit(current.id, if (current.deleting) current.name else current.value,
                    current.deleting, newCurrency, rename = current.value.trim() != current.name)
                if (mutableState.value?.id == current.id) mutableState.value = null
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (mutableState.value?.id == current.id) mutableState.value = current.copy(error = true, confirmCurrency = false) }
        }
    }
}

@Composable
internal fun BudgetManagementSheet(state: BudgetManagementState, onChange: (String) -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier, onCurrencyClick: () -> Unit = {}) {
    SaldomiBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (state.deleting) {
                Surface(modifier = Modifier.align(Alignment.CenterHorizontally), color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.large) {
                    Icon(Icons.Outlined.DeleteOutline, null, Modifier.padding(20.dp).size(36.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
            Text(stringResource(if (state.deleting) R.string.budget_manage_delete_title else R.string.budget_manage_rename), style = MaterialTheme.typography.headlineSmall)
            if (!state.deleting) Text(stringResource(R.string.budget_manage_edit_description), style = MaterialTheme.typography.bodyMedium)
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(if (state.family) FillinIcons.FamilyAccess else FillinIcons.Wallet, null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(state.name, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(if (state.family) R.string.profile_family_budget else R.string.profile_personal_budget, state.originalCurrency.name), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (state.deleting) {
                Text(stringResource(R.string.budget_manage_warning), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            SaldomiTextField(value = state.value, onValueChange = onChange, enabled = !state.busy,
                label = stringResource(if (state.deleting) R.string.budget_manage_confirm_name else R.string.cloud_budget_name), modifier = Modifier.fillMaxWidth().testTag("budget_management_input"))
            if (!state.deleting) {
                ListItem(headlineContent = { Text(stringResource(R.string.budget_manage_type)) }, trailingContent = { Text(stringResource(if (state.family) R.string.budget_manage_family else R.string.budget_manage_personal)) })
                BudgetCurrencyRow(state.currency, !state.busy, onCurrencyClick, Modifier.testTag("budget_management_currency"))
            }
            if (state.error) Text(stringResource(R.string.budget_manage_error), color = MaterialTheme.colorScheme.error)
            SaldomiButton(onClick = onConfirm, modifier = Modifier.fillMaxWidth().testTag("budget_management_confirm"), large = true, isLoading = state.busy, destructive = state.deleting,
                enabled = !state.busy && if (state.deleting) state.value.trim() == state.name.trim() else validBudgetName(state.value)) {
                Text(stringResource(if (state.deleting) R.string.budget_manage_delete else R.string.budget_manage_save))
            }
            SaldomiOutlinedButton(onClick = onDismiss, enabled = !state.busy, large = true, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.profile_delete_cancel)) }
        }
    }
}

@Composable
internal fun BudgetCurrencyConfirmation(
    from: BudgetCurrency,
    to: BudgetCurrency,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.budget_currency_change_title)) },
        text = { Text(stringResource(R.string.budget_currency_change_message, from.symbol, to.symbol)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.budget_manage_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.profile_delete_cancel)) } },
    )
}

@Preview(name = "Confirm currency")
@Preview(name = "Confirm currency dark", uiMode = UI_MODE_NIGHT_YES)
@Composable private fun CurrencyConfirmationPreview() {
    FillinTheme { BudgetCurrencyConfirmation(BudgetCurrency.KZT, BudgetCurrency.RUB, {}, {}) }
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
