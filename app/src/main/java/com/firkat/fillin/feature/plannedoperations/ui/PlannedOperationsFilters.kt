package com.firkat.fillin.feature.plannedoperations.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.firkat.fillin.R
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationPeriod
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationType
import com.firkat.fillin.ui.component.DatePeriodChip
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
internal fun PlannedOperationsFilters(state: PlannedOperationsUiState, onEvent: (PlannedOperationsEvent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DatePeriodChip(
            dateFrom = state.periodDateFrom,
            dateTo = state.periodDateTo,
            isDefaultPeriod = state.filters.period == PlannedOperationPeriod.CurrentMonth,
            onClick = { onEvent(PlannedOperationsEvent.CustomPeriodOpened) },
            onReset = { onEvent(PlannedOperationsEvent.PeriodSelected(PlannedOperationPeriod.CurrentMonth)) },
            modifier = Modifier.testTag("planned-period-filter"),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(null, PlannedOperationType.Expense, PlannedOperationType.Income).forEach { type ->
                FilterChip(
                    selected = state.filters.type == type,
                    onClick = { onEvent(PlannedOperationsEvent.TypeSelected(type)) },
                    label = {
                        Text(stringResource(when (type) {
                            null -> R.string.planned_operations_all
                            PlannedOperationType.Expense -> R.string.planned_operations_expenses
                            PlannedOperationType.Income -> R.string.planned_operations_incomes
                        }))
                    },
                    modifier = Modifier.testTag("planned-type-${type?.apiValue ?: "ALL"}"),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlannedOperationsPeriodPicker(state: PlannedOperationsUiState, onEvent: (PlannedOperationsEvent) -> Unit) {
    val from = state.periodDateFrom
    val to = state.periodDateTo
    val maxDate = state.today.plusYears(5)
    val selectableDates = remember(maxDate) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = pickerDate(utcTimeMillis) <= maxDate
        }
    }
    val picker = rememberDateRangePickerState(
        initialSelectedStartDateMillis = from.pickerMillis(),
        initialSelectedEndDateMillis = to.pickerMillis(),
        yearRange = 1..maxDate.year,
        selectableDates = selectableDates,
    )
    DatePickerDialog(
        onDismissRequest = { onEvent(PlannedOperationsEvent.CustomPeriodClosed) },
        confirmButton = {
            TextButton(
                enabled = picker.selectedStartDateMillis != null && picker.selectedEndDateMillis != null,
                onClick = {
                    val start = picker.selectedStartDateMillis ?: return@TextButton
                    val end = picker.selectedEndDateMillis ?: return@TextButton
                    onEvent(PlannedOperationsEvent.CustomPeriodApplied(pickerDate(start), pickerDate(end)))
                },
            ) { Text(stringResource(R.string.planned_operations_apply)) }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(PlannedOperationsEvent.CustomPeriodClosed) }) {
                Text(stringResource(R.string.planned_operations_cancel))
            }
        },
    ) {
        DateRangePicker(
            state = picker,
            modifier = Modifier.heightIn(max = 500.dp),
            title = {
                Column {
                    Text(stringResource(R.string.planned_operations_choose_period), Modifier.padding(24.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            PlannedOperationPeriod.CurrentMonth,
                            PlannedOperationPeriod.EndOfMonth,
                            PlannedOperationPeriod.EndOfYear,
                        ).forEach { period ->
                            SuggestionChip(
                                onClick = { onEvent(PlannedOperationsEvent.PeriodSelected(period)) },
                                label = { Text(stringResource(period.labelResource)) },
                                modifier = Modifier.testTag("planned-period-${period.name}"),
                            )
                        }
                    }
                }
            },
            headline = {
                val start = picker.selectedStartDateMillis?.let { pickerDate(it).format(DATE_FORMAT) } ?: "…"
                val end = picker.selectedEndDateMillis?.let { pickerDate(it).format(DATE_FORMAT) } ?: "…"
                Text("$start — $end", Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.titleLarge)
            },
        )
    }
}

internal fun LocalDate.pickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
internal fun pickerDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy")

private val PlannedOperationsUiState.periodDateFrom: LocalDate
    get() = filters.dateFrom ?: operations?.dateFrom ?: today

private val PlannedOperationsUiState.periodDateTo: LocalDate
    get() = filters.dateTo ?: operations?.dateTo ?: when (filters.period) {
        PlannedOperationPeriod.EndOfYear -> today.withMonth(12).withDayOfMonth(31)
        else -> YearMonth.from(today).atEndOfMonth()
    }

private val PlannedOperationPeriod.labelResource: Int get() = when (this) {
    PlannedOperationPeriod.CurrentMonth -> R.string.planned_operations_current_month
    PlannedOperationPeriod.EndOfMonth -> R.string.planned_operations_month
    PlannedOperationPeriod.EndOfYear -> R.string.planned_operations_year
    PlannedOperationPeriod.Custom -> R.string.planned_operations_custom
}
