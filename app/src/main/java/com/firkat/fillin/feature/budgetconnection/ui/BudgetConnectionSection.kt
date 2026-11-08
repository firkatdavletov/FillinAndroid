package com.firkat.fillin.feature.budgetconnection.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.auth.domain.*
import com.firkat.fillin.feature.budgetconnection.domain.*
import com.firkat.fillin.feature.budgetconnection.ui.interactor.*
import com.firkat.fillin.ui.component.*
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun BudgetConnectionSection(viewModel: BudgetConnectionViewModel = koinViewModel(), showCreateCancel: Boolean = true) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BudgetConnectionContent(
        session = state.session, budgets = state.budgets, busy = state.busy, loaded = state.loaded,
        draft = state.draft, pending = state.pending, activeBudgetId = state.activeBudgetId,
        showingSaved = state.showingSaved, error = state.error, showCreateCancel = showCreateCancel,
        onDraftChange = { viewModel.onAction(BudgetConnectionAction.EditDraft(it)) },
        onCreateClick = { viewModel.onAction(BudgetConnectionAction.Create) },
        onCancelCreateClick = { viewModel.onAction(BudgetConnectionAction.CancelCreate) },
        onChooseCreateClick = { viewModel.onAction(BudgetConnectionAction.ChooseCreate()) },
        onRefreshClick = { viewModel.onAction(BudgetConnectionAction.Refresh) },
        onBudgetClick = { viewModel.onAction(BudgetConnectionAction.Connect(it)) },
    )
}

@Composable
internal fun BudgetConnectionContent(
    session: AuthSession,
    budgets: List<AvailableBudget>,
    busy: Boolean,
    loaded: Boolean,
    draft: BudgetCreationDraft?,
    pending: PendingBudgetCreation?,
    activeBudgetId: String?,
    showingSaved: Boolean,
    error: String?,
    showCreateCancel: Boolean,
    onDraftChange: (BudgetCreationDraft) -> Unit,
    onCreateClick: () -> Unit,
    onCancelCreateClick: () -> Unit,
    onChooseCreateClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onBudgetClick: (AvailableBudget) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.cloud_budgets_title), style = MaterialTheme.typography.titleMedium)
        when (session) {
            is AuthSession.Authenticated -> {
                if (busy) SaldomiProgressIndicator()
                if (draft != null) {
                    val editable = !busy && pending == null
                    Text(stringResource(R.string.cloud_budget_create_title), style = MaterialTheme.typography.titleSmall)
                    SaldomiTextField(draft.name, { onDraftChange(draft.copy(name = it)) }, label = stringResource(R.string.cloud_budget_name), enabled = editable, modifier = Modifier.fillMaxWidth())
                    SaldomiTextField(draft.openingBalance, { onDraftChange(draft.copy(openingBalance = it)) }, label = stringResource(R.string.cloud_budget_opening_balance), enabled = editable, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    SaldomiTextField(draft.accountingStartedOn, { onDraftChange(draft.copy(accountingStartedOn = it)) }, label = stringResource(R.string.cloud_budget_start_date), enabled = editable, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(if (pending == null) R.string.cloud_budget_create_explanation else R.string.cloud_budget_create_pending))
                    SaldomiButton(onClick = onCreateClick, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (pending == null) R.string.cloud_budget_create else R.string.cloud_budget_create_resume)) }
                    if (pending == null && showCreateCancel) SaldomiTextButton(onClick = onCancelCreateClick, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cloud_budget_create_cancel)) }
                } else {
                    if (loaded && budgets.isEmpty()) {
                        Text(stringResource(R.string.cloud_budgets_empty))
                    }
                    budgets.forEach { budget ->
                        SaldomiOutlinedButton(onClick = { onBudgetClick(budget) }, enabled = !busy, modifier = Modifier.fillMaxWidth().semantics { selected = activeBudgetId == budget.id }) {
                            Text(budget.name + " · " + stringResource(if (budget.role == "OWNER") R.string.cloud_budget_owner else R.string.cloud_budget_member))
                        }
                        if (activeBudgetId == budget.id) Text(stringResource(R.string.cloud_budget_selected), style = MaterialTheme.typography.labelMedium)
                    }
                    if (loaded) SaldomiButton(onClick = onChooseCreateClick, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(if (budgets.isEmpty()) R.string.cloud_budget_create else R.string.cloud_budget_create_another))
                    }
                }
                if (showingSaved) Text(stringResource(R.string.cloud_budgets_saved))
                SaldomiTextButton(onClick = onRefreshClick, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cloud_budgets_refresh)) }
            }
            AuthSession.Loading -> SaldomiProgressIndicator()
            else -> Text(stringResource(R.string.cloud_budgets_sign_in))
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
