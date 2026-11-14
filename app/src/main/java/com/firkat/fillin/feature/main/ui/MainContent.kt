package com.firkat.fillin.feature.main.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.ContentTransform
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import com.firkat.fillin.feature.budgetprofile.ui.ActiveBudgetContent
import com.firkat.fillin.feature.budgetprofile.ui.MainBudgetViewModelStore
import com.firkat.fillin.feature.dashboard.ui.DashboardRoute
import com.firkat.fillin.feature.operationhistory.ui.OperationHistoryRoute
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationType
import com.firkat.fillin.feature.plannedoperations.ui.PlannedOperationsEvent
import com.firkat.fillin.feature.plannedoperations.ui.PlannedOperationsRoute
import com.firkat.fillin.feature.plannedoperations.ui.planningRulesViewModel
import com.firkat.fillin.ui.component.SaldomiSnackbarHost
import com.firkat.fillin.ui.navigation.slideNavigation

@Composable
internal fun MainContent(
    modifier: Modifier = Modifier,
    selectedDestination: AppDestination,
    onSelectDestination: (AppDestination) -> Unit,
    onAccountClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
    budgetViewModelStoreOwner: MainBudgetViewModelStore,
    onOperationClick: (String, String) -> Unit,
    onForecastClick: () -> Unit,
    onBack: () -> Unit,
    readOnly: Boolean = false,
    onPlannedOperationEditorClick: (PlannedOperationType, String?) -> Unit
) {
    Scaffold(
        modifier = modifier.testTag("main_screen"),
        snackbarHost = {
            SaldomiSnackbarHost(snackbarHostState)
        },
        topBar = {
        },
        bottomBar = {
            MainBottomNavigation(
                selectedDestination = selectedDestination,
                onSelectDestination = onSelectDestination
            )
        }
    ) { innerPadding ->
        val bottomBarOverlap = innerPadding.calculateBottomPadding()
        val provider =
            entryProvider<NavKey> {
                entry(AppDestination.Dashboard) {
                    ActiveBudgetContent(viewModelStoreOwner = budgetViewModelStoreOwner) {
                        val plansViewModel = planningRulesViewModel()
                        DashboardRoute(
                            modifier = Modifier
                                .fillMaxSize(),
                            bottomBarOverlap = bottomBarOverlap,
                            onForecastClick = onForecastClick,
                            onPaymentsClick = { _, _ ->
                                plansViewModel.onEvent(
                                    PlannedOperationsEvent.TypeSelected(PlannedOperationType.Expense)
                                )
                                onSelectDestination(AppDestination.Planned)
                            },
                            onProfileClick = onAccountClick
                        )
                    }
                }
                entry(AppDestination.History) {
                    ActiveBudgetContent(viewModelStoreOwner = budgetViewModelStoreOwner) {
                        OperationHistoryRoute(
                            snackbarHostState = snackbarHostState,
                            onOperationClick = onOperationClick,
                            bottomBarOverlap = bottomBarOverlap,
                            onBack = onBack
                        )
                    }
                }
                entry(AppDestination.Planned) {
                    ActiveBudgetContent(viewModelStoreOwner = budgetViewModelStoreOwner) {
                        PlannedOperationsRoute(
                            readOnly = readOnly,
                            onOpenEditor = { onPlannedOperationEditorClick(it, null) },
                            onEditRule = { onPlannedOperationEditorClick(it.type, it.id) },
                            bottomBarOverlap = bottomBarOverlap
                        )
                    }
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
                        entryProvider = provider
                    )
                }
            }
        val visibleTabs =
            if (selectedDestination == AppDestination.Dashboard) {
                listOf(AppDestination.Dashboard)
            } else {
                listOf(AppDestination.Dashboard, selectedDestination)
            }
        val tabsByContentKey = tabEntries.entries.associate { (destination, entries) ->
            entries.last().contentKey to destination
        }
        val tabTransition: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
            val from = tabsByContentKey.getValue(initialState.key)
            val to = tabsByContentKey.getValue(targetState.key)
            val direction = if (to > from) SlideDirection.Start else SlideDirection.End
            slideNavigation(direction)
        }
        NavDisplay(
            entries = visibleTabs.flatMap { tabEntries.getValue(it) },
            onBack = { onSelectDestination(AppDestination.Dashboard) },
            modifier = Modifier.fillMaxSize().clipToBounds(),
            transitionSpec = tabTransition,
            popTransitionSpec = tabTransition,
            predictivePopTransitionSpec = { tabTransition() }
        )
    }
}
