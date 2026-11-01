package com.firkat.fillin.feature.operationhistory.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.operationhistory.domain.model.Operation
import com.firkat.fillin.feature.operationhistory.domain.repository.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

internal data class OperationPlanLinkState(
    val operation: Operation? = null,
    val links: OperationPlanLinks = OperationPlanLinks(),
    val selected: SuggestedPlan? = null,
    val saving: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
)

internal enum class OperationPlanLinkEffect { Close, OpenConfirmation, ChooseAnother }

internal class OperationPlanLinkViewModel(
    private val operationId: String,
    private val history: OperationHistoryStore,
    private val store: OperationPlanLinkStore,
) : ViewModel() {
    private val mutable = MutableStateFlow(OperationPlanLinkState())
    val state = mutable.asStateFlow()
    private val channel = Channel<OperationPlanLinkEffect>(Channel.BUFFERED)
    val effects = channel.receiveAsFlow()
    private var completed = false

    init {
        viewModelScope.launch {
            try {
                val selected = store.proposal(operationId)
                mutable.update { it.copy(selected = selected) }
                combine(history.observeOperation(operationId), store.observeLinks(operationId)) { operation, links -> operation to links }
                    .collect { (operation, links) ->
                        mutable.update { current ->
                            val candidate = current.selected?.let { chosen -> links.candidates.firstOrNull {
                                it.target.stableKey == chosen.target.stableKey && it.target.type == chosen.target.type
                            } }
                            val selectionError = if (current.selected == null || current.saving || completed) null
                                else if (candidate == null) "plan_unavailable"
                                else if (candidate.version != current.selected.version) "plan_changed" else null
                            current.copy(operation = operation, links = links, loaded = true,
                                selected = candidate?.takeIf { it.version == current.selected.version } ?: current.selected,
                                error = selectionError ?: current.error)
                        }
                    }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.update { it.copy(loaded = true, error = "link_failed") }
            }
        }
    }

    fun chooseAnother() = runAction {
        store.dismissProposal(operationId)
        completed = true
        channel.send(OperationPlanLinkEffect.ChooseAnother)
    }

    fun select(plan: SuggestedPlan) = runAction {
        store.selectProposal(operationId, plan)
        completed = true
        channel.send(OperationPlanLinkEffect.OpenConfirmation)
    }

    fun confirm() {
        val state = mutable.value
        if (!state.loaded || state.selected == null || state.operation == null || state.error in setOf("plan_changed", "plan_unavailable")) return
        runAction {
            store.setLink(operationId, state.selected, state.operation.version)
            completed = true
            channel.send(OperationPlanLinkEffect.Close)
        }
    }

    fun remove() = runAction { store.removeLink(operationId, requireNotNull(mutable.value.operation).version) }

    fun dismiss() = runAction {
        store.dismissProposal(operationId)
        completed = true
        channel.send(OperationPlanLinkEffect.Close)
    }

    /** All gesture/back dismissals persist the decision before navigation removes this model. */
    fun requestDismiss(): Boolean { dismiss(); return false }

    private fun runAction(action: suspend () -> Unit) {
        if (completed || mutable.value.saving) return
        mutable.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutable.update { it.copy(error = (error as? com.firkat.fillin.feature.sync.data.BudgetLocalFailure)?.code ?: "link_failed") }
            } finally {
                mutable.update { it.copy(saving = false) }
            }
        }
    }
}
