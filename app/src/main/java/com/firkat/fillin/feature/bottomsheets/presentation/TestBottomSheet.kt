package com.firkat.fillin.feature.bottomsheets.presentation

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.ui.theme.FillinTheme
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun TestBottomSheetRoute(
    operationId: String,
    action: TestBottomSheetAction,
    onClose: () -> Unit,
    onOperationAction: () -> Unit,
    viewModel: TestBottomSheetViewModel = koinViewModel { parametersOf(operationId, action) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnClose by rememberUpdatedState(onClose)
    val currentOnOperationAction by rememberUpdatedState(onOperationAction)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                TestBottomSheetEffect.Close -> currentOnClose()
                TestBottomSheetEffect.PerformOperationAction -> currentOnOperationAction()
            }
        }
    }
    TestBottomSheetContent(
        operationId = state.operationId,
        action = state.action,
        onCloseClick = { viewModel.onEvent(TestBottomSheetEvent.CloseClicked) },
        onActionClick = { viewModel.onEvent(TestBottomSheetEvent.ActionClicked) },
    )
}

@Composable
internal fun TestBottomSheetContent(
    operationId: String,
    action: TestBottomSheetAction,
    onCloseClick: () -> Unit,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().testTag("test_bottom_sheet")
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.test_bottom_sheet_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.test_bottom_sheet_operation_id, operationId),
            modifier = Modifier.testTag("test_bottom_sheet_operation_id"),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (action != TestBottomSheetAction.None) {
            Button(onClick = onActionClick, modifier = Modifier.fillMaxWidth().testTag("test_bottom_sheet_action")) {
                Text(stringResource(
                    if (action == TestBottomSheetAction.Correct) R.string.test_bottom_sheet_correct
                    else R.string.test_bottom_sheet_edit,
                ))
            }
        }
        TextButton(onClick = onCloseClick, modifier = Modifier.fillMaxWidth().testTag("test_bottom_sheet_close")) {
            Text(stringResource(R.string.test_bottom_sheet_close))
        }
    }
}

@Preview(name = "Edit")
@Preview(name = "Edit dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun TestBottomSheetEditPreview() {
    FillinTheme { Surface { TestBottomSheetContent("operation-42", TestBottomSheetAction.Edit, {}, {}) } }
}

@Preview(name = "Correct")
@Preview(name = "Correct dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun TestBottomSheetCorrectPreview() {
    FillinTheme { Surface { TestBottomSheetContent("operation-42", TestBottomSheetAction.Correct, {}, {}) } }
}

@Preview(name = "Close only")
@Preview(name = "Close only dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun TestBottomSheetCloseOnlyPreview() {
    FillinTheme { Surface { TestBottomSheetContent("operation-42", TestBottomSheetAction.None, {}, {}) } }
}

@Preview(name = "Long ID, compact", widthDp = 320)
@Preview(name = "Long ID, large font", widthDp = 320, fontScale = 2f)
@Preview(name = "Wide", widthDp = 840)
@Composable
private fun TestBottomSheetLongIdPreview() {
    FillinTheme {
        Surface { TestBottomSheetContent("2ca5de12-9325-432f-a47d-dca2c6e0c903", TestBottomSheetAction.Correct, {}, {}) }
    }
}
