package com.firkat.fillin.feature.operationhistory.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.budgetprofile.ui.LocalBudgetCurrency
import com.firkat.fillin.feature.plannedoperations.domain.model.PlanningFrequency
import com.firkat.fillin.ui.theme.FillinTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun OperationPlanPickerRoute(
    id: String,
    onClose: () -> Unit,
    model: OperationPlanPickerViewModel = koinViewModel { parametersOf(id) }
) {
    val state by model.state.collectAsStateWithLifecycle()
    BackHandler { model.back() }
    LaunchedEffect(state.closed) { if (state.closed) onClose() }
    OperationPlanPickerScreen(state, model::group, model::select, model::back, model::save, model::load)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OperationPlanPickerScreen(
    state: PlanPickerState,
    onGroup: (String) -> Unit,
    onSelect: (com.firkat.fillin.feature.operationhistory.domain.repository.SuggestedPlan) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val locale = LocalConfiguration.current.locales[0]
    Scaffold(modifier.fillMaxSize().testTag("operation-plan-picker"), topBar = {
        TopAppBar(
            title = {
                Text(
                    stringResource(
                        if (state.group ==
                            null
                        ) {
                            R.string.plan_picker_rules
                        } else {
                            R.string.plan_picker_payments
                        }
                    )
                )
            },
            navigationIcon = {
                IconButton(onBack, enabled = !state.saving) {
                    Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigation_back))
                }
            }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.plan_picker_category, state.category))
                if (state.loading) CircularProgressIndicator()
                if (state.error) {
                    Text(stringResource(R.string.plan_link_error), color = MaterialTheme.colorScheme.error)
                    TextButton(onRetry, enabled = !state.saving) {
                        Text(stringResource(R.string.planned_operations_retry))
                    }
                }
                if (!state.loading && state.groups.isEmpty()) Text(stringResource(R.string.plan_link_empty))
                if (state.group == null) {
                    state.groups.forEach { group ->
                        OutlinedCard(onClick = {
                            onGroup(group.key)
                        }, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(group.name, style = MaterialTheme.typography.titleMedium)
                                Text(group.payments.first().categoryName)
                                Text(
                                    stringResource(
                                        when (group.frequency) {
                                            PlanningFrequency.Once -> R.string.income_frequency_once
                                            PlanningFrequency.Weekly -> R.string.income_frequency_weekly
                                            PlanningFrequency.Biweekly -> R.string.income_frequency_biweekly
                                            PlanningFrequency.Monthly -> R.string.income_frequency_monthly
                                        }
                                    )
                                )
                                Text(
                                    stringResource(
                                        R.string.plan_link_planned,
                                        group.payments.first().plannedAmount,
                                        LocalBudgetCurrency.current.symbol
                                    )
                                )
                            }
                        }
                    }
                } else {
                    val payments = state.groups.firstOrNull { it.key == state.group }?.payments.orEmpty()
                    if (!state.loading && payments.isEmpty()) Text(stringResource(R.string.plan_link_empty))
                    payments.forEach { plan ->
                        OutlinedCard(
                            onClick = { onSelect(plan) },
                            enabled = !state.saving,
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (state.selected ==
                                    plan
                                ) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(12.dp)) {
                                RadioButton(selected = state.selected == plan, onClick = null)
                                Column(Modifier.padding(start = 8.dp)) {
                                    Text(
                                        LocalDate.parse(
                                            plan.target.dueOn
                                        ).format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))
                                    )
                                    Text(
                                        stringResource(
                                            R.string.plan_link_planned,
                                            plan.plannedAmount,
                                            LocalBudgetCurrency.current.symbol
                                        )
                                    )
                                    Text(
                                        stringResource(
                                            R.string.plan_link_remaining,
                                            plan.remainingAmount,
                                            LocalBudgetCurrency.current.symbol
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (state.group !=
                null
            ) {
                Button(
                    onSave,
                    enabled = state.selected != null && !state.saving && !state.loading && !state.error,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    if (state.saving) {
                        CircularProgressIndicator(
                            Modifier.size(24.dp)
                        )
                    } else {
                        Text(stringResource(R.string.plan_link_confirm))
                    }
                }
            }
        }
    }
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun EmptyPickerPreview() {
    FillinTheme { OperationPlanPickerScreen(PlanPickerState(loading = false), {}, {}, {}, {}, {}) }
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun LoadingPickerPreview() {
    FillinTheme { OperationPlanPickerScreen(PlanPickerState(), {}, {}, {}, {}, {}) }
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ErrorPickerPreview() {
    FillinTheme { OperationPlanPickerScreen(PlanPickerState(loading = false, error = true), {}, {}, {}, {}, {}) }
}

private val pickerPreviewPlan = com.firkat.fillin.feature.operationhistory.domain.repository.SuggestedPlan(
    com.firkat.fillin.feature.operationhistory.domain.repository.PlanLinkTarget(
        "EXPENSE",
        ruleId = "health",
        dueOn = "2026-11-04"
    ),
    "health",
    "Здоровье",
    "585.00",
    "585.00",
    1
)

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun RulesPickerPreview() {
    FillinTheme {
        OperationPlanPickerScreen(
            PlanPickerState(
                groups = pickerGroups(listOf(pickerPreviewPlan), emptyList()),
                category = "Здоровье",
                loading = false
            ),
            {
            },
            {},
            {},
            {},
            {}
        )
    }
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun PaymentsPickerPreview() {
    FillinTheme {
        OperationPlanPickerScreen(
            PlanPickerState(
                groups = pickerGroups(listOf(pickerPreviewPlan), emptyList()),
                group = "EXPENSE:health",
                category = "Здоровье",
                loading = false
            ),
            {
            },
            {},
            {},
            {},
            {}
        )
    }
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Preview(widthDp = 320, fontScale = 2f)
@Composable
private fun SelectedPickerPreview() {
    FillinTheme {
        OperationPlanPickerScreen(
            PlanPickerState(
                groups = pickerGroups(listOf(pickerPreviewPlan), emptyList()),
                group = "EXPENSE:health",
                selected = pickerPreviewPlan,
                category = "Здоровье",
                loading = false
            ),
            {
            },
            {},
            {},
            {},
            {}
        )
    }
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun SavingPickerPreview() {
    FillinTheme {
        OperationPlanPickerScreen(
            PlanPickerState(
                groups = pickerGroups(listOf(pickerPreviewPlan), emptyList()),
                group = "EXPENSE:health",
                selected = pickerPreviewPlan,
                category = "Здоровье",
                loading = false,
                saving = true
            ),
            {
            },
            {},
            {},
            {},
            {}
        )
    }
}
