package com.firkat.fillin.feature.operationhistory.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.budgetprofile.ui.LocalBudgetCurrency
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationPlanLinkStore
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationPlanLinks
import com.firkat.fillin.feature.operationhistory.domain.repository.PlanLinkTarget
import com.firkat.fillin.feature.operationhistory.domain.repository.SuggestedPlan
import com.firkat.fillin.ui.theme.FillinTheme
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun OperationPlanLinkActions(
    operationId: String,
    enabled: Boolean,
    onSelected: () -> Unit,
    viewModel: OperationPlanLinkViewModel =
        koinViewModel(key = "link-selector-$operationId") { parametersOf(operationId) }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selected by rememberUpdatedState(onSelected)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect {
            if (it ==
                OperationPlanLinkEffect.OpenConfirmation
            ) {
                selected()
            }
        }
    }
    OperationPlanLinkChoices(
        state.links,
        enabled && !state.saving,
        state.error != null,
        viewModel::select,
        viewModel::remove
    )
}

@Composable
internal fun OperationPlanLinkChoices(
    links: OperationPlanLinks,
    enabled: Boolean,
    hasError: Boolean,
    onSelect: (SuggestedPlan) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.plan_link_section), style = MaterialTheme.typography.titleMedium)
        links.current.forEach { Text(stringResource(R.string.plan_link_current, it.categoryName, it.target.dueOn)) }
        if (links.current.isNotEmpty()) {
            TextButton(onClick = onRemove, enabled = enabled) {
                Text(stringResource(R.string.plan_link_remove))
            }
        }
        if (links.candidates.isEmpty()) Text(stringResource(R.string.plan_link_empty))
        links.candidates.forEach { plan ->
            TextButton(onClick = { onSelect(plan) }, enabled = enabled) {
                Text(
                    stringResource(
                        R.string.plan_link_choice,
                        plan.categoryName,
                        plan.target.dueOn,
                        plan.remainingAmount,
                        LocalBudgetCurrency.current.symbol
                    )
                )
            }
        }
        if (hasError) Text(stringResource(R.string.plan_link_error), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
internal fun PendingPlanLinkRecovery(
    onPending: (String) -> Unit,
    isActive: Boolean,
    store: OperationPlanLinkStore = koinInject()
) {
    val pending by store.pendingProposal.collectAsStateWithLifecycle(initialValue = null)
    val callback by rememberUpdatedState(onPending)
    LaunchedEffect(pending, isActive) { if (isActive) pending?.let(callback) }
}

private val previewPlan =
    SuggestedPlan(
        PlanLinkTarget("EXPENSE", ruleId = "rule", dueOn = "2026-10-30"),
        "category",
        "Коммунальные услуги",
        "20000.00",
        "12000.00",
        0
    )

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ChoicesPreview() {
    FillinTheme {
        OperationPlanLinkChoices(OperationPlanLinks(listOf(previewPlan), listOf(previewPlan)), true, false, {}, {})
    }
}

@Preview @Composable
private fun ChoicesEmptyPreview() {
    FillinTheme { OperationPlanLinkChoices(OperationPlanLinks(), true, false, {}, {}) }
}

@Preview @Composable
private fun ChoicesDisabledPreview() {
    FillinTheme { OperationPlanLinkChoices(OperationPlanLinks(listOf(previewPlan)), false, true, {}, {}) }
}
