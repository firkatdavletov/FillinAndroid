package com.firkat.fillin.feature.operationhistory.ui

import androidx.compose.foundation.layout.fillMaxWidth
import com.firkat.fillin.ui.component.SaldomiOutlinedButton
import com.firkat.fillin.ui.component.SaldomiButton
import com.firkat.fillin.ui.component.SaldomiActionBottomSheet
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
            currency = state.periodTotals?.currency ?: "KZT",
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
}

@Composable
internal fun OperationPeriodFilterBottomSheet(
    filters: OperationHistoryFilter,
    onEvent: (OperationHistoryEvent) -> Unit,
) {
    val picker = rememberDateRangePickerState(
        initialSelectedStartDateMillis = filters.dateFrom.toPickerMillis(),
        initialSelectedEndDateMillis = filters.dateTo.toPickerMillis(),
    )
    SaldomiActionBottomSheet(
        onDismissRequest = { onEvent(OperationHistoryEvent.PeriodFilterClosed) },
        confirmButton = {
            SaldomiButton(
                enabled = picker.selectedStartDateMillis != null && picker.selectedEndDateMillis != null,
                onClick = {
                    val from = picker.selectedStartDateMillis ?: return@SaldomiButton
                    val to = picker.selectedEndDateMillis ?: return@SaldomiButton
                    onEvent(OperationHistoryEvent.PeriodSelected(pickerMillisToDate(from), pickerMillisToDate(to)))
                }, modifier = Modifier.fillMaxWidth(), large = true) {
                Text(stringResource(R.string.operation_history_apply))
            }
        },
        dismissButton = {
            SaldomiOutlinedButton(onClick = { onEvent(OperationHistoryEvent.PeriodFilterClosed) }, modifier = Modifier.fillMaxWidth(), large = true) {
                Text(stringResource(R.string.operation_history_refund_cancel))
            }
        },
        edgeToEdgeContent = true,
        text = {
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
        },
    )
}

private val PERIOD_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy")

@androidx.compose.ui.tooling.preview.Preview(name = "Period")
@androidx.compose.ui.tooling.preview.Preview(name = "Period dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@androidx.compose.ui.tooling.preview.Preview(name = "Period compact", widthDp = 320, heightDp = 600, fontScale = 2f)
@androidx.compose.ui.tooling.preview.Preview(name = "Period wide", widthDp = 840)
@Composable
private fun PeriodFilterSheetPreview() {
    com.firkat.fillin.ui.theme.FillinTheme {
        OperationPeriodFilterBottomSheet(
            filters = OperationHistoryFilter.currentMonth(java.time.LocalDate.of(2026, 11, 7)),
            onEvent = {},
        )
    }
}
