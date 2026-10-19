package com.firkat.fillin.feature.plannedoperations.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpense
import com.firkat.fillin.feature.plannedexpenses.ui.CloseExpenseConfirmation
import com.firkat.fillin.feature.plannedexpenses.ui.PlannedExpenseItem
import com.firkat.fillin.feature.plannedexpenses.ui.PlannedExpensesEvent
import com.firkat.fillin.feature.plannedexpenses.ui.PlannedExpensesScreen
import com.firkat.fillin.feature.plannedexpenses.ui.PlannedExpensesViewModel
import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncome
import com.firkat.fillin.feature.plannedincomes.ui.PlannedIncomeItem
import com.firkat.fillin.feature.plannedincomes.ui.PlannedIncomesEvent
import com.firkat.fillin.feature.plannedincomes.ui.PlannedIncomesScreen
import com.firkat.fillin.feature.plannedincomes.ui.PlannedIncomesViewModel
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperation
import com.firkat.fillin.feature.plannedoperations.domain.repository.PlannedOperationsException
import com.firkat.fillin.feature.sync.ui.BudgetSyncPanel
import com.firkat.fillin.feature.sync.ui.BudgetSyncViewModel
import com.firkat.fillin.ui.component.FeatureScaffold
import com.firkat.fillin.ui.component.RefreshErrorContent
import com.firkat.fillin.ui.component.RefreshableContent
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun PlannedOperationsRoute(
    viewModel: PlannedOperationsViewModel = koinViewModel(),
    syncViewModel: BudgetSyncViewModel = koinViewModel(),
    expenseEditor: PlannedExpensesViewModel =
        koinViewModel(
            key = "planned-expense-editor",
            parameters = { parametersOf(true) },
        ),
    incomeEditor: PlannedIncomesViewModel =
        koinViewModel(
            key = "planned-income-editor",
            parameters = { parametersOf(true) },
        ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sync by syncViewModel.context.collectAsStateWithLifecycle()
    val expenseState by expenseEditor.uiState.collectAsStateWithLifecycle()
    val incomeState by incomeEditor.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(expenseState.changeVersion, incomeState.changeVersion) {
        if (expenseState.changeVersion > 0 || incomeState.changeVersion > 0) viewModel.reloadAfterMutation()
    }
    val closeEditor: (() -> Unit)? =
        when {
            expenseState.editor != null -> ({ expenseEditor.onEvent(PlannedExpensesEvent.EditorClosed) })
            incomeState.editor != null -> ({ incomeEditor.onEvent(PlannedIncomesEvent.EditorClosed) })
            else -> null
        }
    FeatureScaffold(
        titleResource = R.string.planned_operations_title,
        onNavigateBack = closeEditor,
        navigateBackEnabled = expenseState.editor?.isSaving != true && incomeState.editor?.isSaving != true,
    ) {
        Column {
            BudgetSyncPanel(syncViewModel)
            when {
                expenseState.editor != null -> {
                    PlannedExpensesScreen(expenseState, expenseEditor::onEvent)
                }

                incomeState.editor != null -> {
                    PlannedIncomesScreen(incomeState, incomeEditor::onEvent)
                }

                else -> {
                    PlannedOperationsScreen(
                        state = state,
                        onEvent = viewModel::onEvent,
                        onCreateExpense = { expenseEditor.onEvent(PlannedExpensesEvent.CreateRequested) },
                        onCreateIncome = { incomeEditor.onEvent(PlannedIncomesEvent.CreateRequested) },
                        onEditExpense = expenseEditor::edit,
                        onEditIncome = incomeEditor::edit,
                        onCloseExpense = expenseEditor::requestClose,
                        canWrite = sync.canWrite,
                    )
                }
            }
        }
        expenseState.items.firstOrNull { it.id == expenseState.closeConfirmationExpenseId }?.let { expense ->
            CloseExpenseConfirmation(
                expense = expense,
                isClosing = expenseState.isClosingExpense,
                error = expenseState.closeError,
                onEvent = expenseEditor::onEvent,
            )
        }
    }
}

@Composable
fun PlannedOperationsScreen(
    state: PlannedOperationsUiState,
    onEvent: (PlannedOperationsEvent) -> Unit,
    onCreateExpense: () -> Unit,
    onCreateIncome: () -> Unit,
    onEditExpense: (PlannedExpense) -> Unit,
    onEditIncome: (PlannedIncome) -> Unit,
    onCloseExpense: (PlannedExpense) -> Unit,
    canWrite: Boolean = true,
) {
    Column(Modifier.fillMaxSize()) {
        PlannedOperationsFilters(state, onEvent)
        HorizontalDivider()
        Box(Modifier.weight(1f)) {
            val items = state.operations?.items.orEmpty()
            RefreshableContent(
                isRefreshing = state.isRefreshing,
                onRefresh = { onEvent(PlannedOperationsEvent.Refresh) },
                hasScrollableContent = items.isNotEmpty(),
            ) {
                when {
                    state.isLoading && items.isEmpty() -> {
                        Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    items.isEmpty() -> {
                        Column(
                            Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(stringResource(state.error?.messageResource ?: R.string.planned_operations_empty))
                            if (state.error != null) {
                                TextButton(onClick = { onEvent(PlannedOperationsEvent.Retry) }) {
                                    Text(stringResource(R.string.planned_operations_retry))
                                }
                            }
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().testTag("planned-operations-list"),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            state.error?.let { error ->
                                item(key = "error") {
                                    RefreshErrorContent(error.messageResource, R.string.planned_operations_retry) {
                                        onEvent(PlannedOperationsEvent.Retry)
                                    }
                                }
                            }
                            items(items, key = { it.key }) { item ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text =
                                            stringResource(
                                                if (item is PlannedOperation.Expense) {
                                                    R.string.planned_operations_expense
                                                } else {
                                                    R.string.planned_operations_income
                                                },
                                            ),
                                        style = MaterialTheme.typography.labelMedium,
                                        color =
                                            if (item is PlannedOperation.Expense) {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            },
                                    )
                                    when (item) {
                                        is PlannedOperation.Expense -> {
                                            PlannedExpenseItem(
                                                expense = item.expense,
                                                onManageRule = {
                                                    if (canWrite && !state.isLoading &&
                                                        !state.isRefreshing
                                                    ) {
                                                        onEditExpense(item.expense)
                                                    }
                                                },
                                                onClose = {
                                                    if (canWrite && !state.isLoading &&
                                                        !state.isRefreshing
                                                    ) {
                                                        onCloseExpense(item.expense)
                                                    }
                                                },
                                            )
                                        }

                                        is PlannedOperation.Income -> {
                                            PlannedIncomeItem(
                                                income = item.income,
                                                onEdit = {
                                                    if (canWrite && !state.isLoading &&
                                                        !state.isRefreshing
                                                    ) {
                                                        onEditIncome(item.income)
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                var showCreateMenu by rememberSaveable { mutableStateOf(false) }
                if (canWrite) {
                    ExtendedFloatingActionButton(
                        onClick = { showCreateMenu = true },
                        modifier = Modifier.testTag("planned-create"),
                    ) { Text(stringResource(R.string.planned_operations_add)) }
                }
                DropdownMenu(expanded = showCreateMenu, onDismissRequest = { showCreateMenu = false }) {
                    DropdownMenuItem(
                        modifier = Modifier.testTag("planned-create-expense"),
                        text = { Text(stringResource(R.string.planned_operations_expense)) },
                        onClick = {
                            showCreateMenu = false
                            onCreateExpense()
                        },
                    )
                    DropdownMenuItem(
                        modifier = Modifier.testTag("planned-create-income"),
                        text = { Text(stringResource(R.string.planned_operations_income)) },
                        onClick = {
                            showCreateMenu = false
                            onCreateIncome()
                        },
                    )
                }
            }
        }
    }
    if (state.isPeriodPickerVisible) PlannedOperationsPeriodPicker(state, onEvent)
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
