package com.firkat.fillin.feature.plannedincomes.ui

import com.firkat.fillin.feature.plannedoperations.domain.model.PlanningFrequency
import com.firkat.fillin.feature.plannedoperations.domain.model.PlanningRule
import com.firkat.fillin.feature.categories.domain.model.Category
import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncome
import java.math.BigDecimal
import java.time.LocalDate

data class PlannedIncomesUiState(
    val asOf: LocalDate? = null,
    val items: List<PlannedIncome> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: PlannedIncomesError? = null,
    val incomeCategories: List<Category> = emptyList(),
    val isCategoriesLoading: Boolean = false,
    val editor: PlannedIncomeEditorUiState? = null,
    val changeVersion: Long = 0,
)

data class PlannedIncomeEditorUiState(
    val original: PlannedIncome? = null,
    val originalRule: PlanningRule? = null,
    val name: String = original?.name.orEmpty(),
    val frequency: com.firkat.fillin.feature.dailylimit.domain.IncomeFrequency = com.firkat.fillin.feature.dailylimit.domain.IncomeFrequency.ONCE,
    val active: Boolean = true,
    val confirmRemainder: Boolean = false,
    val today: LocalDate,
    val categoryId: String = "",
    val amount: String = "",
    val dueOn: String = "",
    val isSaving: Boolean = false,
    val error: PlannedIncomesError? = null,
    val isDeleteConfirmationVisible: Boolean = false,
) {
    val isCreating: Boolean
        get() = original == null && originalRule == null

    val parsedAmount: BigDecimal?
        get() {
            val normalized = amount.trim().replace(',', '.')
            if (!DECIMAL_AMOUNT.matches(normalized)) return null
            return normalized.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }
        }

    val parsedDueOn: LocalDate?
        get() = runCatching { LocalDate.parse(dueOn) }.getOrNull()

    val isDueOnAllowed: Boolean
        get() = parsedDueOn?.let { date ->
            (!confirmRemainder && date == (originalRule?.startsOn ?: original?.dueOn)) || !date.isBefore(today)
        } == true

    val hasChanges: Boolean
        get() = originalRule?.let { rule ->
            name != rule.name || categoryId != rule.categoryId || parsedAmount?.compareTo(rule.amount) != 0 ||
                parsedDueOn != rule.startsOn || frequency.name != when (rule.frequency) {
                    PlanningFrequency.Once -> "ONCE"
                    PlanningFrequency.Weekly -> "WEEKLY"
                    PlanningFrequency.Biweekly -> "BIWEEKLY"
                    PlanningFrequency.Monthly -> "MONTHLY"
                } || !active
        } ?: original?.let { income ->
            name != income.name || frequency != income.frequency || active != income.active || confirmRemainder ||
            categoryId != income.categoryId ||
                parsedAmount?.compareTo(income.plannedAmount) != 0 ||
                parsedDueOn != income.dueOn
        } ?: true

    val canSave: Boolean
        get() = !isSaving &&
            name.isNotBlank() && name.length <= 160 &&
            categoryId.isNotBlank() &&
            parsedAmount != null &&
            isDueOnAllowed &&
            hasChanges

    val canDelete: Boolean
        get() = !isSaving && (original != null || originalRule != null)
}

enum class PlannedIncomesError {
    MissingApiToken,
    Unauthorized,
    Forbidden,
    InvalidRequest,
    NotFound,
    Conflict,
    Network,
    InvalidResponse,
    Unknown,
}

sealed interface PlannedIncomesEvent {
    data object Refresh : PlannedIncomesEvent
    data object Retry : PlannedIncomesEvent
    data object CreateRequested : PlannedIncomesEvent
    data class EditRequested(val incomeId: String) : PlannedIncomesEvent
    data object EditorClosed : PlannedIncomesEvent
    data class CategorySelected(val categoryId: String) : PlannedIncomesEvent
    data class AmountChanged(val amount: String) : PlannedIncomesEvent
    data class DueOnChanged(val dueOn: String) : PlannedIncomesEvent
    data class NameChanged(val value: String) : PlannedIncomesEvent
    data class FrequencyChanged(val value: com.firkat.fillin.feature.dailylimit.domain.IncomeFrequency) : PlannedIncomesEvent
    data class ActiveChanged(val value: Boolean) : PlannedIncomesEvent
    data class RemainderChanged(val value: Boolean) : PlannedIncomesEvent
    data object SaveRequested : PlannedIncomesEvent
    data object DeleteRequested : PlannedIncomesEvent
    data object DeleteDismissed : PlannedIncomesEvent
    data object DeleteConfirmed : PlannedIncomesEvent
}

private val DECIMAL_AMOUNT = Regex("[0-9]+([.,][0-9]{1,2})?")
