package com.firkat.fillin.feature.budgettransfer.ui

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R

@Composable
internal fun BudgetTransferRoute(
    viewModel: BudgetTransferViewModel,
    profileIdle: Boolean,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openImportPicker = rememberBudgetImportPicker(viewModel)
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        viewModel.exportFile(uri?.toString())
    }
    val enabled = profileIdle && !state.busy
    Column(modifier) {
        BudgetTransferActions(
            canImport = enabled,
            canExport = enabled && state.canExport,
            working = state.working,
            onImportClick = openImportPicker,
            onExportClick = {
                viewModel.beginExport()?.let { filename ->
                    launchFilePicker(viewModel) { exportPicker.launch(filename) }
                }
            }
        )
        state.result?.let { BudgetTransferMessage(it, Modifier.padding(top = 8.dp)) }
    }
}

@Composable
internal fun rememberBudgetImportPicker(
    viewModel: BudgetTransferViewModel,
    nameSuffix: String = stringResource(R.string.budget_csv_name_suffix)
): () -> Unit {
    val fallbackName = stringResource(R.string.budget_csv_default_name)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.importFile(uri?.toString(), fallbackName, nameSuffix)
    }
    return {
        if (viewModel.beginImport()) launchFilePicker(viewModel) { picker.launch(arrayOf("*/*")) }
    }
}

private inline fun launchFilePicker(
    viewModel: BudgetTransferViewModel,
    launch: () -> Unit
) {
    try {
        launch()
    } catch (_: ActivityNotFoundException) {
        viewModel.pickerUnavailable()
    }
}
