package com.firkat.fillin.feature.editcategory.ui

import com.firkat.fillin.feature.categories.domain.model.CATEGORY_INITIAL_ICON_CODE
import com.firkat.fillin.feature.categories.domain.model.Category
import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import com.firkat.fillin.feature.categories.domain.model.isValidCategoryName
import com.firkat.fillin.feature.categories.ui.CategoriesError

internal data class CategoryEditorUiState(
    val original: Category? = null,
    val name: String = original?.name.orEmpty(),
    val kind: CategoryKind = original?.kind ?: CategoryKind.Expense,
    val iconCode: String = original?.icon?.code ?: CATEGORY_INITIAL_ICON_CODE,
    val operation: CategoryEditorOperation? = null,
    val error: CategoriesError? = null,
    val overlay: CategoryEditorOverlay? = null
) {
    val isCreating get() = original == null
    val isBusy get() = operation != null
    val canRename get() = !isBusy && original?.dictionaryKey != "OTHER"
    val canChangeKind get() = !isBusy && isCreating
    val hasChanges: Boolean
        get() = if (original == null) {
            name.isNotBlank() || kind != CategoryKind.Expense || iconCode != CATEGORY_INITIAL_ICON_CODE
        } else {
            name.trim() != original.name || iconCode != original.icon.code
        }
    val hasInvalidName get() = name.isNotBlank() && name.trim() != original?.name && !isValidCategoryName(name.trim())
    val canSave get() = !isBusy &&
        hasChanges &&
        name.isNotBlank() &&
        !hasInvalidName &&
        (iconCode.isNotBlank() || iconCode == original?.icon?.code)
    val supportsDelete get() = original?.kind == CategoryKind.Expense && original.dictionaryKey != "OTHER"
}

internal sealed interface EditCategoryState {
    data object Loading : EditCategoryState
    data class LoadFailed(val error: CategoriesError) : EditCategoryState
    data class Ready(val form: CategoryEditorUiState) : EditCategoryState
    data object Closed : EditCategoryState
}

internal enum class CategoryEditorOperation { Save, Delete }
internal enum class CategoryEditorOverlay { IconPicker, DeleteConfirmation, DiscardConfirmation }

internal sealed interface EditCategoryEvent {
    data object Retry : EditCategoryEvent
    data class NameChanged(val value: String) : EditCategoryEvent
    data class KindChanged(val value: CategoryKind) : EditCategoryEvent
    data object PickIcon : EditCategoryEvent
    data class IconSelected(val code: String) : EditCategoryEvent
    data object DismissOverlay : EditCategoryEvent
    data object Save : EditCategoryEvent
    data object RequestDelete : EditCategoryEvent
    data object ConfirmDelete : EditCategoryEvent
    data object Back : EditCategoryEvent
    data object Discard : EditCategoryEvent
}
