package com.firkat.fillin.feature.operationhistory.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.firkat.fillin.feature.operationhistory.domain.model.OperationHistoryFilter
import com.firkat.fillin.feature.operationhistory.domain.model.OperationPeriodTotals
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.ui.component.DatePeriodChip

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
