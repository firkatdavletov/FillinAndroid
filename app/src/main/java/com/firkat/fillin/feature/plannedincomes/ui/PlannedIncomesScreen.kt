package com.firkat.fillin.feature.plannedincomes.ui

import androidx.annotation.StringRes
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
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncome
import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncomeStatus
import com.firkat.fillin.ui.theme.FillinTheme
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PlannedIncomesRoute(
    viewModel: PlannedIncomesViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PlannedIncomesScreen(
        state = state,
        onEvent = viewModel::onEvent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannedIncomesScreen(
    state: PlannedIncomesUiState,
    onEvent: (PlannedIncomesEvent) -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.planned_incomes_title)) },
            )
        },
    ) { innerPadding ->
        when {
            state.isLoading && state.items.isEmpty() -> LoadingContent(
                modifier = Modifier.padding(innerPadding),
            )

            state.error != null && state.items.isEmpty() -> ErrorContent(
                error = state.error,
                onRetry = { onEvent(PlannedIncomesEvent.Retry) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )

            state.items.isEmpty() -> EmptyContent(
                modifier = Modifier.padding(innerPadding),
            )

            else -> PlannedIncomesList(
                state = state,
                contentPadding = innerPadding,
            )
        }
    }
}

@Composable
private fun PlannedIncomesList(
    state: PlannedIncomesUiState,
    contentPadding: PaddingValues,
) {
    val overdueItems = remember(state.items) { state.items.filter(PlannedIncome::overdue) }
    val upcomingItems = remember(state.items) { state.items.filterNot(PlannedIncome::overdue) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            end = 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "summary") {
            PlannedIncomesSummary(state = state)
        }

        if (overdueItems.isNotEmpty()) {
            item(key = "overdue-title") {
                SectionTitle(
                    title = stringResource(R.string.planned_incomes_overdue_section),
                    count = overdueItems.size,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            items(overdueItems, key = PlannedIncome::id) { income ->
                PlannedIncomeItem(income = income)
            }
        }

        if (upcomingItems.isNotEmpty()) {
            item(key = "upcoming-title") {
                SectionTitle(
                    title = stringResource(R.string.planned_incomes_upcoming_section),
                    count = upcomingItems.size,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            items(upcomingItems, key = PlannedIncome::id) { income ->
                PlannedIncomeItem(income = income)
            }
        }
    }
}

@Composable
private fun PlannedIncomesSummary(state: PlannedIncomesUiState) {
    val totals = remember(state.items) { plannedIncomeTotals(state.items) }
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.planned_incomes_expected),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = totals.joinToString(" · ") { formatMoney(it.amount, it.currency) },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = pluralStringResource(
                    R.plurals.planned_incomes_count,
                    state.items.size,
                    state.items.size,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            state.asOf?.let { asOf ->
                Text(
                    text = stringResource(
                        R.string.planned_incomes_as_of,
                        formatShortDate(asOf),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f),
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlannedIncomeItem(income: PlannedIncome) {
    val dueColor = if (income.overdue) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = income.categoryName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (income.overdue) {
                        stringResource(
                            R.string.planned_incomes_overdue_due_date,
                            formatLongDate(income.dueOn),
                        )
                    } else {
                        stringResource(
                            R.string.planned_incomes_due_date,
                            formatLongDate(income.dueOn),
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = dueColor,
                )
            }
            Text(
                text = formatMoney(income.plannedAmount, income.currency),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
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
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.planned_incomes_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ErrorContent(
    error: PlannedIncomesError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
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
            Text(stringResource(R.string.planned_incomes_retry))
        }
    }
}

private val PlannedIncomesError.messageResource: Int
    @StringRes get() = when (this) {
        PlannedIncomesError.MissingApiToken,
        PlannedIncomesError.Unauthorized,
        -> R.string.planned_incomes_error_unauthorized

        PlannedIncomesError.Forbidden -> R.string.planned_incomes_error_forbidden
        PlannedIncomesError.Network -> R.string.planned_incomes_error_network
        PlannedIncomesError.InvalidResponse -> R.string.planned_incomes_error_invalid_response
        PlannedIncomesError.Unknown -> R.string.planned_incomes_error_generic
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

private val shortDateFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("ru"))
private val longDateFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru"))

private fun formatShortDate(date: LocalDate): String = date.format(shortDateFormatter)

private fun formatLongDate(date: LocalDate): String = date.format(longDateFormatter)

@Preview(showBackground = true)
@Composable
private fun PlannedIncomesPreview() {
    FillinTheme(dynamicColor = false) {
        PlannedIncomesScreen(
            state = PlannedIncomesUiState(
                asOf = LocalDate.of(2026, 10, 7),
                items = listOf(
                    PlannedIncome(
                        id = "preview-overdue",
                        categoryId = "freelance",
                        categoryName = "Фриланс",
                        plannedAmount = BigDecimal("150000.00"),
                        currency = "KZT",
                        dueOn = LocalDate.of(2026, 10, 5),
                        overdue = true,
                        status = PlannedIncomeStatus.Scheduled,
                        version = 1,
                        createdAt = Instant.parse("2026-10-01T04:00:00Z"),
                        modifiedAt = Instant.parse("2026-10-01T04:00:00Z"),
                    ),
                    PlannedIncome(
                        id = "preview-upcoming",
                        categoryId = "salary",
                        categoryName = "Зарплата",
                        plannedAmount = BigDecimal("1300000.00"),
                        currency = "KZT",
                        dueOn = LocalDate.of(2026, 10, 10),
                        overdue = false,
                        status = PlannedIncomeStatus.Scheduled,
                        version = 0,
                        createdAt = Instant.parse("2026-10-01T04:00:00Z"),
                        modifiedAt = Instant.parse("2026-10-01T04:00:00Z"),
                    ),
                ),
            ),
            onEvent = {},
        )
    }
}
