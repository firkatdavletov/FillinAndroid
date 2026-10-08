package com.firkat.fillin.feature.categories.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.categories.domain.model.Category
import com.firkat.fillin.feature.categories.domain.repository.CategoriesException
import com.firkat.fillin.feature.categories.domain.usecase.CreateCategory
import com.firkat.fillin.feature.categories.domain.usecase.DeleteCategory
import com.firkat.fillin.feature.categories.domain.usecase.GetCategories
import com.firkat.fillin.feature.categories.domain.usecase.UpdateCategory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CategoriesViewModel(
    private val getCategories: GetCategories,
    private val createCategory: CreateCategory,
    private val updateCategory: UpdateCategory,
    private val deleteCategory: DeleteCategory,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CategoriesUiState())
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: CategoriesEvent) {
        when (event) {
            CategoriesEvent.Retry -> load()
            CategoriesEvent.CreateRequested -> openCreateEditor()
            is CategoriesEvent.EditRequested -> openEditEditor(event.categoryId)
            CategoriesEvent.EditorClosed -> closeEditor()
            is CategoriesEvent.NameChanged -> updateEditor { copy(name = event.name, error = null) }
            is CategoriesEvent.KindChanged -> updateEditor {
                if (isCreating) copy(kind = event.kind, error = null) else this
            }
            is CategoriesEvent.IconChanged -> updateEditor {
                copy(iconCode = event.iconCode.uppercase(), error = null)
            }
            CategoriesEvent.SaveRequested -> save()
            CategoriesEvent.DeleteRequested -> updateEditor {
                if (canDelete) copy(isDeleteConfirmationVisible = true) else this
            }
            CategoriesEvent.DeleteDismissed -> updateEditor {
                copy(isDeleteConfirmationVisible = false)
            }
            CategoriesEvent.DeleteConfirmed -> delete()
        }
    }

    private fun load() {
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val categories = getCategories()
                _uiState.update {
                    it.copy(
                        items = categories,
                        isLoading = false,
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: CategoriesException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = exception.toUiError(),
                    )
                }
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = CategoriesError.Unknown,
                    )
                }
            }
        }
    }

    private fun openCreateEditor() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(editor = CategoryEditorUiState()) }
    }

    private fun openEditEditor(categoryId: String) {
        val category = _uiState.value.items.firstOrNull { it.id == categoryId } ?: return
        _uiState.update {
            it.copy(
                editor = CategoryEditorUiState(
                    original = category,
                    name = category.name,
                    kind = category.kind,
                    iconCode = category.icon.code,
                ),
            )
        }
    }

    private fun closeEditor() {
        if (_uiState.value.editor?.isSaving == true) return
        _uiState.update { it.copy(editor = null) }
    }

    private fun save() {
        val editor = _uiState.value.editor?.takeIf(CategoryEditorUiState::canSave) ?: return
        updateEditor { copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                val savedCategory = editor.original?.let { original ->
                    updateCategory(
                        category = original,
                        name = editor.name,
                        iconCode = editor.iconCode,
                    )
                } ?: createCategory(
                    name = editor.name,
                    kind = editor.kind,
                    iconCode = editor.iconCode,
                )
                _uiState.update { state ->
                    state.copy(
                        items = state.items.upsert(savedCategory),
                        editor = null,
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: CategoriesException) {
                showEditorError(exception.toUiError())
            } catch (exception: Exception) {
                showEditorError(CategoriesError.Unknown)
            }
        }
    }

    private fun delete() {
        val editor = _uiState.value.editor ?: return
        val category = editor.original ?: return
        if (!editor.canDelete || !editor.isDeleteConfirmationVisible) return

        updateEditor {
            copy(
                isSaving = true,
                error = null,
                isDeleteConfirmationVisible = false,
            )
        }
        viewModelScope.launch {
            try {
                deleteCategory(category)
                _uiState.update { state ->
                    state.copy(
                        items = state.items.filterNot { it.id == category.id },
                        editor = null,
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: CategoriesException) {
                showEditorError(exception.toUiError())
            } catch (exception: Exception) {
                showEditorError(CategoriesError.Unknown)
            }
        }
    }

    private fun showEditorError(error: CategoriesError) {
        updateEditor {
            copy(
                isSaving = false,
                error = error,
                isDeleteConfirmationVisible = false,
            )
        }
    }

    private fun updateEditor(transform: CategoryEditorUiState.() -> CategoryEditorUiState) {
        _uiState.update { state ->
            state.copy(editor = state.editor?.transform())
        }
    }
}

private fun List<Category>.upsert(category: Category): List<Category> {
    val index = indexOfFirst { it.id == category.id }
    if (index == -1) return this + category
    return toMutableList().apply { this[index] = category }
}

private fun CategoriesException.toUiError(): CategoriesError = when (this) {
    CategoriesException.MissingApiToken -> CategoriesError.MissingApiToken
    CategoriesException.Unauthorized -> CategoriesError.Unauthorized
    CategoriesException.Forbidden -> CategoriesError.Forbidden
    CategoriesException.InvalidRequest -> CategoriesError.InvalidRequest
    CategoriesException.NotFound -> CategoriesError.NotFound
    CategoriesException.Conflict -> CategoriesError.Conflict
    CategoriesException.Network -> CategoriesError.Network
    CategoriesException.InvalidResponse -> CategoriesError.InvalidResponse
    CategoriesException.Unknown -> CategoriesError.Unknown
}
