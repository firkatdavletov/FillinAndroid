@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.firkat.fillin.feature.budgetmanagement.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.R
import com.firkat.fillin.feature.auth.domain.AuthRepository
import com.firkat.fillin.feature.auth.domain.AuthSession
import com.firkat.fillin.feature.budgetmanagement.data.BudgetManagement
import com.firkat.fillin.feature.budgetmanagement.domain.validBudgetName
import com.firkat.fillin.feature.budgetprofile.domain.BudgetCurrency
import com.firkat.fillin.ui.component.*
import com.firkat.fillin.ui.theme.FillinTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class BudgetManagementState(
    val id: String,
    val name: String,
    val deleting: Boolean,
    val value: String,
    val busy: Boolean = false,
    val error: Boolean = false,
    val family: Boolean = false,
    val originalCurrency: BudgetCurrency = BudgetCurrency.KZT,
    val currency: BudgetCurrency = originalCurrency,
    val pickerOpen: Boolean = false,
    val confirmCurrency: Boolean = false
)
class BudgetManagementViewModel(private val management: BudgetManagement, auth: AuthRepository) : ViewModel() {
    private val mutableState = MutableStateFlow<BudgetManagementState?>(null)
    val state = mutableState.asStateFlow()
    internal var editingStarted = false
        private set
    val pending = management.entries.combine(auth.session) { entries, session ->
        val uid = (session as? AuthSession.Authenticated)?.user?.uid
        entries.filter { (it.deleting || it.error == "access_denied") && (it.uid == null || it.uid == uid) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    init {
        var previousSession = auth.session.value
        viewModelScope.launch {
            auth.session.collect { session ->
                if (session != previousSession) {
                    previousSession = session
                    mutableState.value = null
                }
            }
        }
    }

    internal fun startEditing(id: String, name: String, currency: BudgetCurrency) {
        if (editingStarted) return
        editingStarted = true
        open(id, name, deleting = false, currency = currency)
    }

    fun open(
        id: String,
        name: String,
        deleting: Boolean,
        family: Boolean = false,
        currency: BudgetCurrency = BudgetCurrency.KZT
    ) {
        mutableState.value =
            BudgetManagementState(
                id,
                name,
                deleting,
                if (deleting) "" else name,
                family = family,
                originalCurrency = currency
            )
    }
    fun openCurrencyPicker() = mutableState.update {
        it?.let { state ->
            if (state.busy ||
                state.deleting
            ) {
                state
            } else {
                state.copy(pickerOpen = true)
            }
        }
    }
    fun closeCurrencyPicker() = mutableState.update { it?.copy(pickerOpen = false) }
    fun selectCurrency(
        currency: BudgetCurrency
    ) = mutableState.update { it?.copy(currency = currency, pickerOpen = false, error = false) }
    fun dismissCurrencyConfirmation() = mutableState.update { it?.copy(confirmCurrency = false) }
    fun confirmCurrencyChange() = confirm(allowCurrencyChange = true)
    fun change(
        value: String
    ) {
        mutableState.value?.takeUnless { it.busy }?.let {
            mutableState.value =
                it.copy(value = value, error = false)
        }
    }
    fun close() {
        if (mutableState.value?.busy != true) mutableState.value = null
    }
    fun retry() = management.retry()
    fun confirm() = confirm(allowCurrencyChange = false)

    private fun confirm(
        allowCurrencyChange: Boolean
    ) {
        val current = mutableState.value?.takeUnless { it.busy } ?: return
        if (current.deleting &&
            current.value.trim() != current.name.trim() ||
            !current.deleting &&
            !validBudgetName(current.value)
        ) {
            return
        }
        val newCurrency = current.currency.takeUnless { current.deleting || it == current.originalCurrency }
        if (newCurrency != null && !allowCurrencyChange) {
            mutableState.value = current.copy(confirmCurrency = true)
            return
        }
        mutableState.value = current.copy(busy = true, confirmCurrency = false)
        viewModelScope.launch {
            try {
                management.submit(
                    current.id,
                    if (current.deleting) current.name else current.value,
                    current.deleting,
                    newCurrency,
                    rename = current.value.trim() != current.name
                )
                if (mutableState.value?.id == current.id) mutableState.value = null
            } catch (
                cancelled: CancellationException
            ) {
                throw cancelled
            } catch (_: Exception) {
                if (mutableState.value?.id ==
                    current.id
                ) {
                    mutableState.value = current.copy(error = true, confirmCurrency = false)
                }
            }
        }
    }
}

@Composable
internal fun PendingBudgetDeletion(
    name: String,
    failed: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    sending: Boolean = false,
    denied: Boolean = false
) {
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(name, style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(
                if (denied) {
                    R.string.budget_manage_denied
                } else if (sending) {
                    R.string.budget_manage_sending
                } else if (failed) {
                    R.string.budget_manage_error
                } else {
                    R.string.budget_manage_pending_delete
                }
            )
        )
        if (failed) TextButton(onClick = onRetry) { Text(stringResource(R.string.budget_profile_retry)) }
    }
}

@Preview(name = "Pending deletion")
@Preview(name = "Pending deletion dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun PendingPreview() {
    FillinTheme { PendingBudgetDeletion("Семейный", false, {}) }
}

@Preview(name = "Failed deletion")
@Preview(name = "Failed deletion dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun FailedPreview() {
    FillinTheme { PendingBudgetDeletion("Семейный", true, {}) }
}
