package com.firkat.fillin.feature.operationhistory.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import com.firkat.fillin.feature.categories.domain.usecase.DEFAULT_USED_CATEGORY_LIMIT
import com.firkat.fillin.feature.categories.domain.usecase.GetFrequentlyUsedCategories
import com.firkat.fillin.feature.categories.domain.usecase.GetRecentlyUsedCategories
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.feature.operationhistory.domain.repository.NewOperation
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryException
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryStore
import com.firkat.fillin.feature.operationhistory.domain.usecase.OperationInputResult
import com.firkat.fillin.feature.operationhistory.domain.usecase.parseOperationInput
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OperationCreateViewModel(
    private val history: OperationHistoryStore,
    frequent: GetFrequentlyUsedCategories,
    recent: GetRecentlyUsedCategories,
    private val clock: Clock = Clock.system(ZoneId.of("Asia/Almaty"))
) : ViewModel() {
    private val _uiState =
        MutableStateFlow(OperationCreateState(OperationCreateUiState(occurredOn = LocalDate.now(clock))))
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch { history.context.collect { context -> _uiState.update { it.copy(offline = context) } } }
        viewModelScope.launch {
            uiState.map { it.createEditor?.type }.distinctUntilChanged().collectLatest { type ->
                if (type == null) return@collectLatest
                val kind = if (type == OperationType.Expense) CategoryKind.Expense else CategoryKind.Income
                combine(
                    frequent(limit = FREQUENT_LIMIT, kind = kind),
                    recent(limit = RECENT_LIMIT, kind = kind)
                ) { frequentCategories, recentCategories -> frequentCategories to recentCategories }
                    .collect { (frequentCategories, recentCategories) ->
                        updateCreator {
                            if (this.type != type) {
                                this
                            } else {
                                copy(
                                    frequentlyUsed = frequentCategories,
                                    recentlyUsed = recentCategories
                                )
                            }
                        }
                    }
            }
        }
    }

    fun onEvent(
        event: OperationCreateEvent
    ) {
        when (event) {
            OperationCreateEvent.CreateClosed -> if (_uiState.value.createEditor?.isSaving !=
                true
            ) {
                _uiState.update { it.copy(createEditor = null) }
            }
            is OperationCreateEvent.CreateInputChanged -> updateCreator {
                if (isSaving ||
                    !Regex("[0-9]*([.,][0-9]{0,2})?").matches(event.value)
                ) {
                    this
                } else {
                    copy(input = event.value, inputError = null, error = null)
                }
            }
            is OperationCreateEvent.CreateTypeChanged -> updateCreator {
                if (isSaving || type == event.type) {
                    this
                } else {
                    copy(type = event.type, error = null, frequentlyUsed = emptyList(), recentlyUsed = emptyList())
                }
            }
            is OperationCreateEvent.CreateCategoryClicked -> create(event.id)
            is OperationCreateEvent.CreateFeedbackShown -> updateCreator {
                if (feedbackSequence ==
                    event.sequence
                ) {
                    copy(feedback = null)
                } else {
                    this
                }
            }
        }
    }

    private fun create(
        categoryId: String
    ) {
        val current = _uiState.value
        val editor = current.createEditor?.takeUnless { it.isSaving } ?: return
        val parsed = parseOperationInput(editor.input)
        if (parsed is OperationInputResult.Invalid) {
            updateCreator {
                copy(
                    inputError = parsed.reason,
                    feedback = OperationCreateFeedback.InvalidInput,
                    feedbackSequence =
                    feedbackSequence + 1
                )
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
                        null
                    )
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
                        feedbackSequence = feedbackSequence + 1
                    )
                }
            }
        }
    }

    private fun createFeedback(
        feedback: OperationCreateFeedback
    ) {
        updateCreator { copy(feedback = feedback, feedbackSequence = feedbackSequence + 1) }
    }

    private fun updateCreator(
        update: OperationCreateUiState.() -> OperationCreateUiState
    ) {
        _uiState.update { it.copy(createEditor = it.createEditor?.update()) }
    }
}

private const val FREQUENT_LIMIT = DEFAULT_USED_CATEGORY_LIMIT
private const val RECENT_LIMIT = DEFAULT_USED_CATEGORY_LIMIT
