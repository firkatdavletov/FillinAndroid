package com.firkat.fillin.feature.editcategory.ui

import androidx.compose.foundation.BorderStroke
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import com.firkat.fillin.feature.categories.ui.CategoriesEvent
import com.firkat.fillin.feature.categories.ui.CategoriesViewModel
import com.firkat.fillin.feature.categories.ui.CategoryEditorUiState
import com.firkat.fillin.feature.categories.ui.CategoriesError
import com.firkat.fillin.feature.sync.ui.BudgetSyncViewModel
import com.firkat.fillin.ui.component.FeatureScaffold
import com.firkat.fillin.ui.component.CategoryIconImage
import com.firkat.fillin.ui.icon.CategoryIconOption
import com.firkat.fillin.ui.icon.CategoryIcons
import androidx.annotation.StringRes
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun EditCategoryRoute(
    categoryId: String?,
    onClose: () -> Unit,
    viewModel: CategoriesViewModel = koinViewModel(),
    syncViewModel: BudgetSyncViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val screenError = state.error
    val sync by syncViewModel.context.collectAsStateWithLifecycle()
    var editorWasOpen by remember { mutableStateOf(false) }
    var editorInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(categoryId, state.items) {
        if (!editorInitialized && state.editor == null &&
            !state.isLoading && !state.isRefreshing &&
            (categoryId == null || state.items.any { it.id == categoryId })
        ) {
            editorInitialized = true
            if (categoryId == null) viewModel.onEvent(CategoriesEvent.CreateRequested)
            else viewModel.onEvent(CategoriesEvent.EditRequested(categoryId))
        }
    }
    LaunchedEffect(state.editor) {
        if (state.editor != null) editorWasOpen = true
        else if (editorWasOpen) onClose()
    }
    FeatureScaffold(
        titleResource = R.string.categories_title,
        onNavigateBack = { viewModel.onEvent(CategoriesEvent.EditorClosed); onClose() },
        navigateBackEnabled = state.editor?.isSaving != true,
    ) {
        val editor = state.editor
        when {
            editor != null && sync.canWrite -> CategoryEditor(editor, viewModel::onEvent)
            editor != null -> Text(
                text = stringResource(CategoriesError.Forbidden.messageResource),
                modifier = Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.error,
            )
            editor == null && screenError != null -> Text(
                text = stringResource(screenError.messageResource),
                modifier = Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.error,
            )
            editor == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

private val CategoryKind.labelResource: Int
    @StringRes get() = when (this) {
        CategoryKind.Expense -> R.string.categories_kind_expense
        CategoryKind.Income -> R.string.categories_kind_income
    }

private val CategoriesError.messageResource: Int
    @StringRes get() = when (this) {
        CategoriesError.MissingApiToken, CategoriesError.Unauthorized -> R.string.categories_error_unauthorized
        CategoriesError.Forbidden -> R.string.categories_error_forbidden
        CategoriesError.InvalidRequest -> R.string.categories_error_invalid_request
        CategoriesError.NotFound -> R.string.categories_error_not_found
        CategoriesError.Conflict -> R.string.categories_error_conflict
        CategoriesError.Network -> R.string.categories_error_network
        CategoriesError.InvalidResponse -> R.string.categories_error_invalid_response
        CategoriesError.Unknown -> R.string.categories_error_generic
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryEditor(
    state: CategoryEditorUiState,
    onEvent: (CategoriesEvent) -> Unit,
) {
    BackHandler(enabled = !state.isSaving) {
        onEvent(CategoriesEvent.EditorClosed)
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text =
                stringResource(
                    if (state.isCreating) {
                        R.string.categories_editor_create_title
                    } else {
                        R.string.categories_editor_edit_title
                    },
                ),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )

        OutlinedTextField(
            value = state.name,
            onValueChange = { onEvent(CategoriesEvent.NameChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isSaving && state.original?.dictionaryKey != "OTHER",
            label = { Text(stringResource(R.string.categories_name_label)) },
            singleLine = true,
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.categories_kind_label),
                style = MaterialTheme.typography.labelLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryKind.entries.forEach { kind ->
                    FilterChip(
                        selected = state.kind == kind,
                        onClick = { onEvent(CategoriesEvent.KindChanged(kind)) },
                        enabled = state.isCreating && !state.isSaving,
                        label = { Text(stringResource(kind.labelResource)) },
                    )
                }
            }
            if (!state.isCreating) {
                Text(
                    text = stringResource(R.string.categories_kind_immutable_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        CategoryIconSelector(
            iconCode = state.iconCode,
            enabled = !state.isSaving,
            onClick = { onEvent(CategoriesEvent.IconPickerRequested) },
        )

        state.error?.let { error ->
            Text(
                text = stringResource(error.messageResource),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(
            onClick = { onEvent(CategoriesEvent.SaveRequested) },
            modifier = Modifier.fillMaxWidth(),
            enabled = state.canSave,
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    stringResource(
                        if (state.isCreating) {
                            R.string.categories_create_submit
                        } else {
                            R.string.categories_save_submit
                        },
                    ),
                )
            }
        }

        if (state.original?.kind == CategoryKind.Expense) {
            OutlinedButton(
                onClick = { onEvent(CategoriesEvent.DeleteRequested) },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.canDelete,
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
            ) {
                Text(stringResource(R.string.categories_delete_action))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (state.isDeleteConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { onEvent(CategoriesEvent.DeleteDismissed) },
            title = { Text(stringResource(R.string.categories_delete_title)) },
            text = { Text(stringResource(R.string.categories_delete_message)) },
            confirmButton = {
                TextButton(onClick = { onEvent(CategoriesEvent.DeleteConfirmed) }) {
                    Text(
                        text = stringResource(R.string.categories_delete_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(CategoriesEvent.DeleteDismissed) }) {
                    Text(stringResource(R.string.categories_delete_cancel))
                }
            },
        )
    }

    if (state.isIconPickerVisible) {
        CategoryIconPickerBottomSheet(
            selectedIconCode = state.iconCode,
            onIconSelected = { onEvent(CategoriesEvent.IconSelected(it)) },
            onDismiss = { onEvent(CategoriesEvent.IconPickerDismissed) },
        )
    }
}

@Composable
private fun CategoryIconSelector(
    iconCode: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIcon = CategoryIcons.find(iconCode)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.categories_icon_label),
            style = MaterialTheme.typography.labelLarge,
        )
        OutlinedButton(
            onClick = onClick,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp),
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            if (iconCode.isNotBlank()) {
                CategoryIconImage(iconCode, Modifier.alpha(if (enabled) 1f else 0.38f))
                Text(
                    text = stringResource(selectedIcon?.labelResource ?: R.string.category_icon_default),
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                    textAlign = TextAlign.Start,
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                Text(
                    text = stringResource(R.string.categories_icon_select_action),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Start,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Text(
                text = "▾",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryIconPickerBottomSheet(
    selectedIconCode: String,
    onIconSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp),
        ) {
            Text(
                text = stringResource(R.string.categories_icon_picker_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(104.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(384.dp)
                        .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                gridItems(
                    items = CategoryIcons.options,
                    key = { it.code },
                ) { icon ->
                    CategoryIconPickerItem(
                        icon = icon,
                        isSelected = icon == CategoryIcons.find(selectedIconCode),
                        onClick = { onIconSelected(icon.code) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryIconPickerItem(
    icon: CategoryIconOption,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 104.dp).semantics { selected = isSelected },
        shape = MaterialTheme.shapes.large,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CategoryIconImage(icon.code, Modifier.size(32.dp))
            Text(
                text = stringResource(icon.labelResource),
                modifier = Modifier.padding(top = 8.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
