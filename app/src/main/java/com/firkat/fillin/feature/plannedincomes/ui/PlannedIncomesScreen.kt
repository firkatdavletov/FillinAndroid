package com.firkat.fillin.feature.plannedincomes.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncome
import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncomeStatus
import com.firkat.fillin.ui.component.FeatureScaffold
import com.firkat.fillin.ui.component.RefreshableContent
import com.firkat.fillin.ui.component.RefreshErrorContent
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
    FeatureScaffold(
        titleResource = R.string.planned_incomes_title,
        onNavigateBack = if (state.editor != null) {
            { viewModel.onEvent(PlannedIncomesEvent.EditorClosed) }
        } else {
            null
        },
        navigateBackEnabled = state.editor?.isSaving != true,
    ) {
        PlannedIncomesScreen(state = state, onEvent = viewModel::onEvent)
    }
}

@Composable
fun PlannedIncomesScreen(
    state: PlannedIncomesUiState,
    onEvent: (PlannedIncomesEvent) -> Unit,
) {
    val editor = state.editor
    if (editor != null) {
        BackHandler(enabled = !editor.isSaving) {
            onEvent(PlannedIncomesEvent.EditorClosed)
        }
        PlannedIncomeEditor(state = state, editor = editor, onEvent = onEvent)
        if (editor.isDeleteConfirmationVisible) {
            DeleteIncomeConfirmation(onEvent = onEvent)
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        RefreshableContent(
            isRefreshing = state.isRefreshing,
            onRefresh = { onEvent(PlannedIncomesEvent.Refresh) },
            hasScrollableContent = state.items.isNotEmpty(),
        ) {
            when {
                state.isLoading && state.items.isEmpty() -> LoadingContent()
                state.error != null && state.items.isEmpty() -> ErrorContent(
                    error = state.error,
                    onRetry = { onEvent(PlannedIncomesEvent.Retry) },
                    modifier = Modifier.fillMaxSize(),
                )
                state.items.isEmpty() -> EmptyContent()
                else -> PlannedIncomesList(state = state, onEvent = onEvent)
            }
        }

        ExtendedFloatingActionButton(
            onClick = { onEvent(PlannedIncomesEvent.CreateRequested) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Text(stringResource(R.string.planned_incomes_create_action))
        }
    }
}

@Composable
private fun PlannedIncomeEditor(
    state: PlannedIncomesUiState,
    editor: PlannedIncomeEditorUiState,
    onEvent: (PlannedIncomesEvent) -> Unit,
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
                    R.string.planned_incomes_editor_create_title
                } else {
                    R.string.planned_incomes_editor_edit_title
                },
            ),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = stringResource(R.string.planned_incomes_category_label),
            style = MaterialTheme.typography.labelLarge,
        )
        when {
            state.isCategoriesLoading -> CircularProgressIndicator()
            state.incomeCategories.isEmpty() && editor.isCreating -> Text(
                text = stringResource(R.string.planned_incomes_no_income_categories),
                color = MaterialTheme.colorScheme.error,
            )
            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val original = editor.original
                if (
                    original != null &&
                    state.incomeCategories.none { it.id == original.categoryId }
                ) {
                    FilterChip(
                        selected = editor.categoryId == original.categoryId,
                        onClick = {
                            onEvent(PlannedIncomesEvent.CategorySelected(original.categoryId))
                        },
                        enabled = !editor.isSaving,
                        label = { Text(original.categoryName) },
                    )
                }
                state.incomeCategories.forEach { category ->
                    FilterChip(
                        selected = editor.categoryId == category.id,
                        onClick = {
                            onEvent(PlannedIncomesEvent.CategorySelected(category.id))
                        },
                        enabled = !editor.isSaving,
                        label = { Text("${category.icon.emoji} ${category.name}") },
                    )
                }
            }
        }

        OutlinedTextField(
            value = editor.amount,
            onValueChange = { onEvent(PlannedIncomesEvent.AmountChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !editor.isSaving,
            label = { Text(stringResource(R.string.planned_incomes_amount_label)) },
            suffix = { Text("KZT") },
            supportingText = { Text(stringResource(R.string.planned_incomes_amount_hint)) },
            isError = editor.amount.isNotBlank() && editor.parsedAmount == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
        )

        OutlinedTextField(
            value = editor.dueOn,
            onValueChange = { onEvent(PlannedIncomesEvent.DueOnChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !editor.isSaving,
            label = { Text(stringResource(R.string.planned_incomes_due_on_label)) },
            supportingText = {
                Text(
                    stringResource(
                        if (editor.original?.overdue == true) {
                            R.string.planned_incomes_overdue_due_on_hint
                        } else {
                            R.string.planned_incomes_due_on_hint
                        },
                    ),
                )
            },
            isError = editor.dueOn.isNotBlank() && !editor.isDueOnAllowed,
            singleLine = true,
        )

        editor.error?.let { error ->
            Text(
                text = stringResource(
                    if (error == PlannedIncomesError.Unknown) {
                        R.string.planned_incomes_error_action
                    } else {
                        error.messageResource
                    },
                ),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Button(
            onClick = { onEvent(PlannedIncomesEvent.SaveRequested) },
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
                            R.string.planned_incomes_create_submit
                        } else {
                            R.string.planned_incomes_save_submit
                        },
                    ),
                )
            }
        }

        if (!editor.isCreating) {
            OutlinedButton(
                onClick = { onEvent(PlannedIncomesEvent.DeleteRequested) },
                modifier = Modifier.fillMaxWidth(),
                enabled = editor.canDelete,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(stringResource(R.string.planned_incomes_delete_action))
            }
        }
    }
}

@Composable
private fun DeleteIncomeConfirmation(onEvent: (PlannedIncomesEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(PlannedIncomesEvent.DeleteDismissed) },
        title = { Text(stringResource(R.string.planned_incomes_delete_title)) },
        text = { Text(stringResource(R.string.planned_incomes_delete_message)) },
        confirmButton = {
            TextButton(onClick = { onEvent(PlannedIncomesEvent.DeleteConfirmed) }) {
                Text(
                    text = stringResource(R.string.planned_incomes_delete_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(PlannedIncomesEvent.DeleteDismissed) }) {
                Text(stringResource(R.string.planned_incomes_delete_cancel))
            }
        },
    )
}

@Composable
private fun PlannedIncomesList(
    state: PlannedIncomesUiState,
    onEvent: (PlannedIncomesEvent) -> Unit,
) {
    val overdueItems = remember(state.items) { state.items.filter(PlannedIncome::overdue) }
    val upcomingItems = remember(state.items) { state.items.filterNot(PlannedIncome::overdue) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        state.error?.let {
            item(key = "refresh-error") {
                RefreshErrorContent(
                    messageResource = it.messageResource,
                    retryResource = R.string.planned_incomes_retry,
                    onRetry = { onEvent(PlannedIncomesEvent.Refresh) },
                )
            }
        }

        item(key = "summary") { PlannedIncomesSummary(state = state) }

        if (overdueItems.isNotEmpty()) {
            item(key = "overdue-title") {
                SectionTitle(
                    title = stringResource(R.string.planned_incomes_overdue_section),
                    count = overdueItems.size,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            items(overdueItems, key = PlannedIncome::id) { income ->
                PlannedIncomeItem(
                    income = income,
                    onEdit = { onEvent(PlannedIncomesEvent.EditRequested(income.id)) },
                )
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
                PlannedIncomeItem(
                    income = income,
                    onEdit = { onEvent(PlannedIncomesEvent.EditRequested(income.id)) },
                )
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
            modifier = Modifier.fillMaxWidth().padding(20.dp),
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
                    text = stringResource(R.string.planned_incomes_as_of, formatShortDate(asOf)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f),
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, count: Int, modifier: Modifier = Modifier) {
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
internal fun PlannedIncomeItem(income: PlannedIncome, onEdit: () -> Unit) {
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
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                        text = stringResource(
                            if (income.overdue) {
                                R.string.planned_incomes_overdue_due_date
                            } else {
                                R.string.planned_incomes_due_date
                            },
                            formatLongDate(income.dueOn),
                        ),
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
            TextButton(
                onClick = onEdit,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.planned_incomes_edit_action))
            }
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(horizontal = 32.dp),
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
        Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
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
        PlannedIncomesError.InvalidRequest -> R.string.planned_incomes_error_invalid_request
        PlannedIncomesError.NotFound -> R.string.planned_incomes_error_not_found
        PlannedIncomesError.Conflict -> R.string.planned_incomes_error_conflict
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
