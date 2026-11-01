package com.firkat.fillin.feature.operationhistory.ui

import com.firkat.fillin.feature.operationhistory.domain.model.Operation
import com.firkat.fillin.feature.operationhistory.domain.model.OperationHistoryFilter
import com.firkat.fillin.feature.operationhistory.domain.model.OperationPeriodTotals
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.feature.operationhistory.domain.repository.OfflineHistoryContext
import java.time.LocalDate
import java.time.ZoneId

data class OperationHistoryUiState(
    val operations: List<Operation> = emptyList(),
    val filters: OperationHistoryFilter = OperationHistoryFilter.currentMonth(LocalDate.now(ZoneId.of("Asia/Almaty"))),
    val periodTotals: OperationPeriodTotals? = null,
    val isPeriodFilterVisible: Boolean = false,
    val isCustomPeriod: Boolean = false,
    val isInitialLoading: Boolean = false,
    val isLoadingNextPage: Boolean = false,
    val isRefreshing: Boolean = false,
    val canLoadMore: Boolean = true,
    val error: OperationHistoryError? = null,
    val offline: OfflineHistoryContext = OfflineHistoryContext(),
)

enum class OperationHistoryError {
    MissingApiToken,
    Unauthorized,
    Forbidden,
    InvalidRequest,
    NotFound,
    Conflict,
    Network,
    InvalidResponse,
    UnlinkBeforeCategoryChange,
    Unknown,
}

sealed interface OperationHistoryEvent {
    data class TypeSelected(
        val type: OperationType?,
    ) : OperationHistoryEvent

    data object PeriodFilterOpened : OperationHistoryEvent

    data object PeriodFilterClosed : OperationHistoryEvent

    data object PeriodFilterReset : OperationHistoryEvent

    data class PeriodSelected(
        val dateFrom: LocalDate,
        val dateTo: LocalDate,
    ) : OperationHistoryEvent

    data object Refresh : OperationHistoryEvent

    data object LoadNextPage : OperationHistoryEvent

    data object Retry : OperationHistoryEvent

}
