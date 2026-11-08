package com.firkat.fillin.feature.operationhistory.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.firkat.fillin.feature.categories.domain.model.Category
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.feature.operationhistory.domain.repository.OfflineHistoryContext
import com.firkat.fillin.feature.operationhistory.domain.usecase.OperationInputError
import com.firkat.fillin.ui.component.SaldomiSnackbarHost
import com.firkat.fillin.ui.component.SnackbarType
import com.firkat.fillin.ui.component.showSaldomiSnackbar
import com.firkat.fillin.ui.theme.FillinTheme
import com.firkat.fillin.ui.theme.saldomiDesignColors
import kotlinx.coroutines.flow.first
import org.koin.compose.viewmodel.koinViewModel
import java.time.LocalDate

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
) {
    val categories =
        context.categories.filter {
            it.kind == if (editor.type == OperationType.Expense) CategoryKind.Expense else CategoryKind.Income
        }
    val gridState = rememberLazyGridState()
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
    LaunchedEffect(Unit) {
        if (!preview && !editor.isSaving) {
            snapshotFlow {
                window.isWindowFocused && gridState.layoutInfo.visibleItemsInfo.any { it.key == AMOUNT_ITEM_KEY }
            }.first { it }
            focus.requestFocus()
            keyboard?.show()
        }
    }
    LaunchedEffect(editor.feedbackSequence) {
        if (message != null) {
            onEvent(OperationCreateEvent.CreateFeedbackShown(editor.feedbackSequence))
            gridState.scrollToItem(0)
            snapshotFlow { gridState.layoutInfo.visibleItemsInfo.any { it.key == AMOUNT_ITEM_KEY } }.first { it }
            focus.requestFocus()
            keyboard?.show()
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSaldomiSnackbar(message, type = SnackbarType.Error)
        }
    }
    Column(modifier.fillMaxSize().imePadding()) {
        BoxWithConstraints(Modifier.weight(1f)) {
            val columns = if (maxWidth - 32.dp < 340.dp || LocalDensity.current.fontScale > 1.3f) 2 else 4
            val onCategoryClick: (String) -> Unit = { onEvent(OperationCreateEvent.CreateCategoryClicked(it)) }
            val available = categories.mapTo(hashSetOf()) { it.id }
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
                modifier = Modifier.fillMaxSize().testTag("operation-create-category-list"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    OperationTypeTabs(editor.type, !editor.isSaving, { onEvent(OperationCreateEvent.CreateTypeChanged(it)) })
                }
                item(key = AMOUNT_ITEM_KEY, span = { GridItemSpan(maxLineSpan) }) {
                    OperationAmountInput(
                        editor.input,
                        !editor.isSaving,
                        editor.inputError != null,
                        { onEvent(OperationCreateEvent.CreateInputChanged(it)) },
                        Modifier.fillMaxWidth().focusRequester(focus).testTag("operation-create-input"),
                    )
                }
                categorySection(R.string.operation_create_frequent, "frequent",
                    editor.frequentlyUsed.filter { it.id in available }, !editor.isSaving, onCategoryClick)
                categorySection(R.string.operation_create_recent, "recent",
                    editor.recentlyUsed.filter { it.id in available }, !editor.isSaving, onCategoryClick)
                categorySection(R.string.operation_create_all, "all", categories, !editor.isSaving, onCategoryClick)
                if (categories.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(stringResource(R.string.operation_create_no_categories), Modifier.testTag("operation-create-no-categories"))
                    }
                }
            }
        }
        SaldomiSnackbarHost(snackbar)
    }
}

private const val AMOUNT_ITEM_KEY = "operation-amount"

private fun LazyGridScope.categorySection(
    @StringRes title: Int,
    section: String,
    categories: List<Category>,
    enabled: Boolean,
    onClick: (String) -> Unit,
) {
    if (categories.isEmpty() && section != "all") return
    item(key = "section-$section", span = { GridItemSpan(maxLineSpan) }) {
        Text(
            stringResource(title),
            modifier = Modifier.padding(top = 12.dp).semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
    items(categories, key = { "$section-${it.id}" }) { category ->
        val tag = if (section == "all") "operation-create-category-${category.id}"
            else "operation-create-$section-${category.id}"
        OperationCategoryTile(category, enabled, { onClick(category.id) }, Modifier.testTag(tag))
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

@Preview(name = "Used categories", widthDp = 393, heightDp = 850)
@Preview(name = "Used categories dark", widthDp = 393, heightDp = 850, uiMode = UI_MODE_NIGHT_YES)
@Preview(name = "Used categories large text", widthDp = 320, heightDp = 850, fontScale = 2f)
@Composable
private fun OperationCreateUsedCategoriesPreview() {
    val categories = previewOperationCategories().filter { it.kind == CategoryKind.Expense }
    FillinTheme {
        OperationCreateForm(
            OperationCreateUiState(
                occurredOn = LocalDate.of(2026, 10, 24),
                frequentlyUsed = categories.take(4),
                recentlyUsed = categories.takeLast(4).reversed(),
            ),
            OfflineHistoryContext(categories = categories),
            {},
        )
    }
}

@Preview(name = "One used category", widthDp = 393)
@Preview(name = "One used category dark", widthDp = 393, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OperationCreateOneUsedCategoryPreview() {
    val categories = previewOperationCategories()
    val used = categories.filter { it.kind == CategoryKind.Expense }.take(1)
    FillinTheme {
        OperationCreateForm(
            OperationCreateUiState(
                occurredOn = LocalDate.of(2026, 10, 24),
                frequentlyUsed = used,
                recentlyUsed = used,
                isSaving = true,
            ),
            OfflineHistoryContext(categories = categories),
            {},
        )
    }
}
