package com.firkat.fillin.feature.operationhistory.ui

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.result.LocalResultEventBus
import androidx.navigation3.runtime.result.ResultEffect
import com.firkat.fillin.R
import com.firkat.fillin.feature.dashboard.ui.MainScreenHeader
import com.firkat.fillin.feature.operationhistory.domain.model.Operation
import com.firkat.fillin.feature.operationhistory.domain.model.OperationPeriodTotals
import com.firkat.fillin.feature.operationhistory.domain.model.OperationStatus
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.ui.component.RefreshableContent
import com.firkat.fillin.ui.component.showSaldomiSnackbar
import com.firkat.fillin.ui.format.formatMoney
import com.firkat.fillin.ui.theme.FillinTheme
import org.koin.compose.viewmodel.koinViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

@Composable
fun OperationHistoryRoute(
    viewModel: OperationHistoryViewModel = koinViewModel(),
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
    onOperationClick: (String, String) -> Unit,
    bottomBarOverlap: Dp = 0.dp,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val resultKey = rememberSaveable { "operation-history-${UUID.randomUUID()}" }
    val resultBus = LocalResultEventBus.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    ResultEffect<EditOperationResult>(resultKey = resultKey) { result ->
        resultBus.removeResult(resultKey)
        scope.launch { snackbarHostState.showSaldomiSnackbar(
            resources.getString(when (result.outcome) {
                EditOperationOutcome.Updated -> R.string.edit_operation_saved
                EditOperationOutcome.Corrected -> R.string.edit_operation_corrected
                EditOperationOutcome.Refunded -> R.string.edit_operation_refund_saved
            }), type = com.firkat.fillin.ui.component.SnackbarType.Success,
        ) }
    }
    DisposableEffect(resultBus, resultKey) {
        onDispose { resultBus.removeResult(resultKey) }
    }
    OperationHistoryScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOperationClick = { id -> onOperationClick(id, resultKey) },
        bottomBarOverlap = bottomBarOverlap,
    )
}

@Composable
fun OperationHistoryScreen(
    state: OperationHistoryUiState,
    onEvent: (OperationHistoryEvent) -> Unit,
    onOperationClick: (String) -> Unit = {},
    bottomBarOverlap: Dp = 0.dp,
) {
    Column(Modifier.fillMaxSize()) {
        MainScreenHeader(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .statusBarsPadding(),
            title = stringResource(R.string.operation_history_title)
        )
        OperationHistoryFilters(
            filters = state.filters,
            isCustomPeriod = state.isCustomPeriod,
            totals = state.periodTotals,
            isLoading = state.isInitialLoading,
            onPeriodClick = { onEvent(OperationHistoryEvent.PeriodFilterOpened) },
            onPeriodReset = { onEvent(OperationHistoryEvent.PeriodFilterReset) },
            onTypeSelected = { onEvent(OperationHistoryEvent.TypeSelected(it)) },
        )

        key(state.filters.dateFrom, state.filters.dateTo) {
            OperationHistoryContent(
                state = state,
                onOperationClick = onOperationClick,
                onRefresh = { onEvent(OperationHistoryEvent.Refresh) },
                onRetry = { onEvent(OperationHistoryEvent.Retry) },
                onLoadNextPage = { onEvent(OperationHistoryEvent.LoadNextPage) },
                bottomBarOverlap = bottomBarOverlap,
                modifier = Modifier.weight(1f),
            )
        }
    }
    if (state.isPeriodFilterVisible) {
        OperationPeriodFilterBottomSheet(filters = state.filters, onEvent = onEvent)
    }
}

private const val HistoryLoadingDelayMillis = 300L

@Composable
private fun OperationHistoryContent(
    state: OperationHistoryUiState,
    onOperationClick: (String) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadNextPage: () -> Unit,
    bottomBarOverlap: Dp,
    modifier: Modifier = Modifier,
) {
    val latestState by rememberUpdatedState(state)
    var displayedSnapshot by remember { mutableStateOf(state) }
    var contentVisible by remember { mutableStateOf(true) }
    val opacity = remember { Animatable(1f) }
    val switching = displayedSnapshot.requestId != state.requestId
    var showDelayedLoading by remember(state.requestId) { mutableStateOf(false) }

    // Keep the last displayed result available even if Room answers before the next frame.
    if (!switching) SideEffect { displayedSnapshot = state }

    LaunchedEffect(state.requestId) {
        if (switching) {
            if (opacity.value > 0f) opacity.animateTo(0f, tween(HistoryFadeOutMillis))
            contentVisible = false
            displayedSnapshot = snapshotFlow { latestState }.first {
                it.requestId == state.requestId && it.resultRequestId == state.requestId
            }
            contentVisible = true
            opacity.animateTo(1f, tween(HistoryFadeInMillis))
        }
    }

    LaunchedEffect(state.requestId) {
        if (switching && state.resultRequestId != state.requestId) {
            delay(HistoryLoadingDelayMillis)
            showDelayedLoading = true
        }
    }

    Box(modifier.fillMaxWidth()) {
        if (contentVisible) {
            val displayed = if (switching) displayedSnapshot else state
            val hasOperations = displayed.operations.isNotEmpty() && !displayed.isInitialLoading
            key(displayed.requestId) {
                RefreshableContent(
                    isRefreshing = !switching && displayed.isRefreshing,
                    onRefresh = { if (!switching) onRefresh() },
                    hasScrollableContent = hasOperations,
                    modifier = Modifier
                        .graphicsLayer { alpha = opacity.value }
                        .semantics { if (switching) hideFromAccessibility() }
                        .padding(bottom = if (hasOperations) 0.dp else bottomBarOverlap),
                ) {
                    when {
                        displayed.isInitialLoading -> LoadingContent()
                        displayed.error != null && displayed.operations.isEmpty() -> ErrorContent(
                            error = displayed.error,
                            onRetry = onRetry,
                            modifier = Modifier.fillMaxSize(),
                            enabled = !switching,
                        )
                        displayed.operations.isEmpty() -> EmptyContent()
                        else -> OperationList(
                            operations = displayed.operations,
                            isLoadingNextPage = displayed.isLoadingNextPage,
                            isRefreshing = displayed.isRefreshing,
                            canLoadMore = displayed.canLoadMore,
                            error = displayed.error,
                            enabled = !switching,
                            onOperationClick = onOperationClick,
                            onLoadNextPage = onLoadNextPage,
                            onRetry = onRetry,
                            bottomBarOverlap = bottomBarOverlap,
                        )
                    }
                }
            }
        } else if (showDelayedLoading && state.resultRequestId != state.requestId) {
            LoadingContent(Modifier.padding(bottom = bottomBarOverlap))
        }
    }
}

@Composable
private fun OperationList(
    operations: List<Operation>,
    isLoadingNextPage: Boolean,
    isRefreshing: Boolean,
    canLoadMore: Boolean,
    error: OperationHistoryError?,
    enabled: Boolean,
    onOperationClick: (String) -> Unit,
    onLoadNextPage: () -> Unit,
    onRetry: () -> Unit,
    bottomBarOverlap: Dp,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val operationDayGroups =
        remember(operations) {
            groupOperationsByDate(operations)
        }
    val reachedListEnd by remember {
        derivedStateOf {
            val lastVisibleIndex =
                listState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: -1
            val totalItems = listState.layoutInfo.totalItemsCount
            totalItems > 0 && lastVisibleIndex >= totalItems - 1
        }
    }

    LaunchedEffect(
        reachedListEnd,
        isLoadingNextPage,
        isRefreshing,
        canLoadMore,
        error,
        enabled,
    ) {
        if (
            reachedListEnd &&
            !isLoadingNextPage &&
            !isRefreshing &&
            canLoadMore &&
            error == null &&
            enabled
        ) {
            onLoadNextPage()
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("operation_history_list"),
        state = listState,
        userScrollEnabled = enabled,
        contentPadding =
            PaddingValues(
                top = 4.dp,
                bottom = 12.dp + bottomBarOverlap,
            ),
    ) {
        operationDayGroups.forEach { group ->
            item(key = "date-${group.date}", contentType = "day-header") {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .semantics { heading() }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatDateHeader(group.date),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatDayTotals(group.totals),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                    )
                }
            }

            items(
                items = group.operations,
                key = Operation::id,
                contentType = { "operation" },
            ) { operation ->
                OperationItem(
                    operation = operation,
                    onClick = { onOperationClick(operation.id) },
                    modifier = Modifier.testTag("operation-${operation.id}"),
                    enabled = enabled,
                )
            }
        }

        if (isLoadingNextPage) {
            item(key = "next-page-loading") {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        if (error != null) {
            item(key = "next-page-error") {
                ErrorContent(
                    error = error,
                    onRetry = onRetry,
                    enabled = enabled,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                )
            }
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(Modifier.testTag("history-list-loading"))
    }
}

@Composable
private fun EmptyContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.operation_history_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorContent(
    error: OperationHistoryError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(
        modifier =
            modifier
                .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(error.messageResource),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(
            onClick = onRetry,
            enabled = enabled,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text(stringResource(R.string.operation_history_retry))
        }
    }
}

internal val OperationHistoryError.messageResource: Int
    @StringRes get() =
        when (this) {
            OperationHistoryError.MissingApiToken,
            OperationHistoryError.Unauthorized,
            -> R.string.operation_history_error_unauthorized

            OperationHistoryError.Forbidden -> R.string.operation_history_error_forbidden

            OperationHistoryError.InvalidRequest -> R.string.operation_history_error_invalid_request

            OperationHistoryError.NotFound -> R.string.operation_history_error_not_found

            OperationHistoryError.Conflict -> R.string.operation_history_error_conflict

            OperationHistoryError.Network -> R.string.operation_history_error_network

            OperationHistoryError.InvalidResponse -> R.string.operation_history_error_invalid_response

            OperationHistoryError.UnlinkBeforeCategoryChange -> R.string.plan_link_unlink_before_category
            OperationHistoryError.Unknown -> R.string.operation_history_error_generic
        }

fun formatOperationAmount(
    amount: BigDecimal,
    currency: String,
    type: OperationType,
): String =
    (
        if (amount.signum() == 0) {
            ""
        } else if (type == OperationType.Income) {
            "+"
        } else {
            "−"
        }
    ) + formatMoney(amount, currency)

internal fun formatDateHeader(date: LocalDate): String =
    date.format(
        DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru")),
    )

private fun formatDayTotals(totals: List<OperationDayTotal>): String {
    val amount = totals.sumOf {
        when (it.type) {
            OperationType.Expense -> (-it.amount)
            OperationType.Income -> it.amount
        }
    }
    val amountType = if (amount <= BigDecimal(0)) OperationType.Expense else OperationType.Income
    return formatOperationAmount(amount.abs(), totals.first().currency, amountType)
}

@Preview(showBackground = true)
@Composable
private fun OperationHistoryPreview() {
    FillinTheme(dynamicColor = false) {
        OperationHistoryScreen(
            state =
                OperationHistoryUiState(
                    periodTotals = OperationPeriodTotals(expenses = BigDecimal("15000.00")),
                    operations =
                        listOf(
                            Operation(
                                id = "preview",
                                categoryName = "Продукты",
                                description = "Супермаркет",
                                amount = BigDecimal("15000.00"),
                                currency = "KZT",
                                originalAmount = BigDecimal("30.00"),
                                originalCurrency = "USD",
                                occurredOn = LocalDate.of(2026, 10, 6),
                                status = OperationStatus.Posted,
                                version = 0,
                            ),
                        ),
                    isInitialLoading = false,
                ),
            onEvent = {},
        )
    }
}
