package com.firkat.fillin.feature.operationhistory.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DateRangePickerDefaults
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
import com.firkat.fillin.feature.operationhistory.domain.model.OperationPeriodTotals
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.ui.component.DatePeriodChip
import com.firkat.fillin.ui.component.SaldomiActionBottomSheet
import com.firkat.fillin.ui.component.SaldomiButton
import com.firkat.fillin.ui.component.SaldomiOutlinedButton
import java.time.format.DateTimeFormatter

@Composable
internal fun OperationHistoryFilters(
    filters: OperationHistoryFilter,
    isCustomPeriod: Boolean,
    totals: OperationPeriodTotals?,
    isLoading: Boolean,
    onPeriodClick: () -> Unit,
    onPeriodReset: () -> Unit,
    onTypeSelected: (OperationType?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DatePeriodChip(
            dateFrom = filters.dateFrom,
            dateTo = filters.dateTo,
            isDefaultPeriod = !isCustomPeriod,
            onClick = onPeriodClick,
            onReset = onPeriodReset,
            modifier = Modifier.testTag("history-period-filter")
        )
        OperationHistoryTotals(
            totals = totals,
            selectedType = filters.type,
            isLoading = isLoading,
            onTypeSelected = onTypeSelected
        )
    }
}

@Composable
internal fun OperationPeriodFilterBottomSheet(
    filters: OperationHistoryFilter,
    onEvent: (OperationHistoryEvent) -> Unit
) {
    val picker = rememberDateRangePickerState(
        initialSelectedStartDateMillis = filters.dateFrom.toPickerMillis(),
        initialSelectedEndDateMillis = filters.dateTo.toPickerMillis()
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
                },
                modifier = Modifier.fillMaxWidth(),
                large = true
            ) {
                Text(stringResource(R.string.operation_history_apply))
            }
        },
        dismissButton = {
            SaldomiOutlinedButton(onClick = {
                onEvent(OperationHistoryEvent.PeriodFilterClosed)
            }, modifier = Modifier.fillMaxWidth(), large = true) {
                Text(stringResource(R.string.operation_history_refund_cancel))
            }
        },
        edgeToEdgeContent = true,
        text = {
            DateRangePicker(
                state = picker,
                modifier = Modifier.heightIn(max = 500.dp),
                headline = {
                    val start = picker.selectedStartDateMillis?.let {
                        pickerMillisToDate(it).format(PERIOD_DATE_FORMAT)
                    }
                    val end = picker.selectedEndDateMillis?.let { pickerMillisToDate(it).format(PERIOD_DATE_FORMAT) }
                    Text(
                        text = stringResource(
                            R.string.operation_history_period,
                            start ?: stringResource(R.string.operation_history_period_start),
                            end ?: stringResource(R.string.operation_history_period_end)
                        ),
                        modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 12.dp),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                title = {
                    Text(
                        stringResource(R.string.operation_history_period_title),
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp)
                    )
                },
                colors = DatePickerDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    )
}

private val PERIOD_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy")

@androidx.compose.ui.tooling.preview.Preview(name = "Period")
@androidx.compose.ui.tooling.preview.Preview(
    name = "Period dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
)
@androidx.compose.ui.tooling.preview.Preview(name = "Period compact", widthDp = 320, heightDp = 600, fontScale = 2f)
@androidx.compose.ui.tooling.preview.Preview(name = "Period wide", widthDp = 840)
@Composable
private fun PeriodFilterSheetPreview() {
    com.firkat.fillin.ui.theme.FillinTheme {
        OperationPeriodFilterBottomSheet(
            filters = OperationHistoryFilter.currentMonth(java.time.LocalDate.of(2026, 11, 7)),
            onEvent = {}
        )
    }
}
