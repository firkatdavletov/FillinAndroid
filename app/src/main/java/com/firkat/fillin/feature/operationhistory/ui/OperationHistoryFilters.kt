package com.firkat.fillin.feature.operationhistory.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.firkat.fillin.R
import com.firkat.fillin.feature.operationhistory.domain.model.OperationHistoryFilter
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import java.time.format.DateTimeFormatter

@Composable
internal fun OperationHistoryFilters(
    state: OperationHistoryUiState,
    onEvent: (OperationHistoryEvent) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(OperationType.Expense, OperationType.Income, null).forEach { type ->
                FilterChip(
                    selected = state.filters.type == type,
                    onClick = { onEvent(OperationHistoryEvent.TypeSelected(type)) },
                    label = {
                        Text(
                            stringResource(
                                when (type) {
                                    OperationType.Expense -> R.string.operation_history_expenses
                                    OperationType.Income -> R.string.operation_history_incomes
                                    null -> R.string.operation_history_all_types
                                },
                            ),
                        )
                    },
                    modifier = Modifier.testTag("history-type-${type?.apiValue ?: "ALL"}"),
                )
            }
        }
        OutlinedButton(
            onClick = { onEvent(OperationHistoryEvent.CategoryFilterOpened) },
            modifier = Modifier.fillMaxWidth().testTag("history-category-filter"),
        ) {
            val selected = state.categories.firstOrNull { it.id == state.filters.categoryId }
            Text(selected?.name ?: stringResource(R.string.operation_history_all_categories))
        }
        OutlinedButton(
            onClick = { onEvent(OperationHistoryEvent.PeriodFilterOpened) },
            modifier = Modifier.fillMaxWidth().testTag("history-period-filter"),
        ) {
            Text(
                stringResource(
                    R.string.operation_history_period,
                    state.filters.dateFrom.format(PERIOD_DATE_FORMAT),
                    state.filters.dateTo.format(PERIOD_DATE_FORMAT),
                ),
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
internal fun OperationCategoryFilterDialog(
    state: OperationHistoryUiState,
    onEvent: (OperationHistoryEvent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onEvent(OperationHistoryEvent.CategoryFilterClosed) },
        title = { Text(stringResource(R.string.operation_history_category_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    item(key = "all-categories") {
                        CategoryOption(
                            name = stringResource(R.string.operation_history_all_categories),
                            selected = state.filters.categoryId == null,
                            onSelect = { onEvent(OperationHistoryEvent.CategorySelected(null)) },
                        )
                    }
                    items(state.availableCategories, key = { it.id }) { category ->
                        CategoryOption(
                            name = "${category.icon.emoji} ${category.name}",
                            selected = state.filters.categoryId == category.id,
                            onSelect = { onEvent(OperationHistoryEvent.CategorySelected(category.id)) },
                        )
                    }
                }
                if (state.isCategoriesLoading) {
                    CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                }
                if (state.categoriesError != null) {
                    Text(stringResource(R.string.operation_history_categories_error))
                    TextButton(onClick = { onEvent(OperationHistoryEvent.CategoriesRetry) }) {
                        Text(stringResource(R.string.operation_history_retry))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onEvent(OperationHistoryEvent.CategoryFilterClosed) }) {
                Text(stringResource(R.string.operation_history_close))
            }
        },
    )
}

@Composable
private fun CategoryOption(name: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(name, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OperationPeriodFilterDialog(
    filters: OperationHistoryFilter,
    onEvent: (OperationHistoryEvent) -> Unit,
) {
    val picker = rememberDateRangePickerState(
        initialSelectedStartDateMillis = filters.dateFrom.toPickerMillis(),
        initialSelectedEndDateMillis = filters.dateTo.toPickerMillis(),
    )
    DatePickerDialog(
        onDismissRequest = { onEvent(OperationHistoryEvent.PeriodFilterClosed) },
        confirmButton = {
            TextButton(
                enabled = picker.selectedStartDateMillis != null && picker.selectedEndDateMillis != null,
                onClick = {
                    val from = picker.selectedStartDateMillis ?: return@TextButton
                    val to = picker.selectedEndDateMillis ?: return@TextButton
                    onEvent(OperationHistoryEvent.PeriodSelected(pickerMillisToDate(from), pickerMillisToDate(to)))
                },
            ) {
                Text(stringResource(R.string.operation_history_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(OperationHistoryEvent.PeriodFilterClosed) }) {
                Text(stringResource(R.string.operation_history_refund_cancel))
            }
        },
    ) {
        DateRangePicker(
            state = picker,
            modifier = Modifier.heightIn(max = 500.dp),
            headline = {
                val start = picker.selectedStartDateMillis?.let { pickerMillisToDate(it).format(PERIOD_DATE_FORMAT) }
                val end = picker.selectedEndDateMillis?.let { pickerMillisToDate(it).format(PERIOD_DATE_FORMAT) }
                Text(
                    text = stringResource(
                        R.string.operation_history_period,
                        start ?: stringResource(R.string.operation_history_period_start),
                        end ?: stringResource(R.string.operation_history_period_end),
                    ),
                    modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 12.dp),
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            title = {
                Text(
                    stringResource(R.string.operation_history_period_title),
                    modifier = Modifier.padding(start = 24.dp, top = 16.dp),
                )
            },
        )
    }
}

private val PERIOD_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy")
