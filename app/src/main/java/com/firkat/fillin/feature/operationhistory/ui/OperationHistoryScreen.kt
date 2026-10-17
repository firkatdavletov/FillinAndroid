package com.firkat.fillin.feature.operationhistory.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.operationhistory.domain.model.Operation
import com.firkat.fillin.feature.operationhistory.domain.model.OperationStatus
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.ui.component.FeatureScaffold
import com.firkat.fillin.ui.component.RefreshableContent
import com.firkat.fillin.ui.theme.FillinTheme
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun OperationHistoryRoute(
    viewModel: OperationHistoryViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FeatureScaffold(
        titleResource = R.string.operation_history_title,
        onNavigateBack = if (state.editor != null) {
            { viewModel.onEvent(OperationHistoryEvent.EditorClosed) }
        } else {
            null
        },
        navigateBackEnabled = state.editor?.isSaving != true,
    ) {
        OperationHistoryScreen(
            state = state,
            onEvent = viewModel::onEvent,
        )
    }
}

@Composable
fun OperationHistoryScreen(
    state: OperationHistoryUiState,
    onEvent: (OperationHistoryEvent) -> Unit,
) {
    val editor = state.editor
    if (editor != null) {
        BackHandler(enabled = !editor.isSaving) {
            onEvent(OperationHistoryEvent.EditorClosed)
        }
        PurchaseEditor(editor = editor, onEvent = onEvent)
        if (editor.isRefundConfirmationVisible) {
            RefundConfirmation(onEvent = onEvent)
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        OperationHistoryFilters(state = state, onEvent = onEvent)
        Box(Modifier.weight(1f)) {
            key(state.filters) {
                RefreshableContent(
                    isRefreshing = state.isRefreshing,
                    onRefresh = { onEvent(OperationHistoryEvent.Refresh) },
                    hasScrollableContent = state.operations.isNotEmpty() && !state.isInitialLoading,
                ) {
                    when {
                        state.isInitialLoading -> LoadingContent()

                        state.error != null && state.operations.isEmpty() -> ErrorContent(
                            error = state.error,
                            onRetry = { onEvent(OperationHistoryEvent.Retry) },
                            modifier = Modifier.fillMaxSize(),
                        )

                        state.operations.isEmpty() -> EmptyContent()

                        else -> OperationList(
                            state = state,
                            onEdit = { onEvent(OperationHistoryEvent.EditRequested(it)) },
                            onLoadNextPage = { onEvent(OperationHistoryEvent.LoadNextPage) },
                            onRetry = { onEvent(OperationHistoryEvent.Retry) },
                        )
                    }
                }
            }
        }
    }
    if (state.isCategoryFilterVisible) {
        OperationCategoryFilterDialog(state = state, onEvent = onEvent)
    }
    if (state.isPeriodFilterVisible) {
        OperationPeriodFilterDialog(filters = state.filters, onEvent = onEvent)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OperationList(
    state: OperationHistoryUiState,
    onEdit: (String) -> Unit,
    onLoadNextPage: () -> Unit,
    onRetry: () -> Unit,
) {
    val listState = rememberLazyListState()
    val operationDayGroups = remember(state.operations) {
        groupOperationsByDate(state.operations)
    }
    val reachedListEnd by remember {
        derivedStateOf {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val totalItems = listState.layoutInfo.totalItemsCount
            totalItems > 0 && lastVisibleIndex >= totalItems - 1
        }
    }

    LaunchedEffect(
        reachedListEnd,
        state.isLoadingNextPage,
        state.isRefreshing,
        state.canLoadMore,
        state.error,
    ) {
        if (
            reachedListEnd &&
            !state.isLoadingNextPage &&
            !state.isRefreshing &&
            state.canLoadMore &&
            state.error == null
        ) {
            onLoadNextPage()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(
            top = 4.dp,
            bottom = 12.dp,
        ),
    ) {
        operationDayGroups.forEach { group ->
            stickyHeader(key = "date-${group.date}") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatDateHeader(group.date),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatDayTotals(group.totals),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                    )
                }
            }

            items(
                items = group.operations,
                key = Operation::id,
            ) { purchase ->
                OperationItem(
                    purchase = purchase,
                    onEdit = { onEdit(purchase.id) },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }

        if (state.isLoadingNextPage) {
            item(key = "next-page-loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        state.error?.let { error ->
            item(key = "next-page-error") {
                ErrorContent(
                    error = error,
                    onRetry = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                )
            }
        }
    }
}

@Composable
private fun PurchaseEditor(
    editor: PurchaseEditorUiState,
    onEvent: (OperationHistoryEvent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.operation_history_editor_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = editor.original.categoryName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = formatDateHeader(editor.original.occurredOn),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            editor.original.description?.takeIf(String::isNotBlank)?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        OutlinedTextField(
            value = editor.amount,
            onValueChange = { onEvent(OperationHistoryEvent.AmountChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !editor.isSaving,
            label = { Text(stringResource(R.string.operation_history_amount_label)) },
            suffix = { Text(editor.original.originalCurrency) },
            supportingText = {
                Text(stringResource(R.string.operation_history_amount_hint))
            },
            isError = editor.amount.isNotBlank() && editor.parsedAmount == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
        )

        Text(
            text = stringResource(
                R.string.operation_history_currency_immutable_hint,
                editor.original.originalCurrency,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        editor.error?.let { error ->
            Text(
                text = stringResource(error.messageResource),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Button(
            onClick = { onEvent(OperationHistoryEvent.SaveRequested) },
            modifier = Modifier.fillMaxWidth(),
            enabled = editor.canSave,
        ) {
            if (editor.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(2.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(stringResource(R.string.operation_history_save_action))
            }
        }

        OutlinedButton(
            onClick = { onEvent(OperationHistoryEvent.RefundRequested) },
            modifier = Modifier.fillMaxWidth(),
            enabled = editor.canRefund,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
        ) {
            Text(stringResource(R.string.operation_history_refund_action))
        }
    }
}

@Composable
private fun RefundConfirmation(
    onEvent: (OperationHistoryEvent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onEvent(OperationHistoryEvent.RefundDismissed) },
        title = { Text(stringResource(R.string.operation_history_refund_title)) },
        text = { Text(stringResource(R.string.operation_history_refund_message)) },
        confirmButton = {
            TextButton(onClick = { onEvent(OperationHistoryEvent.RefundConfirmed) }) {
                Text(
                    text = stringResource(R.string.operation_history_refund_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(OperationHistoryEvent.RefundDismissed) }) {
                Text(stringResource(R.string.operation_history_refund_cancel))
            }
        },
    )
}

@Composable
private fun OperationItem(purchase: Operation, onEdit: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = purchase.categoryName,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = formatOperationAmount(purchase.amount, purchase.currency, purchase.type),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (purchase.type == OperationType.Income) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
        Text(
            text = purchase.description
                ?.takeIf(String::isNotBlank)
                ?: stringResource(R.string.operation_history_no_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (purchase.status) {
            OperationStatus.Posted -> if (purchase.type == OperationType.Expense) TextButton(
                onClick = onEdit,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.operation_history_edit_action))
            }
            OperationStatus.Voided -> Text(
                text = stringResource(R.string.operation_history_voided_status),
                modifier = Modifier.align(Alignment.End),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OperationStatus.Unknown -> Unit
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
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
) {
    Column(
        modifier = modifier
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
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text(stringResource(R.string.operation_history_retry))
        }
    }
}

private val OperationHistoryError.messageResource: Int
    @StringRes get() = when (this) {
        OperationHistoryError.MissingApiToken,
        OperationHistoryError.Unauthorized,
        -> R.string.operation_history_error_unauthorized

        OperationHistoryError.Forbidden -> R.string.operation_history_error_forbidden
        OperationHistoryError.InvalidRequest -> R.string.operation_history_error_invalid_request
        OperationHistoryError.NotFound -> R.string.operation_history_error_not_found
        OperationHistoryError.Conflict -> R.string.operation_history_error_conflict
        OperationHistoryError.Network -> R.string.operation_history_error_network
        OperationHistoryError.InvalidResponse -> R.string.operation_history_error_invalid_response
        OperationHistoryError.Unknown -> R.string.operation_history_error_generic
    }

private fun formatMoney(amount: BigDecimal, currencyCode: String): String = runCatching {
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("ru-KZ")).apply {
        currency = Currency.getInstance(currencyCode)
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(amount)
}.getOrElse {
    "${amount.setScale(2).toPlainString()} $currencyCode"
}

private fun formatOperationAmount(amount: BigDecimal, currency: String, type: OperationType): String =
    (if (amount.signum() == 0) "" else if (type == OperationType.Income) "+" else "−") + formatMoney(amount, currency)

private fun formatDateHeader(date: LocalDate): String = date.format(
    DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru")),
)

private fun formatDayTotals(totals: List<OperationDayTotal>): String = totals.joinToString("\n") {
    formatOperationAmount(it.amount, it.currency, it.type)
}

@Preview(showBackground = true)
@Composable
private fun OperationHistoryPreview() {
    FillinTheme(dynamicColor = false) {
        OperationHistoryScreen(
            state = OperationHistoryUiState(
                operations = listOf(
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
