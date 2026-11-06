package com.firkat.fillin.feature.main.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.firkat.fillin.feature.auth.domain.AuthUser
import com.firkat.fillin.feature.budgetprofile.ui.ActiveBudgetContent
import com.firkat.fillin.feature.budgetprofile.ui.MainBudgetViewModelStore
import com.firkat.fillin.feature.dashboard.ui.DashboardRoute
import com.firkat.fillin.feature.operationhistory.ui.OperationHistoryRoute
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationType
import com.firkat.fillin.feature.plannedoperations.ui.PlannedOperationsEvent
import com.firkat.fillin.feature.plannedoperations.ui.PlannedOperationsRoute
import com.firkat.fillin.feature.plannedoperations.ui.planningRulesViewModel
import com.firkat.fillin.feature.profile.ui.ProfileRoute
import com.firkat.fillin.ui.component.SaldomiSnackbarHost

@Composable
private fun MainContent(
    modifier: Modifier = Modifier,
    selectedDestination: AppDestination,
    onSelectDestination: (AppDestination) -> Unit,
    authBusy: Boolean,
    onAccountClick: () -> Unit,
    onMethodsClick: () -> Unit = onAccountClick,
    snackbarHostState: SnackbarHostState,
    user: AuthUser?,
    budgetViewModelStoreOwner: MainBudgetViewModelStore,
    onCategoriesClick: () -> Unit,
    onFamilyAccessClick: () -> Unit,
    onCloudSyncClick: () -> Unit,
    onOperationClick: (String, String) -> Unit,
    onForecastClick: () -> Unit,
    onPlannedOperationEditorClick: (PlannedOperationType, String?) -> Unit,
) {
    Scaffold(
        modifier = modifier.testTag("main_screen"),
        snackbarHost = {
            SaldomiSnackbarHost(snackbarHostState)
        },
        bottomBar = {
            MainBottomNavigation(
                selectedDestination = selectedDestination,
                onSelectDestination = onSelectDestination,
            )
        },
    ) { innerPadding ->
        val bottomBarOverlap = innerPadding.calculateBottomPadding()
        val provider =
            entryProvider<NavKey> {
                entry(AppDestination.Dashboard) {
                    ActiveBudgetContent(viewModelStoreOwner = budgetViewModelStoreOwner) {
                        val plansViewModel = planningRulesViewModel()
                        DashboardRoute(
                            modifier =
                                Modifier
                                    .fillMaxSize(),
                            bottomBarOverlap = bottomBarOverlap,
                            onForecastClick = onForecastClick,
                            onPaymentsClick = { _, _ ->
                                plansViewModel.onEvent(
                                    PlannedOperationsEvent.TypeSelected(PlannedOperationType.Expense),
                                )
                                onSelectDestination(AppDestination.Planned)
                            },
                        )
                    }
                }
                entry(AppDestination.History) {
                    ActiveBudgetContent(viewModelStoreOwner = budgetViewModelStoreOwner) {
                        OperationHistoryRoute(
                            snackbarHostState = snackbarHostState,
                            onOperationClick = onOperationClick,
                            bottomBarOverlap = bottomBarOverlap,
                        )
                    }
                }
                entry(AppDestination.Planned) {
                    ActiveBudgetContent(viewModelStoreOwner = budgetViewModelStoreOwner) {
                        PlannedOperationsRoute(
                            onOpenEditor = { onPlannedOperationEditorClick(it, null) },
                            onEditRule = { onPlannedOperationEditorClick(it.type, it.id) },
                            bottomBarOverlap = bottomBarOverlap,
                        )
                    }
                }
                entry(AppDestination.Profile) {
                    ProfileRoute(
                        user = user,
                        authBusy = authBusy,
                        onAccountClick = onAccountClick,
                        onMethodsClick = onMethodsClick,
                        onCategoriesClick = onCategoriesClick,
                        onFamilyAccessClick = onFamilyAccessClick,
                        onCloudSyncClick = onCloudSyncClick,
                        bottomBarOverlap = bottomBarOverlap,
                    )
                }
            }
        // Keep each tab's decorators alive even while that tab is absent from NavDisplay.
        val tabEntries =
            AppDestination.entries.filter { it != AppDestination.AddOperation }.associateWith { destination ->
                key(destination) {
                    val stack = rememberNavBackStack(destination)
                    rememberDecoratedNavEntries(
                        backStack = stack,
                        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
                        entryProvider = provider,
                    )
                }
            }
        val visibleTabs =
            if (selectedDestination == AppDestination.Dashboard) {
                listOf(AppDestination.Dashboard)
            } else {
                listOf(AppDestination.Dashboard, selectedDestination)
            }
        NavDisplay(
            entries = visibleTabs.flatMap { tabEntries.getValue(it) },
            onBack = { onSelectDestination(AppDestination.Dashboard) },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
