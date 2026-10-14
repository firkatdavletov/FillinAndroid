package com.firkat.fillin.feature.plannedexpenses.ui

import androidx.annotation.StringRes
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpense
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpenseFrequency
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpenseStatus
import com.firkat.fillin.ui.component.FeatureScaffold
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
    FeatureScaffold(
        titleResource = R.string.planned_expenses_title,
        onNavigateBack = if (state.editor != null) {
            { viewModel.onEvent(PlannedExpensesEvent.EditorClosed) }
        } else {
            null
        },
        navigateBackEnabled = state.editor?.isSaving != true,
    ) {
        PlannedExpensesScreen(
            state = state,
            onEvent = viewModel::onEvent,
        )
    }
}

@Composable
fun PlannedExpensesScreen(
    state: PlannedExpensesUiState,
    onEvent: (PlannedExpensesEvent) -> Unit,
) {
    val editor = state.editor
    if (editor != null) {
        BackHandler(enabled = !editor.isSaving) {
            onEvent(PlannedExpensesEvent.EditorClosed)
        }
        PlannedExpenseRuleEditor(
            state = state,
            editor = editor,
            onEvent = onEvent,
        )
        if (editor.isDeleteConfirmationVisible) {
            DeleteRuleConfirmation(onEvent = onEvent)
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading && state.items.isEmpty() -> LoadingContent()

            state.error != null && state.items.isEmpty() -> ErrorContent(
                error = state.error,
                onRetry = { onEvent(PlannedExpensesEvent.Retry) },
                modifier = Modifier.fillMaxSize(),
            )

            state.items.isEmpty() -> EmptyContent(
                throughInclusive = plannedExpensesPeriodEnd(
                    asOf = state.asOf,
                    throughInclusive = state.throughInclusive,
                    period = state.selectedPeriod,
                ),
            )

            else -> PlannedExpensesList(
                state = state,
                onEvent = onEvent,
            )
        }

        ExtendedFloatingActionButton(
            onClick = { onEvent(PlannedExpensesEvent.CreateRequested) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Text(stringResource(R.string.planned_expenses_create_action))
        }
    }

    state.closeConfirmationExpenseId
        ?.let { expenseId -> state.items.firstOrNull { it.id == expenseId } }
        ?.let { expense ->
            CloseExpenseConfirmation(
                expense = expense,
                isClosing = state.isClosingExpense,
                error = state.closeError,
                onEvent = onEvent,
            )
        }
}

@Composable
private fun PlannedExpenseRuleEditor(
    state: PlannedExpensesUiState,
    editor: PlannedExpenseRuleEditorUiState,
    onEvent: (PlannedExpensesEvent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(
                if (editor.isCreating) {
                    R.string.planned_expenses_editor_create_title
                } else {
                    R.string.planned_expenses_editor_edit_title
                },
            ),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        if (editor.isCreating) {
            Text(
                text = stringResource(R.string.planned_expenses_category_label),
                style = MaterialTheme.typography.labelLarge,
            )
            when {
                state.isCategoriesLoading -> CircularProgressIndicator()
                state.expenseCategories.isEmpty() -> Text(
                    text = stringResource(R.string.planned_expenses_no_expense_categories),
                    color = MaterialTheme.colorScheme.error,
                )
                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.expenseCategories.forEach { category ->
                        FilterChip(
                            selected = editor.categoryId == category.id,
                            onClick = {
                                onEvent(PlannedExpensesEvent.CategorySelected(category.id))
                            },
                            enabled = !editor.isSaving,
                            label = { Text("${category.icon.emoji} ${category.name}") },
                        )
                    }
                }
            }
        } else {
            Text(
                text = editor.original?.categoryName.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.planned_expenses_edit_scope_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedTextField(
            value = editor.amount,
            onValueChange = { onEvent(PlannedExpensesEvent.AmountChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !editor.isSaving,
            label = { Text(stringResource(R.string.planned_expenses_amount_label)) },
            suffix = { Text("KZT") },
            supportingText = {
                Text(stringResource(R.string.planned_expenses_amount_hint))
            },
            isError = editor.amount.isNotBlank() && editor.parsedAmount == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
        )

        if (editor.isCreating) {
            Text(
                text = stringResource(R.string.planned_expenses_frequency_label),
                style = MaterialTheme.typography.labelLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlannedExpenseFrequency.entries.forEach { frequency ->
                    FilterChip(
                        selected = editor.frequency == frequency,
                        onClick = { onEvent(PlannedExpensesEvent.FrequencyChanged(frequency)) },
                        enabled = !editor.isSaving,
                        label = { Text(stringResource(frequency.labelResource)) },
                    )
                }
            }

            OutlinedTextField(
                value = editor.scheduleValue,
                onValueChange = { onEvent(PlannedExpensesEvent.ScheduleValueChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !editor.isSaving,
                label = { Text(stringResource(editor.frequency.scheduleLabelResource)) },
                supportingText = {
                    Text(stringResource(editor.frequency.scheduleHintResource))
                },
                isError = editor.scheduleValue.isNotBlank() && editor.parsedScheduleValue == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )

            OutlinedTextField(
                value = editor.startsOn,
                onValueChange = { onEvent(PlannedExpensesEvent.StartsOnChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !editor.isSaving,
                label = { Text(stringResource(R.string.planned_expenses_starts_on_label)) },
                supportingText = {
                    Text(stringResource(R.string.planned_expenses_starts_on_hint))
                },
                isError = editor.startsOn.isNotBlank() && editor.parsedStartsOn == null,
                singleLine = true,
            )
        }

        editor.error?.let { error ->
            Text(
                text = stringResource(error.messageResource),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Button(
            onClick = { onEvent(PlannedExpensesEvent.SaveRequested) },
            modifier = Modifier.fillMaxWidth(),
            enabled = editor.canSave,
        ) {
            if (editor.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(2.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(
                    stringResource(
                        if (editor.isCreating) {
                            R.string.planned_expenses_create_submit
                        } else {
                            R.string.planned_expenses_save_submit
                        },
                    ),
                )
            }
        }

        if (!editor.isCreating) {
            OutlinedButton(
                onClick = { onEvent(PlannedExpensesEvent.DeleteRequested) },
                modifier = Modifier.fillMaxWidth(),
                enabled = editor.canDelete,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(stringResource(R.string.planned_expenses_delete_action))
            }
        }
    }
}

@Composable
private fun DeleteRuleConfirmation(onEvent: (PlannedExpensesEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(PlannedExpensesEvent.DeleteDismissed) },
        title = { Text(stringResource(R.string.planned_expenses_delete_title)) },
        text = { Text(stringResource(R.string.planned_expenses_delete_message)) },
        confirmButton = {
            TextButton(onClick = { onEvent(PlannedExpensesEvent.DeleteConfirmed) }) {
                Text(
                    text = stringResource(R.string.planned_expenses_delete_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(PlannedExpensesEvent.DeleteDismissed) }) {
                Text(stringResource(R.string.planned_expenses_delete_cancel))
            }
        },
    )
}

@Composable
private fun CloseExpenseConfirmation(
    expense: PlannedExpense,
    isClosing: Boolean,
    error: PlannedExpensesError?,
    onEvent: (PlannedExpensesEvent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onEvent(PlannedExpensesEvent.CloseDismissed) },
        title = { Text(stringResource(R.string.planned_expenses_close_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(
                        R.string.planned_expenses_close_message,
                        formatMoney(expense.paidAmount, expense.currency),
                        formatMoney(expense.plannedAmount, expense.currency),
                        formatMoney(expense.remainingAmount, expense.currency),
                    ),
                )
                error?.let {
                    Text(
                        text = stringResource(it.closeMessageResource),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onEvent(PlannedExpensesEvent.CloseConfirmed) },
                enabled = !isClosing,
            ) {
                Text(
                    stringResource(
                        if (isClosing) {
                            R.string.planned_expenses_close_in_progress
                        } else {
                            R.string.planned_expenses_close_confirm
                        },
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onEvent(PlannedExpensesEvent.CloseDismissed) },
                enabled = !isClosing,
            ) {
                Text(stringResource(R.string.planned_expenses_close_cancel))
            }
        },
    )
}

@Composable
private fun PlannedExpensesList(
    state: PlannedExpensesUiState,
    onEvent: (PlannedExpensesEvent) -> Unit,
) {
    val visibleItems = remember(
        state.items,
        state.asOf,
        state.throughInclusive,
        state.selectedPeriod,
    ) {
        plannedExpensesForPeriod(
            items = state.items,
            asOf = state.asOf,
            throughInclusive = state.throughInclusive,
            period = state.selectedPeriod,
        )
    }
    val selectedThroughInclusive = remember(
        state.asOf,
        state.throughInclusive,
        state.selectedPeriod,
    ) {
        plannedExpensesPeriodEnd(
            asOf = state.asOf,
            throughInclusive = state.throughInclusive,
            period = state.selectedPeriod,
        )
    }
    val overdueItems = remember(visibleItems) {
        visibleItems.filter(PlannedExpense::overdue)
    }
    val upcomingItems = remember(visibleItems) {
        visibleItems.filterNot(PlannedExpense::overdue)
    }

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
        item(key = "period-tabs") {
            PlannedExpensesPeriodTabs(
                selectedPeriod = state.selectedPeriod,
                onPeriodSelected = {
                    onEvent(PlannedExpensesEvent.PeriodSelected(it))
                },
            )
        }

        item(key = "summary") {
            PlannedExpensesSummary(
                items = visibleItems,
                allItems = state.items,
                asOf = state.asOf,
                throughInclusive = selectedThroughInclusive,
            )
        }

        if (visibleItems.isEmpty()) {
            item(key = "empty-period") {
                EmptyPeriodContent(throughInclusive = selectedThroughInclusive)
            }
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
                PlannedExpenseItem(
                    expense = expense,
                    onManageRule = {
                        onEvent(PlannedExpensesEvent.EditRequested(expense.id))
                    },
                    onClose = {
                        onEvent(PlannedExpensesEvent.CloseRequested(expense.id))
                    },
                )
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
                PlannedExpenseItem(
                    expense = expense,
                    onManageRule = {
                        onEvent(PlannedExpensesEvent.EditRequested(expense.id))
                    },
                    onClose = {
                        onEvent(PlannedExpensesEvent.CloseRequested(expense.id))
                    },
                )
            }
        }
    }
}

@Composable
private fun PlannedExpensesPeriodTabs(
    selectedPeriod: PlannedExpensesPeriod,
    onPeriodSelected: (PlannedExpensesPeriod) -> Unit,
) {
    PrimaryTabRow(
        selectedTabIndex = selectedPeriod.ordinal,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        PlannedExpensesPeriod.entries.forEach { period ->
            Tab(
                selected = selectedPeriod == period,
                onClick = { onPeriodSelected(period) },
                text = { Text(stringResource(period.labelResource)) },
            )
        }
    }
}

@Composable
private fun PlannedExpensesSummary(
    items: List<PlannedExpense>,
    allItems: List<PlannedExpense>,
    asOf: LocalDate?,
    throughInclusive: LocalDate?,
) {
    val totals = remember(items, allItems) {
        remainingTotals(items).ifEmpty {
            allItems.distinctBy(PlannedExpense::currency).map { expense ->
                PlannedExpenseTotal(
                    amount = BigDecimal.ZERO,
                    currency = expense.currency,
                )
            }
        }
    }
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
                    items.size,
                    items.size,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (asOf != null && throughInclusive != null) {
                Text(
                    text = stringResource(
                        R.string.planned_expenses_period,
                        formatShortDate(asOf),
                        formatShortDate(throughInclusive),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                )
            }
        }
    }
}

@Composable
private fun EmptyPeriodContent(
    throughInclusive: LocalDate?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
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
private fun PlannedExpenseItem(
    expense: PlannedExpense,
    onManageRule: () -> Unit,
    onClose: () -> Unit,
) {
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

            if (expense.canBeClosed) {
                OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.planned_expenses_close_action))
                }
            }
            TextButton(
                onClick = onManageRule,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.planned_expenses_manage_rule))
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
        PlannedExpensesError.InvalidRequest -> R.string.planned_expenses_error_invalid_request
        PlannedExpensesError.NotFound -> R.string.planned_expenses_error_not_found
        PlannedExpensesError.Conflict -> R.string.planned_expenses_error_conflict
        PlannedExpensesError.Network -> R.string.planned_expenses_error_network
        PlannedExpensesError.InvalidResponse -> R.string.planned_expenses_error_invalid_response
        PlannedExpensesError.Unknown -> R.string.planned_expenses_error_generic
    }

private val PlannedExpensesError.closeMessageResource: Int
    @StringRes get() = when (this) {
        PlannedExpensesError.MissingApiToken,
        PlannedExpensesError.Unauthorized,
        -> R.string.planned_expenses_error_unauthorized

        PlannedExpensesError.Forbidden -> R.string.planned_expenses_close_error_forbidden
        PlannedExpensesError.InvalidRequest -> R.string.planned_expenses_close_error_invalid_request
        PlannedExpensesError.NotFound -> R.string.planned_expenses_close_error_not_found
        PlannedExpensesError.Conflict -> R.string.planned_expenses_close_error_conflict
        PlannedExpensesError.Network -> R.string.planned_expenses_error_network
        PlannedExpensesError.InvalidResponse -> R.string.planned_expenses_error_invalid_response
        PlannedExpensesError.Unknown -> R.string.planned_expenses_close_error_generic
    }

private val PlannedExpenseFrequency.labelResource: Int
    @StringRes get() = when (this) {
        PlannedExpenseFrequency.Monthly -> R.string.planned_expenses_frequency_monthly
        PlannedExpenseFrequency.Weekly -> R.string.planned_expenses_frequency_weekly
    }

private val PlannedExpensesPeriod.labelResource: Int
    @StringRes get() = when (this) {
        PlannedExpensesPeriod.EndOfMonth -> R.string.planned_expenses_period_end_of_month
        PlannedExpensesPeriod.EndOfYear -> R.string.planned_expenses_period_end_of_year
    }

private val PlannedExpenseFrequency.scheduleLabelResource: Int
    @StringRes get() = when (this) {
        PlannedExpenseFrequency.Monthly -> R.string.planned_expenses_month_day_label
        PlannedExpenseFrequency.Weekly -> R.string.planned_expenses_week_day_label
    }

private val PlannedExpenseFrequency.scheduleHintResource: Int
    @StringRes get() = when (this) {
        PlannedExpenseFrequency.Monthly -> R.string.planned_expenses_month_day_hint
        PlannedExpenseFrequency.Weekly -> R.string.planned_expenses_week_day_hint
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
