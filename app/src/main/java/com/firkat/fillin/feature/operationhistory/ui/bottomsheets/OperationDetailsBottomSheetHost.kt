package com.firkat.fillin.feature.operationhistory.ui.bottomsheets

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.firkat.fillin.R
import com.firkat.fillin.feature.categories.domain.model.Category
import com.firkat.fillin.feature.categories.ui.bottomsheets.CategorySelectionSheet
import com.firkat.fillin.feature.operationhistory.ui.OperationDetailsWindow
import com.firkat.fillin.ui.component.SaldomiActionBottomSheet
import com.firkat.fillin.ui.component.SaldomiButton
import com.firkat.fillin.ui.component.SaldomiOutlinedButton
import com.firkat.fillin.ui.theme.FillinTheme

@Composable
internal fun OperationDetailsBottomSheetHost(
    window: OperationDetailsWindow?,
    readOnly: Boolean,
    categories: List<Category>,
    categoryId: String?,
    showRefundConfirmation: Boolean,
    dirty: Boolean,
    onCategorySelected: (String) -> Unit,
    onDismissWindow: () -> Unit,
    onDiscard: () -> Unit,
    onUnlink: () -> Unit,
    onRefund: () -> Unit,
    onDismissRefund: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (readOnly) return

    when (window) {
        OperationDetailsWindow.Category -> CategorySelectionSheet(
            categories = categories,
            selectedId = categoryId,
            loading = false,
            hasError = false,
            onSelect = onCategorySelected,
            onDismiss = onDismissWindow,
            onRetry = {},
            modifier = modifier
        )
        OperationDetailsWindow.Discard -> OperationDetailsConfirmation(
            R.string.rule_discard_question,
            onDiscard,
            onDismissWindow,
            modifier
        )
        OperationDetailsWindow.Unlink -> OperationDetailsConfirmation(
            R.string.operation_details_unlink_question,
            onUnlink,
            onDismissWindow,
            modifier
        )
        null -> Unit
    }
    if (showRefundConfirmation) {
        RefundConfirmation(onRefund, onDismissRefund, modifier, dirty)
    }
}

@Composable
private fun OperationDetailsConfirmation(
    @StringRes title: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    SaldomiActionBottomSheet(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        confirmButton = {
            SaldomiButton(onConfirm, modifier = Modifier.fillMaxWidth(), large = true) {
                Text(stringResource(R.string.rule_confirm))
            }
        },
        dismissButton = {
            SaldomiOutlinedButton(onDismiss, modifier = Modifier.fillMaxWidth(), large = true) {
                Text(stringResource(R.string.rule_cancel))
            }
        }
    )
}

@Composable
private fun RefundConfirmation(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dirty: Boolean = false
) {
    SaldomiActionBottomSheet(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.operation_history_refund_title)) },
        text = {
            Text(
                stringResource(
                    if (dirty) R.string.operation_details_refund_draft else R.string.operation_history_refund_message
                )
            )
        },
        confirmButton = {
            SaldomiButton(onConfirm, modifier = Modifier.fillMaxWidth(), large = true) {
                Text(stringResource(R.string.operation_history_refund_confirm))
            }
        },
        dismissButton = {
            SaldomiOutlinedButton(onDismiss, modifier = Modifier.fillMaxWidth(), large = true) {
                Text(stringResource(R.string.operation_history_refund_cancel))
            }
        }
    )
}

@Preview(name = "Refund confirmation")
@Preview(name = "Refund confirmation dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun RefundConfirmationPreview() {
    FillinTheme { RefundConfirmation({}, {}) }
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun RefundWithDraftPreview() {
    FillinTheme { RefundConfirmation({}, {}, dirty = true) }
}

@Preview(name = "Discard operation changes")
@Preview(name = "Discard operation changes dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun DiscardOperationPreview() {
    FillinTheme { OperationDetailsConfirmation(R.string.rule_discard_question, {}, {}) }
}

@Preview(name = "Unlink plan")
@Preview(name = "Unlink plan dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun UnlinkOperationPreview() {
    FillinTheme { OperationDetailsConfirmation(R.string.operation_details_unlink_question, {}, {}) }
}
