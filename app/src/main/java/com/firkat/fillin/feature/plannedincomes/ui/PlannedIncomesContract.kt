package com.firkat.fillin.feature.plannedincomes.ui

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
)

data class PlannedIncomeEditorUiState(
    val original: PlannedIncome? = null,
    val today: LocalDate,
    val categoryId: String = "",
    val amount: String = "",
    val dueOn: String = "",
    val isSaving: Boolean = false,
    val error: PlannedIncomesError? = null,
    val isDeleteConfirmationVisible: Boolean = false,
) {
    val isCreating: Boolean
        get() = original == null

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
            date == original?.dueOn || !date.isBefore(today)
        } == true

    val hasChanges: Boolean
        get() = original?.let { income ->
            categoryId != income.categoryId ||
                parsedAmount?.compareTo(income.plannedAmount) != 0 ||
                parsedDueOn != income.dueOn
        } ?: true

    val canSave: Boolean
        get() = !isSaving &&
            categoryId.isNotBlank() &&
            parsedAmount != null &&
            isDueOnAllowed &&
            hasChanges

    val canDelete: Boolean
        get() = !isSaving && original != null
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
    data object SaveRequested : PlannedIncomesEvent
    data object DeleteRequested : PlannedIncomesEvent
    data object DeleteDismissed : PlannedIncomesEvent
    data object DeleteConfirmed : PlannedIncomesEvent
}

private val DECIMAL_AMOUNT = Regex("[0-9]+([.,][0-9]{1,2})?")
