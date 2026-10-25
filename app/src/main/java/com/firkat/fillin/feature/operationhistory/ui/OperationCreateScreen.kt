package com.firkat.fillin.feature.operationhistory.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.operationhistory.domain.repository.OfflineHistoryContext
import com.firkat.fillin.ui.theme.FillinTheme
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import java.time.LocalDate

@Composable
internal fun OperationCreateRoute(
    onBack: () -> Unit,
    viewModel: OperationHistoryViewModel = koinViewModel(parameters = { parametersOf(true) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.createEditor == null) {
        if (state.createEditor == null) onBack()
    }
    OperationCreateScreen(
        editor = state.createEditor,
        context = state.offline,
        onEvent = viewModel::onEvent,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun OperationCreateScreen(
    editor: OperationCreateEditorUiState?,
    context: OfflineHistoryContext,
    onEvent: (OperationHistoryEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onEvent(OperationHistoryEvent.CreateClosed) }
    Scaffold(
        modifier = modifier.fillMaxSize().testTag("operation_create_screen"),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.operation_record_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = { onEvent(OperationHistoryEvent.CreateClosed) },
                        enabled = editor?.isSaving != true,
                    ) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigation_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        editor?.let {
            OperationCreateEditor(
                editor = it,
                context = context,
                onEvent = onEvent,
                showTitle = false,
                modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            )
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationCreateScreenPreview() {
    FillinTheme {
        OperationCreateScreen(
            editor = OperationCreateEditorUiState(occurredOn = LocalDate.of(2026, 10, 24)),
            context = OfflineHistoryContext(categories = previewOperationCategories()),
            onEvent = {},
        )
    }
}

@Preview(name = "Compact, large font", widthDp = 320, heightDp = 600, fontScale = 2f)
@Composable
private fun OperationCreateCompactPreview() {
    FillinTheme {
        OperationCreateScreen(
            OperationCreateEditorUiState(input = "Продукты 1200", occurredOn = LocalDate.of(2026, 10, 24)),
            OfflineHistoryContext(categories = previewOperationCategories()), {},
        )
    }
}
