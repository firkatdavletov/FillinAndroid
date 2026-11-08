package com.firkat.fillin.feature.plannedexpenses.ui

import com.firkat.fillin.feature.plannedoperations.domain.model.PlanningRule
import com.firkat.fillin.feature.categories.domain.model.Category
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpense
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpenseFrequency
import java.math.BigDecimal
import java.time.LocalDate

data class PlannedExpensesUiState(
    val asOf: LocalDate? = null,
    val throughInclusive: LocalDate? = null,
    val items: List<PlannedExpense> = emptyList(),
    val selectedPeriod: PlannedExpensesPeriod = PlannedExpensesPeriod.EndOfMonth,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: PlannedExpensesError? = null,
    val expenseCategories: List<Category> = emptyList(),
    val isCategoriesLoading: Boolean = false,
    val editor: PlannedExpenseRuleEditorUiState? = null,
    val closeConfirmationExpenseId: String? = null,
    val isClosingExpense: Boolean = false,
    val closeError: PlannedExpensesError? = null,
    val changeVersion: Long = 0,
)

enum class PlannedExpensesPeriod {
    EndOfMonth,
    EndOfYear,
}

data class PlannedExpenseRuleEditorUiState(
    val original: PlannedExpense? = null,
    val originalRule: PlanningRule? = null,
    val today: LocalDate = LocalDate.now(java.time.ZoneId.of("Asia/Almaty")),
    val categoryId: String = "",
    val amount: String = "",
    val frequency: PlannedExpenseFrequency = PlannedExpenseFrequency.Once,
    val scheduleValue: String = "",
    val startsOn: String = "",
    val isSaving: Boolean = false,
    val error: PlannedExpensesError? = null,
    val isDeleteConfirmationVisible: Boolean = false,
) {
    val isCreating: Boolean
        get() = original == null && originalRule == null

    val parsedAmount: BigDecimal?
        get() = amount.trim()
            .replace(',', '.')
            .toBigDecimalOrNull()
            ?.takeIf { it > BigDecimal.ZERO && it.scale() <= 2 }

    val parsedScheduleValue: Int?
        get() = scheduleValue.toIntOrNull()?.takeIf {
            when (frequency) {
                PlannedExpenseFrequency.Once -> false
                PlannedExpenseFrequency.Monthly -> it in 1..31
                PlannedExpenseFrequency.Weekly -> it in 1..7
            }
        }

    val parsedStartsOn: LocalDate?
        get() = runCatching { LocalDate.parse(startsOn) }.getOrNull()
            ?.takeIf { frequency != PlannedExpenseFrequency.Once || it >= today }

    val canSave: Boolean
        get() {
            val validAmount = parsedAmount ?: return false
            return !isSaving && if (isCreating) {
                categoryId.isNotBlank() &&
                    (frequency == PlannedExpenseFrequency.Once || parsedScheduleValue != null) &&
                    parsedStartsOn != null
            } else {
                validAmount.compareTo((originalRule?.amount ?: requireNotNull(original).plannedAmount)) != 0
            }
        }

    val canDelete: Boolean
        get() = !isSaving && (original != null || originalRule != null)
}

enum class PlannedExpensesError {
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

sealed interface PlannedExpensesEvent {
    data object Refresh : PlannedExpensesEvent
    data object Retry : PlannedExpensesEvent
    data class PeriodSelected(val period: PlannedExpensesPeriod) : PlannedExpensesEvent
    data object CreateRequested : PlannedExpensesEvent
    data class EditRequested(val expenseId: String) : PlannedExpensesEvent
    data object EditorClosed : PlannedExpensesEvent
    data class CategorySelected(val categoryId: String) : PlannedExpensesEvent
    data class AmountChanged(val amount: String) : PlannedExpensesEvent
    data class FrequencyChanged(val frequency: PlannedExpenseFrequency) : PlannedExpensesEvent
    data class ScheduleValueChanged(val scheduleValue: String) : PlannedExpensesEvent
    data class StartsOnChanged(val startsOn: String) : PlannedExpensesEvent
    data object SaveRequested : PlannedExpensesEvent
    data object DeleteRequested : PlannedExpensesEvent
    data object DeleteDismissed : PlannedExpensesEvent
    data object DeleteConfirmed : PlannedExpensesEvent
    data class CloseRequested(val expenseId: String) : PlannedExpensesEvent
    data object CloseDismissed : PlannedExpensesEvent
    data object CloseConfirmed : PlannedExpensesEvent
}
