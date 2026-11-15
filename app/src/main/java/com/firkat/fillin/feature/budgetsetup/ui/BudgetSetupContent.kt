package com.firkat.fillin.feature.budgetsetup.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import com.firkat.fillin.R
import com.firkat.fillin.feature.budgetconnection.domain.BudgetCreationErrors
import com.firkat.fillin.feature.budgetsetup.ui.bottomsheets.BudgetSetupBottomSheetHost
import com.firkat.fillin.feature.budgetsetup.ui.interactor.*
import com.firkat.fillin.ui.component.*
import com.firkat.fillin.ui.icon.FillinIcons

@Composable
internal fun BudgetSetupContent(
    state: BudgetSetupState,
    onAction: (BudgetSetupAction) -> Unit,
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier,
    fromProfile: Boolean = false
) {
    BudgetSetupBottomSheetHost(
        pickerOpen = state.currencyPickerOpen,
        editable = state.editable,
        page = state.page,
        currency = state.currency,
        onCurrencySelected = { onAction(BudgetSetupAction.CurrencyChanged(it)) },
        onDismiss = { onAction(BudgetSetupAction.CloseCurrencyPicker) }
    )
    Box(modifier.fillMaxSize()) {
        Image(
            painterResource(R.drawable.saldomi_auth_background),
            null,
            Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Column(
            Modifier.align(
                Alignment.TopCenter
            ).widthIn(
                max = 640.dp
            ).fillMaxWidth().fillMaxHeight().windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Image(
                painterResource(R.drawable.saldomi_logo),
                stringResource(R.string.app_name),
                Modifier.widthIn(max = 180.dp).heightIn(max = 56.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(
                    when (state.page) {
                        SetupPage.Create -> R.string.setup_title
                        SetupPage.List -> R.string.setup_list_title
                        SetupPage.Join -> R.string.family_join_title
                    }
                ),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            when (state.page) {
                SetupPage.Create -> {
                    BudgetCreationForm(
                        name = state.name, balance = state.balance, currency = state.currency,
                        mode = state.mode, enabled = state.editable,
                        errors = BudgetCreationErrors(name = state.nameInvalid, balance = state.balanceInvalid),
                        cloudAllowed = state.cloudAllowed, pending = state.pending != null, busy = state.busy,
                        canCreate = state.canCreate,
                        localLimitReached = state.localLimitReached,
                        onNameChange = { onAction(BudgetSetupAction.NameChanged(it)) },
                        onBalanceChange = { onAction(BudgetSetupAction.BalanceChanged(it)) },
                        onCurrencyClick = { onAction(BudgetSetupAction.OpenCurrencyPicker) },
                        onStorageChange = { onAction(BudgetSetupAction.StorageChanged(it)) },
                        onCreateClick = { onAction(BudgetSetupAction.Create) }
                    )
                    if (state.cloudAllowed) {
                        SaldomiOutlinedButton(
                            onClick = { onAction(BudgetSetupAction.OpenJoin) },
                            enabled = state.editable,
                            modifier = Modifier.fillMaxWidth().testTag("setup_open_join"),
                            large = true,
                            leadingIcon = FillinIcons.FamilyAccess
                        ) { Text(stringResource(R.string.family_join_title)) }
                    }
                    if (state.cloudAllowed && state.budgets.isNotEmpty() && state.pending == null) {
                        SaldomiTextButton(onClick = { onAction(BudgetSetupAction.OpenList) }, enabled = !state.busy) {
                            Text(stringResource(R.string.setup_list_title))
                        }
                    }
                }
                SetupPage.List -> {
                    state.budgets.forEach { budget ->
                        SaldomiOutlinedButton(
                            onClick = {
                                onAction(BudgetSetupAction.Connect(budget))
                            },
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth(),
                            large = true
                        ) {
                            Text(
                                budget.name + " · " +
                                    stringResource(
                                        if (budget.role ==
                                            "OWNER"
                                        ) {
                                            R.string.cloud_budget_owner
                                        } else {
                                            R.string.cloud_budget_member
                                        }
                                    )
                            )
                        }
                    }
                    if (state.showingSaved) Text(stringResource(R.string.cloud_budgets_saved))
                    SaldomiButton(onClick = {
                        onAction(BudgetSetupAction.OpenCreate)
                    }, enabled = !state.busy, modifier = Modifier.fillMaxWidth(), large = true) {
                        Text(stringResource(R.string.cloud_budget_create_another))
                    }
                    SaldomiOutlinedButton(onClick = {
                        onAction(BudgetSetupAction.OpenJoin)
                    }, enabled = !state.busy, modifier = Modifier.fillMaxWidth(), large = true, leadingIcon = FillinIcons.FamilyAccess) {
                        Text(stringResource(R.string.family_join_title))
                    }
                }
                SetupPage.Join -> BudgetJoinForm(
                    state.code,
                    !state.busy,
                    state.busy,
                    state.codeInvalid,
                    { onAction(BudgetSetupAction.CodeChanged(it)) },
                    { onAction(BudgetSetupAction.Join) }
                )
            }
            if (state.loading) SaldomiProgressIndicator()
            if (state.cloudAllowed && state.uid != null && !state.loading && !state.loaded) {
                SaldomiTextButton(onClick = {
                    onAction(BudgetSetupAction.Refresh)
                }, enabled = !state.busy) { Text(stringResource(R.string.cloud_budgets_refresh)) }
            }
            if (state.cloudAllowed || fromProfile) {
                SaldomiOutlinedButton(
                    onClick = { onAction(BudgetSetupAction.Back) },
                    modifier = Modifier.fillMaxWidth().testTag("setup_back"),
                    enabled = !state.busy,
                    large = true
                ) {
                    Text(stringResource(R.string.navigation_back))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        SaldomiSnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding())
    }
}

@Composable
private fun BudgetJoinForm(
    code: String,
    enabled: Boolean,
    busy: Boolean,
    invalid: Boolean,
    onCodeChange: (String) -> Unit,
    onJoinClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.setup_join_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
        SaldomiFormTextField(
            code,
            onCodeChange,
            stringResource(R.string.family_join_code),
            FillinIcons.FamilyAccess,
            Modifier.fillMaxWidth().testTag("setup_code"),
            enabled,
            if (invalid) stringResource(R.string.setup_code_error) else null
        )
        SaldomiButton(onJoinClick, Modifier.fillMaxWidth(), enabled, large = true, isLoading = busy) {
            Text(stringResource(R.string.family_join))
        }
    }
}
