package com.firkat.fillin.feature.editcategory.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.categories.domain.model.Category
import com.firkat.fillin.feature.categories.domain.model.CategoryIcon
import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import com.firkat.fillin.feature.categories.ui.CategoriesError
import com.firkat.fillin.feature.categories.ui.messageResource
import com.firkat.fillin.feature.sync.ui.BudgetSyncViewModel
import com.firkat.fillin.ui.component.CategoryIconImage
import com.firkat.fillin.ui.component.FeatureScaffold
import com.firkat.fillin.ui.component.SaldomiActionBottomSheet
import com.firkat.fillin.ui.component.SaldomiBasicTextField
import com.firkat.fillin.ui.component.SaldomiDeleteButton
import com.firkat.fillin.ui.component.SaldomiOutlinedButton
import com.firkat.fillin.ui.component.SaldomiPrimaryButton
import com.firkat.fillin.ui.component.SaldomiRadioButton
import com.firkat.fillin.ui.theme.FillinTheme
import com.firkat.fillin.ui.icon.CategoryIconOption
import com.firkat.fillin.ui.icon.CategoryIcons
import com.firkat.fillin.ui.theme.saldomiDesignColors
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import androidx.compose.foundation.lazy.grid.items as gridItems

@Composable
internal fun EditCategoryRoute(
    categoryId: String?,
    readOnly: Boolean,
    onClose: () -> Unit,
    viewModel: EditCategoryViewModel = koinViewModel(parameters = { parametersOf(categoryId) }),
    syncViewModel: BudgetSyncViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sync by syncViewModel.context.collectAsStateWithLifecycle()
    LaunchedEffect(state) {
        if (state == EditCategoryState.Closed) onClose()
    }
    EditCategoryScreen(
        isCreating = categoryId == null,
        state = state,
        canWrite = sync.canWrite && !readOnly,
        onEvent = { viewModel.onEvent(it, syncViewModel.context.value.canWrite && !readOnly) },
    )
}

@Composable
internal fun EditCategoryScreen(
    isCreating: Boolean,
    state: EditCategoryState,
    canWrite: Boolean,
    onEvent: (EditCategoryEvent) -> Unit,
) {
    val form = (state as? EditCategoryState.Ready)?.form
    val back = { onEvent(EditCategoryEvent.Back) }
    // Consume Back even while a command is running so NavDisplay cannot pop the entry.
    BackHandler(onBack = back)
    FeatureScaffold(
        titleResource = if (isCreating) R.string.categories_editor_create_title else R.string.categories_editor_edit_title,
        onNavigateBack = back,
        navigateBackEnabled = form?.isBusy != true && state != EditCategoryState.Closed,
    ) {
        when (state) {
            EditCategoryState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is EditCategoryState.LoadFailed -> Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                EditorError(state.error)
                SaldomiPrimaryButton(onClick = { onEvent(EditCategoryEvent.Retry) }) {
                    Text(stringResource(R.string.categories_retry))
                }
            }
            is EditCategoryState.Ready -> {
                CategoryEditorForm(state.form, canWrite, onEvent)
                CategoryEditorOverlays(state.form, canWrite, onEvent)
            }
            EditCategoryState.Closed -> Unit
        }
    }
}

@Composable
private fun CategoryEditorForm(
    form: CategoryEditorUiState,
    canWrite: Boolean,
    onEvent: (EditCategoryEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().imePadding().padding(horizontal = 20.dp)) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryIconButton(
                    iconCode = form.iconCode,
                    enabled = canWrite && !form.isBusy,
                    onClick = { onEvent(EditCategoryEvent.PickIcon) },
                )
                SaldomiBasicTextField(
                    value = form.name,
                    onValueChange = { onEvent(EditCategoryEvent.NameChanged(it)) },
                    placeholder = stringResource(R.string.categories_name_label),
                    modifier = Modifier.weight(1f),
                    enabled = canWrite && form.canRename,
                    isError = form.hasInvalidName,
                )
            }
            Column(Modifier.selectableGroup()) {
                CategoryKind.entries.forEach { kind ->
                    SaldomiRadioButton(
                        label = stringResource(
                            if (kind == CategoryKind.Expense) R.string.categories_kind_expense else R.string.categories_kind_income,
                        ),
                        selected = form.kind == kind,
                        enabled = canWrite && form.canChangeKind,
                        onClick = { onEvent(EditCategoryEvent.KindChanged(kind)) },
                    )
                }
                if (!form.isCreating) {
                    Text(
                        text = stringResource(R.string.categories_kind_immutable_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when {
                !canWrite -> EditorError(CategoriesError.Forbidden)
                form.hasInvalidName -> EditorError(CategoriesError.InvalidRequest)
                else -> form.error?.let { EditorError(it) }
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (form.supportsDelete && canWrite) {
                SaldomiDeleteButton(
                    onClick = { onEvent(EditCategoryEvent.RequestDelete) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !form.isBusy,
                    isLoading = form.operation == CategoryEditorOperation.Delete,
                ) {
                    Text(stringResource(R.string.categories_delete_confirm))
                }
            }
            SaldomiPrimaryButton(
                onClick = { onEvent(EditCategoryEvent.Save) },
                modifier = Modifier.fillMaxWidth(),
                enabled = canWrite && form.canSave,
                isLoading = form.operation == CategoryEditorOperation.Save,
            ) {
                Text(stringResource(R.string.categories_save_submit))
            }
        }
    }
}

@Composable
private fun CategoryIconButton(
    iconCode: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        enabled = enabled,
        shape = CircleShape,
    ) {
        CategoryIconImage(
            iconCode = iconCode,
            modifier = Modifier.alpha(if (enabled) 1f else 0.38f),
            contentDescription = stringResource(R.string.categories_icon_select_action),
        )
    }
}

@Composable
private fun EditorError(error: CategoriesError) {
    Text(stringResource(error.messageResource), color = MaterialTheme.colorScheme.error)
}

@Composable
private fun CategoryEditorOverlays(
    form: CategoryEditorUiState,
    canWrite: Boolean,
    onEvent: (EditCategoryEvent) -> Unit,
) {
    val dismiss = { onEvent(EditCategoryEvent.DismissOverlay) }
    when (form.overlay) {
        CategoryEditorOverlay.IconPicker -> CategoryIconPickerBottomSheet(
            selectedIconCode = form.iconCode,
            onIconSelected = { onEvent(EditCategoryEvent.IconSelected(it)) },
            onDismiss = dismiss,
        )
        CategoryEditorOverlay.DeleteConfirmation -> SaldomiActionBottomSheet(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.categories_delete_title)) },
            text = { Text(stringResource(R.string.categories_delete_message)) },
            confirmButton = {
                SaldomiDeleteButton(
                    onClick = { onEvent(EditCategoryEvent.ConfirmDelete) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canWrite,
                ) { Text(stringResource(R.string.categories_delete_confirm)) }
            },
            dismissButton = {
                SaldomiOutlinedButton(dismiss, Modifier.fillMaxWidth(), large = true) {
                    Text(stringResource(R.string.categories_delete_cancel))
                }
            },
        )
        CategoryEditorOverlay.DiscardConfirmation -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.category_editor_discard_title)) },
            confirmButton = {
                TextButton(onClick = { onEvent(EditCategoryEvent.Discard) }) {
                    Text(stringResource(R.string.category_editor_discard_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = dismiss) {
                    Text(stringResource(R.string.category_editor_discard_cancel))
                }
            },
        )
        null -> Unit
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
    val other = CategoryIconOption("OTHER", R.drawable.ic_category_household_goods, R.string.category_editor_icon_other)
    val selectedIcon = if (selectedIconCode == other.code) other else CategoryIcons.find(selectedIconCode)
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
                columns = GridCells.Adaptive(64.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(384.dp)
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                gridItems(
                    items = listOf(other) + CategoryIcons.options,
                    key = { it.code },
                ) { icon ->
                    CategoryIconPickerItem(
                        icon = icon,
                        isSelected = icon == selectedIcon,
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
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics { selected = isSelected },
        shape = MaterialTheme.shapes.large,
        color = if (isSelected)
            MaterialTheme.saldomiDesignColors.primaryContainer
        else
            MaterialTheme.saldomiDesignColors.surfaceContainerHighest,
        contentColor = if (isSelected)
            MaterialTheme.saldomiDesignColors.onPrimaryContainer
        else
            MaterialTheme.colorScheme.onSurface,
        border = if (isSelected)
            BorderStroke(2.dp, MaterialTheme.saldomiDesignColors.primary)
        else null,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CategoryIconImage(icon.code, Modifier.size(32.dp), stringResource(icon.labelResource))
        }
    }
}

private val previewCategory = Category("preview", "Продукты", CategoryKind.Expense, CategoryIcon("GROCERIES"))

@Preview(name = "Create")
@Preview(name = "Create dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CreateCategoryPreview() {
    EditorPreview(CategoryEditorUiState())
}

@Preview(name = "Edit")
@Preview(name = "Edit dark", uiMode = UI_MODE_NIGHT_YES)
@Preview(name = "Edit large text", widthDp = 320, fontScale = 2f)
@Preview(name = "Edit wide", widthDp = 840)
@Composable
private fun EditCategoryPreview() {
    EditorPreview(CategoryEditorUiState(original = previewCategory, name = "Продукты и напитки"))
}

@Preview(name = "Saving")
@Preview(name = "Saving dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun SavingCategoryPreview() {
    EditorPreview(CategoryEditorUiState(original = previewCategory, operation = CategoryEditorOperation.Save))
}

@Preview(name = "Deleting")
@Preview(name = "Deleting dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun DeletingCategoryPreview() {
    EditorPreview(CategoryEditorUiState(original = previewCategory, operation = CategoryEditorOperation.Delete))
}

@Preview(name = "Read only")
@Preview(name = "Read only dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ReadOnlyCategoryPreview() {
    EditorPreview(CategoryEditorUiState(original = previewCategory), canWrite = false)
}

@Preview(name = "Selected icon")
@Preview(name = "Selected icon dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun SelectedIconPreview() {
    FillinTheme {
        CategoryIconPickerItem(CategoryIcons.options.first(), isSelected = true, onClick = {})
    }
}

@Preview(name = "Unselected icon")
@Preview(name = "Unselected icon dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun UnselectedIconPreview() {
    FillinTheme {
        CategoryIconPickerItem(CategoryIcons.options.first(), isSelected = false, onClick = {})
    }
}

@Composable
private fun EditorPreview(form: CategoryEditorUiState, canWrite: Boolean = true) {
    FillinTheme {
        EditCategoryScreen(form.isCreating, EditCategoryState.Ready(form), canWrite, onEvent = {})
    }
}
