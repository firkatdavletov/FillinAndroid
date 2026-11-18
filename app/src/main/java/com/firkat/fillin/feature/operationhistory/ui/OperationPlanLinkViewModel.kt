package com.firkat.fillin.feature.operationhistory.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationPlanLinkContext
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationPlanOffer
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationPlanOfferStore
import com.firkat.fillin.feature.operationhistory.domain.repository.PlanLinkOperation
import com.firkat.fillin.feature.operationhistory.domain.repository.PlanSuggestion
import com.firkat.fillin.feature.operationhistory.domain.repository.SuggestedPlan
import com.firkat.fillin.feature.operationhistory.domain.repository.matches
import com.firkat.fillin.feature.sync.data.BudgetLocalFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class PlanOfferPage { Loading, Suggestions, Manual, Review }
internal enum class PlanOfferError { Load, Save, Dismiss, Changed, Unavailable }

internal data class PlanOfferSelection(val operation: PlanLinkOperation, val plan: SuggestedPlan, val isManual: Boolean)

internal data class OperationPlanLinkState(
    val page: PlanOfferPage = PlanOfferPage.Loading,
    val offer: OperationPlanOffer? = null,
    val context: OperationPlanLinkContext? = null,
    val selected: PlanOfferSelection? = null,
    val groupKey: String? = null,
    val saving: Boolean = false,
    val failure: PlanOfferError? = null
) {
    val operation get() = selected?.operation ?: context?.operation ?: offer?.operation
    val error get() = if (saving) null else liveError() ?: failure

    val canProceed get() = !saving &&
        selected != null &&
        context != null &&
        error !in setOf(PlanOfferError.Changed, PlanOfferError.Unavailable, PlanOfferError.Load)

    val groups: List<PlanPickerGroup> get() = pickerGroups(context?.links?.candidates.orEmpty(), emptyList())

    fun offeredPlans(): PlanSuggestion.Choice? = offer?.suggestion as? PlanSuggestion.Choice

    private fun liveError(): PlanOfferError? {
        val current = context ?: return null
        val selection = selected
        val choices = offeredPlans()
        return when {
            selection != null -> current.selectionError(selection)
            page == PlanOfferPage.Suggestions && choices?.plans?.none(::canSelect) == true -> PlanOfferError.Changed
            else -> null
        }
    }

    fun canSelect(
        plan: SuggestedPlan
    ): Boolean {
        val op = if (page == PlanOfferPage.Manual) context?.operation else offer?.operation
        return !saving &&
            op != null &&
            context?.let {
                it.selectionError(PlanOfferSelection(op, plan, page == PlanOfferPage.Manual)) == null
            } == true
    }
}

private fun OperationPlanLinkContext.selectionError(
    selection: PlanOfferSelection
): PlanOfferError? {
    val live = links.candidates.firstOrNull {
        it.target.type == selection.plan.target.type && it.target.stableKey == selection.plan.target.stableKey
    }
    return when {
        isLinked || operation == null -> PlanOfferError.Unavailable
        !selection.operation.matches(operation) -> PlanOfferError.Changed
        live == null -> PlanOfferError.Unavailable
        !selection.plan.matches(live) -> PlanOfferError.Changed
        else -> null
    }
}

internal data class OperationPlanLinkClosed(val planName: String? = null, val remainderKey: String? = null)

internal class OperationPlanLinkViewModel(private val operationId: String, private val store: OperationPlanOfferStore) :
    ViewModel() {
    private val mutable = MutableStateFlow(OperationPlanLinkState())
    val state = mutable.asStateFlow()
    private val channel = Channel<OperationPlanLinkClosed>(Channel.BUFFERED)
    val effects = channel.receiveAsFlow()
    private var completed = false
    private var observation: Job? = null

    init {
        load()
    }

    fun load() {
        if (completed || state.value.saving) return
        observation?.cancel()
        mutable.value = OperationPlanLinkState()
        observation = viewModelScope.launch {
            try {
                val offer = store.proposal(operationId)
                when (val suggestion = offer?.suggestion) {
                    is PlanSuggestion.Recommended -> mutable.update {
                        it.copy(
                            page = PlanOfferPage.Review,
                            offer = offer,
                            selected = PlanOfferSelection(offer.operation, suggestion.plan, isManual = false)
                        )
                    }
                    is PlanSuggestion.Choice -> mutable.update {
                        it.copy(page = PlanOfferPage.Suggestions, offer = offer)
                    }
                    PlanSuggestion.None, null -> mutable.update {
                        it.copy(page = PlanOfferPage.Review, failure = PlanOfferError.Unavailable)
                    }
                }
                store.observeOfferContext(operationId).collect { context ->
                    mutable.update { it.copy(context = context) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutable.update { it.copy(page = PlanOfferPage.Loading, failure = PlanOfferError.Load) }
            }
        }
    }

    fun chooseAnother() {
        val current = state.value
        if (completed || current.saving || current.context == null) return
        mutable.update {
            it.copy(page = PlanOfferPage.Manual, selected = null, groupKey = null, failure = null)
        }
    }

    fun group(
        key: String?
    ) {
        val current = state.value
        if (!completed && !current.saving && current.page == PlanOfferPage.Manual) {
            mutable.update { it.copy(groupKey = key, selected = null, failure = null) }
        }
    }

    fun select(
        plan: SuggestedPlan
    ) {
        val current = state.value
        if (completed || !current.canSelect(plan)) return
        val manual = current.page == PlanOfferPage.Manual
        val displayed = when (current.page) {
            PlanOfferPage.Suggestions -> current.offeredPlans()?.plans
            PlanOfferPage.Manual -> current.groups.firstOrNull { it.key == current.groupKey }?.payments
            else -> null
        }.orEmpty()
        val operation = if (manual) current.context?.operation else current.offer?.operation
        if (operation != null && plan in displayed) {
            mutable.update { it.copy(selected = PlanOfferSelection(operation, plan, manual), failure = null) }
        }
    }

    fun proceed() {
        val current = state.value
        if (!current.canProceed || completed) return
        if (current.page != PlanOfferPage.Review) {
            mutable.update { it.copy(page = PlanOfferPage.Review, failure = null) }
        } else {
            val selection = requireNotNull(current.selected)
            runAction(defaultError = PlanOfferError.Save) {
                store.confirmOffer(operationId, selection.operation, selection.plan)
                val remainderKey = store.observeOfferContext(operationId)
                    .catch { emit(OperationPlanLinkContext()) }
                    .firstOrNull()?.links?.remainder?.plan?.target?.stableKey
                    ?.takeIf { it == selection.plan.target.stableKey }
                close(selection.plan.displayName, remainderKey)
            }
        }
    }

    fun dismiss() = runAction(defaultError = PlanOfferError.Dismiss) {
        store.dismissProposal(operationId)
        close()
    }

    fun requestDismiss(): Boolean {
        dismiss()
        return false
    }

    private suspend fun close(
        planName: String? = null,
        remainderKey: String? = null
    ) {
        completed = true
        channel.send(OperationPlanLinkClosed(planName, remainderKey))
    }

    private fun runAction(
        defaultError: PlanOfferError,
        action: suspend () -> Unit
    ) {
        if (completed || state.value.saving) return
        mutable.update { it.copy(saving = true, failure = null) }
        viewModelScope.launch {
            try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val failure = when ((error as? BudgetLocalFailure)?.code) {
                    "plan_changed", "operation_changed", "conflict" -> PlanOfferError.Changed
                    "plan_unavailable", "not_found", "access_denied", "forbidden" -> PlanOfferError.Unavailable
                    else -> defaultError
                }
                mutable.update { it.copy(failure = failure) }
            } finally {
                mutable.update { it.copy(saving = false) }
            }
        }
    }
}
