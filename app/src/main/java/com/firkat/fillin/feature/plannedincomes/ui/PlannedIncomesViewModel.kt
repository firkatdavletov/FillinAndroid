package com.firkat.fillin.feature.plannedincomes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import com.firkat.fillin.feature.categories.domain.repository.CategoriesException
import com.firkat.fillin.feature.categories.domain.usecase.GetCategories
import com.firkat.fillin.feature.plannedincomes.domain.repository.PlannedIncomesException
import com.firkat.fillin.feature.plannedincomes.domain.usecase.CreatePlannedIncome
import com.firkat.fillin.feature.plannedincomes.domain.usecase.DeletePlannedIncome
import com.firkat.fillin.feature.plannedincomes.domain.usecase.GetPlannedIncomes
import com.firkat.fillin.feature.plannedincomes.domain.usecase.UpdatePlannedIncome
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PlannedIncomesViewModel(
    private val getPlannedIncomes: GetPlannedIncomes,
    private val getCategories: GetCategories,
    private val createPlannedIncome: CreatePlannedIncome,
    private val updatePlannedIncome: UpdatePlannedIncome,
    private val deletePlannedIncome: DeletePlannedIncome,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlannedIncomesUiState())
    val uiState: StateFlow<PlannedIncomesUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: PlannedIncomesEvent) {
        when (event) {
            PlannedIncomesEvent.Retry -> load()
            PlannedIncomesEvent.CreateRequested -> openCreateEditor()
            is PlannedIncomesEvent.EditRequested -> openEditEditor(event.incomeId)
            PlannedIncomesEvent.EditorClosed -> closeEditor()
            is PlannedIncomesEvent.CategorySelected -> updateEditor {
                copy(categoryId = event.categoryId, error = null)
            }
            is PlannedIncomesEvent.AmountChanged -> updateEditor {
                copy(amount = event.amount, error = null)
            }
            is PlannedIncomesEvent.DueOnChanged -> updateEditor {
                copy(dueOn = event.dueOn, error = null)
            }
            PlannedIncomesEvent.SaveRequested -> save()
            PlannedIncomesEvent.DeleteRequested -> updateEditor {
                if (canDelete) copy(isDeleteConfirmationVisible = true) else this
            }
            PlannedIncomesEvent.DeleteDismissed -> updateEditor {
                copy(isDeleteConfirmationVisible = false)
            }
            PlannedIncomesEvent.DeleteConfirmed -> delete()
        }
    }

    private fun load() {
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val plannedIncomes = getPlannedIncomes()
                _uiState.update {
                    it.copy(
                        asOf = plannedIncomes.asOf,
                        items = plannedIncomes.items,
                        isLoading = false,
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: PlannedIncomesException) {
                _uiState.update {
                    it.copy(isLoading = false, error = exception.toUiError())
                }
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = PlannedIncomesError.Unknown)
                }
            }
        }
    }

    private fun openCreateEditor() {
        if (_uiState.value.isLoading) return
        val today = LocalDate.now(ALMATY_ZONE)
        _uiState.update { state ->
            state.copy(
                editor = PlannedIncomeEditorUiState(
                    today = today,
                    categoryId = state.incomeCategories.firstOrNull()?.id.orEmpty(),
                    dueOn = today.toString(),
                ),
            )
        }
        loadIncomeCategoriesIfNeeded()
    }

    private fun openEditEditor(incomeId: String) {
        val income = _uiState.value.items.firstOrNull { it.id == incomeId } ?: return
        _uiState.update {
            it.copy(
                editor = PlannedIncomeEditorUiState(
                    original = income,
                    today = LocalDate.now(ALMATY_ZONE),
                    categoryId = income.categoryId,
                    amount = income.plannedAmount.toPlainString(),
                    dueOn = income.dueOn.toString(),
                ),
            )
        }
        loadIncomeCategoriesIfNeeded()
    }

    private fun closeEditor() {
        if (_uiState.value.editor?.isSaving == true) return
        _uiState.update { it.copy(editor = null) }
    }

    private fun loadIncomeCategoriesIfNeeded() {
        if (_uiState.value.incomeCategories.isNotEmpty() || _uiState.value.isCategoriesLoading) {
            return
        }
        _uiState.update { it.copy(isCategoriesLoading = true) }
        viewModelScope.launch {
            try {
                val categories = getCategories().filter { it.kind == CategoryKind.Income }
                _uiState.update { state ->
                    state.copy(
                        incomeCategories = categories,
                        isCategoriesLoading = false,
                        editor = state.editor?.let { editor ->
                            if (editor.isCreating && editor.categoryId.isBlank()) {
                                editor.copy(categoryId = categories.firstOrNull()?.id.orEmpty())
                            } else {
                                editor
                            }
                        },
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: CategoriesException) {
                showCategoriesError(exception.toPlannedIncomesError())
            } catch (exception: Exception) {
                showCategoriesError(PlannedIncomesError.Unknown)
            }
        }
    }

    private fun save() {
        val editor = _uiState.value.editor?.takeIf(PlannedIncomeEditorUiState::canSave) ?: return
        val amount = editor.parsedAmount ?: return
        val dueOn = editor.parsedDueOn ?: return
        updateEditor { copy(isSaving = true, error = null) }

        viewModelScope.launch {
            try {
                editor.original?.let { original ->
                    updatePlannedIncome(
                        income = original,
                        categoryId = editor.categoryId.takeIf { it != original.categoryId },
                        amount = amount.takeIf { it.compareTo(original.plannedAmount) != 0 },
                        dueOn = dueOn.takeIf { it != original.dueOn },
                    )
                } ?: createPlannedIncome(
                    categoryId = editor.categoryId,
                    amount = amount,
                    dueOn = dueOn,
                )
                _uiState.update { it.copy(editor = null) }
                load()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: PlannedIncomesException) {
                handleEditorException(exception)
            } catch (exception: Exception) {
                showEditorError(PlannedIncomesError.Unknown)
            }
        }
    }

    private fun delete() {
        val editor = _uiState.value.editor ?: return
        val income = editor.original ?: return
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
                deletePlannedIncome(income)
                _uiState.update {
                    it.copy(
                        items = it.items.filterNot { item -> item.id == income.id },
                        editor = null,
                    )
                }
                load()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: PlannedIncomesException) {
                handleEditorException(exception)
            } catch (exception: Exception) {
                showEditorError(PlannedIncomesError.Unknown)
            }
        }
    }

    private fun showCategoriesError(error: PlannedIncomesError) {
        _uiState.update { state ->
            state.copy(
                isCategoriesLoading = false,
                editor = state.editor?.copy(error = error),
            )
        }
    }

    private fun showEditorError(error: PlannedIncomesError) {
        updateEditor {
            copy(
                isSaving = false,
                error = error,
                isDeleteConfirmationVisible = false,
            )
        }
    }

    private fun handleEditorException(exception: PlannedIncomesException) {
        showEditorError(exception.toUiError())
        if (
            exception == PlannedIncomesException.Conflict ||
            exception == PlannedIncomesException.NotFound
        ) {
            load()
        }
    }

    private fun updateEditor(
        transform: PlannedIncomeEditorUiState.() -> PlannedIncomeEditorUiState,
    ) {
        _uiState.update { state -> state.copy(editor = state.editor?.transform()) }
    }
}

private fun PlannedIncomesException.toUiError(): PlannedIncomesError = when (this) {
    PlannedIncomesException.MissingApiToken -> PlannedIncomesError.MissingApiToken
    PlannedIncomesException.Unauthorized -> PlannedIncomesError.Unauthorized
    PlannedIncomesException.Forbidden -> PlannedIncomesError.Forbidden
    PlannedIncomesException.InvalidRequest -> PlannedIncomesError.InvalidRequest
    PlannedIncomesException.NotFound -> PlannedIncomesError.NotFound
    PlannedIncomesException.Conflict -> PlannedIncomesError.Conflict
    PlannedIncomesException.Network -> PlannedIncomesError.Network
    PlannedIncomesException.InvalidResponse -> PlannedIncomesError.InvalidResponse
    PlannedIncomesException.Unknown -> PlannedIncomesError.Unknown
}

private fun CategoriesException.toPlannedIncomesError(): PlannedIncomesError = when (this) {
    CategoriesException.MissingApiToken -> PlannedIncomesError.MissingApiToken
    CategoriesException.Unauthorized -> PlannedIncomesError.Unauthorized
    CategoriesException.Forbidden -> PlannedIncomesError.Forbidden
    CategoriesException.InvalidRequest -> PlannedIncomesError.InvalidRequest
    CategoriesException.NotFound -> PlannedIncomesError.NotFound
    CategoriesException.Conflict -> PlannedIncomesError.Conflict
    CategoriesException.Network -> PlannedIncomesError.Network
    CategoriesException.InvalidResponse -> PlannedIncomesError.InvalidResponse
    CategoriesException.Unknown -> PlannedIncomesError.Unknown
}

private val ALMATY_ZONE: ZoneId = ZoneId.of("Asia/Almaty")
