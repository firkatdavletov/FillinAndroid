package com.firkat.fillin.feature.plannedincomes.ui

import com.firkat.fillin.feature.plannedoperations.domain.model.PlanningFrequency
import com.firkat.fillin.feature.plannedoperations.domain.model.PlanningRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import com.firkat.fillin.feature.categories.domain.repository.CategoriesException
import com.firkat.fillin.feature.categories.domain.usecase.GetCategories
import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncome
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
    private val editorOnly: Boolean = false,
    private val repository: com.firkat.fillin.feature.plannedincomes.domain.repository.PlannedIncomesRepository? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlannedIncomesUiState())
    val uiState: StateFlow<PlannedIncomesUiState> = _uiState.asStateFlow()

    init {
        if (!editorOnly) load()
    }

    fun onEvent(event: PlannedIncomesEvent) {
        when (event) {
            PlannedIncomesEvent.Retry -> load()
            PlannedIncomesEvent.Refresh -> load(isRefreshing = true)
            PlannedIncomesEvent.CreateRequested -> openCreateEditor()
            is PlannedIncomesEvent.EditRequested -> openEditEditor(event.incomeId)
            PlannedIncomesEvent.EditorClosed -> closeEditor()
            is PlannedIncomesEvent.CategorySelected -> updateEditor {
                copy(categoryId = event.categoryId, name = name.ifBlank { _uiState.value.incomeCategories.firstOrNull { it.id == event.categoryId }?.name.orEmpty() }, error = null)
            }
            is PlannedIncomesEvent.AmountChanged -> updateEditor {
                copy(amount = event.amount, error = null)
            }
            is PlannedIncomesEvent.DueOnChanged -> updateEditor {
                copy(dueOn = event.dueOn, error = null)
            }
            is PlannedIncomesEvent.NameChanged -> updateEditor { copy(name = event.value) }
            is PlannedIncomesEvent.FrequencyChanged -> updateEditor { copy(frequency = event.value) }
            is PlannedIncomesEvent.ActiveChanged -> updateEditor { copy(active = event.value) }
            is PlannedIncomesEvent.RemainderChanged -> updateEditor { copy(confirmRemainder = event.value,
                amount = if (event.value) original?.remainingAmount?.toPlainString() ?: amount else original?.plannedAmount?.toPlainString() ?: amount) }
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

    private fun load(isRefreshing: Boolean = false) {
        if (editorOnly) {
            _uiState.update { it.copy(changeVersion = it.changeVersion + 1) }
            return
        }
        if (_uiState.value.isLoading || _uiState.value.isRefreshing) return
        if (isRefreshing && _uiState.value.editor != null) return

        _uiState.update {
            it.copy(isLoading = !isRefreshing, isRefreshing = isRefreshing, error = null)
        }
        viewModelScope.launch {
            try {
                val plannedIncomes = getPlannedIncomes()
                _uiState.update {
                    it.copy(
                        asOf = plannedIncomes.asOf,
                        items = plannedIncomes.items,
                        isLoading = false,
                        isRefreshing = false,
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: PlannedIncomesException) {
                _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, error = exception.toUiError())
                }
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, error = PlannedIncomesError.Unknown)
                }
            }
        }
    }

    private fun openCreateEditor() {
        if (_uiState.value.isLoading || _uiState.value.isRefreshing) return
        val today = LocalDate.now(ALMATY_ZONE)
        _uiState.update { state ->
            state.copy(
                editor = PlannedIncomeEditorUiState(
                    today = today,
                    categoryId = state.incomeCategories.firstOrNull()?.id.orEmpty(),
                    name = state.incomeCategories.firstOrNull()?.name.orEmpty(),
                    dueOn = today.toString(),
                ),
            )
        }
        loadIncomeCategoriesIfNeeded()
    }

    private fun openEditEditor(incomeId: String) {
        if (_uiState.value.isRefreshing) return
        val income = _uiState.value.items.firstOrNull { it.id == incomeId } ?: return
        edit(income)
    }

    fun edit(rule: PlanningRule) {
        loadIncomeCategoriesIfNeeded()
        val frequency = com.firkat.fillin.feature.dailylimit.domain.IncomeFrequency.valueOf(when (rule.frequency) {
            PlanningFrequency.Once -> "ONCE"
            PlanningFrequency.Weekly -> "WEEKLY"
            PlanningFrequency.Biweekly -> "BIWEEKLY"
            PlanningFrequency.Monthly -> "MONTHLY"
        })
        _uiState.update { it.copy(editor = PlannedIncomeEditorUiState(originalRule = rule,
            today = LocalDate.now(ZoneId.of("Asia/Almaty")), categoryId = rule.categoryId,
            name = rule.name, amount = rule.amount.toPlainString(), dueOn = rule.startsOn.toString(), frequency = frequency)) }
    }

    fun edit(income: PlannedIncome) {
        if (_uiState.value.isRefreshing) return
        _uiState.update {
            it.copy(
                editor = PlannedIncomeEditorUiState(
                    original = income,
                    name = income.name, frequency = income.frequency, active = income.active,
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
                                editor.copy(categoryId = categories.firstOrNull()?.id.orEmpty(), name = editor.name.ifBlank { categories.firstOrNull()?.name.orEmpty() })
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
                if (editor.originalRule != null) {
                    requireNotNull(repository).saveRuleDetails(editor.originalRule, editor.categoryId, amount, dueOn,
                        editor.name, editor.frequency, editor.active)
                } else if (repository != null) {
                    repository.saveDetails(editor.original, editor.categoryId, amount, dueOn,
                        editor.name.ifBlank { _uiState.value.incomeCategories.firstOrNull { it.id == editor.categoryId }?.name.orEmpty() },
                        editor.frequency, editor.active, editor.confirmRemainder)
                } else editor.original?.let { original ->
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
        val income = editor.original
        val rule = editor.originalRule
        if (income == null && rule == null) return
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
                if (rule != null) requireNotNull(repository).deletePlannedIncome(rule.id, rule.version) else deletePlannedIncome(requireNotNull(income))
                _uiState.update {
                    it.copy(
                        items = it.items.filterNot { item -> item.id == (rule?.id ?: income?.id) },
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
