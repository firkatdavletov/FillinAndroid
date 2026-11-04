package com.firkat.fillin.feature.plannedoperations.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.firkat.fillin.feature.plannedoperations.domain.model.*
import com.firkat.fillin.ui.theme.FillinTheme
import java.math.BigDecimal
import java.time.LocalDate

private val previewDate = LocalDate.of(2026, 11, 4)
private val previewExpense = PlanningRule("rent", 1, PlannedOperationType.Expense, "home", "Жильё (аренда)", "HOME",
    "Жильё (аренда)", BigDecimal("20000"), frequency = PlanningFrequency.Monthly, startsOn = previewDate, scheduleValue = 5)
private val previewIncome = previewExpense.copy(id = "salary", type = PlannedOperationType.Income, name = "Зарплата", amount = BigDecimal("500000"))

@Preview @Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun CatalogPreview() { FillinTheme { PlannedOperationsScreen(PlannedOperationsUiState(rules = listOf(previewExpense, previewIncome), isLoading = false), {}, {}, {}, {}) } }
@Preview(widthDp = 320, fontScale = 2f) @Preview(widthDp = 840)
@Composable private fun LongCatalogPreview() { FillinTheme { PlannedOperationsScreen(PlannedOperationsUiState(rules = listOf(previewExpense.copy(name = "Долгосрочная аренда семейной квартиры и коммунальные услуги")), isLoading = false), {}, {}, {}, {}) } }
@Preview @Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun EmptyPreview() { FillinTheme { PlannedOperationsScreen(PlannedOperationsUiState(isLoading = false), {}, {}, {}, {}) } }
@Preview @Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun LoadingPreview() { FillinTheme { PlannedOperationsScreen(PlannedOperationsUiState(), {}, {}, {}, {}) } }
@Preview @Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun CollapsedPreview() { FillinTheme { PlanningGroupCard(PlanningFrequency.Monthly, listOf(previewExpense), false, {}, {}, true, previewDate) } }
@Preview @Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun ExpandedPreview() { FillinTheme { PlanningGroupCard(PlanningFrequency.Monthly, listOf(previewExpense, previewIncome), true, {}, {}, true, previewDate) } }
@Preview @Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun ExpensePreview() { FillinTheme { PlanningRuleRow(previewExpense, previewDate, {}, true) } }
@Preview @Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun IncomePreview() { FillinTheme { PlanningRuleRow(previewIncome, previewDate, {}, true) } }
@Preview @Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun ReadOnlyPreview() { FillinTheme { PlanningRuleRow(previewExpense, previewDate, {}, false) } }
@Preview @Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun OncePreview() { FillinTheme { PlanningRuleRow(previewExpense.copy(frequency = PlanningFrequency.Once), previewDate, {}, true) } }
@Preview @Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun WeeklyPreview() { FillinTheme { PlanningRuleRow(previewIncome.copy(frequency = PlanningFrequency.Weekly), previewDate, {}, true) } }
@Preview @Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun BiweeklyPreview() { FillinTheme { PlanningRuleRow(previewIncome.copy(frequency = PlanningFrequency.Biweekly), previewDate, {}, true) } }
@Preview @Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun AllTypesPreview() { FillinTheme { PlanningTypeFilters(null, {}) } }
@Preview @Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun ExpensesFilterPreview() { FillinTheme { PlanningTypeFilters(PlannedOperationType.Expense, {}) } }
@Preview @Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun IncomesFilterPreview() { FillinTheme { PlanningTypeFilters(PlannedOperationType.Income, {}) } }
@Preview @Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun AddPreview() { FillinTheme { PlanningAddButton({}, {}) } }
