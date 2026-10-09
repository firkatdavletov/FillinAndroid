package com.firkat.fillin.feature.dashboard.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.dashboard.domain.model.Dashboard
import com.firkat.fillin.feature.dashboard.domain.model.ExpenseCategory
import com.firkat.fillin.feature.dashboard.domain.model.FreeMoneyState
import com.firkat.fillin.feature.dashboard.domain.model.Money
import com.firkat.fillin.feature.dashboard.domain.model.PaceStatus
import com.firkat.fillin.feature.dashboard.domain.model.UpcomingPayment
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale
import org.koin.compose.viewmodel.koinViewModel

private val DashboardGreen = Color(0xFF2ED47A)
private val DashboardRed = Color(0xFFFF5D68)
private val DashboardWarning = Color(0xFFFFCA28)
private val CategoryBar = Color(0xFF71839D)

@Composable
fun DashboardRoute(
    onOpenPlannedExpenses: () -> Unit,
    onOpenCategories: () -> Unit,
    onAddOperation: () -> Unit,
    viewModel: DashboardViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DashboardScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenPlannedExpenses = onOpenPlannedExpenses,
        onOpenCategories = onOpenCategories,
        onAddOperation = onAddOperation,
    )
}

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onEvent: (DashboardEvent) -> Unit,
    onOpenPlannedExpenses: () -> Unit,
    onOpenCategories: () -> Unit,
    onAddOperation: () -> Unit,
) {
    when {
        state.dashboard != null -> DashboardContent(
            dashboard = state.dashboard,
            isRefreshing = state.isLoading,
            onOpenPlannedExpenses = onOpenPlannedExpenses,
            onOpenCategories = onOpenCategories,
            onAddOperation = onAddOperation,
        )

        state.isLoading -> DashboardLoading()

        state.error != null -> DashboardErrorContent(
            error = state.error,
            onRetry = { onEvent(DashboardEvent.Retry) },
        )
    }
}

@Composable
private fun DashboardContent(
    dashboard: Dashboard,
    isRefreshing: Boolean,
    onOpenPlannedExpenses: () -> Unit,
    onOpenCategories: () -> Unit,
    onAddOperation: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 12.dp,
            end = 16.dp,
            bottom = 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (isRefreshing) {
            item(key = "refreshing") {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        item(key = "header") {
            DashboardHeader(
                date = dashboard.date,
                onCalendarClick = onOpenPlannedExpenses,
                onSettingsClick = onOpenCategories,
            )
        }

        item(key = "free-money") {
            FreeMoneySummary(dashboard = dashboard)
        }

        item(key = "actuals") {
            ActualsSummary(dashboard = dashboard)
        }

        item(key = "budget") {
            BudgetExecutionCard(dashboard = dashboard)
        }

        item(key = "categories") {
            ExpenseCategoriesSection(
                categories = dashboard.expenseCategories,
                onOpenCategories = onOpenCategories,
            )
        }

        item(key = "upcoming") {
            UpcomingPaymentsCard(
                payments = dashboard.upcomingPayments,
                onClick = onOpenPlannedExpenses,
            )
        }

        item(key = "add-operation") {
            Button(
                onClick = onAddOperation,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onBackground,
                    contentColor = MaterialTheme.colorScheme.background,
                ),
            ) {
                Text(
                    text = "+  ${stringResource(R.string.dashboard_add_operation)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun DashboardHeader(
    date: LocalDate,
    onCalendarClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.dashboard_family_budget),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.8.sp,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatMonth(date),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = onCalendarClick) {
                Icon(
                    painter = painterResource(R.drawable.ic_calendar),
                    contentDescription = stringResource(R.string.dashboard_open_calendar),
                )
            }
            IconButton(onClick = onSettingsClick) {
                Icon(
                    painter = painterResource(R.drawable.ic_tune),
                    contentDescription = stringResource(R.string.dashboard_open_settings),
                )
            }
        }
    }
}

@Composable
private fun FreeMoneySummary(dashboard: Dashboard) {
    val statusColor = when (dashboard.freeMoney.state) {
        FreeMoneyState.Available -> DashboardGreen
        FreeMoneyState.Deficit -> DashboardRed
        FreeMoneyState.Unknown -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusText = when (dashboard.freeMoney.state) {
        FreeMoneyState.Available -> stringResource(R.string.dashboard_obligations_accounted)
        FreeMoneyState.Deficit -> stringResource(R.string.dashboard_free_money_deficit)
        FreeMoneyState.Unknown -> stringResource(R.string.dashboard_free_money_unknown)
    }

    Column(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.dashboard_free_until_month_end),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = formatMoney(dashboard.freeMoney.amount),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "✓  $statusText",
            style = MaterialTheme.typography.bodyLarge,
            color = statusColor,
        )
        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            MoneyLabel(
                label = stringResource(R.string.dashboard_on_accounts),
                money = dashboard.availableMoney,
                modifier = Modifier.weight(1f),
            )
            MoneyLabel(
                label = stringResource(R.string.dashboard_reserved),
                money = dashboard.freeMoney.reservedTotal,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MoneyLabel(
    label: String,
    money: Money,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = formatMoney(money),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ActualsSummary(dashboard: Dashboard) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ActualCard(
            label = stringResource(R.string.dashboard_income),
            marker = "↙",
            markerColor = DashboardGreen,
            money = dashboard.monthActuals.income,
            modifier = Modifier.weight(1f),
        )
        ActualCard(
            label = stringResource(R.string.dashboard_expenses),
            marker = "↗",
            markerColor = DashboardRed,
            money = dashboard.monthActuals.netExpenses,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ActualCard(
    label: String,
    marker: String,
    markerColor: Color,
    money: Money,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        modifier = modifier,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.background,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = marker,
                    style = MaterialTheme.typography.titleLarge,
                    color = markerColor,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = formatMoney(money),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BudgetExecutionCard(dashboard: Dashboard) {
    val total = dashboard.budgetExecution.total
    val spent = total.spent ?: dashboard.monthActuals.netExpenses
    val planned = total.plannedAmount
    val progress = total.usedPercent
        .divide(BigDecimal(100), 4, RoundingMode.HALF_UP)
        .toFloat()
        .coerceIn(0f, 1f)
    val remaining = planned?.let { plan ->
        Money(
            amount = (plan.amount - spent.amount).max(BigDecimal.ZERO),
            currency = plan.currency,
        )
    }

    OutlinedCard(
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.background,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.dashboard_monthly_budget),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = formatDayMonth(dashboard.date),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = formatMoney(spent),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                planned?.let {
                    Text(
                        text = stringResource(
                            R.string.dashboard_out_of_planned,
                            formatMoney(it),
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(10.dp)),
                color = MaterialTheme.colorScheme.tertiary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                drawStopIndicator = {},
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(
                        R.string.dashboard_used_percent,
                        formatPercent(total.usedPercent),
                    ),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                remaining?.let {
                    Text(
                        text = stringResource(
                            R.string.dashboard_remaining,
                            formatMoney(it),
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                    )
                }
            }
            PaceStatusLine(status = dashboard.budgetExecution.variable.paceStatus)
        }
    }
}

@Composable
private fun PaceStatusLine(status: PaceStatus) {
    val (textResource, color) = when (status) {
        PaceStatus.AheadOfPlan -> R.string.dashboard_pace_ahead to DashboardWarning
        PaceStatus.OnPlan -> R.string.dashboard_pace_on_plan to DashboardGreen
        PaceStatus.BelowPlan -> R.string.dashboard_pace_below to DashboardGreen
        PaceStatus.InsufficientData ->
            R.string.dashboard_pace_insufficient to MaterialTheme.colorScheme.onSurfaceVariant

        PaceStatus.Unknown ->
            R.string.dashboard_pace_unknown to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val marker = if (status == PaceStatus.AheadOfPlan) "⚠" else "✓"

    Text(
        text = "$marker  ${stringResource(textResource)}",
        modifier = Modifier.padding(top = 4.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = color,
    )
}

@Composable
private fun ExpenseCategoriesSection(
    categories: List<ExpenseCategory>,
    onOpenCategories: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            title = stringResource(R.string.dashboard_largest_categories),
            action = stringResource(R.string.dashboard_all),
            onClick = onOpenCategories,
        )
        if (categories.isEmpty()) {
            Text(
                text = stringResource(R.string.dashboard_no_expenses),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val largestShare = remember(categories) {
                categories.maxOfOrNull(ExpenseCategory::sharePercent) ?: BigDecimal.ONE
            }
            categories.forEach { category ->
                ExpenseCategoryRow(
                    category = category,
                    largestShare = largestShare,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    action: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "$action  ›",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ExpenseCategoryRow(
    category: ExpenseCategory,
    largestShare: BigDecimal,
) {
    val progress = if (largestShare.signum() > 0) {
        category.sharePercent
            .divide(largestShare, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal("0.8"))
            .toFloat()
            .coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = category.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = formatMoney(category.amount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(7.dp)),
            color = CategoryBar,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun UpcomingPaymentsCard(
    payments: List<UpcomingPayment>,
    onClick: () -> Unit,
) {
    OutlinedCard(
        onClick = onClick,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.background,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader(
                title = stringResource(R.string.dashboard_upcoming_payments),
                action = "",
                onClick = onClick,
            )
            if (payments.isEmpty()) {
                Text(
                    text = stringResource(R.string.dashboard_no_upcoming_payments),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                payments.forEachIndexed { index, payment ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    UpcomingPaymentRow(payment = payment)
                }
            }
        }
    }
}

@Composable
private fun UpcomingPaymentRow(payment: UpcomingPayment) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = payment.categoryName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = formatDayMonth(payment.dueOn),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = formatMoney(payment.remainingAmount),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun DashboardLoading() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun DashboardErrorContent(
    error: DashboardError,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(error.messageResource),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text(stringResource(R.string.dashboard_retry))
        }
    }
}

private val DashboardError.messageResource: Int
    @StringRes get() = when (this) {
        DashboardError.MissingApiToken,
        DashboardError.Unauthorized,
        -> R.string.dashboard_error_unauthorized

        DashboardError.Forbidden -> R.string.dashboard_error_forbidden
        DashboardError.Network -> R.string.dashboard_error_network
        DashboardError.InvalidResponse -> R.string.dashboard_error_invalid_response
        DashboardError.Unknown -> R.string.dashboard_error_generic
    }

private val dashboardLocale = Locale.forLanguageTag("ru-KZ")

private fun formatMoney(money: Money): String {
    val value = money.amount.stripTrailingZeros()
    val digits = value.scale().coerceIn(0, 2)
    val amount = NumberFormat.getNumberInstance(dashboardLocale).apply {
        minimumFractionDigits = digits
        maximumFractionDigits = digits
    }.format(value)
    val symbol = runCatching {
        Currency.getInstance(money.currency).getSymbol(dashboardLocale)
    }.getOrDefault(money.currency)
    return "$amount $symbol"
}

private fun formatMonth(date: LocalDate): String = date
    .format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("ru")))
    .replaceFirstChar { character ->
        if (character.isLowerCase()) character.titlecase(Locale.forLanguageTag("ru"))
        else character.toString()
    }

private fun formatDayMonth(date: LocalDate): String = date.format(
    DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru")),
)

private fun formatPercent(value: BigDecimal): String = value
    .stripTrailingZeros()
    .toPlainString()
