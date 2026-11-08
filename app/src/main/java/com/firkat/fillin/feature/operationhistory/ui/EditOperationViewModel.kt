package com.firkat.fillin.feature.operationhistory.ui

import com.firkat.fillin.feature.operationhistory.domain.repository.OperationPlanLinkStore
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationType
import com.firkat.fillin.feature.plannedoperations.domain.usecase.ObservePlanningRules
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import com.firkat.fillin.feature.operationhistory.domain.model.Operation
import com.firkat.fillin.feature.operationhistory.domain.model.OperationStatus
import com.firkat.fillin.feature.operationhistory.domain.model.OperationSyncStatus
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.feature.operationhistory.domain.repository.NewOperation
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationUpdate
import com.firkat.fillin.feature.operationhistory.domain.repository.OfflineHistoryContext
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryException
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryStore
import com.firkat.fillin.feature.operationhistory.domain.usecase.OperationInputError
import com.firkat.fillin.feature.operationhistory.domain.usecase.OperationInputResult
import com.firkat.fillin.feature.operationhistory.domain.usecase.RefundPurchase
import com.firkat.fillin.feature.operationhistory.domain.usecase.UpdateOperation
import com.firkat.fillin.feature.operationhistory.domain.usecase.parseOperationInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class EditOperationViewModel(
    private val operationId: String,
    private val history: OperationHistoryStore,
    private val updateOperation: UpdateOperation,
    private val refundPurchase: RefundPurchase,
    private val clock: Clock = Clock.system(ZoneId.of("Asia/Almaty")),
    private val links: OperationPlanLinkStore? = null,
    private val rules: ObservePlanningRules? = null,
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
        if (links != null) viewModelScope.launch {
            try { links.observeLinks(operationId).collect { publish(state.value.copy(links = it)) } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { publish(state.value.copy(planError = true)) }
        }
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
                                description = operation.description.orEmpty(),
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
            is EditOperationEvent.WindowChanged -> publish(current.copy(window = event.window))
            EditOperationEvent.DiscardConfirmed -> close()
            EditOperationEvent.UnlinkConfirmed -> if (current.window == OperationDetailsWindow.Unlink) linkAction { links?.removeLink(operationId, requireNotNull(current.operation).version) }
            is EditOperationEvent.PlanSelected -> linkAction {
                requireNotNull(links).selectProposal(operationId, event.plan)
                effectChannel.send(EditOperationEffect.OpenLinkConfirmation)
            }
            is EditOperationEvent.OpenPlan -> linkAction {
                val type = if (event.plan.target.type == "EXPENSE") PlannedOperationType.Expense else PlannedOperationType.Income
                val id = event.plan.target.ruleId ?: event.plan.target.planId
                val rule = id?.let { rules?.observeRule(it)?.first()?.takeIf { rule -> rule.type == type } }
                if (rule == null) publish(state.value.copy(planError = true))
                else effectChannel.send(EditOperationEffect.OpenPlan(type, rule.id))
            }
            is EditOperationEvent.InputChanged -> if (current.mode != EditOperationMode.ReadOnly) {
                val next = current.copy(input = event.value, error = null, inputError = null)
                val invalid = parse(next) as? OperationInputResult.Invalid
                publish(next.copy(inputError = invalid?.reason?.takeIf { event.value.isNotBlank() }))
            }
            is EditOperationEvent.DescriptionChanged -> if (current.mode == EditOperationMode.Edit && current.operation?.refundedAmount?.signum() == 0) {
                publish(current.copy(description = event.value, error = null))
            }
            is EditOperationEvent.TypeChanged -> if (current.mode == EditOperationMode.Correct && current.type != event.type) {
                publish(current.copy(type = event.type, categoryId = null, error = null))
            }
            is EditOperationEvent.CategorySelected -> if ((current.mode == EditOperationMode.Correct || current.mode == EditOperationMode.Edit && current.operation?.refundedAmount?.signum() == 0) && current.categories.any { it.id == event.id }) {
                if (current.links.current.isNotEmpty() && event.id != current.operation?.categoryId) {
                    publish(current.copy(error = OperationHistoryError.UnlinkBeforeCategoryChange, window = null))
                } else publish(current.copy(categoryId = event.id, error = null, window = null))
            }
            EditOperationEvent.SaveClicked -> save()
            EditOperationEvent.RefundClicked -> if (current.canRefund) publish(current.copy(isRefundConfirmationVisible = true))
            EditOperationEvent.RefundDismissed -> publish(current.copy(isRefundConfirmationVisible = false))
            EditOperationEvent.RefundConfirmed -> if (current.canRefund && current.isRefundConfirmationVisible) {
                mutate(EditOperationOutcome.Refunded) { refundPurchase(requireNotNull(original)) }
            }
            EditOperationEvent.CloseClicked -> {
                when {
                    current.window != null -> publish(current.copy(window = null))
                    current.isRefundConfirmationVisible -> publish(current.copy(isRefundConfirmationVisible = false))
                    current.dirty -> publish(current.copy(window = OperationDetailsWindow.Discard))
                    else -> close()
                }
            }
        }
    }

    private fun close() {
        completed = true
        effectChannel.trySend(EditOperationEffect.Close)
    }

    private fun linkAction(action: suspend () -> Unit) {
        val current = state.value
        if (current.operation?.version != original?.version) {
            publish(current.copy(error = OperationHistoryError.Conflict)); return
        }
        publish(current.copy(isSaving = true, window = null, planError = false))
        viewModelScope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { publish(state.value.copy(planError = true)) }
            finally { publish(state.value.copy(isSaving = false)) }
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
            mutate(EditOperationOutcome.Updated) {
                updateOperation(operation, OperationUpdate(parsed.amount, requireNotNull(current.categoryId), current.description))
            }
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
            operation?.status == OperationStatus.Posted && !operation.hasOriginalValuation -> EditOperationMode.Edit
            else -> EditOperationMode.ReadOnly
        }
        val categories = context.categories.filter { it.kind == if (next.type == OperationType.Expense) CategoryKind.Expense else CategoryKind.Income }
        val initialInput = original?.let { if (it.syncStatus == OperationSyncStatus.Rejected) listOfNotNull(it.description?.takeIf(String::isNotBlank), it.originalAmount.toPlainString()).joinToString(" ") else it.originalAmount.toPlainString() }
        val dirty = original != null && (next.input != initialInput || next.description != original?.description.orEmpty() || next.categoryId != original?.categoryId || next.type != original?.type)
        val candidate = next.copy(mode = mode, categories = categories, dirty = dirty,
            descriptionError = mode == EditOperationMode.Edit && next.description.trim().length > 160)
        val parsed = parse(candidate) as? OperationInputResult.Valid
        val available = !completed && !next.isSaving && context.initialSyncComplete && context.syncError != "scope_changed"
        state.value = candidate.copy(
            canSave = available && parsed != null && when (mode) {
                EditOperationMode.Edit -> operation!!.refundedAmount.signum() == 0 && !candidate.descriptionError &&
                    categories.any { it.id == next.categoryId } &&
                    (parsed.amount.compareTo(operation.originalAmount) != 0 || next.categoryId != operation.categoryId ||
                        next.description.trim().takeIf(String::isNotEmpty) != operation.description?.trim()?.takeIf(String::isNotEmpty))
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
