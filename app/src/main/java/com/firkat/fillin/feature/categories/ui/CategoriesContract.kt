package com.firkat.fillin.feature.categories.ui

import com.firkat.fillin.feature.categories.domain.model.Category
import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import com.firkat.fillin.feature.categories.domain.model.OTHER_CATEGORY_ICON_CODE

data class CategoriesUiState(
    val items: List<Category> = emptyList(),
    val isLoading: Boolean = false,
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
) {
    val isCreating: Boolean
        get() = original == null

    val isOther: Boolean
        get() = original?.isOther == true

    val hasChanges: Boolean
        get() = original == null ||
            name.trim() != original.name ||
            iconCode.trim().uppercase() != original.icon.code

    val canSave: Boolean
        get() = !isSaving &&
            !isOther &&
            name.isNotBlank() &&
            iconCode.isNotBlank() &&
            iconCode.trim().uppercase() != OTHER_CATEGORY_ICON_CODE &&
            hasChanges

    val canDelete: Boolean
        get() = !isSaving &&
            original?.kind == CategoryKind.Expense &&
            !isOther
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
    data object Retry : CategoriesEvent
    data object CreateRequested : CategoriesEvent
    data class EditRequested(val categoryId: String) : CategoriesEvent
    data object EditorClosed : CategoriesEvent
    data class NameChanged(val name: String) : CategoriesEvent
    data class KindChanged(val kind: CategoryKind) : CategoriesEvent
    data class IconChanged(val iconCode: String) : CategoriesEvent
    data object SaveRequested : CategoriesEvent
    data object DeleteRequested : CategoriesEvent
    data object DeleteDismissed : CategoriesEvent
    data object DeleteConfirmed : CategoriesEvent
}
