package com.firkat.fillin.ui.component

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.firkat.fillin.R
import com.firkat.fillin.ui.theme.FillinTheme

/** Shared layout for confirmations and forms, with the same spacing and actions as budget sheets. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SaldomiActionBottomSheet(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    dismissButton: (@Composable () -> Unit)? = null,
    dismissEnabled: Boolean = true,
    edgeToEdgeContent: Boolean = false,
    text: (@Composable () -> Unit)? = null,
) {
    val currentDismissEnabled = rememberUpdatedState(dismissEnabled)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || currentDismissEnabled.value },
    )
    SaldomiBottomSheet(
        onDismissRequest = { if (dismissEnabled) onDismissRequest() },
        modifier = modifier,
        sheetState = sheetState,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = dismissEnabled),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                title?.let {
                    Column(Modifier.padding(horizontal = 24.dp)) {
                        ProvideTextStyle(MaterialTheme.typography.headlineSmall, it)
                    }
                }
                text?.let {
                    Column(Modifier.fillMaxWidth().padding(horizontal = if (edgeToEdgeContent) 0.dp else 24.dp)) {
                        ProvideTextStyle(MaterialTheme.typography.bodyLarge, it)
                    }
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                confirmButton()
                dismissButton?.invoke()
            }
        }
    }
}

@Preview(name = "Confirmation")
@Preview(name = "Confirmation dark", uiMode = UI_MODE_NIGHT_YES)
@Preview(name = "Confirmation large text", widthDp = 320, fontScale = 2f)
@Preview(name = "Confirmation wide", widthDp = 840)
@Composable
private fun ActionSheetPreview() {
    FillinTheme {
        SaldomiActionBottomSheet(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.planned_expenses_delete_title)) },
            text = { Text(stringResource(R.string.planned_expenses_delete_message)) },
            confirmButton = {
                SaldomiButton({}, Modifier.fillMaxWidth(), large = true, destructive = true) {
                    Text(stringResource(R.string.planned_expenses_delete_confirm))
                }
            },
            dismissButton = {
                SaldomiOutlinedButton({}, Modifier.fillMaxWidth(), large = true) {
                    Text(stringResource(R.string.planned_expenses_delete_cancel))
                }
            },
        )
    }
}

@Preview(name = "Busy")
@Preview(name = "Busy dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ActionSheetBusyPreview() {
    FillinTheme {
        SaldomiActionBottomSheet(
            onDismissRequest = {},
            dismissEnabled = false,
            title = { Text(stringResource(R.string.profile_new_budget)) },
            confirmButton = {
                SaldomiButton({}, Modifier.fillMaxWidth(), large = true, isLoading = true) {
                    Text(stringResource(R.string.cloud_budget_create))
                }
            },
        )
    }
}
