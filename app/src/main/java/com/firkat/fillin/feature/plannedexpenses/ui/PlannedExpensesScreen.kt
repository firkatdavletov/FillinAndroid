package com.firkat.fillin.feature.plannedexpenses.ui

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpense
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpenseStatus
import com.firkat.fillin.ui.theme.FillinTheme
import java.math.BigDecimal
import java.math.MathContext
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PlannedExpensesRoute(
    viewModel: PlannedExpensesViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PlannedExpensesScreen(
        state = state,
        onEvent = viewModel::onEvent,
    )
}

@Composable
fun PlannedExpensesScreen(
    state: PlannedExpensesUiState,
    onEvent: (PlannedExpensesEvent) -> Unit,
) {
    when {
        state.isLoading && state.items.isEmpty() -> LoadingContent()

        state.error != null && state.items.isEmpty() -> ErrorContent(
            error = state.error,
            onRetry = { onEvent(PlannedExpensesEvent.Retry) },
            modifier = Modifier.fillMaxSize(),
        )

        state.items.isEmpty() -> EmptyContent(
            throughInclusive = state.throughInclusive,
        )

        else -> PlannedExpensesList(state = state)
    }
}

@Composable
private fun PlannedExpensesList(
    state: PlannedExpensesUiState,
) {
    val overdueItems = remember(state.items) { state.items.filter(PlannedExpense::overdue) }
    val upcomingItems = remember(state.items) { state.items.filterNot(PlannedExpense::overdue) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 8.dp,
            end = 16.dp,
            bottom = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "summary") {
            PlannedExpensesSummary(state = state)
        }

        if (overdueItems.isNotEmpty()) {
            item(key = "overdue-title") {
                SectionTitle(
                    title = stringResource(R.string.planned_expenses_overdue_section),
                    count = overdueItems.size,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            items(overdueItems, key = PlannedExpense::id) { expense ->
                PlannedExpenseItem(expense = expense)
            }
        }

        if (upcomingItems.isNotEmpty()) {
            item(key = "upcoming-title") {
                SectionTitle(
                    title = stringResource(R.string.planned_expenses_upcoming_section),
                    count = upcomingItems.size,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            items(upcomingItems, key = PlannedExpense::id) { expense ->
                PlannedExpenseItem(expense = expense)
            }
        }
    }
}

@Composable
private fun PlannedExpensesSummary(state: PlannedExpensesUiState) {
    val totals = remember(state.items) { remainingTotals(state.items) }
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.planned_expenses_remaining),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = totals.joinToString(" · ") { formatMoney(it.amount, it.currency) },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = pluralStringResource(
                    R.plurals.planned_expenses_count,
                    state.items.size,
                    state.items.size,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state.asOf != null && state.throughInclusive != null) {
                Text(
                    text = stringResource(
                        R.string.planned_expenses_period,
                        formatShortDate(state.asOf),
                        formatShortDate(state.throughInclusive),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
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
private fun PlannedExpenseItem(expense: PlannedExpense) {
    val dueColor = if (expense.overdue) {
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
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = expense.categoryName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (expense.overdue) {
                            stringResource(
                                R.string.planned_expenses_overdue_due_date,
                                formatLongDate(expense.dueOn),
                            )
                        } else {
                            stringResource(
                                R.string.planned_expenses_due_date,
                                formatLongDate(expense.dueOn),
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = dueColor,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatMoney(expense.remainingAmount, expense.currency),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.planned_expenses_left),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            LinearProgressIndicator(
                progress = { paidFraction(expense) },
                modifier = Modifier.fillMaxWidth(),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AmountCaption(
                    label = stringResource(R.string.planned_expenses_planned),
                    amount = formatMoney(expense.plannedAmount, expense.currency),
                    modifier = Modifier.weight(1f),
                )
                AmountCaption(
                    label = stringResource(R.string.planned_expenses_paid),
                    amount = formatMoney(expense.paidAmount, expense.currency),
                    modifier = Modifier.weight(1f),
                    alignEnd = true,
                )
            }
        }
    }
}

@Composable
private fun AmountCaption(
    label: String,
    amount: String,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = amount,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
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
private fun EmptyContent(
    throughInclusive: LocalDate?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = throughInclusive?.let {
                stringResource(
                    R.string.planned_expenses_empty_through,
                    formatLongDate(it),
                )
            } ?: stringResource(R.string.planned_expenses_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ErrorContent(
    error: PlannedExpensesError,
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
            Text(stringResource(R.string.planned_expenses_retry))
        }
    }
}

private val PlannedExpensesError.messageResource: Int
    @StringRes get() = when (this) {
        PlannedExpensesError.MissingApiToken,
        PlannedExpensesError.Unauthorized,
        -> R.string.planned_expenses_error_unauthorized

        PlannedExpensesError.Forbidden -> R.string.planned_expenses_error_forbidden
        PlannedExpensesError.Network -> R.string.planned_expenses_error_network
        PlannedExpensesError.InvalidResponse -> R.string.planned_expenses_error_invalid_response
        PlannedExpensesError.Unknown -> R.string.planned_expenses_error_generic
    }

private fun paidFraction(expense: PlannedExpense): Float {
    if (expense.plannedAmount <= BigDecimal.ZERO) return 0f
    return expense.paidAmount
        .divide(expense.plannedAmount, MathContext.DECIMAL64)
        .toFloat()
        .coerceIn(0f, 1f)
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
private fun PlannedExpensesPreview() {
    FillinTheme(dynamicColor = false) {
        PlannedExpensesScreen(
            state = PlannedExpensesUiState(
                asOf = LocalDate.of(2026, 10, 7),
                throughInclusive = LocalDate.of(2026, 12, 31),
                items = listOf(
                    PlannedExpense(
                        id = "preview-overdue",
                        categoryId = "housing",
                        categoryName = "Жильё",
                        plannedAmount = BigDecimal("120000.00"),
                        paidAmount = BigDecimal("20000.00"),
                        remainingAmount = BigDecimal("100000.00"),
                        currency = "KZT",
                        dueOn = LocalDate.of(2026, 10, 5),
                        overdue = true,
                        status = PlannedExpenseStatus.Scheduled,
                        version = 1,
                        createdAt = Instant.parse("2026-10-01T04:00:00Z"),
                        modifiedAt = Instant.parse("2026-10-07T03:00:00Z"),
                    ),
                    PlannedExpense(
                        id = "preview-upcoming",
                        categoryId = "utilities",
                        categoryName = "Коммунальные услуги",
                        plannedAmount = BigDecimal("35000.00"),
                        paidAmount = BigDecimal.ZERO,
                        remainingAmount = BigDecimal("35000.00"),
                        currency = "KZT",
                        dueOn = LocalDate.of(2026, 10, 20),
                        overdue = false,
                        status = PlannedExpenseStatus.Scheduled,
                        version = 1,
                        createdAt = Instant.parse("2026-10-01T04:00:00Z"),
                        modifiedAt = Instant.parse("2026-10-07T03:00:00Z"),
                    ),
                ),
            ),
            onEvent = {},
        )
    }
}
