package com.firkat.fillin.feature.plannedoperations.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
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
import com.firkat.fillin.ui.component.CategoryIconImage
import com.firkat.fillin.ui.component.RefreshErrorContent
import com.firkat.fillin.ui.component.SaldomiOutlinedButton
import com.firkat.fillin.ui.component.SaldomiPrimaryButton
import com.firkat.fillin.ui.format.formatMoney
import com.firkat.fillin.ui.icon.FillinIcons
import com.firkat.fillin.ui.theme.saldomiColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import org.koin.compose.viewmodel.koinViewModel

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
    readOnly: Boolean = false
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
        bottomBarOverlap = bottomBarOverlap
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
    bottomBarOverlap: Dp = 0.dp
) {
    val listState = rememberLazyListState()
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MainScreenHeader(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.planning_title)
            )
        }
        val expenseCount = state.rules.count { it.type == PlannedOperationType.Expense }
        PlanningTypeFilters(
            expenseCount = expenseCount,
            incomeCount = state.rules.size - expenseCount,
            selectedType = state.selectedType,
            isLoading = state.isLoading,
            onTypeSelected = { onEvent(PlannedOperationsEvent.TypeSelected(it)) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        val groups = state.groups
        val onCreate = if (state.selectedType == PlannedOperationType.Income) onCreateIncome else onCreateExpense

        if (state.isLoading && state.rules.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (groups.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(bottom = bottomBarOverlap)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(
                        state.error?.messageResource
                            ?: if (state.rules.isEmpty()) R.string.planning_empty else R.string.planning_filtered_empty
                    ),
                    textAlign = TextAlign.Center
                )
                if (state.error != null) {
                    TextButton(
                        { onEvent(PlannedOperationsEvent.Retry) }
                    ) { Text(stringResource(R.string.planned_operations_retry)) }
                } else if (canWrite) {
                    SaldomiPrimaryButton(
                        onClick = onCreate,
                        modifier = Modifier.fillMaxWidth().testTag("planned-create")
                    ) {
                        Text(stringResource(R.string.planned_operations_add))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .testTag("planned-operations-list"),
                state = listState,
                contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp + bottomBarOverlap)
            ) {
                state.error?.let { error ->
                    item("error") {
                        RefreshErrorContent(
                            error.messageResource,
                            R.string.planned_operations_retry
                        ) { onEvent(PlannedOperationsEvent.Retry) }
                    }
                }
                groups.forEach { group ->
                    item(key = "frequency-${group.frequency.name}", contentType = "frequency-header") {
                        Text(
                            text = stringResource(group.frequency.titleResource),
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { heading() }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(
                        items = group.rules,
                        key = PlanningRule::key,
                        contentType = { "planning-rule" }
                    ) { rule ->
                        PlanningRuleRow(
                            rule = rule,
                            nextDate = rule.nextDate(state.today),
                            onClick = { onEditRule(rule) },
                            enabled = canWrite
                        )
                    }
                }
            }
            if (canWrite) {
                SaldomiOutlinedButton(
                    onClick = onCreate,
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp, bottom = bottomBarOverlap)
                        .fillMaxWidth()
                        .testTag("planned-create"),
                    large = true
                ) {
                    Text(
                        text = stringResource(R.string.app_add_plan)
                    )
                }
            }
        }
    }
}

@Composable
internal fun PlanningRuleRow(
    rule: PlanningRule,
    nextDate: LocalDate,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val income = rule.type == PlannedOperationType.Income
    val amount = (if (income) "+" else "") + formatMoney(rule.amount, rule.currency)
    val amountStyle = MaterialTheme.typography.titleMedium.copy(
        color = if (income) MaterialTheme.saldomiColors.income else MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Bold
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .testTag("planning-rule-${rule.key}")
            .clickable(
                enabled = enabled,
                onClick = onClick
            )
    ) {
        val compact = maxWidth < 340.dp || LocalDensity.current.fontScale >= 1.5f
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 16.dp,
                    vertical = 12.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CategoryIconImage(
                iconCode = rule.iconCode,
                categoryName = rule.categoryName,
                modifier = Modifier.padding(4.dp)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    rule.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    rule.scheduleLabel(nextDate),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (compact) Text(amount, style = amountStyle)
            }
            if (!compact) Text(amount, modifier = Modifier.widthIn(max = 160.dp), style = amountStyle)
        }
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
    next: LocalDate
): String {
    val locale = LocalConfiguration.current.locales[0]
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
                next.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
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
