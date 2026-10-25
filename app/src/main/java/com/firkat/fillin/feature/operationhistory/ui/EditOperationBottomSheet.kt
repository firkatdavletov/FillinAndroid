package com.firkat.fillin.feature.operationhistory.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.firkat.fillin.ui.navigation.LocalBottomSheetDismissController
import com.firkat.fillin.ui.theme.FillinTheme
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import java.math.BigDecimal
import java.time.LocalDate

@Composable
internal fun EditOperationBottomSheet(
    operationId: String,
    onClose: () -> Unit,
    onResult: (EditOperationResult) -> Unit,
    viewModel: EditOperationViewModel = koinViewModel { parametersOf(operationId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val close by rememberUpdatedState(onClose)
    val result by rememberUpdatedState(onResult)
    val dismissController = LocalBottomSheetDismissController.current
    // Read the model directly: dismissal is blocked immediately, before the next composition.
    DisposableEffect(dismissController, viewModel) {
        val guard = { viewModel.canDismiss }
        dismissController.canDismiss = guard
        onDispose { if (dismissController.canDismiss === guard) dismissController.canDismiss = { true } }
    }
    BackHandler { viewModel.onEvent(EditOperationEvent.CloseClicked) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                EditOperationEffect.Close -> close()
                is EditOperationEffect.Saved -> result(effect.result)
            }
        }
    }
    EditOperationContent(
        operation = state.operation, mode = state.mode, input = state.input, type = state.type,
        categories = state.categories, selectedCategoryId = state.categoryId, isLoading = state.isLoading,
        isSaving = state.isSaving, canSave = state.canSave, canRefund = state.canRefund,
        inputError = state.inputError, error = state.error,
        description = state.description, descriptionError = state.descriptionError,
        onDescriptionChange = { viewModel.onEvent(EditOperationEvent.DescriptionChanged(it)) },
        onInputChange = { viewModel.onEvent(EditOperationEvent.InputChanged(it)) },
        onTypeClick = { viewModel.onEvent(EditOperationEvent.TypeChanged(it)) },
        onCategoryClick = { viewModel.onEvent(EditOperationEvent.CategorySelected(it)) },
        onSaveClick = { viewModel.onEvent(EditOperationEvent.SaveClicked) },
        onRefundClick = { viewModel.onEvent(EditOperationEvent.RefundClicked) },
        onCloseClick = { viewModel.onEvent(EditOperationEvent.CloseClicked) },
    )
    if (state.isRefundConfirmationVisible) {
        RefundConfirmation(
            onConfirm = { viewModel.onEvent(EditOperationEvent.RefundConfirmed) },
            onDismiss = { viewModel.onEvent(EditOperationEvent.RefundDismissed) },
        )
    }
}

@Composable
internal fun EditOperationContent(
    operation: Operation?,
    mode: EditOperationMode,
    input: String,
    type: OperationType,
    categories: List<Category>,
    selectedCategoryId: String?,
    isLoading: Boolean,
    isSaving: Boolean,
    canSave: Boolean,
    canRefund: Boolean,
    inputError: OperationInputError?,
    error: OperationHistoryError?,
    onInputChange: (String) -> Unit,
    onTypeClick: (OperationType) -> Unit,
    onCategoryClick: (String) -> Unit,
    onSaveClick: () -> Unit,
    onRefundClick: () -> Unit,
    onCloseClick: () -> Unit,
    description: String = operation?.description.orEmpty(),
    descriptionError: Boolean = false,
    onDescriptionChange: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().testTag("edit_operation_bottom_sheet")
            .imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.edit_operation_title), style = MaterialTheme.typography.titleLarge)
        if (isLoading) CircularProgressIndicator(modifier = Modifier.testTag("edit_operation_loading"))
        operation?.let {
            Text(it.categoryName, style = MaterialTheme.typography.titleMedium)
            Text(formatDateHeader(it.occurredOn), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (mode == EditOperationMode.ReadOnly || mode == EditOperationMode.Edit && it.refundedAmount.signum() != 0) {
                it.description?.takeIf(String::isNotBlank)?.let { description -> Text(description) }
            }
            if (mode == EditOperationMode.ReadOnly) {
                Text(formatOperationAmount(it.originalAmount, it.originalCurrency, it.type))
                Text(stringResource(R.string.edit_operation_read_only), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(stringResource(if (mode == EditOperationMode.Correct) R.string.operation_create_placeholder else R.string.operation_history_amount_label))
                SaldomiTextField(
                    value = input,
                    onValueChange = onInputChange,
                    enabled = !isSaving && (mode == EditOperationMode.Correct || it.refundedAmount.signum() == 0),
                    isError = inputError != null,
                    keyboardOptions = KeyboardOptions(keyboardType = if (mode == EditOperationMode.Correct) KeyboardType.Text else KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("edit_operation_input"),
                )
                inputError?.let { reason ->
                    Text(stringResource(when (reason) {
                        OperationInputError.DescriptionTooLong -> R.string.operation_create_description_too_long
                        OperationInputError.AmbiguousAmount -> R.string.operation_create_ambiguous_amount
                        else -> R.string.operation_create_invalid_input
                    }), color = MaterialTheme.colorScheme.error)
                }
                if (mode == EditOperationMode.Correct) {
                    OperationTypeTabs(type, !isSaving, onTypeClick)
                    if (categories.isEmpty()) Text(stringResource(R.string.operation_create_no_categories))
                    else OperationCategoryRow(categories, !isSaving, onCategoryClick, selectedCategoryId = selectedCategoryId)
                } else {
                    if (it.refundedAmount.signum() == 0) {
                        Text(stringResource(R.string.operation_create_description))
                        SaldomiTextField(
                            value = description, onValueChange = onDescriptionChange,
                            enabled = !isSaving, isError = descriptionError,
                            modifier = Modifier.fillMaxWidth().testTag("edit_operation_description"),
                        )
                        if (descriptionError) {
                            Text(stringResource(R.string.operation_create_description_too_long),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag("edit_operation_description_error"))
                        }
                        if (categories.isEmpty()) Text(stringResource(R.string.operation_create_no_categories))
                        else OperationCategoryRow(categories, !isSaving, onCategoryClick, selectedCategoryId = selectedCategoryId)
                    }
                    Text(
                        stringResource(if (it.refundedAmount.signum() == 0) R.string.edit_operation_details_hint else R.string.edit_operation_refunded),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PrimaryButton(
                    text = stringResource(if (mode == EditOperationMode.Correct) R.string.operation_sync_correct else R.string.operation_history_save_action),
                    onClick = onSaveClick, enabled = canSave, isLoading = isSaving,
                    modifier = Modifier.fillMaxWidth().testTag("edit_operation_save"),
                )
                if (mode == EditOperationMode.Edit && it.type == OperationType.Expense) {
                    TextButton(onClick = onRefundClick, enabled = canRefund, modifier = Modifier.fillMaxWidth().testTag("edit_operation_refund")) {
                        Text(stringResource(R.string.operation_history_refund_action))
                    }
                }
            }
        }
        error?.let {
            Text(
                stringResource(if (it == OperationHistoryError.Unknown) R.string.edit_operation_save_failed else it.messageResource),
                color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("edit_operation_error"),
            )
        }
        TextButton(onClick = onCloseClick, enabled = !isSaving, modifier = Modifier.fillMaxWidth().testTag("edit_operation_close")) {
            Text(stringResource(R.string.edit_operation_close))
        }
    }
}

@Composable
private fun RefundConfirmation(onConfirm: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.operation_history_refund_title)) },
        text = { Text(stringResource(R.string.operation_history_refund_message)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.operation_history_refund_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.operation_history_refund_cancel)) } },
    )
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
            inputError, error, {}, {}, {}, {}, {}, {}, description = description, descriptionError = descriptionError)
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
