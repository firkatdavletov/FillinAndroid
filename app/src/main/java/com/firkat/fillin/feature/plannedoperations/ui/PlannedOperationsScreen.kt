package com.firkat.fillin.feature.plannedoperations.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.plannedexpenses.ui.*
import com.firkat.fillin.feature.plannedincomes.ui.*
import com.firkat.fillin.feature.plannedoperations.domain.model.*
import com.firkat.fillin.feature.plannedoperations.domain.repository.PlannedOperationsException
import com.firkat.fillin.feature.sync.ui.BudgetSyncViewModel
import com.firkat.fillin.ui.component.*
import com.firkat.fillin.ui.format.formatMoney
import com.firkat.fillin.ui.icon.FillinIcons
import com.firkat.fillin.ui.theme.saldomiColors
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import java.time.format.DateTimeFormatter

@Composable
internal fun planningRulesViewModel(): PlannedOperationsViewModel {
    val profile: com.firkat.fillin.feature.budgetprofile.domain.BudgetProfile = org.koin.compose.koinInject()
    return koinViewModel(key = "planning-rules-${profile.id}")
}

@Composable
fun PlannedOperationsRoute(
    onOpenEditor: (PlannedOperationType) -> Unit,
    onEditRule: (PlanningRule) -> Unit = {},
    viewModel: PlannedOperationsViewModel = planningRulesViewModel(),
    syncViewModel: BudgetSyncViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sync by syncViewModel.context.collectAsStateWithLifecycle()
    PlannedOperationsScreen(state, viewModel::onEvent,
        onCreateExpense = { onOpenEditor(PlannedOperationType.Expense) },
        onCreateIncome = { onOpenEditor(PlannedOperationType.Income) },
        onEditRule = onEditRule, canWrite = sync.canWrite)
}

@Composable
fun PlannedOperationsScreen(
    state: PlannedOperationsUiState,
    onEvent: (PlannedOperationsEvent) -> Unit,
    onCreateExpense: () -> Unit,
    onCreateIncome: () -> Unit,
    onEditRule: (PlanningRule) -> Unit,
    canWrite: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.planning_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.planning_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PlanningTypeFilters(state.selectedType, { onEvent(PlannedOperationsEvent.TypeSelected(it)) })
        val groups = state.groups
        Box(Modifier.weight(1f)) {
            RefreshableContent(state.isRefreshing, { onEvent(PlannedOperationsEvent.Refresh) }, groups.isNotEmpty()) {
                if (state.isLoading && state.rules.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else if (groups.isEmpty()) {
                    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(state.error?.messageResource ?: if (state.rules.isEmpty()) R.string.planning_empty else R.string.planning_filtered_empty))
                        if (state.error != null) TextButton({ onEvent(PlannedOperationsEvent.Retry) }) { Text(stringResource(R.string.planned_operations_retry)) }
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize().testTag("planned-operations-list"), state = listState,
                        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.error?.let { error -> item("error") {
                            RefreshErrorContent(error.messageResource, R.string.planned_operations_retry) { onEvent(PlannedOperationsEvent.Retry) }
                        } }
                        groups.forEach { group ->
                            item(group.frequency.name) {
                                PlanningGroupCard(group.frequency, group.rules, group.frequency !in state.collapsed,
                                    onToggle = { onEvent(PlannedOperationsEvent.GroupToggled(group.frequency)) },
                                    onRuleClick = onEditRule, enabled = canWrite, today = state.today)
                            }
                        }
                    }
                }
            }
        }
        if (canWrite) PlanningAddButton(onCreateExpense, onCreateIncome, Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
    }
}

@Composable
internal fun PlanningTypeFilters(selected: PlannedOperationType?, onSelected: (PlannedOperationType?) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(null, PlannedOperationType.Expense, PlannedOperationType.Income).forEach { type ->
            FilterChip(selected == type, { onSelected(type) },
                label = { Text(stringResource(when (type) {
                    null -> R.string.planning_all
                    PlannedOperationType.Expense -> R.string.planned_operations_expenses
                    PlannedOperationType.Income -> R.string.planned_operations_incomes
                }), Modifier.padding(horizontal = 8.dp)) },
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary), modifier = Modifier.testTag("planning-filter-${type?.name ?: "All"}"))
        }
    }
}

@Composable
internal fun PlanningGroupCard(
    frequency: PlanningFrequency, rules: List<PlanningRule>, expanded: Boolean,
    onToggle: () -> Unit, onRuleClick: (PlanningRule) -> Unit, enabled: Boolean,
    today: java.time.LocalDate, modifier: Modifier = Modifier,
) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        ListItem(
            headlineContent = { Text(stringResource(frequency.titleResource), fontWeight = FontWeight.Bold) },
            supportingContent = { Text(pluralStringResource(R.plurals.planning_rule_count, rules.size, rules.size)) },
            leadingContent = { Icon(painterResource(R.drawable.ic_calendar), null) },
            trailingContent = {
                IconButton(onToggle, Modifier.testTag("planning-toggle-${frequency.name}")) {
                    Icon(painterResource(R.drawable.ic_arrow_drop_down), stringResource(if (expanded) R.string.planning_collapse else R.string.planning_expand), Modifier.rotate(if (expanded) 180f else 0f))
                }
            }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer))
        if (expanded) rules.forEachIndexed { index, rule ->
            PlanningRuleRow(rule, rule.nextDate(today), { onRuleClick(rule) }, enabled)
            if (index != rules.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
internal fun PlanningRuleRow(rule: PlanningRule, nextDate: java.time.LocalDate, onClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    val income = rule.type == PlannedOperationType.Income
    Surface(onClick, modifier.fillMaxWidth().testTag("planning-rule-${rule.key}"), enabled = enabled, color = MaterialTheme.colorScheme.surfaceContainer) {
        BoxWithConstraints {
            val compact = maxWidth < 340.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.5f
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(16.dp), color = if (income) MaterialTheme.saldomiColors.incomeContainer else MaterialTheme.saldomiColors.expenseContainer) {
                    Icon(FillinIcons.category(rule.iconCode), null, Modifier.padding(12.dp).size(24.dp), tint = if (income) MaterialTheme.saldomiColors.onIncomeContainer else MaterialTheme.saldomiColors.onExpenseContainer)
                }
                val amount = (if (income) "+" else "") + formatMoney(rule.amount, rule.currency)
                val amountColor = if (income) MaterialTheme.saldomiColors.income else MaterialTheme.colorScheme.onSurface
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(rule.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(rule.scheduleLabel(nextDate), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (compact) Text(amount, style = MaterialTheme.typography.titleMedium, color = amountColor, fontWeight = FontWeight.Bold)
                }
                if (!compact) Text(amount, modifier = Modifier.widthIn(max = 160.dp), style = MaterialTheme.typography.titleMedium,
                    color = amountColor, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun PlanningAddButton(onExpense: () -> Unit, onIncome: () -> Unit, modifier: Modifier = Modifier) {
    Button(onExpense, modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("planned-create"), shape = RoundedCornerShape(24.dp)) {
        Icon(painterResource(R.drawable.ic_add_24), null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.planning_add))
    }
}

internal val PlanningFrequency.titleResource: Int get() = when (this) {
    PlanningFrequency.Once -> R.string.planning_once
    PlanningFrequency.Weekly -> R.string.planning_weekly
    PlanningFrequency.Biweekly -> R.string.planning_biweekly
    PlanningFrequency.Monthly -> R.string.planning_monthly
}

@Composable
private fun PlanningRule.scheduleLabel(next: java.time.LocalDate): String {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val date = next.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))
    return when (frequency) {
        PlanningFrequency.Once -> stringResource(R.string.planning_once_schedule, date)
        PlanningFrequency.Monthly -> stringResource(R.string.planning_monthly_schedule, scheduleValue ?: startsOn.dayOfMonth)
        PlanningFrequency.Weekly -> stringResource(R.string.planning_weekly_schedule, next.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, locale))
        PlanningFrequency.Biweekly -> stringResource(R.string.planning_biweekly_schedule, date)
    }
}

private val PlannedOperationsException.messageResource: Int get() = when (this) {
    PlannedOperationsException.MissingApiToken -> R.string.planned_operations_error_token
    PlannedOperationsException.Unauthorized -> R.string.planned_operations_error_unauthorized
    PlannedOperationsException.Forbidden -> R.string.planned_operations_error_forbidden
    PlannedOperationsException.InvalidRequest -> R.string.planned_operations_error_request
    PlannedOperationsException.Network -> R.string.planned_operations_error_network
    PlannedOperationsException.InvalidResponse -> R.string.planned_operations_error_response
    PlannedOperationsException.Unknown -> R.string.planned_operations_error_unknown
}
