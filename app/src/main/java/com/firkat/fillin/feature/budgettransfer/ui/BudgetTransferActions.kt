package com.firkat.fillin.feature.budgettransfer.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.firkat.fillin.R
import com.firkat.fillin.feature.budgettransfer.domain.TransferFailure
import com.firkat.fillin.ui.component.SaldomiOutlinedButton
import com.firkat.fillin.ui.theme.FillinTheme

@Composable
internal fun BudgetTransferActions(
    canImport: Boolean,
    canExport: Boolean,
    working: Boolean,
    onImportClick: () -> Unit,
    onExportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        SaldomiOutlinedButton(
            onImportClick,
            Modifier.fillMaxWidth().testTag("budget_csv_import"),
            enabled = canImport
        ) {
            Text(stringResource(R.string.budget_csv_import))
        }
        SaldomiOutlinedButton(
            onExportClick,
            Modifier.fillMaxWidth().testTag("budget_csv_export"),
            enabled = canExport
        ) {
            Text(stringResource(R.string.budget_csv_export))
        }
        if (working) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("budget_csv_progress"))
    }
}

@Composable
internal fun BudgetTransferMessage(
    result: BudgetTransferResult,
    modifier: Modifier = Modifier
) {
    val message = when (result) {
        BudgetTransferResult.Exported -> stringResource(R.string.budget_csv_exported)
        is BudgetTransferResult.Imported -> stringResource(
            if (result.selected) R.string.budget_csv_imported else R.string.budget_csv_imported_inactive
        )
        is BudgetTransferResult.Failed -> when (result.reason) {
            TransferFailure.InvalidFile -> result.row?.let { stringResource(R.string.budget_csv_invalid_row, it) }
                ?: stringResource(R.string.budget_csv_invalid)
            TransferFailure.UnsupportedVersion -> stringResource(R.string.budget_csv_unsupported)
            TransferFailure.FileAccess -> stringResource(R.string.budget_csv_file_error)
            TransferFailure.LocalLimit -> stringResource(R.string.budget_csv_limit)
            TransferFailure.SourceUnavailable -> stringResource(R.string.budget_csv_source_unavailable)
            null -> stringResource(R.string.budget_csv_generic_error)
        }
    }
    Text(
        message,
        modifier.semantics { liveRegion = LiveRegionMode.Polite },
        color = if (result is BudgetTransferResult.Failed) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        style = MaterialTheme.typography.bodyMedium
    )
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(widthDp = 320, fontScale = 2f)
@Composable
private fun AvailableTransferPreview() {
    FillinTheme { BudgetTransferActions(true, true, false, {}, {}) }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ImportOnlyPreview() {
    FillinTheme { BudgetTransferActions(true, false, false, {}, {}) }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun UnavailableTransferPreview() {
    FillinTheme { BudgetTransferActions(false, false, false, {}, {}) }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WorkingTransferPreview() {
    FillinTheme { BudgetTransferActions(false, false, true, {}, {}) }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TransferSuccessPreview() {
    FillinTheme { BudgetTransferMessage(BudgetTransferResult.Exported) }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(widthDp = 320, fontScale = 2f)
@Composable
private fun TransferFailurePreview() {
    FillinTheme { BudgetTransferMessage(BudgetTransferResult.Failed(TransferFailure.LocalLimit)) }
}
