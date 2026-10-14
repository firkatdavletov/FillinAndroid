package com.firkat.fillin.feature.categories.ui

import com.firkat.fillin.feature.categories.domain.model.Category
import com.firkat.fillin.feature.categories.domain.model.CategoryIconType
import com.firkat.fillin.feature.categories.domain.model.CategoryKind

data class CategoriesUiState(
    val items: List<Category> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: CategoriesError? = null,
    val editor: CategoryEditorUiState? = null,
)

data class CategoryEditorUiState(
    val original: Category? = null,
    val name: String = "",
    val kind: CategoryKind = CategoryKind.Expense,
    val iconCode: String = "",
    val isSaving: Boolean = false,
    val error: CategoriesError? = null,
    val isDeleteConfirmationVisible: Boolean = false,
    val isIconPickerVisible: Boolean = false,
) {
    val isCreating: Boolean
        get() = original == null

    val hasChanges: Boolean
        get() = original == null ||
            name.trim() != original.name ||
            iconCode.trim().uppercase() != original.icon.code

    val canSave: Boolean
        get() = !isSaving &&
            name.isNotBlank() &&
            iconCode.isNotBlank() &&
            hasChanges

    val canDelete: Boolean
        get() = !isSaving &&
            original?.kind == CategoryKind.Expense
}

enum class CategoriesError {
    MissingApiToken,
    Unauthorized,
    Forbidden,
    InvalidRequest,
    NotFound,
    Conflict,
    Network,
    InvalidResponse,
    Unknown,
}

sealed interface CategoriesEvent {
    data object Refresh : CategoriesEvent
    data object Retry : CategoriesEvent
    data object CreateRequested : CategoriesEvent
    data class EditRequested(val categoryId: String) : CategoriesEvent
    data object EditorClosed : CategoriesEvent
    data class NameChanged(val name: String) : CategoriesEvent
    data class KindChanged(val kind: CategoryKind) : CategoriesEvent
    data object IconPickerRequested : CategoriesEvent
    data object IconPickerDismissed : CategoriesEvent
    data class IconSelected(val icon: CategoryIconType) : CategoriesEvent
    data object SaveRequested : CategoriesEvent
    data object DeleteRequested : CategoriesEvent
    data object DeleteDismissed : CategoriesEvent
    data object DeleteConfirmed : CategoriesEvent
}
