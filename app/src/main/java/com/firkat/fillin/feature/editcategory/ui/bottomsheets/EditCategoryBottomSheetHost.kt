@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.firkat.fillin.feature.editcategory.ui.bottomsheets

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.firkat.fillin.R
import com.firkat.fillin.feature.categories.domain.model.CATEGORY_INITIAL_ICON_CODE
import com.firkat.fillin.feature.editcategory.ui.CategoryEditorOverlay
import com.firkat.fillin.ui.component.CategoryIconImage
import com.firkat.fillin.ui.component.SaldomiActionBottomSheet
import com.firkat.fillin.ui.component.SaldomiBottomSheet
import com.firkat.fillin.ui.component.SaldomiDeleteButton
import com.firkat.fillin.ui.component.SaldomiOutlinedButton
import com.firkat.fillin.ui.icon.CategoryIcons
import com.firkat.fillin.ui.theme.FillinTheme
import com.firkat.fillin.ui.theme.saldomiDesignColors

@Composable
internal fun EditCategoryBottomSheetHost(
    overlay: CategoryEditorOverlay?,
    iconCode: String,
    categoryName: String,
    canDelete: Boolean,
    onIconSelected: (String) -> Unit,
    onDelete: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (overlay) {
        CategoryEditorOverlay.IconPicker -> CategoryIconPickerBottomSheet(
            selectedIconCode = iconCode,
            categoryName = categoryName,
            onIconSelected = onIconSelected,
            onDismiss = onDismiss,
            modifier = modifier
        )
        CategoryEditorOverlay.DeleteConfirmation -> SaldomiActionBottomSheet(
            onDismissRequest = onDismiss,
            modifier = modifier,
            title = { Text(stringResource(R.string.categories_delete_title)) },
            text = { Text(stringResource(R.string.categories_delete_message)) },
            confirmButton = {
                SaldomiDeleteButton(onDelete, Modifier.fillMaxWidth(), enabled = canDelete) {
                    Text(stringResource(R.string.categories_delete_confirm))
                }
            },
            dismissButton = {
                SaldomiOutlinedButton(onDismiss, Modifier.fillMaxWidth(), large = true) {
                    Text(stringResource(R.string.categories_delete_cancel))
                }
            }
        )
        CategoryEditorOverlay.DiscardConfirmation -> SaldomiActionBottomSheet(
            onDismissRequest = onDismiss,
            modifier = modifier,
            title = { Text(stringResource(R.string.category_editor_discard_title)) },
            confirmButton = {
                SaldomiDeleteButton(onDiscard, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.category_editor_discard_confirm))
                }
            },
            dismissButton = {
                SaldomiOutlinedButton(onDismiss, Modifier.fillMaxWidth(), large = true) {
                    Text(stringResource(R.string.category_editor_discard_cancel))
                }
            }
        )
        null -> Unit
    }
}

@Composable
private fun CategoryIconPickerBottomSheet(
    selectedIconCode: String,
    categoryName: String,
    onIconSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedIcon = CategoryIcons.find(selectedIconCode)
    SaldomiBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = stringResource(R.string.categories_icon_picker_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(64.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(384.dp)
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    CategoryIconPickerItem(
                        iconCode = CATEGORY_INITIAL_ICON_CODE,
                        categoryName = categoryName,
                        label = stringResource(R.string.category_icon_initial),
                        isSelected = selectedIconCode == CATEGORY_INITIAL_ICON_CODE,
                        onClick = { onIconSelected(CATEGORY_INITIAL_ICON_CODE) },
                        showLabel = true
                    )
                }
                gridItems(
                    items = CategoryIcons.options,
                    key = { it.code }
                ) { icon ->
                    CategoryIconPickerItem(
                        iconCode = icon.code,
                        categoryName = categoryName,
                        label = stringResource(icon.labelResource),
                        isSelected = icon == selectedIcon,
                        onClick = { onIconSelected(icon.code) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryIconPickerItem(
    iconCode: String,
    categoryName: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = false
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics { selected = isSelected },
        shape = MaterialTheme.shapes.large,
        color = if (isSelected) {
            MaterialTheme.saldomiDesignColors.primaryContainer
        } else {
            MaterialTheme.saldomiDesignColors.surfaceContainerHighest
        },
        contentColor = if (isSelected) {
            MaterialTheme.saldomiDesignColors.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.saldomiDesignColors.primary)
        } else {
            null
        }
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CategoryIconImage(
                iconCode = iconCode,
                categoryName = categoryName,
                modifier = Modifier.size(32.dp),
                contentDescription = if (showLabel) null else label
            )
            if (showLabel) {
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Preview(name = "Selected icon")
@Preview(name = "Selected icon dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun SelectedIconPreview() {
    FillinTheme {
        val icon = CategoryIcons.options.first()
        CategoryIconPickerItem(
            iconCode = icon.code,
            categoryName = "Продукты",
            label = stringResource(icon.labelResource),
            isSelected = true,
            onClick = {}
        )
    }
}

@Preview(name = "Unselected icon")
@Preview(name = "Unselected icon dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun UnselectedIconPreview() {
    FillinTheme {
        val icon = CategoryIcons.options.first()
        CategoryIconPickerItem(
            iconCode = icon.code,
            categoryName = "Продукты",
            label = stringResource(icon.labelResource),
            isSelected = false,
            onClick = {}
        )
    }
}

@Preview(name = "Automatic icon option")
@Preview(name = "Automatic icon option dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun AutomaticIconOptionPreview() {
    FillinTheme {
        CategoryIconPickerItem(
            iconCode = CATEGORY_INITIAL_ICON_CODE,
            categoryName = "Кафе",
            label = stringResource(R.string.category_icon_initial),
            isSelected = true,
            onClick = {},
            showLabel = true
        )
    }
}

@Preview(name = "Choose category icon")
@Preview(name = "Choose category icon dark", uiMode = UI_MODE_NIGHT_YES)
@Preview(name = "Choose category icon large text", widthDp = 320, fontScale = 2f)
@Composable
private fun CategoryIconPickerPreview() {
    FillinTheme { CategoryIconPickerBottomSheet("GROCERIES", "Продукты", {}, {}) }
}

@Preview(name = "Discard category changes")
@Preview(name = "Discard category changes dark", uiMode = UI_MODE_NIGHT_YES)
@Preview(name = "Discard category changes large text", widthDp = 320, fontScale = 2f)
@Composable
private fun DiscardCategoryPreview() {
    FillinTheme {
        EditCategoryBottomSheetHost(CategoryEditorOverlay.DiscardConfirmation, "", "", true, {}, {}, {}, {})
    }
}

@Preview(name = "Delete category")
@Preview(name = "Delete category dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun DeleteCategoryPreview() {
    FillinTheme {
        EditCategoryBottomSheetHost(CategoryEditorOverlay.DeleteConfirmation, "", "", true, {}, {}, {}, {})
    }
}

@Preview(name = "Delete category disabled")
@Preview(name = "Delete category disabled dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun DeleteCategoryDisabledPreview() {
    FillinTheme {
        EditCategoryBottomSheetHost(CategoryEditorOverlay.DeleteConfirmation, "", "", false, {}, {}, {}, {})
    }
}
