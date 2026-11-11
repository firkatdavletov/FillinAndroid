package com.firkat.fillin.feature.budgetsetup.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.firkat.fillin.R
import com.firkat.fillin.feature.budgetconnection.domain.BudgetCreationDraft
import com.firkat.fillin.feature.budgetconnection.domain.BudgetCreationErrors
import com.firkat.fillin.feature.budgetprofile.ui.BudgetCurrencyRow
import com.firkat.fillin.feature.budgetsetup.domain.BudgetSetupMode
import com.firkat.fillin.ui.component.*
import com.firkat.fillin.ui.icon.FillinIcons
import com.firkat.fillin.ui.theme.FillinTheme

@Composable
internal fun BudgetCreationForm(
    draft: BudgetCreationDraft,
    mode: BudgetSetupMode,
    enabled: Boolean,
    errors: BudgetCreationErrors,
    cloudAllowed: Boolean,
    pending: Boolean,
    busy: Boolean,
    canCreate: Boolean,
    localLimitReached: Boolean,
    onDraftChange: (BudgetCreationDraft) -> Unit,
    onCurrencyClick: () -> Unit,
    onStorageChange: (BudgetSetupMode) -> Unit,
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SaldomiFormTextField(
            draft.name, { onDraftChange(draft.copy(name = it)) }, stringResource(R.string.setup_name), FillinIcons.Folder,
            Modifier.fillMaxWidth().testTag("budget_setup_name"), enabled,
            if (errors.name) stringResource(R.string.setup_name_error) else null,
            KeyboardOptions(imeAction = ImeAction.Next),
        )
        BudgetCurrencyRow(draft.currency, enabled, onCurrencyClick, Modifier.fillMaxWidth().testTag("budget_setup_currency"))
        SaldomiFormTextField(
            draft.openingBalance, { onDraftChange(draft.copy(openingBalance = it)) }, stringResource(R.string.setup_balance), FillinIcons.Wallet,
            Modifier.fillMaxWidth().testTag("budget_setup_balance"), enabled,
            if (errors.balance) stringResource(R.string.setup_balance_error) else null,
            KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        )
        OutlinedTextField(
            value = draft.accountingStartedOn,
            onValueChange = { onDraftChange(draft.copy(accountingStartedOn = it)) },
            label = { Text(stringResource(R.string.cloud_budget_start_date)) },
            supportingText = { Text(stringResource(if (errors.date) R.string.setup_date_error else R.string.setup_date_hint)) },
            isError = errors.date,
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().testTag("budget_setup_date"),
        )
        if (cloudAllowed) {
            Text(stringResource(R.string.setup_storage), style = MaterialTheme.typography.titleLarge)
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SaldomiRadioCard(mode == BudgetSetupMode.Local, { onStorageChange(BudgetSetupMode.Local) },
                    stringResource(R.string.setup_local), stringResource(R.string.setup_local_hint), FillinIcons.Device,
                    Modifier.fillMaxWidth().testTag("setup_local"), enabled)
                SaldomiRadioCard(mode == BudgetSetupMode.Cloud, { onStorageChange(BudgetSetupMode.Cloud) },
                    stringResource(R.string.setup_cloud), stringResource(R.string.setup_cloud_hint), FillinIcons.Cloud,
                    Modifier.fillMaxWidth().testTag("setup_cloud"), enabled)
            }
        }
        if (localLimitReached && mode == BudgetSetupMode.Local) Text(stringResource(R.string.local_budget_limit))
        if (pending) Text(stringResource(R.string.setup_pending))
        SaldomiButton(onCreateClick, Modifier.fillMaxWidth().testTag("setup_create"), enabled = canCreate, large = true, isLoading = busy) {
            Text(stringResource(if (pending) R.string.cloud_budget_create_resume else R.string.budget_setup_create))
        }
    }
}

@Composable
private fun FormPreview(
    mode: BudgetSetupMode = BudgetSetupMode.Local,
    cloudAllowed: Boolean = true,
    pending: Boolean = false,
    busy: Boolean = false,
    limit: Boolean = false,
    errors: BudgetCreationErrors = BudgetCreationErrors(),
    name: String = "Личный бюджет",
) {
    FillinTheme {
        Surface {
            BudgetCreationForm(BudgetCreationDraft(name, "0", "2026-11-11"), mode, !busy && !pending, errors,
                cloudAllowed, pending, busy, !busy && !(limit && mode == BudgetSetupMode.Local), limit, {}, {}, {}, {})
        }
    }
}

@Preview(name = "Local")
@Preview(name = "Local dark", uiMode = UI_MODE_NIGHT_YES)
@Preview(name = "Compact", widthDp = 320, fontScale = 2f)
@Preview(name = "Wide", widthDp = 840)
@Composable private fun LocalPreview() = FormPreview()
@Preview(name = "Cloud")
@Preview(name = "Cloud dark", uiMode = UI_MODE_NIGHT_YES)
@Composable private fun CloudPreview() = FormPreview(mode = BudgetSetupMode.Cloud)
@Preview(name = "Guest")
@Preview(name = "Guest dark", uiMode = UI_MODE_NIGHT_YES)
@Composable private fun GuestPreview() = FormPreview(cloudAllowed = false)
@Preview(name = "Pending")
@Preview(name = "Pending dark", uiMode = UI_MODE_NIGHT_YES)
@Composable private fun PendingPreview() = FormPreview(mode = BudgetSetupMode.Cloud, pending = true)
@Preview(name = "Busy")
@Preview(name = "Busy dark", uiMode = UI_MODE_NIGHT_YES)
@Composable private fun BusyPreview() = FormPreview(busy = true)
@Preview(name = "Invalid")
@Preview(name = "Invalid dark", uiMode = UI_MODE_NIGHT_YES)
@Composable private fun InvalidPreview() = FormPreview(errors = BudgetCreationErrors(true, true, true), name = "")
@Preview(name = "Local limit")
@Preview(name = "Local limit dark", uiMode = UI_MODE_NIGHT_YES)
@Composable private fun LimitPreview() = FormPreview(limit = true)
@Preview(name = "Long name", widthDp = 320)
@Composable private fun LongNamePreview() = FormPreview(name = "Семейный бюджет для большой поездки всей семьёй")
