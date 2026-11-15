package com.firkat.fillin.feature.editcategory.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.categories.domain.repository.CategoriesException
import com.firkat.fillin.feature.categories.domain.usecase.CreateCategory
import com.firkat.fillin.feature.categories.domain.usecase.DeleteCategory
import com.firkat.fillin.feature.categories.domain.usecase.GetCategories
import com.firkat.fillin.feature.categories.domain.usecase.UpdateCategory
import com.firkat.fillin.feature.categories.ui.CategoriesError
import com.firkat.fillin.feature.categories.ui.toUiError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class EditCategoryViewModel(
    private val categoryId: String?,
    private val getCategories: GetCategories,
    private val createCategory: CreateCategory,
    private val updateCategory: UpdateCategory,
    private val deleteCategory: DeleteCategory
) : ViewModel() {
    private val mutableState = MutableStateFlow<EditCategoryState>(EditCategoryState.Loading)
    val state = mutableState.asStateFlow()
    private var loadJob: Job? = null

    init {
        load()
    }

    fun onEvent(
        event: EditCategoryEvent,
        canWrite: Boolean
    ) {
        val current = state.value
        if (current == EditCategoryState.Closed) return
        val form = (current as? EditCategoryState.Ready)?.form
        if (form?.isBusy == true) return

        when (event) {
            EditCategoryEvent.Retry -> if (current is EditCategoryState.LoadFailed) load()
            EditCategoryEvent.Back -> when {
                form?.overlay != null -> show(form.copy(overlay = null))
                form?.hasChanges == true -> show(form.copy(overlay = CategoryEditorOverlay.DiscardConfirmation))
                else -> close()
            }
            EditCategoryEvent.Discard -> if (form?.overlay == CategoryEditorOverlay.DiscardConfirmation) close()
            EditCategoryEvent.DismissOverlay -> if (form != null) show(form.copy(overlay = null))
            else -> if (canWrite && form != null) edit(form, event)
        }
    }

    private fun edit(
        form: CategoryEditorUiState,
        event: EditCategoryEvent
    ) {
        when (event) {
            is EditCategoryEvent.NameChanged -> if (form.canRename) show(form.copy(name = event.value, error = null))
            is EditCategoryEvent.KindChanged -> if (form.canChangeKind) {
                show(
                    form.copy(kind = event.value, error = null)
                )
            }
            EditCategoryEvent.PickIcon -> show(form.copy(overlay = CategoryEditorOverlay.IconPicker))
            is EditCategoryEvent.IconSelected -> if (form.overlay == CategoryEditorOverlay.IconPicker) {
                show(form.copy(iconCode = event.code, overlay = null, error = null))
            }
            EditCategoryEvent.RequestDelete -> if (form.supportsDelete) {
                show(form.copy(overlay = CategoryEditorOverlay.DeleteConfirmation))
            }
            EditCategoryEvent.Save -> if (form.canSave && form.overlay == null) {
                runCommand(form, CategoryEditorOperation.Save) {
                    val original = form.original
                    if (original == null) {
                        createCategory(form.name, form.kind, form.iconCode)
                    } else {
                        updateCategory(original, form.name, form.iconCode)
                    }
                }
            }
            EditCategoryEvent.ConfirmDelete -> if (form.supportsDelete &&
                form.overlay == CategoryEditorOverlay.DeleteConfirmation
            ) {
                runCommand(form, CategoryEditorOperation.Delete) { deleteCategory(checkNotNull(form.original)) }
            }
            else -> Unit
        }
    }

    private fun load() {
        mutableState.value = EditCategoryState.Loading
        loadJob = viewModelScope.launch {
            try {
                val original = categoryId?.let { id ->
                    getCategories().firstOrNull { it.id == id } ?: throw CategoriesException.NotFound
                }
                show(CategoryEditorUiState(original = original))
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                mutableState.value = EditCategoryState.LoadFailed(exception.toEditorError())
            }
        }
    }

    private fun runCommand(
        form: CategoryEditorUiState,
        operation: CategoryEditorOperation,
        command: suspend () -> Unit
    ) {
        show(form.copy(operation = operation, overlay = null, error = null))
        viewModelScope.launch {
            try {
                command()
                close()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                show(form.copy(overlay = null, error = exception.toEditorError()))
            }
        }
    }

    private fun show(
        form: CategoryEditorUiState
    ) {
        mutableState.value = EditCategoryState.Ready(form)
    }

    private fun close() {
        loadJob?.cancel()
        mutableState.value = EditCategoryState.Closed
    }
}

private fun Exception.toEditorError() = (this as? CategoriesException)?.toUiError() ?: CategoriesError.Unknown
