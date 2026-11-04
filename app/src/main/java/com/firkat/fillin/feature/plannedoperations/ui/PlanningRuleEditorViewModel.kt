package com.firkat.fillin.feature.plannedoperations.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.categories.domain.model.*
import com.firkat.fillin.feature.categories.domain.usecase.GetCategories
import com.firkat.fillin.feature.plannedoperations.domain.model.*
import com.firkat.fillin.feature.plannedoperations.domain.usecase.ObservePlanningRules
import com.firkat.fillin.feature.plannedexpenses.domain.repository.PlannedExpensesRepository
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpenseFrequency
import com.firkat.fillin.feature.plannedincomes.domain.repository.PlannedIncomesRepository
import com.firkat.fillin.feature.dailylimit.domain.IncomeFrequency
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

internal data class RuleDraft(val type: PlannedOperationType = PlannedOperationType.Expense, val name: String = "",
    val categoryId: String = "", val amount: String = "", val frequency: PlanningFrequency = PlanningFrequency.Once,
    val date: LocalDate = LocalDate.now(ZoneId.of("Asia/Almaty")), val active: Boolean = true)
internal enum class RuleWindow { Category, Date, Frequency, Discard, Delete }
internal data class RuleEditorState(val draft: RuleDraft = RuleDraft(), val initial: RuleDraft = draft,
    val original: PlanningRule? = null, val categories: List<Category> = emptyList(), val loading: Boolean = true, val accountingStart: LocalDate = LocalDate.MIN,
    val categoriesLoading: Boolean = false, val categoriesError: Boolean = false, val saving: Boolean = false,
    val error: Boolean = false, val closed: Boolean = false, val window: RuleWindow? = null) {
    val dirty get() = draft != initial
    val amount get() = draft.amount.replace(',', '.').takeIf { Regex("[0-9]+(\\.[0-9]{1,2})?").matches(it) }?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
    val canSave get() = !loading && !saving && dirty && draft.name.trim().length in 1..160 && amount != null && categories.any { it.id == draft.categoryId && it.kind == if (draft.type == PlannedOperationType.Expense) CategoryKind.Expense else CategoryKind.Income } &&
        (draft.date >= maxOf(accountingStart, LocalDate.now(ZoneId.of("Asia/Almaty"))) || original != null && draft.date == initial.date)
}
internal class PlanningRuleEditorViewModel(private val rules: ObservePlanningRules, private val getCategories: GetCategories,
    private val expenses: PlannedExpensesRepository, private val incomes: PlannedIncomesRepository,
    type: PlannedOperationType, private val ruleId: String?) : ViewModel() {
    private val mutable = MutableStateFlow(RuleEditorState(draft = RuleDraft(type = type), initial = RuleDraft(type = type)))
    val state = mutable.asStateFlow()
    init { load(); categories() }
    fun load() { viewModelScope.launch {
        try {
            val accountingStart = expenses.accountingStart()
            val rule = if (ruleId == null) null else rules().first().firstOrNull { it.id == ruleId && it.type == mutable.value.draft.type } ?: error("Missing rule")
            val draft = rule?.let { RuleDraft(it.type, it.name, it.categoryId, it.amount.toPlainString(), it.frequency, it.nextDate(it.startsOn)) } ?: mutable.value.draft
            mutable.update { it.copy(draft = draft, initial = draft, original = rule, accountingStart = accountingStart, loading = false, error = false) }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { mutable.update { it.copy(loading = false, error = true) } }
    } }
    fun categories() { viewModelScope.launch {
        mutable.update { it.copy(categoriesLoading = true, categoriesError = false) }
        try { val categories = getCategories(); mutable.update { it.copy(categories = categories, categoriesLoading = false) } }
        catch (e: CancellationException) { throw e } catch (e: Exception) { mutable.update { it.copy(categoriesLoading = false, categoriesError = true) } }
    } }
    fun change(transform: (RuleDraft) -> RuleDraft) { if (!state.value.saving) mutable.update { it.copy(draft = transform(it.draft), error = false) } }
    fun type(type: PlannedOperationType) { if (ruleId == null) change { it.copy(type = type, categoryId = "", frequency = if (type == PlannedOperationType.Expense && it.frequency == PlanningFrequency.Biweekly) PlanningFrequency.Once else it.frequency) } }
    fun category(id: String) { change { d -> d.copy(categoryId = id, name = d.name.ifBlank { state.value.categories.first { it.id == id }.name }) }; window(null) }
    fun window(window: RuleWindow?) { if (!state.value.saving) mutable.update { it.copy(window = window) } }
    fun back() { if (!state.value.saving) { if (state.value.window != null) window(null) else if (state.value.dirty) window(RuleWindow.Discard) else close() } }
    fun close() { if (!state.value.saving) mutable.update { it.copy(closed = true) } }
    fun save() { val s = state.value; if (!s.canSave || ruleId != null && s.original == null) return
        write {
            val d = s.draft
            if (d.type == PlannedOperationType.Expense) {
                val unchanged = s.original != null && d.date == s.initial.date && d.frequency == s.initial.frequency
                expenses.saveDetails(s.original, d.name.trim(), d.categoryId, requireNotNull(s.amount), PlannedExpenseFrequency.valueOf(d.frequency.name),
                    if (unchanged) s.original.scheduleValue else when (d.frequency) { PlanningFrequency.Monthly -> d.date.dayOfMonth; PlanningFrequency.Weekly -> d.date.dayOfWeek.value; else -> null },
                    if (unchanged) requireNotNull(s.original).startsOn else d.date)
            } else {
                val frequency = IncomeFrequency.valueOf(d.frequency.name.uppercase())
                val date = if (s.original != null && d.date == s.initial.date && d.frequency == s.initial.frequency) s.original.startsOn else d.date
                if (s.original != null) incomes.saveRuleDetails(s.original, d.categoryId, requireNotNull(s.amount), date, d.name.trim(), frequency, d.active)
                else incomes.saveDetails(null, d.categoryId, requireNotNull(s.amount), date, d.name.trim(), frequency, true)
            }
        }
    }
    fun delete() { val r = state.value.original ?: return; write { if (r.type == PlannedOperationType.Expense) expenses.deleteRule(r.id, r.version) else incomes.deletePlannedIncome(r.id, r.version) } }
    private fun write(block: suspend () -> Unit) { if (state.value.saving) return; mutable.update { it.copy(saving = true, window = null, error = false) }; viewModelScope.launch {
        try { block(); mutable.update { it.copy(saving = false, closed = true) } }
        catch (e: CancellationException) { throw e } catch (e: Exception) { mutable.update { it.copy(saving = false, error = true) } }
    } }
}
