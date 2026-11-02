package com.firkat.fillin.feature.budgetsetup.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.firkat.fillin.R
import com.firkat.fillin.feature.auth.domain.AuthSession
import com.firkat.fillin.feature.budgetconnection.domain.*
import com.firkat.fillin.feature.budgetconnection.ui.BudgetConnectionContent
import com.firkat.fillin.feature.budgetsharing.ui.FamilyBudgetJoinContent
import com.firkat.fillin.ui.component.SaldomiTextButton

@Composable
internal fun CloudBudgetSetupContent(
    session: AuthSession,
    budgets: List<AvailableBudget>,
    busy: Boolean,
    loaded: Boolean,
    draft: BudgetCreationDraft?,
    pending: PendingBudgetCreation?,
    activeBudgetId: String?,
    showingSaved: Boolean,
    error: String?,
    joiningFamily: Boolean,
    joinSession: AuthSession,
    joinCode: String,
    joinBusy: Boolean,
    joinError: String?,
    joinedName: String?,
    onDraftChange: (BudgetCreationDraft) -> Unit,
    onCreateClick: () -> Unit,
    onChooseCreateClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onBudgetClick: (AvailableBudget) -> Unit,
    onJoinSectionClick: (Boolean) -> Unit,
    onCodeChange: (String) -> Unit,
    onJoinClick: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.budget_setup_cloud_brand), style = MaterialTheme.typography.headlineLarge)
        if (joiningFamily) {
            FamilyBudgetJoinContent(joinSession, joinCode, joinBusy, joinError, joinedName, onCodeChange, onJoinClick)
            SaldomiTextButton(onClick = { onJoinSectionClick(false) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.budget_setup_back_to_list))
            }
        } else {
            BudgetConnectionContent(session, budgets, busy, loaded, draft, pending, activeBudgetId, showingSaved, error,
                showCreateCancel = false, onDraftChange = onDraftChange, onCreateClick = onCreateClick,
                onCancelCreateClick = {}, onChooseCreateClick = onChooseCreateClick, onRefreshClick = onRefreshClick, onBudgetClick = onBudgetClick)
            SaldomiTextButton(onClick = { onJoinSectionClick(true) }, enabled = !busy && pending == null, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.family_join_title))
            }
        }
        SaldomiTextButton(onClick = onBack, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.navigation_back))
        }
    }
}
