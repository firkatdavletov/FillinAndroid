package com.firkat.fillin.feature.main.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.firkat.fillin.R
import com.firkat.fillin.feature.budgetprofile.ui.ActiveBudgetContent
import com.firkat.fillin.feature.budgetprofile.ui.MainBudgetViewModelStore
import com.firkat.fillin.feature.categories.ui.CategoriesRoute
import com.firkat.fillin.feature.dashboard.ui.DashboardRoute
import com.firkat.fillin.feature.operationhistory.ui.OperationHistoryRoute
import com.firkat.fillin.feature.plannedoperations.ui.PlannedOperationsRoute
import com.firkat.fillin.feature.sync.ui.ProfileScreen
import kotlinx.serialization.Serializable

@Composable
fun MainScreen(
    isAuthenticated: Boolean,
    authBusy: Boolean,
    onAccountClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
    budgetViewModelStoreOwner: MainBudgetViewModelStore? = null,
) {
    var selectedDestination by rememberSaveable { mutableStateOf(AppDestination.Dashboard) }
    ActiveBudgetContent(viewModelStoreOwner = budgetViewModelStoreOwner) {
        MainContent(
            selectedDestination = selectedDestination,
            onSelectDestination = { selectedDestination = it },
            isAuthenticated = isAuthenticated,
            authBusy = authBusy,
            onAccountClick = onAccountClick,
            snackbarHostState = snackbarHostState,
        )
    }
}

@Composable
private fun MainContent(
    selectedDestination: AppDestination,
    onSelectDestination: (AppDestination) -> Unit,
    isAuthenticated: Boolean,
    authBusy: Boolean,
    onAccountClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    Scaffold(
        modifier = Modifier.testTag("main_screen"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                AppDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        modifier = Modifier.testTag("tab_${destination.name}"),
                        selected = selectedDestination == destination,
                        onClick = { onSelectDestination(destination) },
                        icon = {
                            Icon(
                                painter = painterResource(destination.iconResource),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(destination.labelResource),
                                fontSize = 12.sp,
                            )
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        val provider = entryProvider<NavKey> {
            entry(AppDestination.Dashboard) { DashboardRoute() }
            entry(AppDestination.History) { OperationHistoryRoute() }
            entry(AppDestination.Planned) { PlannedOperationsRoute() }
            entry(AppDestination.Categories) { CategoriesRoute() }
            entry(AppDestination.Profile) {
                ProfileScreen(
                    isAuthenticated = isAuthenticated,
                    authBusy = authBusy,
                    onAccountClick = onAccountClick,
                )
            }
        }
        // Keep each tab's decorators alive even while that tab is absent from NavDisplay.
        val tabEntries = AppDestination.entries.associateWith { destination ->
            key(destination) {
                val stack = rememberNavBackStack(destination)
                rememberDecoratedNavEntries(
                    backStack = stack,
                    entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
                    entryProvider = provider,
                )
            }
        }
        val visibleTabs = if (selectedDestination == AppDestination.Dashboard) {
            listOf(AppDestination.Dashboard)
        } else listOf(AppDestination.Dashboard, selectedDestination)
        NavDisplay(
            entries = visibleTabs.flatMap { tabEntries.getValue(it) },
            onBack = { onSelectDestination(AppDestination.Dashboard) },
            modifier = Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding),
        )
    }
}

@Serializable
private enum class AppDestination(
    @StringRes val labelResource: Int,
    @DrawableRes val iconResource: Int,
) : NavKey {
    Dashboard(
        labelResource = R.string.navigation_dashboard,
        iconResource = R.drawable.ic_home,
    ),
    History(
        labelResource = R.string.navigation_history,
        iconResource = R.drawable.ic_receipt,
    ),
    Planned(
        labelResource = R.string.navigation_planned,
        iconResource = R.drawable.ic_calendar,
    ),
    Categories(
        labelResource = R.string.navigation_categories,
        iconResource = R.drawable.ic_categories,
    ),
    Profile(
        labelResource = R.string.navigation_profile,
        iconResource = R.drawable.ic_account_box,
    ),
}
