package com.firkat.fillin.feature.budgetsetup.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.firkat.fillin.R
import com.firkat.fillin.feature.budgetsetup.domain.BudgetSetupMode
import com.firkat.fillin.feature.budgetsetup.ui.interactor.*
import com.firkat.fillin.ui.component.*
import com.firkat.fillin.ui.icon.FillinIcons

@Composable
internal fun BudgetSetupContent(
    state: BudgetSetupState,
    onAction: (BudgetSetupAction) -> Unit,
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.saldomi_auth_background), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Column(
            Modifier.align(Alignment.TopCenter).widthIn(max = 640.dp).fillMaxWidth().fillMaxHeight().windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onAction(BudgetSetupAction.Back) }, enabled = !state.busy, modifier = Modifier.testTag("setup_back")) {
                    Icon(FillinIcons.Back, stringResource(R.string.navigation_back))
                }
                Image(painterResource(R.drawable.saldomi_logo), stringResource(R.string.app_name), Modifier.widthIn(max = 180.dp).heightIn(max = 56.dp), contentScale = ContentScale.Fit)
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(when (state.page) { SetupPage.Create -> R.string.setup_title; SetupPage.List -> R.string.setup_list_title; SetupPage.Join -> R.string.family_join_title }),
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            when (state.page) {
                SetupPage.Create -> BudgetCreationForm(
                    name = state.name, balance = state.balance, mode = state.mode, enabled = state.editable,
                    nameInvalid = state.nameInvalid, balanceInvalid = state.balanceInvalid,
                    pending = state.pending != null, busy = state.busy,
                    canCreate = state.canCreate,
                    onNameChange = { onAction(BudgetSetupAction.NameChanged(it)) },
                    onBalanceChange = { onAction(BudgetSetupAction.BalanceChanged(it)) },
                    onStorageChange = { onAction(BudgetSetupAction.StorageChanged(it)) },
                    onCreateClick = { onAction(BudgetSetupAction.Create) },
                    onJoinClick = { onAction(BudgetSetupAction.OpenJoin) },
                )
                SetupPage.List -> {
                    state.budgets.forEach { budget ->
                        SaldomiOutlinedButton(onClick = { onAction(BudgetSetupAction.Connect(budget)) }, enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth(), large = true) { Text(budget.name + " · " + stringResource(if (budget.role == "OWNER") R.string.cloud_budget_owner else R.string.cloud_budget_member)) }
                    }
                    if (state.showingSaved) Text(stringResource(R.string.cloud_budgets_saved))
                    SaldomiButton(onClick = { onAction(BudgetSetupAction.OpenCreate) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth(), large = true) { Text(stringResource(R.string.cloud_budget_create_another)) }
                    SaldomiOutlinedButton(onClick = { onAction(BudgetSetupAction.OpenJoin) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth(), large = true, leadingIcon = FillinIcons.FamilyAccess) { Text(stringResource(R.string.family_join_title)) }
                }
                SetupPage.Join -> BudgetJoinForm(state.code, !state.busy, state.busy, state.codeInvalid,
                    { onAction(BudgetSetupAction.CodeChanged(it)) }, { onAction(BudgetSetupAction.Join) })
            }
            if (state.loading) SaldomiProgressIndicator()
            if (state.uid != null && !state.loading && !state.loaded) {
                SaldomiTextButton(onClick = { onAction(BudgetSetupAction.Refresh) }, enabled = !state.busy) { Text(stringResource(R.string.cloud_budgets_refresh)) }
            }
            Spacer(Modifier.height(16.dp))
        }
        SaldomiSnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding())
    }
}

@Composable
private fun BudgetCreationForm(
    name: String, balance: String, mode: BudgetSetupMode, enabled: Boolean,
    nameInvalid: Boolean, balanceInvalid: Boolean, pending: Boolean, busy: Boolean, canCreate: Boolean,
    onNameChange: (String) -> Unit, onBalanceChange: (String) -> Unit,
    onStorageChange: (BudgetSetupMode) -> Unit, onCreateClick: () -> Unit, onJoinClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.setup_subtitle), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        SaldomiFormTextField(name, onNameChange, stringResource(R.string.setup_name), FillinIcons.Folder,
            Modifier.fillMaxWidth().testTag("budget_setup_name"), enabled, if (nameInvalid) stringResource(R.string.setup_name_error) else null,
            KeyboardOptions(imeAction = ImeAction.Next))
        SaldomiFormValue(stringResource(R.string.setup_currency), stringResource(R.string.setup_currency_value), FillinIcons.Currency, Modifier.fillMaxWidth())
        SaldomiFormTextField(balance, onBalanceChange, stringResource(R.string.setup_balance), FillinIcons.Wallet,
            Modifier.fillMaxWidth().testTag("budget_setup_balance"), enabled, if (balanceInvalid) stringResource(R.string.setup_balance_error) else null,
            KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.setup_storage), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SaldomiRadioCard(mode == BudgetSetupMode.Cloud, { onStorageChange(BudgetSetupMode.Cloud) }, stringResource(R.string.setup_cloud), stringResource(R.string.setup_cloud_hint), FillinIcons.Cloud, Modifier.fillMaxWidth().testTag("setup_cloud"), enabled)
            SaldomiRadioCard(mode == BudgetSetupMode.Local, { onStorageChange(BudgetSetupMode.Local) }, stringResource(R.string.setup_local), stringResource(R.string.setup_local_hint), FillinIcons.Device, Modifier.fillMaxWidth().testTag("setup_local"), enabled)
        }
        if (pending) Text(stringResource(R.string.setup_pending))
        Spacer(Modifier.height(8.dp))
        SaldomiButton(onCreateClick, Modifier.fillMaxWidth().testTag("setup_create"), enabled = canCreate, large = true, isLoading = busy) {
            Text(stringResource(if (pending) R.string.cloud_budget_create_resume else R.string.budget_setup_create))
        }
        SaldomiOutlinedButton(onJoinClick, Modifier.fillMaxWidth().testTag("setup_open_join"), enabled = enabled, large = true, leadingIcon = FillinIcons.FamilyAccess) { Text(stringResource(R.string.family_join_title)) }
    }
}

@Composable
private fun BudgetJoinForm(code: String, enabled: Boolean, busy: Boolean, invalid: Boolean, onCodeChange: (String) -> Unit, onJoinClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.setup_join_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
        SaldomiFormTextField(code, onCodeChange, stringResource(R.string.family_join_code), FillinIcons.FamilyAccess, Modifier.fillMaxWidth().testTag("setup_code"), enabled, if (invalid) stringResource(R.string.setup_code_error) else null)
        SaldomiButton(onJoinClick, Modifier.fillMaxWidth(), enabled, large = true, isLoading = busy) { Text(stringResource(R.string.family_join)) }
    }
}
