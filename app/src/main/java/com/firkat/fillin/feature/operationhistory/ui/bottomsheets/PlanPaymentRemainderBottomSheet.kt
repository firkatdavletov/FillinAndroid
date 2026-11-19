package com.firkat.fillin.feature.operationhistory.ui.bottomsheets

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.budgetprofile.ui.LocalBudgetCurrency
import com.firkat.fillin.feature.operationhistory.domain.repository.PlanLinkTarget
import com.firkat.fillin.feature.operationhistory.domain.repository.PlanPaymentRemainder
import com.firkat.fillin.feature.operationhistory.domain.repository.SuggestedPlan
import com.firkat.fillin.feature.operationhistory.ui.PaymentRemainderMessage
import com.firkat.fillin.feature.operationhistory.ui.PlanPaymentRemainderViewModel
import com.firkat.fillin.ui.component.SaldomiButton
import com.firkat.fillin.ui.component.SaldomiOutlinedButton
import com.firkat.fillin.ui.format.formatMoney
import com.firkat.fillin.ui.navigation.LocalBottomSheetDismissController
import com.firkat.fillin.ui.theme.FillinTheme
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun PlanPaymentRemainderBottomSheet(
    operationId: String,
    targetKey: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlanPaymentRemainderViewModel = koinViewModel { parametersOf(operationId, targetKey) }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val close by rememberUpdatedState(onClose)
    val controller = LocalBottomSheetDismissController.current
    DisposableEffect(controller, viewModel) {
        val guard = viewModel::canDismiss
        controller.canDismiss = guard
        onDispose { if (controller.canDismiss === guard) controller.canDismiss = { true } }
    }
    LaunchedEffect(state.closed, state.saving) { if (state.closed && !state.saving) close() }
    val remainder = state.remainder
    if (remainder == null) {
        if (!state.closed) LinearProgressIndicator(modifier.fillMaxWidth().padding(24.dp))
    } else {
        PlanPaymentRemainderContent(
            remainder,
            state.saving,
            state.message,
            onDecision = { closePayment -> viewModel.decide(remainder, close = closePayment) },
            modifier = modifier
        )
    }
}

@Composable
private fun PlanPaymentRemainderContent(
    remainder: PlanPaymentRemainder,
    saving: Boolean,
    message: PaymentRemainderMessage?,
    onDecision: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val plan = remainder.plan
    val income = plan.target.type == "INCOME"
    val currency = plan.currency ?: LocalBudgetCurrency.current.name
    val dateFormat = DateTimeFormatter.ofPattern("d MMMM", LocalConfiguration.current.locales[0])
    val due = LocalDate.parse(plan.target.dueOn).format(dateFormat)
    val remaining = formatMoney(plan.remainingAmount.toBigDecimal(), currency)
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stringResource(R.string.plan_payment_close_title, due),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Text(plan.displayName, style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.plan_payment_planned, formatMoney(plan.plannedAmount.toBigDecimal(), currency)))
        Text(
            stringResource(
                if (income) R.string.plan_payment_received else R.string.plan_payment_paid,
                formatMoney(remainder.appliedAmount, currency)
            )
        )
        Text(stringResource(R.string.plan_payment_remaining, remaining))
        remainder.expectedOn?.let {
            Text(stringResource(R.string.plan_payment_forecast_date, it.format(dateFormat)))
        }
        message?.let {
            Text(
                stringResource(
                    if (it == PaymentRemainderMessage.Changed) R.string.plan_payment_changed
                    else R.string.plan_payment_error
                ),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.colorScheme.error
            )
        }
        if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        SaldomiButton({ onDecision(true) }, Modifier.fillMaxWidth(), enabled = !saving, large = true) {
            Text(stringResource(R.string.plan_payment_close))
        }
        SaldomiOutlinedButton({ onDecision(false) }, Modifier.fillMaxWidth(), enabled = !saving, large = true) {
            Text(stringResource(if (income) R.string.plan_payment_expect else R.string.plan_payment_keep, remaining))
        }
    }
}

@Preview(name = "Expense")
@Preview(name = "Expense dark", uiMode = UI_MODE_NIGHT_YES)
@Preview(name = "Expense large text", widthDp = 320, fontScale = 2f)
@Composable
private fun CloseExpensePreview() {
    FillinTheme {
        PlanPaymentRemainderContent(
            PlanPaymentRemainder(
                SuggestedPlan(
                    PlanLinkTarget("EXPENSE", ruleId = "rule", dueOn = "2026-11-19"),
                    categoryId = "category",
                    categoryName = "Переводы",
                    plannedAmount = "320000",
                    remainingAmount = "12000",
                    version = 0,
                    currency = "KZT"
                ),
                BigDecimal("308000")
            ),
            saving = false,
            message = null,
            onDecision = {}
        )
    }
}

@Preview(name = "Income changed")
@Preview(name = "Income changed dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CloseIncomePreview() {
    FillinTheme {
        PlanPaymentRemainderContent(
            PlanPaymentRemainder(
                SuggestedPlan(
                    PlanLinkTarget("INCOME", planId = "plan", dueOn = "2026-11-19"),
                    categoryId = "category",
                    categoryName = "Зарплата",
                    plannedAmount = "320000",
                    remainingAmount = "12000",
                    version = 0,
                    currency = "KZT"
                ),
                BigDecimal("308000"),
                LocalDate.parse("2026-11-19")
            ),
            saving = false,
            message = PaymentRemainderMessage.Changed,
            onDecision = {}
        )
    }
}
