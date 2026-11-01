package com.firkat.fillin.feature.operationhistory.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import com.firkat.fillin.ui.component.SaldomiSnackbarHost
import com.firkat.fillin.ui.component.SaldomiTextField
import com.firkat.fillin.ui.component.SnackbarType
import com.firkat.fillin.ui.component.showSaldomiSnackbar
import com.firkat.fillin.ui.theme.FillinTheme
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first
import org.koin.compose.viewmodel.koinViewModel

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
        containerColor = MaterialTheme.colorScheme.background,
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
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
            OperationCreateUiState(input = "Продукты 1200", occurredOn = LocalDate.of(2026, 10, 24)),
            OfflineHistoryContext(categories = previewOperationCategories()), {},
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
    val kind = if (editor.type == OperationType.Expense) CategoryKind.Expense else CategoryKind.Income
    val categories = context.categories.filter { it.kind == kind }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val window = LocalWindowInfo.current
    val preview = LocalInspectionMode.current
    val snackbar = remember { SnackbarHostState() }
    val feedbackMessage = editor.feedback?.let {
        stringResource(when (it) {
            OperationCreateFeedback.InvalidInput -> when (editor.inputError) {
                OperationInputError.DescriptionTooLong -> R.string.operation_create_description_too_long
                OperationInputError.AmbiguousAmount -> R.string.operation_create_ambiguous_amount
                else -> R.string.operation_create_invalid_input
            }
            OperationCreateFeedback.InvalidCategory -> R.string.operation_create_invalid_category
            OperationCreateFeedback.Unavailable -> R.string.operation_create_unavailable
            OperationCreateFeedback.SaveFailed -> R.string.operation_create_save_failed
        })
    }
    LaunchedEffect(editor.feedbackSequence) {
        if (feedbackMessage != null) {
            onEvent(OperationCreateEvent.CreateFeedbackShown(editor.feedbackSequence))
            snackbar.currentSnackbarData?.dismiss()
            if (!editor.isSaving) focusRequester.requestFocus()
            snackbar.showSaldomiSnackbar(feedbackMessage, type = SnackbarType.Error)
        }
    }
    BoxWithConstraints(modifier.fillMaxSize().imePadding()) {
        LaunchedEffect(Unit) {
            if (!preview) {
                snapshotFlow { window.isWindowFocused }.first { it }
                focusRequester.requestFocus()
                keyboard?.show()
            }
        }
        val compact = maxHeight < 320.dp
        Column(Modifier.fillMaxSize().padding(bottom = if (compact) 8.dp else 16.dp)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = if (compact) 8.dp else 16.dp)) {
                        if (showTitle) {
                            Text(stringResource(R.string.operation_record_title), style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(24.dp))
                        }
                        SaldomiTextField(
                            value = editor.input,
                            onValueChange = { onEvent(OperationCreateEvent.CreateInputChanged(it)) },
                            placeholder = stringResource(R.string.operation_create_placeholder),
                            enabled = !editor.isSaving,
                            isError = editor.inputError != null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).testTag("operation-create-input"),
                        )
                        Spacer(Modifier.height(if (compact) 12.dp else 20.dp))
                        OperationTypeTabs(
                            type = editor.type,
                            enabled = !editor.isSaving,
                            onTypeClick = { onEvent(OperationCreateEvent.CreateTypeChanged(it)) },
                        )
                    }
                    Text(
                        stringResource(R.string.operation_create_quick_add),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = if (compact) 8.dp else 16.dp),
                    )
                }
                SaldomiSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
            }
            if (categories.isEmpty()) {
                Text(
                    stringResource(R.string.operation_create_no_categories),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).testTag("operation-create-no-categories"),
                )
            } else {
                key(editor.type) {
                    OperationCategoryRow(
                        categories = categories,
                        enabled = !editor.isSaving,
                        onCategoryClick = { onEvent(OperationCreateEvent.CreateCategoryClicked(it)) },
                    )
                }
            }
        }
    }
}

@Preview(name = "Editor", widthDp = 393, heightDp = 700)
@Preview(name = "Editor dark", widthDp = 393, heightDp = 700, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationCreateFormPreview() {
    FillinTheme {
        OperationCreateForm(OperationCreateUiState(occurredOn = LocalDate.of(2026, 10, 24)),
            OfflineHistoryContext(categories = previewOperationCategories()), {})
    }
}

@Preview(name = "Income editor", widthDp = 393, heightDp = 700)
@Preview(name = "Income editor dark", widthDp = 393, heightDp = 700, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationCreateIncomeEditorPreview() {
    FillinTheme {
        OperationCreateForm(
            OperationCreateUiState(type = OperationType.Income, input = "Зарплата 120000", occurredOn = LocalDate.of(2026, 10, 24)),
            OfflineHistoryContext(categories = previewOperationCategories()), {},
        )
    }
}

@Preview(name = "Input error", widthDp = 393, heightDp = 700)
@Preview(name = "Input error dark", widthDp = 393, heightDp = 700, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationCreateErrorEditorPreview() {
    FillinTheme {
        OperationCreateForm(
            OperationCreateUiState(input = "Продукты", inputError = OperationInputError.InvalidAmount, occurredOn = LocalDate.of(2026, 10, 24)),
            OfflineHistoryContext(categories = previewOperationCategories()), {},
        )
    }
}

@Preview(name = "Saving", widthDp = 393, heightDp = 700)
@Preview(name = "Saving dark", widthDp = 393, heightDp = 700, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationCreateSavingEditorPreview() {
    FillinTheme {
        OperationCreateForm(
            OperationCreateUiState(input = "Продукты 1200", isSaving = true, occurredOn = LocalDate.of(2026, 10, 24)),
            OfflineHistoryContext(categories = previewOperationCategories()), {},
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
