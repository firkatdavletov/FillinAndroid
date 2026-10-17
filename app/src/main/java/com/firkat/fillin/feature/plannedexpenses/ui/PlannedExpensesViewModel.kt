package com.firkat.fillin.feature.plannedexpenses.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import com.firkat.fillin.feature.categories.domain.repository.CategoriesException
import com.firkat.fillin.feature.categories.domain.usecase.GetCategories
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpense
import com.firkat.fillin.feature.plannedexpenses.domain.repository.PlannedExpensesException
import com.firkat.fillin.feature.plannedexpenses.domain.usecase.ClosePlannedExpense
import com.firkat.fillin.feature.plannedexpenses.domain.usecase.CreatePlannedExpenseRule
import com.firkat.fillin.feature.plannedexpenses.domain.usecase.DeletePlannedExpenseRule
import com.firkat.fillin.feature.plannedexpenses.domain.usecase.GetPlannedExpenses
import com.firkat.fillin.feature.plannedexpenses.domain.usecase.UpdatePlannedExpenseRule
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PlannedExpensesViewModel(
    private val getPlannedExpenses: GetPlannedExpenses,
    private val getCategories: GetCategories,
    private val createRule: CreatePlannedExpenseRule,
    private val updateRule: UpdatePlannedExpenseRule,
    private val deleteRule: DeletePlannedExpenseRule,
    private val closeExpense: ClosePlannedExpense,
    private val editorOnly: Boolean = false,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlannedExpensesUiState())
    val uiState: StateFlow<PlannedExpensesUiState> = _uiState.asStateFlow()

    init {
        if (!editorOnly) load()
    }

    fun onEvent(event: PlannedExpensesEvent) {
        when (event) {
            PlannedExpensesEvent.Retry -> load()
            PlannedExpensesEvent.Refresh -> load(isRefreshing = true)
            is PlannedExpensesEvent.PeriodSelected -> _uiState.update {
                it.copy(selectedPeriod = event.period)
            }
            PlannedExpensesEvent.CreateRequested -> openCreateEditor()
            is PlannedExpensesEvent.EditRequested -> openEditEditor(event.expenseId)
            PlannedExpensesEvent.EditorClosed -> closeEditor()
            is PlannedExpensesEvent.CategorySelected -> updateEditor {
                copy(categoryId = event.categoryId, error = null)
            }
            is PlannedExpensesEvent.AmountChanged -> updateEditor {
                copy(amount = event.amount, error = null)
            }
            is PlannedExpensesEvent.FrequencyChanged -> updateEditor {
                copy(
                    frequency = event.frequency,
                    scheduleValue = "",
                    error = null,
                )
            }
            is PlannedExpensesEvent.ScheduleValueChanged -> updateEditor {
                copy(scheduleValue = event.scheduleValue.filter(Char::isDigit), error = null)
            }
            is PlannedExpensesEvent.StartsOnChanged -> updateEditor {
                copy(startsOn = event.startsOn, error = null)
            }
            PlannedExpensesEvent.SaveRequested -> save()
            PlannedExpensesEvent.DeleteRequested -> updateEditor {
                if (canDelete) copy(isDeleteConfirmationVisible = true) else this
            }
            PlannedExpensesEvent.DeleteDismissed -> updateEditor {
                copy(isDeleteConfirmationVisible = false)
            }
            PlannedExpensesEvent.DeleteConfirmed -> delete()
            is PlannedExpensesEvent.CloseRequested -> requestClose(event.expenseId)
            PlannedExpensesEvent.CloseDismissed -> dismissClose()
            PlannedExpensesEvent.CloseConfirmed -> close()
        }
    }

    private fun load(isRefreshing: Boolean = false) {
        if (editorOnly) {
            _uiState.update { it.copy(changeVersion = it.changeVersion + 1) }
            return
        }
        val currentState = _uiState.value
        if (currentState.isLoading || currentState.isRefreshing) return
        if (
            isRefreshing &&
            (currentState.editor != null ||
                currentState.closeConfirmationExpenseId != null ||
                currentState.isClosingExpense)
        ) return

        _uiState.update {
            it.copy(isLoading = !isRefreshing, isRefreshing = isRefreshing, error = null)
        }
        viewModelScope.launch {
            try {
                val plannedExpenses = getPlannedExpenses()
                _uiState.update {
                    it.copy(
                        asOf = plannedExpenses.asOf,
                        throughInclusive = plannedExpenses.throughInclusive,
                        items = plannedExpenses.items,
                        isLoading = false,
                        isRefreshing = false,
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: PlannedExpensesException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = exception.toUiError(),
                    )
                }
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = PlannedExpensesError.Unknown,
                    )
                }
            }
        }
    }

    private fun openCreateEditor() {
        if (_uiState.value.isLoading || _uiState.value.isRefreshing) return
        _uiState.update { state ->
            state.copy(
                editor = PlannedExpenseRuleEditorUiState(
                    categoryId = state.expenseCategories.firstOrNull()?.id.orEmpty(),
                    startsOn = LocalDate.now(ALMATY_ZONE).toString(),
                ),
            )
        }
        if (_uiState.value.expenseCategories.isEmpty()) loadExpenseCategories()
    }

    private fun openEditEditor(expenseId: String) {
        if (_uiState.value.isRefreshing) return
        val expense = _uiState.value.items.firstOrNull { it.id == expenseId } ?: return
        edit(expense)
    }

    fun edit(expense: PlannedExpense) {
        if (_uiState.value.isRefreshing) return
        _uiState.update {
            it.copy(
                editor = PlannedExpenseRuleEditorUiState(
                    original = expense,
                    categoryId = expense.categoryId,
                    amount = expense.plannedAmount.toPlainString(),
                ),
            )
        }
    }

    private fun closeEditor() {
        if (_uiState.value.editor?.isSaving == true) return
        _uiState.update { it.copy(editor = null) }
    }

    private fun loadExpenseCategories() {
        if (_uiState.value.isCategoriesLoading) return
        _uiState.update { it.copy(isCategoriesLoading = true) }
        viewModelScope.launch {
            try {
                val categories = getCategories().filter { it.kind == CategoryKind.Expense }
                _uiState.update { state ->
                    state.copy(
                        expenseCategories = categories,
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
                showCategoriesError(exception.toPlannedExpensesError())
            } catch (exception: Exception) {
                showCategoriesError(PlannedExpensesError.Unknown)
            }
        }
    }

    private fun save() {
        val editor = _uiState.value.editor?.takeIf(PlannedExpenseRuleEditorUiState::canSave) ?: return
        val amount = editor.parsedAmount ?: return
        updateEditor { copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                editor.original?.let { expense ->
                    updateRule(expense = expense, amount = amount)
                } ?: createRule(
                    categoryId = editor.categoryId,
                    amount = amount,
                    frequency = editor.frequency,
                    scheduleValue = requireNotNull(editor.parsedScheduleValue),
                    startsOn = requireNotNull(editor.parsedStartsOn),
                )
                _uiState.update { it.copy(editor = null) }
                load()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: PlannedExpensesException) {
                showEditorError(exception.toUiError())
            } catch (exception: Exception) {
                showEditorError(PlannedExpensesError.Unknown)
            }
        }
    }

    private fun delete() {
        val editor = _uiState.value.editor ?: return
        val expense = editor.original ?: return
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
                deleteRule(expense)
                _uiState.update { it.copy(editor = null) }
                load()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: PlannedExpensesException) {
                showEditorError(exception.toUiError())
            } catch (exception: Exception) {
                showEditorError(PlannedExpensesError.Unknown)
            }
        }
    }

    private fun requestClose(expenseId: String) {
        if (_uiState.value.isClosingExpense || _uiState.value.isRefreshing) return
        val expense = _uiState.value.items.firstOrNull { it.id == expenseId }
            ?.takeIf { it.canBeClosed }
            ?: return
        requestClose(expense)
    }

    fun requestClose(expense: PlannedExpense) {
        if (_uiState.value.isClosingExpense || _uiState.value.isRefreshing || !expense.canBeClosed) return
        _uiState.update {
            it.copy(
                items = if (editorOnly) listOf(expense) else it.items,
                closeConfirmationExpenseId = expense.id,
                closeError = null,
            )
        }
    }

    private fun dismissClose() {
        if (_uiState.value.isClosingExpense) return
        _uiState.update {
            it.copy(
                closeConfirmationExpenseId = null,
                closeError = null,
            )
        }
    }

    private fun close() {
        val state = _uiState.value
        if (state.isClosingExpense) return
        val expense = state.items
            .firstOrNull { it.id == state.closeConfirmationExpenseId }
            ?.takeIf { it.canBeClosed }
            ?: return

        _uiState.update { it.copy(isClosingExpense = true, closeError = null) }
        viewModelScope.launch {
            try {
                closeExpense(expense)
                _uiState.update {
                    it.copy(
                        items = it.items.filterNot { item -> item.id == expense.id },
                        closeConfirmationExpenseId = null,
                        isClosingExpense = false,
                        closeError = null,
                    )
                }
                load()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: PlannedExpensesException) {
                showCloseError(exception.toUiError())
            } catch (exception: Exception) {
                showCloseError(PlannedExpensesError.Unknown)
            }
        }
    }

    private fun showCloseError(error: PlannedExpensesError) {
        _uiState.update {
            it.copy(
                isClosingExpense = false,
                closeError = error,
            )
        }
    }

    private fun showCategoriesError(error: PlannedExpensesError) {
        _uiState.update { state ->
            state.copy(
                isCategoriesLoading = false,
                editor = state.editor?.copy(error = error),
            )
        }
    }

    private fun showEditorError(error: PlannedExpensesError) {
        updateEditor {
            copy(
                isSaving = false,
                error = error,
                isDeleteConfirmationVisible = false,
            )
        }
    }

    private fun updateEditor(
        transform: PlannedExpenseRuleEditorUiState.() -> PlannedExpenseRuleEditorUiState,
    ) {
        _uiState.update { state ->
            state.copy(editor = state.editor?.transform())
        }
    }
}

private fun PlannedExpensesException.toUiError(): PlannedExpensesError = when (this) {
    PlannedExpensesException.MissingApiToken -> PlannedExpensesError.MissingApiToken
    PlannedExpensesException.Unauthorized -> PlannedExpensesError.Unauthorized
    PlannedExpensesException.Forbidden -> PlannedExpensesError.Forbidden
    PlannedExpensesException.InvalidRequest -> PlannedExpensesError.InvalidRequest
    PlannedExpensesException.NotFound -> PlannedExpensesError.NotFound
    PlannedExpensesException.Conflict -> PlannedExpensesError.Conflict
    PlannedExpensesException.Network -> PlannedExpensesError.Network
    PlannedExpensesException.InvalidResponse -> PlannedExpensesError.InvalidResponse
    PlannedExpensesException.Unknown -> PlannedExpensesError.Unknown
}

private fun CategoriesException.toPlannedExpensesError(): PlannedExpensesError = when (this) {
    CategoriesException.MissingApiToken -> PlannedExpensesError.MissingApiToken
    CategoriesException.Unauthorized -> PlannedExpensesError.Unauthorized
    CategoriesException.Forbidden -> PlannedExpensesError.Forbidden
    CategoriesException.InvalidRequest -> PlannedExpensesError.InvalidRequest
    CategoriesException.NotFound -> PlannedExpensesError.NotFound
    CategoriesException.Conflict -> PlannedExpensesError.Conflict
    CategoriesException.Network -> PlannedExpensesError.Network
    CategoriesException.InvalidResponse -> PlannedExpensesError.InvalidResponse
    CategoriesException.Unknown -> PlannedExpensesError.Unknown
}

private val ALMATY_ZONE: ZoneId = ZoneId.of("Asia/Almaty")
