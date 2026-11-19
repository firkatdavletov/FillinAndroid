@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.firkat.fillin.feature.budgetmanagement.ui.bottomsheets

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.firkat.fillin.R
import com.firkat.fillin.feature.budgetmanagement.ui.BudgetManagementState
import com.firkat.fillin.feature.budgetprofile.domain.BudgetCurrency
import com.firkat.fillin.ui.component.SaldomiActionBottomSheet
import com.firkat.fillin.ui.component.SaldomiBottomSheet
import com.firkat.fillin.ui.component.SaldomiButton
import com.firkat.fillin.ui.component.SaldomiOutlinedButton
import com.firkat.fillin.ui.component.SaldomiTextField
import com.firkat.fillin.ui.icon.FillinIcons
import com.firkat.fillin.ui.theme.FillinTheme

@Composable
internal fun BudgetDeletionSheet(
    state: BudgetManagementState,
    onChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    SaldomiBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Surface(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.large
            ) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    null,
                    Modifier.padding(20.dp).size(36.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
            Text(
                stringResource(R.string.budget_manage_delete_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        if (state.family) FillinIcons.FamilyAccess else FillinIcons.Wallet,
                        null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(state.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(
                                if (state.family) R.string.profile_family_budget else R.string.profile_personal_budget,
                                state.originalCurrency.name
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.budget_manage_warning),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            SaldomiTextField(
                value = state.value,
                onValueChange = onChange,
                enabled = !state.busy,
                label = stringResource(R.string.budget_manage_confirm_name),
                modifier = Modifier.fillMaxWidth().testTag("budget_management_input")
            )
            if (state.error) Text(stringResource(R.string.budget_manage_error), color = MaterialTheme.colorScheme.error)
            SaldomiButton(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth().testTag("budget_management_confirm"),
                large = true,
                isLoading = state.busy,
                destructive = true,
                enabled = !state.busy && state.value.trim() == state.name.trim()
            ) {
                Text(stringResource(R.string.budget_manage_delete))
            }
            SaldomiOutlinedButton(
                onClick = onDismiss,
                enabled = !state.busy,
                large = true,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.profile_delete_cancel))
            }
        }
    }
}

@Composable
internal fun BudgetCurrencyConfirmation(
    from: BudgetCurrency,
    to: BudgetCurrency,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    SaldomiActionBottomSheet(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.budget_currency_change_title)) },
        text = { Text(stringResource(R.string.budget_currency_change_message, from.symbol, to.symbol)) },
        confirmButton = {
            SaldomiButton(onClick = onConfirm, modifier = Modifier.fillMaxWidth(), large = true) {
                Text(stringResource(R.string.budget_manage_save))
            }
        },
        dismissButton = {
            SaldomiOutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), large = true) {
                Text(stringResource(R.string.profile_delete_cancel))
            }
        }
    )
}

@Preview(name = "Confirm currency")
@Preview(name = "Confirm currency dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CurrencyConfirmationPreview() {
    FillinTheme { BudgetCurrencyConfirmation(BudgetCurrency.KZT, BudgetCurrency.RUB, {}, {}) }
}

@Preview(name = "Delete")
@Preview(name = "Delete dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun DeletePreview() {
    FillinTheme { BudgetDeletionSheet(BudgetManagementState("1", "Семейный", true, ""), {}, {}, {}) }
}

@Preview(name = "Delete confirmed", widthDp = 840)
@Preview(name = "Delete confirmed dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun DeleteConfirmedPreview() {
    FillinTheme { BudgetDeletionSheet(BudgetManagementState("1", "Семейный", true, "Семейный"), {}, {}, {}) }
}
