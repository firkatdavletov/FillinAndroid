package com.firkat.fillin.feature.operationhistory.ui.bottomsheets

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.budgetprofile.ui.LocalBudgetCurrency
import com.firkat.fillin.feature.operationhistory.domain.repository.PlanLinkTarget
import com.firkat.fillin.feature.operationhistory.domain.repository.SuggestedPlan
import com.firkat.fillin.feature.operationhistory.ui.OperationPlanLinkEffect
import com.firkat.fillin.feature.operationhistory.ui.OperationPlanLinkViewModel
import com.firkat.fillin.ui.navigation.LocalBottomSheetDismissController
import com.firkat.fillin.ui.theme.FillinTheme
import java.math.BigDecimal
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

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
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

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LinkSavingPreview() {
    FillinTheme { OperationPlanLinkContent(previewPlan, "5000.00", true, true, null, {}, {}) }
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LinkErrorPreview() {
    FillinTheme { OperationPlanLinkContent(previewPlan, "5000.00", false, true, "error", {}, {}) }
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LinkMissingPreview() {
    FillinTheme { OperationPlanLinkContent(null, null, false, true, null, {}, {}) }
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
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
private fun LinkReplacementPreview() {
    FillinTheme { OperationPlanLinkContent(previewPlan, "5000.00", false, true, null, {}, {}, isReplacing = true) }
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LinkChangedPreview() {
    FillinTheme { OperationPlanLinkContent(previewPlan, "5000.00", false, true, "plan_changed", {}, {}) }
}
