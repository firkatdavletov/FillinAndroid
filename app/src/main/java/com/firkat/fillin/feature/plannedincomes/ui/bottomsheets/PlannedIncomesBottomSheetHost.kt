package com.firkat.fillin.feature.plannedincomes.ui.bottomsheets

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.firkat.fillin.R
import com.firkat.fillin.ui.component.SaldomiActionBottomSheet
import com.firkat.fillin.ui.component.SaldomiButton
import com.firkat.fillin.ui.component.SaldomiOutlinedButton
import com.firkat.fillin.ui.theme.FillinTheme

@Composable
internal fun PlannedIncomesBottomSheetHost(
    showDeleteConfirmation: Boolean,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (showDeleteConfirmation) {
        DeleteIncomeConfirmation(onDelete, onDismiss, modifier)
    }
}

@Composable
private fun DeleteIncomeConfirmation(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    SaldomiActionBottomSheet(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.planned_incomes_delete_title)) },
        text = { Text(stringResource(R.string.planned_incomes_delete_message)) },
        confirmButton = {
            SaldomiButton(onClick = onConfirm, modifier = Modifier.fillMaxWidth(), large = true, destructive = true) {
                Text(
                    text = stringResource(R.string.planned_incomes_delete_confirm)
                )
            }
        },
        dismissButton = {
            SaldomiOutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), large = true) {
                Text(stringResource(R.string.planned_incomes_delete_cancel))
            }
        }
    )
}

@Preview(name = "Delete income")
@Preview(name = "Delete income dark", uiMode = UI_MODE_NIGHT_YES)
@Preview(name = "Delete income large text", widthDp = 320, fontScale = 2f)
@Composable
private fun DeleteIncomePreview() {
    FillinTheme { PlannedIncomesBottomSheetHost(true, {}, {}) }
}
