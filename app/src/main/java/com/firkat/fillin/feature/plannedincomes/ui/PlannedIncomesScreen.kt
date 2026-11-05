package com.firkat.fillin.feature.plannedincomes.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.categories.domain.model.Category
import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncome
import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncomeStatus
import com.firkat.fillin.ui.component.CategoryIconImage
import com.firkat.fillin.ui.component.FeatureScaffold
import com.firkat.fillin.ui.component.RefreshErrorContent
import com.firkat.fillin.ui.component.RefreshableContent
import com.firkat.fillin.ui.theme.FillinTheme
import java.math.BigDecimal
import com.firkat.fillin.ui.format.formatMoney
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
        PlannedIncomeEditor(
            categories = state.incomeCategories,
            isCategoriesLoading = state.isCategoriesLoading,
            editor = editor,
            onEvent = onEvent,
            modifier = Modifier.fillMaxSize(),
        )
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
internal fun PlannedIncomeEditor(
    categories: List<Category>,
    isCategoriesLoading: Boolean,
    editor: PlannedIncomeEditorUiState,
    onEvent: (PlannedIncomesEvent) -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
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
            isCategoriesLoading -> CircularProgressIndicator()
            categories.isEmpty() && editor.isCreating -> Text(
                text = stringResource(R.string.planned_incomes_no_income_categories),
                color = MaterialTheme.colorScheme.error,
            )
            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val original = editor.original
                val originalCategoryId = editor.originalRule?.categoryId ?: original?.categoryId
                val originalCategoryName = editor.originalRule?.categoryName ?: original?.categoryName.orEmpty()
                if (originalCategoryId != null && categories.none { it.id == originalCategoryId }) {
                    FilterChip(
                        selected = editor.categoryId == originalCategoryId,
                        onClick = {
                            onEvent(PlannedIncomesEvent.CategorySelected(originalCategoryId))
                        },
                        enabled = !editor.isSaving,
                        label = { Text(originalCategoryName) },
                    )
                }
                categories.forEach { category ->
                    FilterChip(
                        selected = editor.categoryId == category.id,
                        onClick = {
                            onEvent(PlannedIncomesEvent.CategorySelected(category.id))
                        },
                        enabled = !editor.isSaving,
                        label = { Text(category.name) },
                        leadingIcon = { CategoryIconImage(category.icon.code) },
                    )
                }
            }
        }

        OutlinedTextField(value = editor.name, onValueChange = { onEvent(PlannedIncomesEvent.NameChanged(it)) },
            label = { Text(stringResource(R.string.income_name_label)) }, enabled = !editor.isSaving,
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        com.firkat.fillin.feature.dailylimit.domain.IncomeFrequency.entries.forEach { frequency ->
            FilterChip(selected = editor.frequency == frequency,
                onClick = { onEvent(PlannedIncomesEvent.FrequencyChanged(frequency)) }, enabled = !editor.isSaving,
                label = { Text(stringResource(when (frequency) {
                    com.firkat.fillin.feature.dailylimit.domain.IncomeFrequency.ONCE -> R.string.income_frequency_once
                    com.firkat.fillin.feature.dailylimit.domain.IncomeFrequency.WEEKLY -> R.string.income_frequency_weekly
                    com.firkat.fillin.feature.dailylimit.domain.IncomeFrequency.BIWEEKLY -> R.string.income_frequency_biweekly
                    com.firkat.fillin.feature.dailylimit.domain.IncomeFrequency.MONTHLY -> R.string.income_frequency_monthly
                })) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Switch(checked = editor.active, onCheckedChange = { onEvent(PlannedIncomesEvent.ActiveChanged(it)) }, enabled = !editor.isSaving)
            Text(stringResource(R.string.income_active_label))
        }
        if (editor.original?.receivedAmount?.signum() == 1 && editor.original.remainingAmount.signum() > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Checkbox(checked = editor.confirmRemainder,
                    onCheckedChange = { onEvent(PlannedIncomesEvent.RemainderChanged(it)) }, enabled = !editor.isSaving)
                Text(stringResource(R.string.income_remainder_label))
            }
        }
        OutlinedTextField(
            value = editor.amount,
            onValueChange = { onEvent(PlannedIncomesEvent.AmountChanged(it)) },
            modifier = Modifier.fillMaxWidth().testTag("planned-editor-amount"),
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
            modifier = Modifier.fillMaxWidth().testTag("planned-editor-due-on"),
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
            modifier = Modifier.fillMaxWidth().testTag("planned-editor-save"),
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
                modifier = Modifier.fillMaxWidth().testTag("planned-editor-delete"),
                enabled = editor.canDelete,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(stringResource(R.string.planned_incomes_delete_action))
            }
        }
        onClose?.let { close ->
            TextButton(
                onClick = close,
                enabled = !editor.isSaving,
                modifier = Modifier.fillMaxWidth().testTag("planned-editor-close"),
            ) {
                Text(stringResource(R.string.edit_operation_close))
            }
        }
    }
}

@Composable
internal fun DeleteIncomeConfirmation(
    onEvent: (PlannedIncomesEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier,
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

    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onEdit
            ),
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
                    text = income.name,
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
                text = "+" + formatMoney(income.remainingAmount, income.currency),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
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

@Preview(name = "Income Recurring", widthDp = 360, heightDp = 900)
@Preview(name = "Income Recurring dark", widthDp = 360, heightDp = 900, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun IncomeRecurringEditorPreview() {
    FillinTheme { PlannedIncomeEditor(emptyList(), false,
        PlannedIncomeEditorUiState(today = LocalDate.of(2026,11,1), name = "Зарплата", amount = "300000", dueOn = "2026-11-15", frequency = com.firkat.fillin.feature.dailylimit.domain.IncomeFrequency.MONTHLY), {}) }
}

@Preview(name = "Income Disabled", widthDp = 360, heightDp = 900)
@Preview(name = "Income Disabled dark", widthDp = 360, heightDp = 900, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun IncomeDisabledEditorPreview() {
    FillinTheme { PlannedIncomeEditor(emptyList(), false,
        PlannedIncomeEditorUiState(today = LocalDate.of(2026,11,1), name = "Зарплата", amount = "300000", dueOn = "2026-11-15", active = false), {}) }
}

@Preview(name = "Income Saving", widthDp = 360, heightDp = 900)
@Preview(name = "Income Saving dark", widthDp = 360, heightDp = 900, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun IncomeSavingEditorPreview() {
    FillinTheme { PlannedIncomeEditor(emptyList(), false,
        PlannedIncomeEditorUiState(today = LocalDate.of(2026,11,1), name = "Зарплата", amount = "300000", dueOn = "2026-11-15", isSaving = true), {}) }
}
