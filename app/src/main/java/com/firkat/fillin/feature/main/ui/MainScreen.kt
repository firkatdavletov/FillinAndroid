package com.firkat.fillin.feature.main.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.firkat.fillin.feature.budgetprofile.ui.MainBudgetViewModelStore
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationType

@Composable
internal fun MainScreen(
    onAccountClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
    budgetViewModelStoreOwner: MainBudgetViewModelStore,
    selectedDestination: AppDestination,
    onSelectDestination: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onCreateOperationClick: () -> Unit = {},
    onOperationClick: (String, String) -> Unit = { _, _ -> },
    onForecastClick: () -> Unit = {},
    readOnly: Boolean = false,
    onPlannedOperationEditorClick: (PlannedOperationType, String?) -> Unit = { _, _ -> }
) {
    MainContent(
        modifier = modifier,
        readOnly = readOnly,
        selectedDestination = selectedDestination,
        onSelectDestination = {
            if (it == AppDestination.AddOperation) {
                onCreateOperationClick()
            } else {
                onSelectDestination(it)
            }
        },
        onAccountClick = onAccountClick,
        snackbarHostState = snackbarHostState,
        budgetViewModelStoreOwner = budgetViewModelStoreOwner,
        onOperationClick = onOperationClick,
        onForecastClick = onForecastClick,
        onBack = onBack,
        onPlannedOperationEditorClick = onPlannedOperationEditorClick
    )
}
