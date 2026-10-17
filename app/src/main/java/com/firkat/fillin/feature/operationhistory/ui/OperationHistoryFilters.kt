package com.firkat.fillin.feature.operationhistory.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.firkat.fillin.R
import com.firkat.fillin.feature.operationhistory.domain.model.OperationHistoryFilter
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.ui.component.DatePeriodChip
import com.firkat.fillin.ui.component.OperationTotalsRow
import com.firkat.fillin.ui.component.OperationTotalsType
import java.time.format.DateTimeFormatter

@Composable
internal fun OperationHistoryFilters(
    state: OperationHistoryUiState,
    onEvent: (OperationHistoryEvent) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DatePeriodChip(
            dateFrom = state.filters.dateFrom,
            dateTo = state.filters.dateTo,
            isDefaultPeriod = !state.isCustomPeriod,
            onClick = { onEvent(OperationHistoryEvent.PeriodFilterOpened) },
            onReset = { onEvent(OperationHistoryEvent.PeriodFilterReset) },
            modifier = Modifier.testTag("history-period-filter"),
        )
        OperationTotalsRow(
            expenses = state.periodTotals?.expenses,
            incomes = state.periodTotals?.incomes,
            selectedType = when (state.filters.type) {
                OperationType.Expense -> OperationTotalsType.Expense
                OperationType.Income -> OperationTotalsType.Income
                null -> null
            },
            isLoading = state.isInitialLoading,
            onTypeSelected = { type ->
                onEvent(OperationHistoryEvent.TypeSelected(when (type) {
                    OperationTotalsType.Expense -> OperationType.Expense
                    OperationTotalsType.Income -> OperationType.Income
                    null -> null
                }))
            },
            testTagPrefix = "history",
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
