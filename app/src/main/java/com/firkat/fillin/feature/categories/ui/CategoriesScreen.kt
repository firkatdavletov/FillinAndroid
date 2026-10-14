package com.firkat.fillin.feature.categories.ui

import androidx.annotation.StringRes
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.categories.domain.model.Category
import com.firkat.fillin.feature.categories.domain.model.CategoryIcon
import com.firkat.fillin.feature.categories.domain.model.CategoryIconType
import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import com.firkat.fillin.ui.component.FeatureScaffold
import com.firkat.fillin.ui.theme.FillinTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CategoriesRoute(
    viewModel: CategoriesViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FeatureScaffold(
        titleResource = R.string.categories_title,
        onNavigateBack = if (state.editor != null) {
            { viewModel.onEvent(CategoriesEvent.EditorClosed) }
        } else {
            null
        },
        navigateBackEnabled = state.editor?.isSaving != true,
    ) {
        CategoriesScreen(
            state = state,
            onEvent = viewModel::onEvent,
        )
    }
}

@Composable
fun CategoriesScreen(
    state: CategoriesUiState,
    onEvent: (CategoriesEvent) -> Unit,
) {
    val editor = state.editor
    if (editor != null) {
        CategoryEditor(
            state = editor,
            onEvent = onEvent,
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading && state.items.isEmpty() -> LoadingContent()

            state.error != null && state.items.isEmpty() -> ErrorContent(
                error = state.error,
                onRetry = { onEvent(CategoriesEvent.Retry) },
                modifier = Modifier.fillMaxSize(),
            )

            state.items.isEmpty() -> EmptyContent()

            else -> CategoriesList(
                items = state.items,
                onCategoryClick = { onEvent(CategoriesEvent.EditRequested(it.id)) },
            )
        }

        if (!state.isLoading && state.error == null) {
            ExtendedFloatingActionButton(
                onClick = { onEvent(CategoriesEvent.CreateRequested) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                icon = {
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                text = { Text(stringResource(R.string.categories_create_action)) },
            )
        }
    }
}

@Composable
private fun CategoriesList(
    items: List<Category>,
    onCategoryClick: (Category) -> Unit,
    modifier: Modifier = Modifier,
) {
    val expenseCategories = remember(items) {
        items.filter { it.kind == CategoryKind.Expense }
    }
    val incomeCategories = remember(items) {
        items.filter { it.kind == CategoryKind.Income }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 8.dp,
            end = 16.dp,
            bottom = 96.dp,
        ),
    ) {
        categorySection(
            title = R.string.categories_expenses_section,
            categories = expenseCategories,
            onCategoryClick = onCategoryClick,
        )
        categorySection(
            title = R.string.categories_incomes_section,
            categories = incomeCategories,
            onCategoryClick = onCategoryClick,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.categorySection(
    @StringRes title: Int,
    categories: List<Category>,
    onCategoryClick: (Category) -> Unit,
) {
    if (categories.isEmpty()) return

    item(key = "section-$title") {
        Text(
            text = stringResource(title),
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    items(
        items = categories,
        key = Category::id,
    ) { category ->
        CategoryItem(
            category = category,
            onClick = { onCategoryClick(category) },
        )
        HorizontalDivider(
            modifier = Modifier.padding(start = 64.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@Composable
private fun CategoryItem(
    category: Category,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = category.icon.emoji,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
        }
    }
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
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(
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
            enabled = !state.isSaving,
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
                colors = ButtonDefaults.outlinedButtonColors(
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
) {
    val selectedIcon = remember(iconCode) { CategoryIconType.fromCode(iconCode) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.categories_icon_label),
            style = MaterialTheme.typography.labelLarge,
        )
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            if (selectedIcon != null) {
                Text(
                    text = selectedIcon.emoji,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = selectedIcon.name,
                    modifier = Modifier
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
    onIconSelected: (CategoryIconType) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
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
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(384.dp)
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                gridItems(
                    items = CategoryIconType.entries,
                    key = CategoryIconType::name,
                ) { icon ->
                    val isSelected = icon.name == selectedIconCode.trim().uppercase()
                    Surface(
                        onClick = { onIconSelected(icon) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.15f),
                        shape = MaterialTheme.shapes.large,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        contentColor = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        border = if (isSelected) {
                            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            null
                        },
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = icon.emoji,
                                style = MaterialTheme.typography.headlineMedium,
                            )
                            Text(
                                text = icon.name,
                                modifier = Modifier.padding(top = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.categories_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ErrorContent(
    error: CategoriesError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(error.messageResource),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text(stringResource(R.string.categories_retry))
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
        CategoriesError.MissingApiToken,
        CategoriesError.Unauthorized,
        -> R.string.categories_error_unauthorized

        CategoriesError.Forbidden -> R.string.categories_error_forbidden
        CategoriesError.InvalidRequest -> R.string.categories_error_invalid_request
        CategoriesError.NotFound -> R.string.categories_error_not_found
        CategoriesError.Conflict -> R.string.categories_error_conflict
        CategoriesError.Network -> R.string.categories_error_network
        CategoriesError.InvalidResponse -> R.string.categories_error_invalid_response
        CategoriesError.Unknown -> R.string.categories_error_generic
    }

@Preview(showBackground = true)
@Composable
private fun CategoriesPreview() {
    FillinTheme(dynamicColor = false) {
        CategoriesScreen(
            state = CategoriesUiState(
                items = listOf(
                    Category(
                        id = "groceries",
                        name = "Продукты",
                        kind = CategoryKind.Expense,
                        icon = CategoryIcon(code = "FOOD", emoji = "🍔"),
                    ),
                    Category(
                        id = "salary",
                        name = "Зарплата",
                        kind = CategoryKind.Income,
                        icon = CategoryIcon(code = "INCOME", emoji = "💰"),
                    ),
                ),
            ),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CategoryEditorPreview() {
    FillinTheme(dynamicColor = false) {
        CategoriesScreen(
            state = CategoriesUiState(
                editor = CategoryEditorUiState(
                    name = "Кафе",
                    kind = CategoryKind.Expense,
                    iconCode = "FOOD",
                ),
            ),
            onEvent = {},
        )
    }
}
