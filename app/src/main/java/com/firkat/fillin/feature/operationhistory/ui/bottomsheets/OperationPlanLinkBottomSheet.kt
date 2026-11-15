package com.firkat.fillin.feature.operationhistory.ui.bottomsheets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.feature.operationhistory.domain.repository.PlanLinkOperation
import com.firkat.fillin.feature.operationhistory.domain.repository.SuggestedPlan
import com.firkat.fillin.feature.operationhistory.ui.OperationPlanLinkState
import com.firkat.fillin.feature.operationhistory.ui.OperationPlanLinkViewModel
import com.firkat.fillin.feature.operationhistory.ui.PlanOfferError
import com.firkat.fillin.feature.operationhistory.ui.PlanOfferPage
import com.firkat.fillin.feature.operationhistory.ui.PlanOfferSelection
import com.firkat.fillin.feature.operationhistory.ui.formatOperationAmount
import com.firkat.fillin.feature.plannedoperations.domain.model.PlanningFrequency
import com.firkat.fillin.ui.component.CategoryIconImage
import com.firkat.fillin.ui.component.SaldomiButton
import com.firkat.fillin.ui.format.formatMoney
import com.firkat.fillin.ui.navigation.LocalBottomSheetDismissController
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun OperationPlanLinkBottomSheet(
    operationId: String,
    onClose: (String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OperationPlanLinkViewModel = koinViewModel { parametersOf(operationId) }
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val close by rememberUpdatedState(onClose)
    val controller = LocalBottomSheetDismissController.current
    DisposableEffect(controller, viewModel) {
        val guard = viewModel::requestDismiss
        controller.canDismiss = guard
        onDispose { if (controller.canDismiss === guard) controller.canDismiss = { true } }
    }
    BackHandler(onBack = viewModel::dismiss)
    LaunchedEffect(viewModel) { viewModel.effects.collect { close(it.planName) } }
    OperationPlanLinkContent(
        state,
        viewModel::select,
        viewModel::group,
        viewModel::chooseAnother,
        viewModel::proceed,
        viewModel::dismiss,
        viewModel::load,
        modifier
    )
}

@Suppress("LongParameterList")
@Composable
internal fun OperationPlanLinkContent(
    state: OperationPlanLinkState,
    onSelect: (SuggestedPlan) -> Unit,
    onGroup: (String?) -> Unit,
    onChooseAnother: () -> Unit,
    onProceed: () -> Unit,
    onDismiss: () -> Unit,
    onReload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val error = state.error
    val title = stringResource(offerTitle(state.page, error))
    val limit = offerHeightLimit()
    val focus = remember { FocusRequester() }
    val grouping = state.groupKey
    val page = state.page
    Column(
        modifier.fillMaxWidth().heightIn(max = limit)
            .testTag("operation_plan_link_sheet").semantics { paneTitle = title }
    ) {
        key(page, grouping) {
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false).fillMaxWidth()
                    .selectableGroup().testTag("operation_plan_link_body"),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    OfferHeader(
                        title = title,
                        operation = state.operation,
                        page = page,
                        error = error,
                        loading = state.context == null && error == null,
                        modifier = Modifier.focusRequester(focus).focusable()
                    )
                }
                if (error != null) item { OfferWarning(error) }
                when (page) {
                    PlanOfferPage.Loading -> Unit
                    PlanOfferPage.Review -> reviewItems(state, onChooseAnother)
                    PlanOfferPage.Suggestions -> suggestionItems(state, onSelect, onChooseAnother)
                    PlanOfferPage.Manual -> manualItems(state, onSelect, onGroup)
                }
            }
        }
        SheetActions(page, error, state.canProceed, state.saving, onProceed, onDismiss, onReload)
    }
    if (!LocalInspectionMode.current) {
        LaunchedEffect(page, grouping) {
            withFrameNanos { }
            focus.requestFocus()
        }
    }
}

private fun offerTitle(
    page: PlanOfferPage,
    error: PlanOfferError?
): Int = when {
    error == PlanOfferError.Changed && page == PlanOfferPage.Review -> R.string.plan_offer_changed_title
    page == PlanOfferPage.Suggestions -> R.string.plan_offer_choices_title
    page == PlanOfferPage.Manual -> R.string.plan_offer_manual_title
    else -> R.string.plan_offer_title
}

@Composable
private fun offerHeightLimit(): androidx.compose.ui.unit.Dp {
    val fraction = if (LocalDensity.current.fontScale >= LARGE_TEXT_SCALE) LARGE_SHEET_HEIGHT else SHEET_HEIGHT
    return (LocalConfiguration.current.screenHeightDp.dp * fraction - HANDLE_HEIGHT.dp).coerceAtLeast(0.dp)
}

private fun LazyListScope.reviewItems(
    state: OperationPlanLinkState,
    onChooseAnother: () -> Unit
) {
    state.selected?.let { selection ->
        item {
            val isStale = state.error in setOf(PlanOfferError.Unavailable, PlanOfferError.Changed)
            val showResult = state.context != null && !isStale
            ReviewCard(selection, showResult)
        }
    }
    if (state.context != null) {
        item { OtherPlanButton(R.string.plan_link_choose_another, !state.saving, onChooseAnother) }
    }
}

private fun LazyListScope.suggestionItems(
    state: OperationPlanLinkState,
    onSelect: (SuggestedPlan) -> Unit,
    onChooseAnother: () -> Unit
) {
    val choices = state.offeredPlans()
    paymentItems(choices?.plans.orEmpty(), state, onSelect)
    if (state.context != null) {
        val label = if (choices?.hasMore == true) R.string.plan_offer_all else R.string.plan_link_choose_another
        item { OtherPlanButton(label, !state.saving, onChooseAnother) }
    }
}

private fun LazyListScope.manualItems(
    state: OperationPlanLinkState,
    onSelect: (SuggestedPlan) -> Unit,
    onGroup: (String?) -> Unit
) {
    val groups = state.groups
    if (state.groupKey == null) {
        items(groups, key = { it.key }) { group ->
            PlanGroupChoice(group.name, group.frequency, !state.saving, { onGroup(group.key) })
        }
        if (groups.isEmpty()) item { Text(stringResource(R.string.plan_link_empty)) }
    } else {
        val group = groups.firstOrNull { it.key == state.groupKey }
        item { OtherPlanButton(R.string.plan_picker_rules, !state.saving, { onGroup(null) }) }
        paymentItems(group?.payments.orEmpty(), state, onSelect)
        if (group == null) item { Text(stringResource(R.string.plan_link_empty)) }
    }
}

private fun LazyListScope.paymentItems(
    plans: List<SuggestedPlan>,
    state: OperationPlanLinkState,
    onSelect: (SuggestedPlan) -> Unit
) {
    items(plans, key = { it.target.stableKey }) { plan ->
        PaymentChoice(plan, state.selected?.plan == plan, state.canSelect(plan), onClick = { onSelect(plan) })
    }
}

private const val LARGE_TEXT_SCALE = 1.5f
private const val LARGE_SHEET_HEIGHT = 0.96f
private const val SHEET_HEIGHT = 0.94f
private const val HANDLE_HEIGHT = 20

@Composable
private fun OfferHeader(
    title: String,
    operation: PlanLinkOperation?,
    page: PlanOfferPage,
    error: PlanOfferError?,
    loading: Boolean,
    modifier: Modifier = Modifier
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (operation != null) {
            Text(
                stringResource(
                    if (operation.type ==
                        "INCOME"
                    ) {
                        R.string.plan_offer_income_saved
                    } else {
                        R.string.plan_offer_expense_saved
                    }
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(title, modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
        val unavailable = error == PlanOfferError.Changed || error == PlanOfferError.Unavailable
        val intro = when {
            loading -> R.string.plan_offer_loading
            unavailable || page == PlanOfferPage.Loading -> null
            page == PlanOfferPage.Review -> R.string.plan_offer_review_intro
            else -> R.string.plan_offer_choose_payment
        }
        intro?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
        if (operation != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CategoryIconImage(operation.categoryIconCode, operation.categoryName)
                Column(Modifier.weight(1f)) {
                    val type = if (operation.type == "INCOME") OperationType.Income else OperationType.Expense
                    Text(
                        formatOperationAmount(operation.amount.toBigDecimal(), operation.currency, type),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        stringResource(
                            R.string.plan_offer_category_date,
                            operation.categoryName,
                            offerDate(operation.occurredOn)
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            HorizontalDivider()
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable
private fun ReviewCard(
    selection: PlanOfferSelection,
    showResult: Boolean,
    modifier: Modifier = Modifier
) {
    val plan = selection.plan
    val operation = selection.operation
    val planned = plan.plannedAmount.toBigDecimal()
    val remaining = plan.remainingAmount.toBigDecimal()
    val amount = operation.netAmount.toBigDecimal()
    val currency = operation.currency
    val after = (remaining - amount).max(BigDecimal.ZERO)
    val income = operation.type == "INCOME"
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(
                when {
                    !showResult -> R.string.plan_offer_payment
                    selection.isManual -> R.string.plan_offer_selected
                    else -> R.string.plan_offer_similar
                }
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedCard(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(plan.displayName, style = MaterialTheme.typography.titleMedium)
                Text(formatMoney(planned, currency), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(
                        R.string.plan_offer_category_date,
                        offerDate(plan.target.dueOn),
                        stringResource(plan.frequency.labelRes())
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (showResult) {
                    if (!selection.isManual) {
                        Text(
                            stringResource(
                                if (amount.compareTo(remaining) == 0) {
                                    R.string.plan_offer_reason_exact
                                } else {
                                    R.string.plan_offer_reason_near
                                }
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (planned > remaining || amount.compareTo(remaining) != 0) {
                        Text(stringResource(R.string.plan_offer_current_remaining, formatMoney(remaining, currency)))
                    }
                    HorizontalDivider()
                    ReviewOutcome(income, planned, remaining, after, amount, currency)
                }
            }
        }
    }
}

@Composable
private fun ReviewOutcome(
    income: Boolean,
    planned: BigDecimal,
    remaining: BigDecimal,
    after: BigDecimal,
    amount: BigDecimal,
    currency: String,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val outcome = if (after.signum() == 0) {
            stringResource(if (income) R.string.plan_offer_income_complete else R.string.plan_offer_expense_complete)
        } else {
            stringResource(
                if (income) R.string.plan_offer_income_remaining else R.string.plan_offer_expense_remaining,
                formatMoney(after, currency)
            )
        }
        Text(outcome, style = MaterialTheme.typography.titleSmall)
        if (planned > remaining) {
            Text(
                stringResource(
                    if (income) R.string.plan_offer_received else R.string.plan_offer_paid,
                    formatMoney(planned - remaining, currency),
                    formatMoney(amount, currency)
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (amount > remaining) {
            Text(
                stringResource(
                    if (income) R.string.plan_offer_income_over else R.string.plan_offer_expense_over,
                    formatMoney(amount - remaining, currency)
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun PaymentChoice(
    plan: SuggestedPlan,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp)
            .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (selected) colors.primary else colors.outlineVariant),
        color = if (selected) colors.primaryContainer else colors.surface
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RadioButton(selected, onClick = null, enabled = enabled)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val currency = requireNotNull(plan.currency)
                Text(plan.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(
                        R.string.plan_offer_category_date,
                        offerDate(plan.target.dueOn),
                        stringResource(plan.frequency.labelRes())
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    formatMoney(plan.plannedAmount.toBigDecimal(), currency),
                    style = MaterialTheme.typography.titleSmall
                )
                if (plan.plannedAmount.toBigDecimal() > plan.remainingAmount.toBigDecimal()) {
                    Text(
                        stringResource(
                            R.string.plan_offer_current_remaining,
                            formatMoney(plan.remainingAmount.toBigDecimal(), currency)
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanGroupChoice(
    name: String,
    frequency: PlanningFrequency,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(onClick, modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = enabled) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(frequency.labelRes()), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun OtherPlanButton(
    label: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(onClick, modifier.heightIn(min = 48.dp), enabled = enabled) { Text(stringResource(label)) }
}

@Composable
private fun OfferWarning(
    error: PlanOfferError,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Text(
            stringResource(
                when (error) {
                    PlanOfferError.Load -> R.string.plan_offer_load_error
                    PlanOfferError.Save -> R.string.plan_offer_save_error
                    PlanOfferError.Dismiss -> R.string.plan_offer_dismiss_error
                    PlanOfferError.Changed -> R.string.plan_offer_changed
                    PlanOfferError.Unavailable -> R.string.plan_link_unavailable
                }
            ),
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun SheetActions(
    page: PlanOfferPage,
    error: PlanOfferError?,
    enabled: Boolean,
    saving: Boolean,
    onProceed: () -> Unit,
    onDismiss: () -> Unit,
    onReload: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider()
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 6.dp)) {
            val label = when {
                saving -> R.string.plan_offer_saving
                error == PlanOfferError.Load || error == PlanOfferError.Save -> R.string.plan_offer_retry
                page == PlanOfferPage.Suggestions || page == PlanOfferPage.Manual -> R.string.plan_offer_continue
                else -> R.string.plan_offer_confirm
            }
            SaldomiButton(
                onClick = if (error == PlanOfferError.Load) onReload else onProceed,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled || error == PlanOfferError.Load,
                large = true,
                isLoading = saving
            ) {
                Text(stringResource(label))
            }
            TextButton(onDismiss, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !saving) {
                Text(stringResource(R.string.plan_link_skip))
            }
        }
    }
}

@Composable
private fun offerDate(
    date: String
): String =
    LocalDate.parse(date).format(DateTimeFormatter.ofPattern("d MMMM yyyy", LocalConfiguration.current.locales[0]))

private fun PlanningFrequency.labelRes(): Int = when (this) {
    PlanningFrequency.Once -> R.string.income_frequency_once
    PlanningFrequency.Weekly -> R.string.income_frequency_weekly
    PlanningFrequency.Biweekly -> R.string.income_frequency_biweekly
    PlanningFrequency.Monthly -> R.string.income_frequency_monthly
}
