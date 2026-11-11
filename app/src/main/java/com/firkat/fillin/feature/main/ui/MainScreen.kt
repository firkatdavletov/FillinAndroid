package com.firkat.fillin.feature.main.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.firkat.fillin.feature.auth.domain.AuthUser
import com.firkat.fillin.feature.budgetprofile.ui.MainBudgetViewModelStore
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationType

@Composable
internal fun MainScreen(
    user: AuthUser?,
    authBusy: Boolean,
    onAccountClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
    budgetViewModelStoreOwner: MainBudgetViewModelStore,
    selectedDestination: AppDestination,
    onSelectDestination: (AppDestination) -> Unit,
    onCreateBudgetClick: () -> Unit,
    modifier: Modifier = Modifier,
    onMethodsClick: () -> Unit = onAccountClick,
    onCategoriesClick: () -> Unit = {},
    onFamilyAccessClick: () -> Unit = {},
    onCloudSyncClick: () -> Unit = {},
    onCreateOperationClick: () -> Unit = {},
    onOperationClick: (String, String) -> Unit = { _, _ -> },
    onForecastClick: () -> Unit = {},
    readOnly: Boolean = false,
    onPlannedOperationEditorClick: (PlannedOperationType, String?) -> Unit = { _, _ -> },
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
        authBusy = authBusy,
        onAccountClick = onAccountClick,
        onMethodsClick = onMethodsClick,
        snackbarHostState = snackbarHostState,
        user = user,
        budgetViewModelStoreOwner = budgetViewModelStoreOwner,
        onCategoriesClick = onCategoriesClick,
        onFamilyAccessClick = onFamilyAccessClick,
        onCloudSyncClick = onCloudSyncClick,
        onCreateBudgetClick = onCreateBudgetClick,
        onOperationClick = onOperationClick,
        onForecastClick = onForecastClick,
        onPlannedOperationEditorClick = onPlannedOperationEditorClick,
    )
}
