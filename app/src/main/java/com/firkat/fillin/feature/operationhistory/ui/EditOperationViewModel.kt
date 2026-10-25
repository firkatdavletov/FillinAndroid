package com.firkat.fillin.feature.operationhistory.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import com.firkat.fillin.feature.operationhistory.domain.model.Operation
import com.firkat.fillin.feature.operationhistory.domain.model.OperationStatus
import com.firkat.fillin.feature.operationhistory.domain.model.OperationSyncStatus
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.feature.operationhistory.domain.repository.NewOperation
import com.firkat.fillin.feature.operationhistory.domain.repository.OfflineHistoryContext
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryException
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryStore
import com.firkat.fillin.feature.operationhistory.domain.usecase.OperationInputError
import com.firkat.fillin.feature.operationhistory.domain.usecase.OperationInputResult
import com.firkat.fillin.feature.operationhistory.domain.usecase.RefundPurchase
import com.firkat.fillin.feature.operationhistory.domain.usecase.UpdateOperationAmount
import com.firkat.fillin.feature.operationhistory.domain.usecase.parseOperationInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class EditOperationViewModel(
    private val operationId: String,
    private val history: OperationHistoryStore,
    private val updateOperationAmount: UpdateOperationAmount,
    private val refundPurchase: RefundPurchase,
    private val clock: Clock = Clock.system(ZoneId.of("Asia/Almaty")),
) : ViewModel() {
    private val state = MutableStateFlow(EditOperationUiState())
    val uiState = state.asStateFlow()
    private val effectChannel = Channel<EditOperationEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()
    private var original: Operation? = null
    private var context = OfflineHistoryContext()
    private var completed = false
    val canDismiss: Boolean get() = !state.value.isSaving && !completed

    init {
        viewModelScope.launch {
            try {
                combine(history.observeOperation(operationId), history.context) { operation, context -> operation to context }
                    .collect { (operation, updatedContext) ->
                        context = updatedContext
                        val baseline = original
                        // A sync acknowledgement may change tokens without changing the edited data.
                        if (baseline != null && operation != null && baseline.syncStatus != OperationSyncStatus.Rejected &&
                            operation.syncStatus != OperationSyncStatus.Rejected &&
                            baseline.copy(version = operation.version, syncStatus = operation.syncStatus, syncError = operation.syncError) == operation
                        ) original = operation
                        val loading = operation == null && !updatedContext.initialSyncComplete
                        var next = state.value.copy(operation = operation, isLoading = loading)
                        if (original == null && operation != null) {
                            original = operation
                            next = next.copy(
                                input = if (operation.syncStatus == OperationSyncStatus.Rejected) {
                                    listOfNotNull(operation.description?.takeIf(String::isNotBlank), operation.originalAmount.toPlainString()).joinToString(" ")
                                } else operation.originalAmount.toPlainString(),
                                type = operation.type,
                                categoryId = operation.categoryId,
                            )
                        }
                        publish(next.copy(error = when {
                            loading -> null
                            operation == null -> OperationHistoryError.NotFound
                            next.error == OperationHistoryError.NotFound -> null
                            else -> next.error
                        }))
                    }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                publish(state.value.copy(isLoading = false, error = error.uiError()))
            }
        }
    }

    fun onEvent(event: EditOperationEvent) {
        if (completed || state.value.isSaving) return
        val current = state.value
        when (event) {
            is EditOperationEvent.InputChanged -> if (current.mode != EditOperationMode.ReadOnly) {
                val next = current.copy(input = event.value, error = null, inputError = null)
                val invalid = parse(next) as? OperationInputResult.Invalid
                publish(next.copy(inputError = invalid?.reason?.takeIf { event.value.isNotBlank() }))
            }
            is EditOperationEvent.TypeChanged -> if (current.mode == EditOperationMode.Correct && current.type != event.type) {
                publish(current.copy(type = event.type, categoryId = null, error = null))
            }
            is EditOperationEvent.CategorySelected -> if (current.mode == EditOperationMode.Correct && current.categories.any { it.id == event.id }) {
                publish(current.copy(categoryId = event.id, error = null))
            }
            EditOperationEvent.SaveClicked -> save()
            EditOperationEvent.RefundClicked -> if (current.canRefund) publish(current.copy(isRefundConfirmationVisible = true))
            EditOperationEvent.RefundDismissed -> publish(current.copy(isRefundConfirmationVisible = false))
            EditOperationEvent.RefundConfirmed -> if (current.canRefund && current.isRefundConfirmationVisible) {
                mutate(EditOperationOutcome.Refunded) { refundPurchase(requireNotNull(original)) }
            }
            EditOperationEvent.CloseClicked -> {
                completed = true
                effectChannel.trySend(EditOperationEffect.Close)
            }
        }
    }

    private fun save() {
        val current = state.value
        val parsed = parse(current)
        if (parsed is OperationInputResult.Invalid) {
            publish(current.copy(inputError = parsed.reason))
            return
        }
        if (!current.canSave) return
        parsed as OperationInputResult.Valid
        val operation = requireNotNull(original)
        if (current.mode == EditOperationMode.Correct) {
            val date = operation.occurredOn
            if (date > LocalDate.now(clock) || context.accountingStartedOn?.let { date < it } == true) {
                publish(current.copy(error = OperationHistoryError.InvalidRequest))
                return
            }
            mutate(EditOperationOutcome.Corrected) {
                history.create(NewOperation(current.type, requireNotNull(current.categoryId), parsed.amount, date, parsed.description), operationId)
            }
        } else {
            mutate(EditOperationOutcome.Updated) { updateOperationAmount(operation, parsed.amount) }
        }
    }

    private fun mutate(outcome: EditOperationOutcome, action: suspend () -> Unit) {
        val current = state.value
        val latest = current.operation
        if (latest == null || latest.version != original?.version ||
            (latest.syncStatus == OperationSyncStatus.Rejected) != (original?.syncStatus == OperationSyncStatus.Rejected) ||
            latest.status != original?.status || latest.refundedAmount.compareTo(requireNotNull(original).refundedAmount) != 0
        ) {
            publish(current.copy(error = OperationHistoryError.Conflict))
            return
        }
        publish(current.copy(isSaving = true, error = null, isRefundConfirmationVisible = false))
        viewModelScope.launch {
            try {
                action()
                completed = true
                publish(state.value.copy(isSaving = false))
                effectChannel.send(EditOperationEffect.Saved(EditOperationResult(operationId, outcome)))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                publish(state.value.copy(isSaving = false, error = error.uiError()))
            }
        }
    }

    private fun publish(next: EditOperationUiState) {
        val operation = next.operation
        val mode = when {
            operation?.syncStatus == OperationSyncStatus.Rejected -> EditOperationMode.Correct
            operation?.status == OperationStatus.Posted && operation.originalCurrency == "KZT" -> EditOperationMode.Edit
            else -> EditOperationMode.ReadOnly
        }
        val categories = context.categories.filter { it.kind == if (next.type == OperationType.Expense) CategoryKind.Expense else CategoryKind.Income }
        val candidate = next.copy(mode = mode, categories = categories)
        val parsed = parse(candidate) as? OperationInputResult.Valid
        val available = !completed && !next.isSaving && context.initialSyncComplete && context.syncError != "scope_changed"
        state.value = candidate.copy(
            canSave = available && parsed != null && when (mode) {
                EditOperationMode.Edit -> operation!!.refundedAmount.signum() == 0 && parsed.amount.compareTo(operation.originalAmount) != 0
                EditOperationMode.Correct -> categories.any { it.id == next.categoryId }
                EditOperationMode.ReadOnly -> false
            },
            canRefund = available && mode == EditOperationMode.Edit && operation!!.type == OperationType.Expense && operation.refundedAmount < operation.originalAmount,
        )
    }

    private fun parse(current: EditOperationUiState): OperationInputResult {
        if (current.mode != EditOperationMode.Correct && !Regex("[0-9]+([.,][0-9]{1,2})?").matches(current.input.trim())) {
            return OperationInputResult.Invalid(OperationInputError.InvalidAmount)
        }
        return parseOperationInput(current.input)
    }

    override fun onCleared() {
        effectChannel.close()
    }
}

private fun Exception.uiError(): OperationHistoryError = (this as? OperationHistoryException)?.toUiError() ?: OperationHistoryError.Unknown
