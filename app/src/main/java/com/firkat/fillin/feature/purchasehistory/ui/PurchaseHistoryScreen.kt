package com.firkat.fillin.feature.purchasehistory.ui

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
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.purchasehistory.domain.model.Purchase
import com.firkat.fillin.feature.purchasehistory.domain.model.PurchaseStatus
import com.firkat.fillin.ui.theme.FillinTheme
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PurchaseHistoryRoute(
    viewModel: PurchaseHistoryViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PurchaseHistoryScreen(
        state = state,
        onEvent = viewModel::onEvent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseHistoryScreen(
    state: PurchaseHistoryUiState,
    onEvent: (PurchaseHistoryEvent) -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.purchase_history_title)) },
            )
        },
    ) { innerPadding ->
        when {
            state.isInitialLoading -> LoadingContent(
                modifier = Modifier.padding(innerPadding),
            )

            state.error != null && state.purchases.isEmpty() -> ErrorContent(
                error = state.error,
                onRetry = { onEvent(PurchaseHistoryEvent.Retry) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )

            state.purchases.isEmpty() -> EmptyContent(
                modifier = Modifier.padding(innerPadding),
            )

            else -> PurchaseList(
                state = state,
                onLoadNextPage = { onEvent(PurchaseHistoryEvent.LoadNextPage) },
                onRetry = { onEvent(PurchaseHistoryEvent.Retry) },
                contentPadding = innerPadding,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PurchaseList(
    state: PurchaseHistoryUiState,
    onLoadNextPage: () -> Unit,
    onRetry: () -> Unit,
    contentPadding: PaddingValues,
) {
    val listState = rememberLazyListState()
    val purchaseDayGroups = remember(state.purchases) {
        groupPurchasesByDate(state.purchases)
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
        state.canLoadMore,
        state.error,
    ) {
        if (
            reachedListEnd &&
            !state.isLoadingNextPage &&
            state.canLoadMore &&
            state.error == null
        ) {
            onLoadNextPage()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        state = listState,
        contentPadding = PaddingValues(
            top = 4.dp,
            bottom = 12.dp,
        ),
    ) {
        purchaseDayGroups.forEach { group ->
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
                items = group.purchases,
                key = Purchase::id,
            ) { purchase ->
                PurchaseItem(purchase = purchase)
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
private fun PurchaseItem(purchase: Purchase) {
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
                text = formatMoney(purchase.amount, purchase.currency),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = purchase.description
                ?.takeIf(String::isNotBlank)
                ?: stringResource(R.string.purchase_history_no_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
            text = stringResource(R.string.purchase_history_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorContent(
    error: PurchaseHistoryError,
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
            Text(stringResource(R.string.purchase_history_retry))
        }
    }
}

private val PurchaseHistoryError.messageResource: Int
    @StringRes get() = when (this) {
        PurchaseHistoryError.MissingApiToken,
        PurchaseHistoryError.Unauthorized,
        -> R.string.purchase_history_error_unauthorized

        PurchaseHistoryError.Forbidden -> R.string.purchase_history_error_forbidden
        PurchaseHistoryError.Network -> R.string.purchase_history_error_network
        PurchaseHistoryError.InvalidResponse -> R.string.purchase_history_error_invalid_response
        PurchaseHistoryError.Unknown -> R.string.purchase_history_error_generic
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

private fun formatDateHeader(date: LocalDate): String = date.format(
    DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru")),
)

private fun formatDayTotals(totals: List<PurchaseDayTotal>): String = totals.joinToString(" · ") {
    formatMoney(it.amount, it.currency)
}

@Preview(showBackground = true)
@Composable
private fun PurchaseHistoryPreview() {
    FillinTheme(dynamicColor = false) {
        PurchaseHistoryScreen(
            state = PurchaseHistoryUiState(
                purchases = listOf(
                    Purchase(
                        id = "preview",
                        categoryName = "Продукты",
                        description = "Супермаркет",
                        amount = BigDecimal("15000.00"),
                        currency = "KZT",
                        occurredOn = LocalDate.of(2026, 10, 6),
                        status = PurchaseStatus.Posted,
                    ),
                ),
                isInitialLoading = false,
            ),
            onEvent = {},
        )
    }
}
