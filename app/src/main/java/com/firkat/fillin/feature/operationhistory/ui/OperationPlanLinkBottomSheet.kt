package com.firkat.fillin.feature.operationhistory.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.budgetprofile.ui.LocalBudgetCurrency
import com.firkat.fillin.feature.operationhistory.domain.repository.*
import com.firkat.fillin.ui.navigation.LocalBottomSheetDismissController
import com.firkat.fillin.ui.theme.FillinTheme
import java.math.BigDecimal
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun OperationPlanLinkBottomSheet(
    operationId: String,
    onClose: () -> Unit,
    onChooseAnother: () -> Unit,
    viewModel: OperationPlanLinkViewModel = koinViewModel { parametersOf(operationId) }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val close by rememberUpdatedState(onClose)
    val chooseAnother by rememberUpdatedState(onChooseAnother)
    val controller = LocalBottomSheetDismissController.current
    DisposableEffect(controller, viewModel) {
        val guard = { viewModel.requestDismiss() }
        controller.canDismiss = guard
        onDispose { if (controller.canDismiss === guard) controller.canDismiss = { true } }
    }
    BackHandler { viewModel.dismiss() }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect {
            when (it) {
                OperationPlanLinkEffect.Close -> close()
                OperationPlanLinkEffect.ChooseAnother -> chooseAnother()
                OperationPlanLinkEffect.OpenConfirmation -> Unit
            }
        }
    }
    OperationPlanLinkContent(
        plan = state.selected,
        operationAmount = state.operation?.let {
            (it.amount - it.refundedAmount).max(BigDecimal.ZERO).toPlainString()
        },
        saving = state.saving, loaded = state.loaded, error = state.error,
        onConfirm = viewModel::confirm, onDismiss = viewModel::dismiss, onChooseAnother = viewModel::chooseAnother,
        isReplacing = state.links.current.isNotEmpty()
    )
}

@Composable
internal fun OperationPlanLinkContent(
    plan: SuggestedPlan?,
    operationAmount: String?,
    saving: Boolean,
    loaded: Boolean,
    error: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onChooseAnother: () -> Unit = {},
    isReplacing: Boolean = false
) {
    Column(
        modifier.fillMaxWidth().testTag(
            "operation_plan_link_sheet"
        ).verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.plan_link_title), style = MaterialTheme.typography.titleLarge)
        if (!loaded) CircularProgressIndicator()
        if (plan != null && operationAmount != null) {
            Text(
                stringResource(
                    if (plan.target.type ==
                        "INCOME"
                    ) {
                        R.string.plan_link_income
                    } else {
                        R.string.plan_link_expense
                    },
                    operationAmount,
                    LocalBudgetCurrency.current.symbol
                )
            )
            Text(plan.categoryName, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.plan_link_due, plan.target.dueOn))
            Text(stringResource(R.string.plan_link_planned, plan.plannedAmount, LocalBudgetCurrency.current.symbol))
            Text(stringResource(R.string.plan_link_remaining, plan.remainingAmount, LocalBudgetCurrency.current.symbol))
            val remaining = (plan.remainingAmount.toBigDecimal() - operationAmount.toBigDecimal()).max(BigDecimal.ZERO)
            Text(
                stringResource(R.string.plan_link_after, remaining.toPlainString(), LocalBudgetCurrency.current.symbol)
            )
        } else if (loaded) {
            Text(stringResource(R.string.plan_link_unavailable))
        }
        if (error !=
            null
        ) {
            Text(
                stringResource(R.string.plan_link_error),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("plan_link_error")
            )
        }
        Button(
            onClick = onConfirm,
            enabled =
            !saving &&
                loaded &&
                plan != null &&
                operationAmount != null &&
                error !in setOf("plan_changed", "plan_unavailable"),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (saving) {
                CircularProgressIndicator(
                    Modifier.size(24.dp)
                )
            } else {
                Text(stringResource(R.string.plan_link_confirm))
            }
        }
        if (loaded && (plan == null || error in setOf("plan_changed", "plan_unavailable"))) {
            TextButton(onClick = onChooseAnother, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.plan_link_choose_another))
            }
        }
        TextButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (isReplacing) R.string.plan_link_keep_current else R.string.plan_link_skip))
        }
    }
}

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
private fun LinkPreview() {
    FillinTheme { OperationPlanLinkContent(previewPlan, "5000.00", false, true, null, {}, {}) }
}

@Preview @Composable
private fun LinkIncomePreview() {
    FillinTheme {
        OperationPlanLinkContent(
            previewPlan.copy(
                target = previewPlan.target.copy(type = "INCOME", planId = "income", ruleId = null)
            ),
            "5000.00",
            false,
            true,
            null,
            {
            },
            {}
        )
    }
}

@Preview @Composable
private fun LinkSavingPreview() {
    FillinTheme { OperationPlanLinkContent(previewPlan, "5000.00", true, true, null, {}, {}) }
}

@Preview @Composable
private fun LinkErrorPreview() {
    FillinTheme { OperationPlanLinkContent(previewPlan, "5000.00", false, true, "error", {}, {}) }
}

@Preview @Composable
private fun LinkMissingPreview() {
    FillinTheme { OperationPlanLinkContent(null, null, false, true, null, {}, {}) }
}

@Preview @Composable
private fun LinkLoadingPreview() {
    FillinTheme { OperationPlanLinkContent(null, null, false, false, null, {}, {}) }
}

@Preview(widthDp = 320, fontScale = 2f)
@Preview(widthDp = 840)
@Composable
private fun LinkLongPreview() {
    FillinTheme {
        OperationPlanLinkContent(
            previewPlan.copy(
                categoryName = "Оплата коммунальных услуг за несколько месяцев"
            ),
            "5000.00",
            false,
            true,
            null,
            {
            },
            {}
        )
    }
}

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

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LinkReplacementPreview() {
    FillinTheme { OperationPlanLinkContent(previewPlan, "5000.00", false, true, null, {}, {}, isReplacing = true) }
}

@Preview @Composable
private fun LinkChangedPreview() {
    FillinTheme { OperationPlanLinkContent(previewPlan, "5000.00", false, true, "plan_changed", {}, {}) }
}
