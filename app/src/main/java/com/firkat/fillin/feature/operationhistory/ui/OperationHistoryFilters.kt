package com.firkat.fillin.feature.operationhistory.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.firkat.fillin.R
import com.firkat.fillin.feature.operationhistory.domain.model.OperationHistoryFilter
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.ui.component.DatePeriodChip
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
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).testTag("history-type-totals"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OperationType.entries.filter { state.filters.type == null || state.filters.type == it }.forEach { type ->
                OperationTotalBlock(
                    type = type,
                    state = state,
                    onEvent = onEvent,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun OperationTotalBlock(
    type: OperationType,
    state: OperationHistoryUiState,
    onEvent: (OperationHistoryEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = state.filters.type == type
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    val amount = state.periodTotals?.let {
        if (type == OperationType.Expense) it.expenses else it.incomes
    }
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .testTag("history-type-${type.apiValue}")
            .clip(shape)
            .selectable(
                selected = selected,
                role = Role.Button,
                onClick = { onEvent(OperationHistoryEvent.TypeSelected(type)) },
            ),
        shape = shape,
        color = colors.secondaryContainer,
        contentColor = colors.onSecondaryContainer,
        border = if (selected) BorderStroke(1.dp, colors.primary) else null,
    ) {
        Row(
            modifier = Modifier.heightIn(min = 88.dp).padding(start = 12.dp, end = if (selected) 0.dp else 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(
                        if (type == OperationType.Expense) R.string.operation_history_expenses
                        else R.string.operation_history_incomes,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
                when {
                    state.isInitialLoading && amount == null -> CircularProgressIndicator(
                        modifier = Modifier.size(24.dp).testTag("history-totals-loading-${type.apiValue}"),
                        strokeWidth = 2.dp,
                    )
                    else -> Text(
                        text = amount?.let { formatMoney(it, "KZT") } ?: "—",
                        modifier = Modifier.testTag("history-total-${type.apiValue}"),
                        style = if (selected) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (selected) {
                IconButton(
                    onClick = { onEvent(OperationHistoryEvent.TypeSelected(null)) },
                    modifier = Modifier.align(Alignment.CenterVertically).testTag("history-type-reset"),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.operation_history_reset_type),
                    )
                }
            }
        }
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
