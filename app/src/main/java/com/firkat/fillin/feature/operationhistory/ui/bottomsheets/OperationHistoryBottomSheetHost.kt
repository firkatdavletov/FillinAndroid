package com.firkat.fillin.feature.operationhistory.ui.bottomsheets

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.firkat.fillin.R
import com.firkat.fillin.feature.operationhistory.ui.pickerMillisToDate
import com.firkat.fillin.feature.operationhistory.ui.toPickerMillis
import com.firkat.fillin.ui.component.SaldomiActionBottomSheet
import com.firkat.fillin.ui.component.SaldomiButton
import com.firkat.fillin.ui.component.SaldomiOutlinedButton
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
internal fun OperationHistoryBottomSheetHost(
    showPeriodFilter: Boolean,
    dateFrom: LocalDate,
    dateTo: LocalDate,
    onPeriodSelected: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (showPeriodFilter) {
        OperationPeriodFilterBottomSheet(dateFrom, dateTo, onPeriodSelected, onDismiss, modifier)
    }
}

@Composable
private fun OperationPeriodFilterBottomSheet(
    dateFrom: LocalDate,
    dateTo: LocalDate,
    onPeriodSelected: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val picker = rememberDateRangePickerState(
        initialSelectedStartDateMillis = dateFrom.toPickerMillis(),
        initialSelectedEndDateMillis = dateTo.toPickerMillis()
    )
    SaldomiActionBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        confirmButton = {
            SaldomiButton(
                enabled = picker.selectedStartDateMillis != null && picker.selectedEndDateMillis != null,
                onClick = {
                    val from = picker.selectedStartDateMillis ?: return@SaldomiButton
                    val to = picker.selectedEndDateMillis ?: return@SaldomiButton
                    onPeriodSelected(pickerMillisToDate(from), pickerMillisToDate(to))
                },
                modifier = Modifier.fillMaxWidth(),
                large = true
            ) {
                Text(stringResource(R.string.operation_history_apply))
            }
        },
        dismissButton = {
            SaldomiOutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), large = true) {
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
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer
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
            dateFrom = LocalDate.of(2026, 11, 1),
            dateTo = LocalDate.of(2026, 11, 30),
            onPeriodSelected = { _, _ -> },
            onDismiss = {}
        )
    }
}
