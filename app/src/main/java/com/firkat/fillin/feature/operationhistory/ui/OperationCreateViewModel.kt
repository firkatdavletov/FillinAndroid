package com.firkat.fillin.feature.operationhistory.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.feature.operationhistory.domain.repository.NewOperation
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryException
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryStore
import com.firkat.fillin.feature.operationhistory.domain.usecase.OperationInputResult
import com.firkat.fillin.feature.operationhistory.domain.usecase.parseOperationInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class OperationCreateViewModel(
    private val history: OperationHistoryStore,
    private val clock: Clock = Clock.system(ZoneId.of("Asia/Almaty")),
) : ViewModel() {
    private val _uiState = MutableStateFlow(OperationCreateState(OperationCreateUiState(occurredOn = LocalDate.now(clock))))
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch { history.context.collect { context -> _uiState.update { it.copy(offline = context) } } }
    }

    fun onEvent(event: OperationCreateEvent) {
        when (event) {
            OperationCreateEvent.CreateClosed -> if (_uiState.value.createEditor?.isSaving != true) _uiState.update { it.copy(createEditor = null) }
            is OperationCreateEvent.CreateInputChanged -> updateCreator { if (isSaving || !Regex("[0-9]*([.,][0-9]{0,2})?").matches(event.value)) this else copy(input = event.value, inputError = null, error = null) }
            is OperationCreateEvent.CreateTypeChanged -> updateCreator { if (isSaving) this else copy(type = event.type, error = null) }
            OperationCreateEvent.CategoriesOpened -> updateCreator { if (isSaving) this else copy(categoriesOpen = true) }
            OperationCreateEvent.CategoriesDismissed -> updateCreator { copy(categoriesOpen = false) }
            is OperationCreateEvent.CreateCategoryClicked -> {
                updateCreator { copy(categoriesOpen = false) }
                create(event.id)
            }
            is OperationCreateEvent.CreateFeedbackShown -> updateCreator { if (feedbackSequence == event.sequence) copy(feedback = null) else this }
        }
    }

    private fun create(categoryId: String) {
        val current = _uiState.value
        val editor = current.createEditor?.takeUnless { it.isSaving } ?: return
        val parsed = parseOperationInput(editor.input)
        if (parsed is OperationInputResult.Invalid) {
            updateCreator {
                copy(inputError = parsed.reason, feedback = OperationCreateFeedback.InvalidInput, feedbackSequence = feedbackSequence + 1)
            }
            return
        }
        parsed as OperationInputResult.Valid
        if (!current.offline.initialSyncComplete || current.offline.syncError == "scope_changed") {
            createFeedback(OperationCreateFeedback.Unavailable)
            return
        }
        val category = current.offline.categories.firstOrNull { it.id == categoryId }
        val expectedKind = if (editor.type == OperationType.Expense) CategoryKind.Expense else CategoryKind.Income
        if (category?.kind != expectedKind) {
            createFeedback(OperationCreateFeedback.InvalidCategory)
            return
        }
        val occurredOn = LocalDate.now(clock)
        if (occurredOn > LocalDate.now(clock) || current.offline.accountingStartedOn?.let { occurredOn < it } == true) {
            createFeedback(OperationCreateFeedback.Unavailable)
            return
        }
        updateCreator { copy(isSaving = true, error = null, inputError = null, feedback = null) }
        viewModelScope.launch {
            try {
                val result = history.create(
                    NewOperation(
                        editor.type,
                        categoryId,
                        parsed.amount,
                        occurredOn,
                        null,
                    ),
                )
                _uiState.update { it.copy(createEditor = null, created = result) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                updateCreator {
                    copy(
                        isSaving = false,
                        error = (exception as? OperationHistoryException)?.toUiError() ?: OperationHistoryError.Unknown,
                        feedback = OperationCreateFeedback.SaveFailed,
                        feedbackSequence = feedbackSequence + 1,
                    )
                }
            }
        }
    }

    private fun createFeedback(feedback: OperationCreateFeedback) {
        updateCreator { copy(feedback = feedback, feedbackSequence = feedbackSequence + 1) }
    }

    private fun updateCreator(update: OperationCreateUiState.() -> OperationCreateUiState) {
        _uiState.update { it.copy(createEditor = it.createEditor?.update()) }
    }
}
