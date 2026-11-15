package com.firkat.fillin.feature.plannedexpenses.ui.bottomsheets

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.firkat.fillin.R
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpense
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpenseStatus
import com.firkat.fillin.feature.plannedexpenses.ui.PlannedExpensesError
import com.firkat.fillin.ui.component.SaldomiActionBottomSheet
import com.firkat.fillin.ui.component.SaldomiButton
import com.firkat.fillin.ui.component.SaldomiOutlinedButton
import com.firkat.fillin.ui.format.formatMoney
import com.firkat.fillin.ui.theme.FillinTheme
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@Composable
internal fun PlannedExpensesBottomSheetHost(
    editorOpen: Boolean,
    showDeleteConfirmation: Boolean,
    onDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    modifier: Modifier = Modifier,
    closeExpenseId: String? = null,
    expenses: List<PlannedExpense> = emptyList(),
    isClosing: Boolean = false,
    closeError: PlannedExpensesError? = null,
    onCloseExpense: () -> Unit = {},
    onDismissClose: () -> Unit = {}
) {
    if (editorOpen) {
        if (showDeleteConfirmation) {
            DeleteRuleConfirmation(onDelete, onDismissDelete, modifier)
        }
    } else if (closeExpenseId != null) {
        expenses.firstOrNull { it.id == closeExpenseId }?.let { expense ->
            CloseExpenseConfirmation(expense, isClosing, closeError, onCloseExpense, onDismissClose, modifier)
        }
    }
}

@Composable
private fun DeleteRuleConfirmation(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    SaldomiActionBottomSheet(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.planned_expenses_delete_title)) },
        text = { Text(stringResource(R.string.planned_expenses_delete_message)) },
        confirmButton = {
            SaldomiButton(onClick = onConfirm, modifier = Modifier.fillMaxWidth(), large = true, destructive = true) {
                Text(
                    text = stringResource(R.string.planned_expenses_delete_confirm)
                )
            }
        },
        dismissButton = {
            SaldomiOutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), large = true) {
                Text(stringResource(R.string.planned_expenses_delete_cancel))
            }
        }
    )
}

@Composable
private fun CloseExpenseConfirmation(
    expense: PlannedExpense,
    isClosing: Boolean,
    error: PlannedExpensesError?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    SaldomiActionBottomSheet(
        modifier = modifier,
        dismissEnabled = !isClosing,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.planned_expenses_close_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(
                        R.string.planned_expenses_close_message,
                        formatMoney(expense.paidAmount, expense.currency),
                        formatMoney(expense.plannedAmount, expense.currency),
                        formatMoney(expense.remainingAmount, expense.currency)
                    )
                )
                error?.let {
                    Text(
                        text = stringResource(it.closeMessageResource),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            SaldomiButton(
                onClick = onConfirm,
                enabled = !isClosing,
                modifier = Modifier.fillMaxWidth(),
                large = true
            ) {
                Text(
                    stringResource(
                        if (isClosing) {
                            R.string.planned_expenses_close_in_progress
                        } else {
                            R.string.planned_expenses_close_confirm
                        }
                    )
                )
            }
        },
        dismissButton = {
            SaldomiOutlinedButton(
                onClick = onDismiss,
                enabled = !isClosing,
                modifier = Modifier.fillMaxWidth(),
                large = true
            ) {
                Text(stringResource(R.string.planned_expenses_close_cancel))
            }
        }
    )
}

private val PlannedExpensesError.closeMessageResource: Int
    @StringRes get() = when (this) {
        PlannedExpensesError.MissingApiToken,
        PlannedExpensesError.Unauthorized
        -> R.string.planned_expenses_error_unauthorized

        PlannedExpensesError.Forbidden -> R.string.planned_expenses_close_error_forbidden
        PlannedExpensesError.InvalidRequest -> R.string.planned_expenses_close_error_invalid_request
        PlannedExpensesError.NotFound -> R.string.planned_expenses_close_error_not_found
        PlannedExpensesError.Conflict -> R.string.planned_expenses_close_error_conflict
        PlannedExpensesError.Network -> R.string.planned_expenses_error_network
        PlannedExpensesError.InvalidResponse -> R.string.planned_expenses_error_invalid_response
        PlannedExpensesError.Unknown -> R.string.planned_expenses_close_error_generic
    }

@Preview(name = "Delete expense")
@Preview(name = "Delete expense dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun DeleteExpensePreview() {
    FillinTheme { DeleteRuleConfirmation({}, {}) }
}

private val previewExpense = PlannedExpense(
    id = "rent",
    categoryId = "housing",
    categoryName = "Жильё",
    plannedAmount = BigDecimal("30000"),
    paidAmount = BigDecimal("10000"),
    remainingAmount = BigDecimal("20000"),
    currency = "KZT",
    dueOn = LocalDate.of(2026, 11, 15),
    overdue = false,
    status = PlannedExpenseStatus.Scheduled,
    version = 1,
    createdAt = Instant.EPOCH,
    modifiedAt = Instant.EPOCH
)

@Preview(name = "Close expense")
@Preview(name = "Close expense dark", uiMode = UI_MODE_NIGHT_YES)
@Preview(name = "Close expense large text", widthDp = 320, fontScale = 2f)
@Composable
private fun CloseExpensePreview() {
    FillinTheme { CloseExpenseConfirmation(previewExpense, false, null, {}, {}) }
}

@Preview(name = "Closing expense")
@Preview(name = "Closing expense dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ClosingExpensePreview() {
    FillinTheme { CloseExpenseConfirmation(previewExpense, true, null, {}, {}) }
}

@Preview(name = "Close expense failed")
@Preview(name = "Close expense failed dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CloseExpenseFailedPreview() {
    FillinTheme { CloseExpenseConfirmation(previewExpense, false, PlannedExpensesError.Network, {}, {}) }
}
