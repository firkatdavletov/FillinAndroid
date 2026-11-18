package com.firkat.fillin.feature.operationhistory.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.operationhistory.domain.model.*
import com.firkat.fillin.feature.operationhistory.domain.repository.*
import com.firkat.fillin.feature.plannedoperations.domain.model.*
import com.firkat.fillin.feature.plannedoperations.domain.usecase.ObservePlanningRules
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal data class PlanPickerGroup(
    val key: String,
    val name: String,
    val frequency: PlanningFrequency,
    val payments: List<SuggestedPlan>
)
internal fun pickerGroups(
    plans: List<SuggestedPlan>,
    rules: List<PlanningRule>
): List<PlanPickerGroup> =
    plans.groupBy { it.target.type + ":" + (it.target.ruleId ?: it.target.planId) }.map { (key, payments) ->
        val first = payments.first()
        val rule = rules.firstOrNull {
            it.id == (first.target.ruleId ?: first.target.planId) &&
                it.type.name.equals(first.target.type, true)
        }
        PlanPickerGroup(
            key,
            rule?.name?.takeIf(String::isNotBlank) ?: first.displayName,
            rule?.frequency ?: first.frequency,
            payments
        )
    }
internal data class PlanPickerState(
    val groups: List<PlanPickerGroup> = emptyList(),
    val category: String = "",
    val group: String? = null,
    val selected: SuggestedPlan? = null,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: Boolean = false,
    val closed: Boolean = false,
    val remainderKey: String? = null
)
internal class OperationPlanPickerViewModel(
    private val id: String,
    private val history: OperationHistoryStore,
    private val links: OperationPlanLinkStore,
    private val rules: ObservePlanningRules
) : ViewModel() {
    private val mutable = MutableStateFlow(PlanPickerState())
    val state = mutable.asStateFlow()
    private var operation: Operation? = null
    private var job: Job? = null
    init {
        load()
    }
    fun load() {
        job?.cancel()
        mutable.update { it.copy(loading = true, error = false) }
        job = viewModelScope.launch {
            try {
                combine(history.observeOperation(id), links.observeLinks(id), rules()) { op, plans, catalog ->
                    Triple(op, plans, catalog)
                }
                    .collect { (op, plans, catalog) ->
                        operation = op
                        mutable.update { old ->
                            val selected = old.selected?.let { chosen -> plans.candidates.firstOrNull { it == chosen } }
                            old.copy(
                                groups = pickerGroups(plans.candidates, catalog),
                                category = op?.categoryName.orEmpty(),
                                selected = selected,
                                loading = false,
                                error =
                                op == null || old.error || old.selected != null && selected == null
                            )
                        }
                    }
            } catch (
                e: CancellationException
            ) {
                throw e
            } catch (_: Exception) {
                mutable.update { it.copy(loading = false, error = true) }
            }
        }
    }
    fun group(
        key: String
    ) {
        if (!state.value.saving) mutable.update { it.copy(group = key, selected = null, error = false) }
    }
    fun select(
        plan: SuggestedPlan
    ) {
        if (!state.value.saving) mutable.update { it.copy(selected = plan, error = false) }
    }
    fun back() {
        if (!state.value.saving) {
            mutable.update {
                if (it.group ==
                    null
                ) {
                    it.copy(closed = true)
                } else {
                    it.copy(group = null, selected = null, error = false)
                }
            }
        }
    }
    fun save() {
        val s = state.value
        val selected = s.selected ?: return
        val op = operation ?: return
        if (s.saving ||
            s.loading ||
            s.closed ||
            s.error ||
            s.groups.flatMap { it.payments }.none { it == selected }
        ) {
            return
        }
        mutable.update { it.copy(saving = true, error = false) }
        viewModelScope.launch {
            try {
                links.setLink(id, selected, op.version)
                val remainderKey = links.observeLinks(id).catch { emit(OperationPlanLinks()) }.firstOrNull()
                    ?.remainder?.plan?.target?.stableKey?.takeIf { it == selected.target.stableKey }
                mutable.update { it.copy(saving = false, closed = true, remainderKey = remainderKey) }
            } catch (
                e: CancellationException
            ) {
                throw e
            } catch (_: Exception) {
                mutable.update { it.copy(saving = false, error = true) }
            }
        }
    }
}
