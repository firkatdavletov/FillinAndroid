package com.firkat.fillin.feature.plannedoperations.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.dashboard.ui.MainScreenHeader
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationType
import com.firkat.fillin.feature.plannedoperations.domain.model.PlanningFrequency
import com.firkat.fillin.feature.plannedoperations.domain.model.PlanningRule
import com.firkat.fillin.feature.plannedoperations.domain.repository.PlannedOperationsException
import com.firkat.fillin.feature.sync.ui.BudgetSyncViewModel
import com.firkat.fillin.ui.component.RefreshErrorContent
import com.firkat.fillin.ui.component.RefreshableContent
import com.firkat.fillin.ui.component.SaldomiButton
import com.firkat.fillin.ui.component.SaldomiOutlinedButton
import com.firkat.fillin.ui.component.SaldomiTextButton
import com.firkat.fillin.ui.format.formatMoney
import com.firkat.fillin.ui.component.CategoryIconImage
import com.firkat.fillin.ui.theme.saldomiColors
import org.koin.compose.viewmodel.koinViewModel
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
    bottomBarOverlap: Dp = 0.dp,
    readOnly: Boolean = false,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sync by syncViewModel.context.collectAsStateWithLifecycle()
    PlannedOperationsScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onCreateExpense = { onOpenEditor(PlannedOperationType.Expense) },
        onCreateIncome = { onOpenEditor(PlannedOperationType.Income) },
        onEditRule = onEditRule,
        canWrite = sync.canWrite && !readOnly,
        bottomBarOverlap = bottomBarOverlap,
    )
}

@Composable
fun PlannedOperationsScreen(
    state: PlannedOperationsUiState,
    onEvent: (PlannedOperationsEvent) -> Unit,
    onCreateExpense: () -> Unit,
    onCreateIncome: () -> Unit,
    onEditRule: (PlanningRule) -> Unit,
    modifier: Modifier = Modifier,
    canWrite: Boolean = true,
    bottomBarOverlap: Dp = 0.dp,
) {
    val listState = rememberLazyListState()
    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        MainScreenHeader(
            modifier =
                Modifier
                    .padding(horizontal = 16.dp)
                    .statusBarsPadding(),
            title = stringResource(R.string.planning_title),
        )
        PlanningTypeFilters(state.selectedType, { onEvent(PlannedOperationsEvent.TypeSelected(it)) })
        if (canWrite) {
            SaldomiOutlinedButton(
                modifier = Modifier.padding(start = 16.dp),
                onClick = onCreateExpense,
            ) { Text(stringResource(R.string.app_add_plan)) }
        }
        val groups = state.groups

        if (state.isLoading && state.rules.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (groups.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(
                        state.error?.messageResource
                            ?: if (state.rules.isEmpty()) R.string.planning_empty else R.string.planning_filtered_empty,
                    ),
                )
                if (state.error !=
                    null
                ) {
                    TextButton(
                        { onEvent(PlannedOperationsEvent.Retry) },
                    ) { Text(stringResource(R.string.planned_operations_retry)) }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("planned-operations-list"),
                state = listState,
                contentPadding =
                    PaddingValues(
                        start = 16.dp,
                        top = 16.dp,
                        end = 16.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.error?.let { error ->
                    item("error") {
                        RefreshErrorContent(
                            error.messageResource,
                            R.string.planned_operations_retry,
                        ) { onEvent(PlannedOperationsEvent.Retry) }
                    }
                }
                groups.forEach { group ->
                    item(group.frequency.name) {
                        PlanningGroupCard(
                            group.frequency,
                            group.rules,
                            group.frequency !in state.collapsed,
                            onToggle = { onEvent(PlannedOperationsEvent.GroupToggled(group.frequency)) },
                            onRuleClick = onEditRule,
                            enabled = canWrite,
                            today = state.today,
                        )
                    }
                }
                item {
                    Spacer(Modifier.height(184.dp))
                }
            }
        }
    }
}

@Composable
internal fun PlanningTypeFilters(
    selected: PlannedOperationType?,
    onSelected: (PlannedOperationType?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(null, PlannedOperationType.Expense, PlannedOperationType.Income).forEach { type ->
            FilterChip(
                selected == type,
                { onSelected(type) },
                label = {
                    Text(
                        stringResource(
                            when (type) {
                                null -> R.string.planning_all
                                PlannedOperationType.Expense -> R.string.planned_operations_expenses
                                PlannedOperationType.Income -> R.string.planned_operations_incomes
                            },
                        ),
                        Modifier.padding(horizontal = 8.dp),
                    )
                },
                shape = RoundedCornerShape(50),
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                modifier = Modifier.testTag("planning-filter-${type?.name ?: "All"}"),
            )
        }
    }
}

@Composable
internal fun PlanningGroupCard(
    frequency: PlanningFrequency,
    rules: List<PlanningRule>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRuleClick: (PlanningRule) -> Unit,
    enabled: Boolean,
    today: java.time.LocalDate,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        ListItem(
            headlineContent = { Text(stringResource(frequency.titleResource), fontWeight = FontWeight.Bold) },
            supportingContent = { Text(pluralStringResource(R.plurals.planning_rule_count, rules.size, rules.size)) },
            leadingContent = { Icon(painterResource(R.drawable.ic_calendar), null) },
            trailingContent = {
                IconButton(onToggle, Modifier.testTag("planning-toggle-${frequency.name}")) {
                    Icon(
                        painterResource(R.drawable.ic_arrow_drop_down),
                        stringResource(if (expanded) R.string.planning_collapse else R.string.planning_expand),
                        Modifier.rotate(if (expanded) 180f else 0f),
                    )
                }
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        )
        if (expanded) {
            rules.forEachIndexed { index, rule ->
                PlanningRuleRow(rule, rule.nextDate(today), { onRuleClick(rule) }, enabled)
                if (index !=
                    rules.lastIndex
                ) {
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
internal fun PlanningRuleRow(
    rule: PlanningRule,
    nextDate: java.time.LocalDate,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val income = rule.type == PlannedOperationType.Income
    Surface(
        onClick,
        modifier.fillMaxWidth().testTag("planning-rule-${rule.key}"),
        enabled = enabled,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        BoxWithConstraints {
            val compact = maxWidth < 340.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.5f
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (income) MaterialTheme.saldomiColors.incomeContainer else MaterialTheme.saldomiColors.expenseContainer,
                ) {
                    CategoryIconImage(
                        iconCode = rule.iconCode,
                        categoryName = rule.categoryName,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                val amount = (if (income) "+" else "") + formatMoney(rule.amount, rule.currency)
                val amountColor = if (income) MaterialTheme.saldomiColors.income else MaterialTheme.colorScheme.onSurface
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(rule.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        rule.scheduleLabel(nextDate),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (compact) {
                        Text(
                            amount,
                            style = MaterialTheme.typography.titleMedium,
                            color = amountColor,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                if (!compact) {
                    Text(
                        amount,
                        modifier = Modifier.widthIn(max = 160.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = amountColor,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
internal fun PlanningAddButton(
    onExpense: () -> Unit,
    onIncome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SaldomiButton(onExpense, modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("planned-create")) {
        Text(stringResource(R.string.planning_add))
    }
}

internal val PlanningFrequency.titleResource: Int get() =
    when (this) {
        PlanningFrequency.Once -> R.string.planning_once
        PlanningFrequency.Weekly -> R.string.planning_weekly
        PlanningFrequency.Biweekly -> R.string.planning_biweekly
        PlanningFrequency.Monthly -> R.string.planning_monthly
    }

@Composable
private fun PlanningRule.scheduleLabel(
    next: java.time.LocalDate,
): String {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val date = next.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))
    return when (frequency) {
        PlanningFrequency.Once -> {
            stringResource(R.string.planning_once_schedule, date)
        }

        PlanningFrequency.Monthly -> {
            stringResource(R.string.planning_monthly_schedule, scheduleValue ?: startsOn.dayOfMonth)
        }

        PlanningFrequency.Weekly -> {
            stringResource(
                R.string.planning_weekly_schedule,
                next.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, locale),
            )
        }

        PlanningFrequency.Biweekly -> {
            stringResource(R.string.planning_biweekly_schedule, date)
        }
    }
}

private val PlannedOperationsException.messageResource: Int get() =
    when (this) {
        PlannedOperationsException.MissingApiToken -> R.string.planned_operations_error_token
        PlannedOperationsException.Unauthorized -> R.string.planned_operations_error_unauthorized
        PlannedOperationsException.Forbidden -> R.string.planned_operations_error_forbidden
        PlannedOperationsException.InvalidRequest -> R.string.planned_operations_error_request
        PlannedOperationsException.Network -> R.string.planned_operations_error_network
        PlannedOperationsException.InvalidResponse -> R.string.planned_operations_error_response
        PlannedOperationsException.Unknown -> R.string.planned_operations_error_unknown
    }
