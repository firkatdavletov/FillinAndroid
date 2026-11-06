package com.firkat.fillin.feature.operationhistory.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.feature.operationhistory.domain.repository.OfflineHistoryContext
import com.firkat.fillin.feature.operationhistory.domain.usecase.OperationInputError
import com.firkat.fillin.ui.component.CategorySelectionSheet
import com.firkat.fillin.ui.component.SaldomiSnackbarHost
import com.firkat.fillin.ui.component.SaldomiTextField
import com.firkat.fillin.ui.component.SnackbarType
import com.firkat.fillin.ui.component.showSaldomiSnackbar
import com.firkat.fillin.ui.theme.FillinTheme
import com.firkat.fillin.ui.theme.saldomiDesignColors
import kotlinx.coroutines.flow.first
import org.koin.compose.viewmodel.koinViewModel
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun OperationCreateRoute(
    onBack: () -> Unit,
    onCreated: (com.firkat.fillin.feature.operationhistory.domain.repository.CreateOperationResult) -> Unit = { onBack() },
    viewModel: OperationCreateViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.createEditor == null) {
        if (state.createEditor == null) state.created?.let(onCreated) ?: onBack()
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
    editor: OperationCreateUiState?,
    context: OfflineHistoryContext,
    onEvent: (OperationCreateEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onEvent(OperationCreateEvent.CreateClosed) }
    Scaffold(
        modifier = modifier.fillMaxSize().testTag("operation_create_screen"),
        containerColor = MaterialTheme.saldomiDesignColors.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.operation_record_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = { onEvent(OperationCreateEvent.CreateClosed) },
                        enabled = editor?.isSaving != true,
                    ) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigation_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.saldomiDesignColors.background),
            )
        },
    ) { padding ->
        editor?.let {
            OperationCreateForm(
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
            editor = OperationCreateUiState(occurredOn = LocalDate.of(2026, 10, 24)),
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
            OperationCreateUiState(input = "1200", occurredOn = LocalDate.of(2026, 10, 24)),
            OfflineHistoryContext(categories = previewOperationCategories()),
            {},
        )
    }
}

@Composable
private fun OperationCreateForm(
    editor: OperationCreateUiState,
    context: OfflineHistoryContext,
    onEvent: (OperationCreateEvent) -> Unit,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
) {
    val categories =
        context.categories.filter {
            it.kind == if (editor.type == OperationType.Expense) CategoryKind.Expense else CategoryKind.Income
        }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val window = LocalWindowInfo.current
    val preview = LocalInspectionMode.current
    val snackbar = remember { SnackbarHostState() }
    val message =
        editor.feedback?.let {
            stringResource(
                when (it) {
                    OperationCreateFeedback.InvalidInput -> R.string.operation_create_invalid_input
                    OperationCreateFeedback.InvalidCategory -> R.string.operation_create_invalid_category
                    OperationCreateFeedback.Unavailable -> R.string.operation_create_unavailable
                    OperationCreateFeedback.SaveFailed -> R.string.operation_create_save_failed
                },
            )
        }
    LaunchedEffect(editor.categoriesOpen) {
        if (!preview) {
            if (editor.categoriesOpen) {
                keyboard?.hide()
            } else if (!editor.isSaving) {
                snapshotFlow { window.isWindowFocused }.first { it }
                focus.requestFocus()
                keyboard?.show()
            }
        }
    }
    LaunchedEffect(editor.feedbackSequence) {
        if (message != null) {
            onEvent(OperationCreateEvent.CreateFeedbackShown(editor.feedbackSequence))
            focus.requestFocus()
            keyboard?.show()
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSaldomiSnackbar(message, type = SnackbarType.Error)
        }
    }
    Column(modifier.fillMaxSize().imePadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            OperationTypeTabs(editor.type, !editor.isSaving, { onEvent(OperationCreateEvent.CreateTypeChanged(it)) })
            OperationAmountInput(
                editor.input,
                !editor.isSaving,
                editor.inputError != null,
                { onEvent(OperationCreateEvent.CreateInputChanged(it)) },
                Modifier.fillMaxWidth().focusRequester(focus).testTag("operation-create-input"),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.operation_history_category_title),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = { onEvent(OperationCreateEvent.CategoriesOpened) }, enabled = !editor.isSaving) {
                    Text(stringResource(R.string.operation_history_all_categories))
                }
            }
            if (categories.isEmpty()) {
                Text(
                    stringResource(R.string.operation_create_no_categories),
                    Modifier.testTag("operation-create-no-categories"),
                )
            } else {
                OperationCategoryGrid(
                    categories.take(8),
                    !editor.isSaving,
                    { onEvent(OperationCreateEvent.CreateCategoryClicked(it)) },
                )
            }
        }
        SaldomiSnackbarHost(snackbar)
    }
    if (editor.categoriesOpen) {
        CategorySelectionSheet(
            categories = categories,
            selectedId = null,
            loading = false,
            hasError = false,
            onSelect = { onEvent(OperationCreateEvent.CreateCategoryClicked(it)) },
            onDismiss = { onEvent(OperationCreateEvent.CategoriesDismissed) },
            onRetry = {},
        )
    }
}

@Preview(name = "Editor", widthDp = 393, heightDp = 700)
@Preview(name = "Editor dark", widthDp = 393, heightDp = 700, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationCreateFormPreview() {
    FillinTheme {
        OperationCreateForm(
            OperationCreateUiState(occurredOn = LocalDate.of(2026, 10, 24)),
            OfflineHistoryContext(categories = previewOperationCategories()),
            {},
        )
    }
}

@Preview(name = "Income editor", widthDp = 393, heightDp = 700)
@Preview(name = "Income editor dark", widthDp = 393, heightDp = 700, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationCreateIncomeEditorPreview() {
    FillinTheme {
        OperationCreateForm(
            OperationCreateUiState(type = OperationType.Income, input = "120000", occurredOn = LocalDate.of(2026, 10, 24)),
            OfflineHistoryContext(categories = previewOperationCategories()),
            {},
        )
    }
}

@Preview(name = "Input error", widthDp = 393, heightDp = 700)
@Preview(name = "Input error dark", widthDp = 393, heightDp = 700, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationCreateErrorEditorPreview() {
    FillinTheme {
        OperationCreateForm(
            OperationCreateUiState(input = "0", inputError = OperationInputError.InvalidAmount, occurredOn = LocalDate.of(2026, 10, 24)),
            OfflineHistoryContext(categories = previewOperationCategories()),
            {},
        )
    }
}

@Preview(name = "Saving", widthDp = 393, heightDp = 700)
@Preview(name = "Saving dark", widthDp = 393, heightDp = 700, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationCreateSavingEditorPreview() {
    FillinTheme {
        OperationCreateForm(
            OperationCreateUiState(input = "1200", isSaving = true, occurredOn = LocalDate.of(2026, 10, 24)),
            OfflineHistoryContext(categories = previewOperationCategories()),
            {},
        )
    }
}

@Preview(name = "No categories", widthDp = 320, heightDp = 600)
@Preview(name = "No categories dark", widthDp = 320, heightDp = 600, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationCreateEmptyEditorPreview() {
    FillinTheme {
        OperationCreateForm(OperationCreateUiState(occurredOn = LocalDate.of(2026, 10, 24)), OfflineHistoryContext(), {})
    }
}
