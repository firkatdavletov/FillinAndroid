package com.firkat.fillin.feature.plannedoperations.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.firkat.fillin.feature.categories.domain.model.Category
import com.firkat.fillin.feature.categories.domain.model.CategoryIcon
import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpense
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpenseFrequency
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpenseStatus
import com.firkat.fillin.feature.plannedexpenses.ui.DeleteRuleConfirmation
import com.firkat.fillin.feature.plannedexpenses.ui.PlannedExpenseRuleEditor
import com.firkat.fillin.feature.plannedexpenses.ui.PlannedExpenseRuleEditorUiState
import com.firkat.fillin.feature.plannedexpenses.ui.PlannedExpensesError
import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncome
import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncomeStatus
import com.firkat.fillin.feature.plannedincomes.ui.DeleteIncomeConfirmation
import com.firkat.fillin.feature.plannedincomes.ui.PlannedIncomeEditor
import com.firkat.fillin.feature.plannedincomes.ui.PlannedIncomeEditorUiState
import com.firkat.fillin.feature.plannedincomes.ui.PlannedIncomesError
import com.firkat.fillin.ui.theme.FillinTheme
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

private val previewDay = LocalDate.of(2026, 10, 25)
private val previewCreated = Instant.parse("2026-10-01T00:00:00Z")
private val previewExpense = PlannedExpense(
    "rent", "home", "Аренда квартиры", BigDecimal("250000"), BigDecimal.ZERO, BigDecimal("250000"),
    "KZT", previewDay, false, PlannedExpenseStatus.Scheduled, 0, previewCreated, previewCreated,
    ruleId = "rent-rule", ruleVersion = 1,
)
private val previewIncome = PlannedIncome(
    "salary", "salary", "Зарплата", BigDecimal("500000"), "KZT", previewDay,
    false, PlannedIncomeStatus.Scheduled, 0, previewCreated, previewCreated,
)
private val expenseEditor = PlannedExpenseRuleEditorUiState(original = previewExpense, amount = "270000")
private val incomeEditor = PlannedIncomeEditorUiState(
    original = previewIncome, today = previewDay, categoryId = "salary", amount = "550000", dueOn = previewDay.toString(),
)

@Composable
private fun ExpensePreview(
    modifier: Modifier = Modifier,
    editor: PlannedExpenseRuleEditorUiState = expenseEditor,
    categories: List<Category> = listOf(Category("home", "Жильё", CategoryKind.Expense, CategoryIcon("HOME", "🏠"))),
    loading: Boolean = false,
) {
    FillinTheme(dynamicColor = false) {
        Surface {
            PlannedExpenseRuleEditor(categories, loading, editor, {}, modifier, onClose = {})
        }
    }
}

@Composable
private fun IncomePreview(
    modifier: Modifier = Modifier,
    editor: PlannedIncomeEditorUiState = incomeEditor,
    categories: List<Category> = listOf(Category("salary", "Зарплата", CategoryKind.Income, CategoryIcon("INCOME", "💰"))),
    loading: Boolean = false,
) {
    FillinTheme(dynamicColor = false) {
        Surface {
            PlannedIncomeEditor(categories, loading, editor, {}, modifier, onClose = {})
        }
    }
}

@Preview(name = "ExpenseEdit")
@Preview(name = "ExpenseEdit dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ExpenseEditPreview() { ExpensePreview() }

@Preview(name = "ExpenseUnchanged")
@Preview(name = "ExpenseUnchanged dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ExpenseUnchangedPreview() { ExpensePreview(editor = expenseEditor.copy(amount = "250000")) }

@Preview(name = "ExpenseSave")
@Preview(name = "ExpenseSave dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ExpenseSavePreview() { ExpensePreview(editor = expenseEditor.copy(isSaving = true)) }

@Preview(name = "ExpenseInvalidAmount")
@Preview(name = "ExpenseInvalidAmount dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ExpenseInvalidAmountPreview() { ExpensePreview(editor = expenseEditor.copy(amount = "0")) }

@Preview(name = "ExpenseCreateMonthly")
@Preview(name = "ExpenseCreateMonthly dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ExpenseCreateMonthlyPreview() { ExpensePreview(editor = expenseEditor.copy(original = null, categoryId = "home", scheduleValue = "25", startsOn = previewDay.toString())) }

@Preview(name = "ExpenseCreateWeekly")
@Preview(name = "ExpenseCreateWeekly dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ExpenseCreateWeeklyPreview() { ExpensePreview(editor = expenseEditor.copy(original = null, categoryId = "home", frequency = PlannedExpenseFrequency.Weekly, scheduleValue = "7", startsOn = previewDay.toString())) }

@Preview(name = "ExpenseCreateInvalid")
@Preview(name = "ExpenseCreateInvalid dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ExpenseCreateInvalidPreview() { ExpensePreview(editor = expenseEditor.copy(original = null, scheduleValue = "0", startsOn = "invalid")) }

@Preview(name = "ExpenseNoCategories")
@Preview(name = "ExpenseNoCategories dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ExpenseNoCategoriesPreview() { ExpensePreview(editor = expenseEditor.copy(original = null), categories = emptyList()) }

@Preview(name = "ExpenseLoadingCategories")
@Preview(name = "ExpenseLoadingCategories dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ExpenseLoadingCategoriesPreview() { ExpensePreview(editor = expenseEditor.copy(original = null), loading = true) }

@Preview(name = "IncomeEdit")
@Preview(name = "IncomeEdit dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun IncomeEditPreview() { IncomePreview() }

@Preview(name = "IncomeUnchanged")
@Preview(name = "IncomeUnchanged dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun IncomeUnchangedPreview() { IncomePreview(editor = incomeEditor.copy(amount = "500000")) }

@Preview(name = "IncomeSaving")
@Preview(name = "IncomeSaving dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun IncomeSavingPreview() { IncomePreview(editor = incomeEditor.copy(isSaving = true)) }

@Preview(name = "IncomeInvalidAmount")
@Preview(name = "IncomeInvalidAmount dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun IncomeInvalidAmountPreview() { IncomePreview(editor = incomeEditor.copy(amount = "0")) }

@Preview(name = "IncomeInvalidDate")
@Preview(name = "IncomeInvalidDate dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun IncomeInvalidDatePreview() { IncomePreview(editor = incomeEditor.copy(dueOn = "invalid")) }

@Preview(name = "IncomeOverdue")
@Preview(name = "IncomeOverdue dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun IncomeOverduePreview() { IncomePreview(editor = incomeEditor.copy(original = previewIncome.copy(overdue = true))) }

@Preview(name = "IncomeCreate")
@Preview(name = "IncomeCreate dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun IncomeCreatePreview() { IncomePreview(editor = incomeEditor.copy(original = null)) }

@Preview(name = "IncomeNoCategories")
@Preview(name = "IncomeNoCategories dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun IncomeNoCategoriesPreview() { IncomePreview(editor = incomeEditor.copy(original = null), categories = emptyList()) }

@Preview(name = "IncomeMissingOriginalCategory")
@Preview(name = "IncomeMissingOriginalCategory dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun IncomeMissingOriginalCategoryPreview() { IncomePreview(categories = emptyList()) }

@Preview(name = "IncomeLoadingCategories")
@Preview(name = "IncomeLoadingCategories dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun IncomeLoadingCategoriesPreview() { IncomePreview(loading = true) }

@Preview(name = "ExpenseDeleteConfirmation")
@Preview(name = "ExpenseDeleteConfirmation dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ExpenseDeleteConfirmationPreview() { FillinTheme { DeleteRuleConfirmation({}) } }

@Preview(name = "IncomeDeleteConfirmation")
@Preview(name = "IncomeDeleteConfirmation dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun IncomeDeleteConfirmationPreview() { FillinTheme { DeleteIncomeConfirmation({}) } }

@Preview(name = "ExpenseMissingApiToken")
@Preview(name = "ExpenseMissingApiToken dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ExpenseMissingApiTokenPreview() { ExpensePreview(editor = expenseEditor.copy(error = PlannedExpensesError.MissingApiToken)) }

@Preview(name = "ExpenseUnauthorized")
@Preview(name = "ExpenseUnauthorized dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ExpenseUnauthorizedPreview() { ExpensePreview(editor = expenseEditor.copy(error = PlannedExpensesError.Unauthorized)) }

@Preview(name = "ExpenseForbidden")
@Preview(name = "ExpenseForbidden dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ExpenseForbiddenPreview() { ExpensePreview(editor = expenseEditor.copy(error = PlannedExpensesError.Forbidden)) }

@Preview(name = "ExpenseInvalidRequest")
@Preview(name = "ExpenseInvalidRequest dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ExpenseInvalidRequestPreview() { ExpensePreview(editor = expenseEditor.copy(error = PlannedExpensesError.InvalidRequest)) }

@Preview(name = "ExpenseNotFound")
@Preview(name = "ExpenseNotFound dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ExpenseNotFoundPreview() { ExpensePreview(editor = expenseEditor.copy(error = PlannedExpensesError.NotFound)) }

@Preview(name = "ExpenseConflict")
@Preview(name = "ExpenseConflict dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ExpenseConflictPreview() { ExpensePreview(editor = expenseEditor.copy(error = PlannedExpensesError.Conflict)) }

@Preview(name = "ExpenseNetwork")
@Preview(name = "ExpenseNetwork dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ExpenseNetworkPreview() { ExpensePreview(editor = expenseEditor.copy(error = PlannedExpensesError.Network)) }

@Preview(name = "ExpenseInvalidResponse")
@Preview(name = "ExpenseInvalidResponse dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ExpenseInvalidResponsePreview() { ExpensePreview(editor = expenseEditor.copy(error = PlannedExpensesError.InvalidResponse)) }

@Preview(name = "ExpenseUnknown")
@Preview(name = "ExpenseUnknown dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ExpenseUnknownPreview() { ExpensePreview(editor = expenseEditor.copy(error = PlannedExpensesError.Unknown)) }

@Preview(name = "IncomeMissingApiToken")
@Preview(name = "IncomeMissingApiToken dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun IncomeMissingApiTokenPreview() { IncomePreview(editor = incomeEditor.copy(error = PlannedIncomesError.MissingApiToken)) }

@Preview(name = "IncomeUnauthorized")
@Preview(name = "IncomeUnauthorized dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun IncomeUnauthorizedPreview() { IncomePreview(editor = incomeEditor.copy(error = PlannedIncomesError.Unauthorized)) }

@Preview(name = "IncomeForbidden")
@Preview(name = "IncomeForbidden dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun IncomeForbiddenPreview() { IncomePreview(editor = incomeEditor.copy(error = PlannedIncomesError.Forbidden)) }

@Preview(name = "IncomeInvalidRequest")
@Preview(name = "IncomeInvalidRequest dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun IncomeInvalidRequestPreview() { IncomePreview(editor = incomeEditor.copy(error = PlannedIncomesError.InvalidRequest)) }

@Preview(name = "IncomeNotFound")
@Preview(name = "IncomeNotFound dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun IncomeNotFoundPreview() { IncomePreview(editor = incomeEditor.copy(error = PlannedIncomesError.NotFound)) }

@Preview(name = "IncomeConflict")
@Preview(name = "IncomeConflict dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun IncomeConflictPreview() { IncomePreview(editor = incomeEditor.copy(error = PlannedIncomesError.Conflict)) }

@Preview(name = "IncomeNetwork")
@Preview(name = "IncomeNetwork dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun IncomeNetworkPreview() { IncomePreview(editor = incomeEditor.copy(error = PlannedIncomesError.Network)) }

@Preview(name = "IncomeInvalidResponse")
@Preview(name = "IncomeInvalidResponse dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun IncomeInvalidResponsePreview() { IncomePreview(editor = incomeEditor.copy(error = PlannedIncomesError.InvalidResponse)) }

@Preview(name = "IncomeUnknown")
@Preview(name = "IncomeUnknown dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun IncomeUnknownPreview() { IncomePreview(editor = incomeEditor.copy(error = PlannedIncomesError.Unknown)) }

@Preview(name = "Expense compact", widthDp = 320, fontScale = 2f)
@Preview(name = "Expense wide", widthDp = 840)
@Composable
private fun ExpenseAdaptivePreview() { ExpensePreview(editor = expenseEditor.copy(original = previewExpense.copy(categoryName = "Планирование семейного бюджета и обязательные платежи"))) }

@Preview(name = "Income compact", widthDp = 320, fontScale = 2f)
@Preview(name = "Income wide", widthDp = 840)
@Composable
private fun IncomeAdaptivePreview() { IncomePreview(editor = incomeEditor.copy(original = previewIncome.copy(categoryName = "Планирование семейного бюджета и обязательные платежи"))) }
