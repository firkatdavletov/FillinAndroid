package com.firkat.fillin.feature.operationhistory.ui

import com.firkat.fillin.feature.budgetprofile.ui.LocalBudgetCurrency

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.res.painterResource
import com.firkat.fillin.ui.component.CategorySelectionSheet
import com.firkat.fillin.feature.operationhistory.domain.repository.SuggestedPlan
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationType
import java.time.format.DateTimeFormatter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.categories.domain.model.Category
import com.firkat.fillin.feature.operationhistory.domain.model.Operation
import com.firkat.fillin.feature.operationhistory.domain.model.OperationStatus
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.feature.operationhistory.domain.usecase.OperationInputError
import com.firkat.fillin.ui.component.PrimaryButton
import com.firkat.fillin.ui.component.SaldomiTextField
import com.firkat.fillin.ui.theme.FillinTheme
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import java.math.BigDecimal
import java.time.LocalDate

@Composable
internal fun OperationDetailsRoute(
    operationId: String, onClose: () -> Unit, onResult: (EditOperationResult) -> Unit,
    onLinkSelected: () -> Unit, onOpenPlan: (PlannedOperationType, String) -> Unit,
    viewModel: EditOperationViewModel = koinViewModel { parametersOf(operationId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val close by rememberUpdatedState(onClose)
    val result by rememberUpdatedState(onResult)
    val openPlan by rememberUpdatedState(onOpenPlan)
    val openLink by rememberUpdatedState(onLinkSelected)
    val event = viewModel::onEvent
    BackHandler { event(EditOperationEvent.CloseClicked) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect -> when (effect) {
            EditOperationEffect.Close -> close()
            is EditOperationEffect.Saved -> result(effect.result)
            EditOperationEffect.OpenLinkConfirmation -> openLink()
            is EditOperationEffect.OpenPlan -> openPlan(effect.type, effect.ruleId)
        } }
    }
    EditOperationContent(
        operation = state.operation, mode = state.mode, input = state.input, type = state.type,
        categories = state.categories, selectedCategoryId = state.categoryId, isLoading = state.isLoading,
        isSaving = state.isSaving, canSave = state.canSave, canRefund = state.canRefund,
        inputError = state.inputError, error = state.error, description = state.description, descriptionError = state.descriptionError,
        onDescriptionChange = { event(EditOperationEvent.DescriptionChanged(it)) },
        onInputChange = { event(EditOperationEvent.InputChanged(it)) }, onTypeClick = { event(EditOperationEvent.TypeChanged(it)) },
        onCategoryOpen = { event(EditOperationEvent.WindowChanged(OperationDetailsWindow.Category)) },
        onSaveClick = { event(EditOperationEvent.SaveClicked) }, onRefundClick = { event(EditOperationEvent.RefundClicked) },
        onCloseClick = { event(EditOperationEvent.CloseClicked) },
        planLinksContent = {
            state.links.current.forEach { plan ->
                OperationLinkedPlanCard(plan, !state.isSaving && state.mode == EditOperationMode.Edit,
                    { event(EditOperationEvent.WindowChanged(OperationDetailsWindow.Unlink)) }, { event(EditOperationEvent.OpenPlan(plan)) })
            }
            if (state.links.current.isEmpty()) OutlinedButton(onClick = onLinkSelected,
                enabled = !state.isSaving && state.mode == EditOperationMode.Edit && state.operation?.let { it.amount > it.refundedAmount } == true,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.operation_details_bind))
            }
            if (state.planError) Text(stringResource(R.string.operation_details_plan_error), color = MaterialTheme.colorScheme.error)
        },
    )
    if (state.window == OperationDetailsWindow.Category) CategorySelectionSheet(state.categories, state.categoryId, false, false,
        { event(EditOperationEvent.CategorySelected(it)) }, { event(EditOperationEvent.WindowChanged(null)) }, {})
    if (state.window == OperationDetailsWindow.Discard || state.window == OperationDetailsWindow.Unlink) AlertDialog(
        onDismissRequest = { event(EditOperationEvent.WindowChanged(null)) },
        title = { Text(stringResource(if (state.window == OperationDetailsWindow.Discard) R.string.rule_discard_question else R.string.operation_details_unlink_question)) },
        confirmButton = { TextButton(onClick = { event(if (state.window == OperationDetailsWindow.Discard) EditOperationEvent.DiscardConfirmed else EditOperationEvent.UnlinkConfirmed) }) { Text(stringResource(R.string.rule_confirm)) } },
        dismissButton = { TextButton(onClick = { event(EditOperationEvent.WindowChanged(null)) }) { Text(stringResource(R.string.rule_cancel)) } },
    )
    if (state.isRefundConfirmationVisible) RefundConfirmation(
        { event(EditOperationEvent.RefundConfirmed) }, { event(EditOperationEvent.RefundDismissed) }, dirty = state.dirty)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditOperationContent(
    operation: Operation?, mode: EditOperationMode, input: String, type: OperationType,
    categories: List<Category>, selectedCategoryId: String?, isLoading: Boolean, isSaving: Boolean,
    canSave: Boolean, canRefund: Boolean, inputError: OperationInputError?, error: OperationHistoryError?,
    onInputChange: (String) -> Unit, onTypeClick: (OperationType) -> Unit,
    onSaveClick: () -> Unit, onRefundClick: () -> Unit, onCloseClick: () -> Unit,
    description: String = operation?.description.orEmpty(), descriptionError: Boolean = false,
    onDescriptionChange: (String) -> Unit = {}, modifier: Modifier = Modifier,
    planLinksContent: @Composable () -> Unit = {}, onCategoryOpen: () -> Unit = {},
) {
    val editable = !isSaving && (mode == EditOperationMode.Correct || mode == EditOperationMode.Edit && operation?.refundedAmount?.signum() == 0)
    val locale = LocalConfiguration.current.locales[0]
    Scaffold(modifier.fillMaxSize().testTag("operation_details"), topBar = {
        TopAppBar(title = { Text(stringResource(R.string.edit_operation_title)) }, navigationIcon = {
            IconButton(onClick = onCloseClick, enabled = !isSaving, modifier = Modifier.testTag("edit_operation_close")) {
                Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigation_back))
            }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                if (mode == EditOperationMode.Correct) OperationTypeTabs(type = type, enabled = !isSaving, onTypeClick = onTypeClick)
                else Text(stringResource(if (type == OperationType.Expense) R.string.planned_operations_expense else R.string.planned_operations_income), style = MaterialTheme.typography.titleMedium)
                planLinksContent()
                if (isLoading) CircularProgressIndicator(Modifier.testTag("edit_operation_loading"))
                operation?.let { op ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.rule_category), style = MaterialTheme.typography.titleMedium)
                        OutlinedCard(onClick = onCategoryOpen, enabled = editable, modifier = Modifier.fillMaxWidth().testTag("operation_details_category")) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                val selected = categories.firstOrNull { it.id == selectedCategoryId }
                                selected?.let { com.firkat.fillin.ui.component.CategoryIconImage(it.icon.code) }
                                Text(selected?.name ?: op.categoryName, Modifier.weight(1f))
                                if (editable) Text("›")
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.operation_details_date), style = MaterialTheme.typography.titleMedium)
                        OutlinedCard(Modifier.fillMaxWidth().testTag("operation_details_date")) {
                            Text(op.occurredOn.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale)), Modifier.padding(16.dp))
                        }
                    }
                    if (mode == EditOperationMode.ReadOnly) {
                        Text(formatOperationAmount(op.amount, op.currency, op.type))
                        if (op.hasOriginalValuation) {
                            Text(stringResource(R.string.operation_original_amount, formatOperationAmount(op.originalAmount, op.originalCurrency, op.type)))
                            op.rubKztRate?.let { Text(stringResource(R.string.operation_original_rub_rate, it.toPlainString())) }
                        }
                        op.description?.let { Text(it) }
                        Text(stringResource(R.string.edit_operation_read_only))
                    } else {
                        Text(stringResource(if (mode == EditOperationMode.Correct) R.string.operation_create_placeholder else R.string.operation_history_amount_label), style = MaterialTheme.typography.titleMedium)
                        SaldomiTextField(input, onInputChange, enabled = editable, isError = inputError != null,
                            keyboardOptions = KeyboardOptions(keyboardType = if (mode == EditOperationMode.Correct) KeyboardType.Text else KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth().testTag("edit_operation_input"))
                        inputError?.let { reason -> Text(stringResource(when (reason) {
                            OperationInputError.DescriptionTooLong -> R.string.operation_create_description_too_long
                            OperationInputError.AmbiguousAmount -> R.string.operation_create_ambiguous_amount
                            else -> R.string.operation_create_invalid_input
                        }), color = MaterialTheme.colorScheme.error) }
                        if (mode == EditOperationMode.Edit) {
                            Text(stringResource(R.string.operation_create_description), style = MaterialTheme.typography.titleMedium)
                            SaldomiTextField(description, onDescriptionChange, enabled = editable, isError = descriptionError,
                                placeholder = stringResource(R.string.operation_details_description_hint), modifier = Modifier.fillMaxWidth().testTag("edit_operation_description"))
                            if (descriptionError) Text(stringResource(R.string.operation_create_description_too_long), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("edit_operation_description_error"))
                            if (op.refundedAmount.signum() != 0) Text(stringResource(R.string.edit_operation_refunded))
                        }
                    }
                }
                error?.let { Text(stringResource(if (it == OperationHistoryError.Unknown) R.string.edit_operation_save_failed else it.messageResource), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("edit_operation_error")) }
            }
            if (operation != null && mode != EditOperationMode.ReadOnly) Column(Modifier.fillMaxWidth().padding(16.dp)) {
                PrimaryButton(stringResource(if (mode == EditOperationMode.Correct) R.string.operation_sync_correct else R.string.operation_details_save), onSaveClick,
                    enabled = canSave, isLoading = isSaving, modifier = Modifier.fillMaxWidth().testTag("edit_operation_save"))
                if (mode == EditOperationMode.Edit && type == OperationType.Expense) TextButton(onRefundClick, enabled = canRefund, modifier = Modifier.fillMaxWidth().testTag("edit_operation_refund")) {
                    Text(stringResource(R.string.operation_history_refund_action))
                }
            }
        }
    }
}

@Composable
private fun OperationLinkedPlanCard(plan: SuggestedPlan, enabled: Boolean, onUnlink: () -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    Card(modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
                    Icon(androidx.compose.material.icons.Icons.Outlined.EventAvailable, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(12.dp).size(28.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.operation_details_linked), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.operation_details_paid), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(plan.categoryName, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.operation_details_money, plan.plannedAmount, LocalBudgetCurrency.current.symbol), style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(R.string.operation_details_payment_date, LocalDate.parse(plan.target.dueOn).format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))), color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onUnlink, enabled = enabled, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)) {
                    Icon(androidx.compose.material.icons.Icons.Outlined.LinkOff, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.operation_details_unlink), style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(onOpen, enabled = enabled, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)) {
                    Icon(androidx.compose.material.icons.Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.operation_details_open_plan), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun RefundConfirmation(onConfirm: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier, dirty: Boolean = false) {
    AlertDialog(modifier = modifier, onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.operation_history_refund_title)) },
        text = { Text(stringResource(if (dirty) R.string.operation_details_refund_draft else R.string.operation_history_refund_message)) },
        confirmButton = { TextButton(onConfirm) { Text(stringResource(R.string.operation_history_refund_confirm)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.operation_history_refund_cancel)) } })
}

private val previewOperation = Operation("preview", "Продукты", "Супермаркет", BigDecimal("1200"), "KZT", BigDecimal("1200"), "KZT", LocalDate.of(2026, 10, 24), OperationStatus.Posted, 0)
private val previewIncome = previewOperation.copy(type = OperationType.Income, categoryName = "Зарплата", description = "Аванс")

@Composable
private fun EditPreview(
    mode: EditOperationMode = EditOperationMode.Edit,
    saving: Boolean = false,
    inputError: OperationInputError? = null,
    error: OperationHistoryError? = null,
    operation: Operation? = previewOperation,
    loading: Boolean = false,
    type: OperationType = OperationType.Expense,
    categories: List<Category> = previewOperationCategories(),
    input: String = "1300",
    canSave: Boolean = true,
    description: String = operation?.description.orEmpty(),
    descriptionError: Boolean = false,
) {
    val visibleCategories = categories.filter { it.kind == if (type == OperationType.Expense) com.firkat.fillin.feature.categories.domain.model.CategoryKind.Expense else com.firkat.fillin.feature.categories.domain.model.CategoryKind.Income }
    FillinTheme { Surface {
        EditOperationContent(operation, mode, input, type, visibleCategories, if (type == OperationType.Expense) "food" else "salary", loading, saving,
            canSave && !saving && inputError == null && operation?.refundedAmount?.signum() == 0,
            !saving && operation != null && operation.type == OperationType.Expense && operation.refundedAmount < operation.originalAmount,
            inputError, error, {}, {}, {}, {}, {}, description = description, descriptionError = descriptionError)
    } }
}

@Preview(name = "Edit") @Preview(name = "Edit dark", uiMode = UI_MODE_NIGHT_YES)
@Composable private fun EditOperationPreview() { EditPreview() }
@Preview(name = "Empty description") @Preview(name = "Empty description dark", uiMode = UI_MODE_NIGHT_YES)
@Composable private fun EditOperationEmptyDescriptionPreview() { EditPreview(description = "") }
@Preview(name = "Description error") @Preview(name = "Description error dark", uiMode = UI_MODE_NIGHT_YES)
@Composable private fun EditOperationDescriptionErrorPreview() { EditPreview(description = "Длинное описание ".repeat(12), descriptionError = true, canSave = false) }
@Preview(name = "No edit categories") @Preview(name = "No edit categories dark", uiMode = UI_MODE_NIGHT_YES)
@Composable private fun EditOperationNoEditCategoriesPreview() { EditPreview(categories = emptyList(), canSave = false) }
@Preview(name = "Unchanged") @Preview(name = "Unchanged dark", uiMode = UI_MODE_NIGHT_YES)
@Composable private fun EditOperationUnchangedPreview() { EditPreview(input = "1200", canSave = false) }
@Preview(name = "Saving") @Preview(name = "Saving dark", uiMode = UI_MODE_NIGHT_YES)
@Composable private fun EditOperationSavingPreview() { EditPreview(saving = true) }
@Preview(name = "Edit income") @Preview(name = "Edit income dark", uiMode = UI_MODE_NIGHT_YES)
@Composable private fun EditIncomeAmountPreview() { EditPreview(operation = previewIncome, type = OperationType.Income) }
@Preview(name = "Income unchanged") @Preview(name = "Income unchanged dark", uiMode = UI_MODE_NIGHT_YES)
@Composable private fun EditIncomeAmountUnchangedPreview() { EditPreview(operation = previewIncome, type = OperationType.Income, input = "1200", canSave = false) }
@Preview(name = "Income saving") @Preview(name = "Income saving dark", uiMode = UI_MODE_NIGHT_YES)
@Composable private fun EditIncomeAmountSavingPreview() { EditPreview(operation = previewIncome, type = OperationType.Income, saving = true) }
@Preview(name = "Income save failed") @Preview(name = "Income save failed dark", uiMode = UI_MODE_NIGHT_YES)
@Composable private fun EditIncomeAmountErrorPreview() { EditPreview(operation = previewIncome, type = OperationType.Income, error = OperationHistoryError.Unknown) }
@Preview(name = "Income invalid amount") @Preview(name = "Income invalid amount dark", uiMode = UI_MODE_NIGHT_YES)
@Composable private fun EditIncomeAmountInvalidPreview() { EditPreview(operation = previewIncome, type = OperationType.Income, input = "12.345", inputError = OperationInputError.InvalidAmount) }
@Preview(name = "Income compact", widthDp = 320, fontScale = 2f) @Preview(name = "Income wide", widthDp = 840)
@Composable private fun EditIncomeAmountLongPreview() { EditPreview(operation = previewIncome.copy(categoryName = "Вознаграждение за дополнительную работу", description = "Выплата за завершённую работу по семейному проекту"), type = OperationType.Income) }
@Preview(name = "Correct") @Preview(name = "Correct dark", uiMode = UI_MODE_NIGHT_YES)
@Composable private fun EditOperationCorrectPreview() { EditPreview(mode = EditOperationMode.Correct) }
@Preview(name = "Correct income") @Preview(name = "Correct income dark", uiMode = UI_MODE_NIGHT_YES)
@Composable private fun EditOperationIncomePreview() { EditPreview(mode = EditOperationMode.Correct, type = OperationType.Income) }
@Preview(name = "Correct saving") @Preview(name = "Correct saving dark", uiMode = UI_MODE_NIGHT_YES)
@Composable private fun EditOperationCorrectSavingPreview() { EditPreview(mode = EditOperationMode.Correct, saving = true) }
@Preview(name = "Correct income saving") @Preview(name = "Correct income saving dark", uiMode = UI_MODE_NIGHT_YES)
@Composable private fun EditOperationIncomeSavingPreview() { EditPreview(mode = EditOperationMode.Correct, type = OperationType.Income, saving = true) }
@Preview(name = "No categories") @Preview(name = "No categories dark", uiMode = UI_MODE_NIGHT_YES)
@Composable private fun EditOperationNoCategoriesPreview() { EditPreview(mode = EditOperationMode.Correct, categories = emptyList()) }
@Preview(name = "Read only") @Preview(name = "Read only dark", uiMode = UI_MODE_NIGHT_YES)
@Composable private fun EditOperationReadOnlyPreview() { EditPreview(mode = EditOperationMode.ReadOnly) }
@Preview(name = "Partially refunded") @Preview(name = "Partially refunded dark", uiMode = UI_MODE_NIGHT_YES)
@Composable private fun EditOperationRefundedPreview() { EditPreview(operation = previewOperation.copy(refundedAmount = BigDecimal("100"))) }
@Preview(name = "Fully refunded") @Preview(name = "Fully refunded dark", uiMode = UI_MODE_NIGHT_YES)
@Composable private fun EditOperationFullyRefundedPreview() { EditPreview(operation = previewOperation.copy(refundedAmount = previewOperation.originalAmount)) }
@Preview(name = "Loading") @Preview(name = "Loading dark", uiMode = UI_MODE_NIGHT_YES)
@Composable private fun EditOperationLoadingPreview() { EditPreview(operation = null, loading = true) }
@Preview(name = "Not found") @Preview(name = "Not found dark", uiMode = UI_MODE_NIGHT_YES)
@Composable private fun EditOperationMissingPreview() { EditPreview(operation = null, error = OperationHistoryError.NotFound) }
@Preview(name = "Invalid amount") @Preview(name = "Invalid amount dark", uiMode = UI_MODE_NIGHT_YES)
@Composable private fun EditOperationInvalidPreview() { EditPreview(inputError = OperationInputError.InvalidAmount, input = "12.345") }
@Preview(name = "Ambiguous amount") @Preview(name = "Ambiguous amount dark", uiMode = UI_MODE_NIGHT_YES)
@Composable private fun EditOperationAmbiguousPreview() { EditPreview(mode = EditOperationMode.Correct, inputError = OperationInputError.AmbiguousAmount, input = "12 продукты 34") }
@Preview(name = "Long description") @Preview(name = "Long description dark", uiMode = UI_MODE_NIGHT_YES)
@Composable private fun EditOperationDescriptionPreview() { EditPreview(mode = EditOperationMode.Correct, inputError = OperationInputError.DescriptionTooLong) }
@Preview(name = "Save failed") @Preview(name = "Save failed dark", uiMode = UI_MODE_NIGHT_YES)
@Composable private fun EditOperationErrorPreview() { EditPreview(error = OperationHistoryError.Unknown) }
@Preview(name = "Compact", widthDp = 320, fontScale = 2f) @Preview(name = "Wide", widthDp = 840)
@Composable private fun EditOperationLongPreview() { EditPreview(operation = previewOperation.copy(categoryName = "Обслуживание семейного автомобиля", description = "Плановое обслуживание и замена расходных материалов")) }
@Preview(name = "Refund confirmation") @Preview(name = "Refund confirmation dark", uiMode = UI_MODE_NIGHT_YES)
@Composable private fun RefundConfirmationPreview() { FillinTheme { RefundConfirmation({}, {}) } }

private val previewLinkedPlan = SuggestedPlan(com.firkat.fillin.feature.operationhistory.domain.repository.PlanLinkTarget("EXPENSE", ruleId = "rule", dueOn = "2026-11-04"), "health", "Здоровье", "585.00", "0.00", 1)
@Preview @Preview(uiMode = UI_MODE_NIGHT_YES) @Composable private fun LinkedPlanPreview() { FillinTheme { OperationLinkedPlanCard(previewLinkedPlan, true, {}, {}) } }
@Preview @Preview(uiMode = UI_MODE_NIGHT_YES) @Composable private fun LinkedPlanDisabledPreview() { FillinTheme { OperationLinkedPlanCard(previewLinkedPlan, false, {}, {}) } }
@Preview(widthDp = 320, fontScale = 2f) @Preview(widthDp = 840) @Composable private fun LinkedPlanLongPreview() { FillinTheme { OperationLinkedPlanCard(previewLinkedPlan.copy(categoryName = "Плановое обслуживание семейного автомобиля"), true, {}, {}) } }
@Preview @Preview(uiMode = UI_MODE_NIGHT_YES) @Composable private fun RefundWithDraftPreview() { FillinTheme { RefundConfirmation({}, {}, dirty = true) } }
