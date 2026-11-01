package com.firkat.fillin.feature.main.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
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
import com.firkat.fillin.feature.auth.domain.AuthUser
import com.firkat.fillin.feature.budgetprofile.ui.ActiveBudgetContent
import com.firkat.fillin.feature.budgetprofile.ui.MainBudgetViewModelStore
import com.firkat.fillin.feature.dashboard.ui.DashboardRoute
import com.firkat.fillin.feature.operationhistory.ui.OperationHistoryRoute
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationType
import com.firkat.fillin.feature.plannedoperations.ui.PlannedOperationsRoute
import com.firkat.fillin.feature.profile.ui.ProfileRoute
import kotlinx.serialization.Serializable

@Composable
fun MainScreen(
    user: AuthUser?,
    authBusy: Boolean,
    onAccountClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
    budgetViewModelStoreOwner: MainBudgetViewModelStore? = null,
    onCategoriesClick: () -> Unit = {},
    onFamilyAccessClick: () -> Unit = {},
    onCloudSyncClick: () -> Unit = {},
    onCreateOperationClick: () -> Unit = {},
    onOperationClick: (String, String) -> Unit = { _, _ -> },
    onPlannedOperationEditorClick: (PlannedOperationType) -> Unit = {},
) {
    var selectedDestination by rememberSaveable { mutableStateOf(AppDestination.Dashboard) }
    ActiveBudgetContent(viewModelStoreOwner = budgetViewModelStoreOwner) {
        MainContent(
            selectedDestination = selectedDestination,
            onSelectDestination = {
                if (it == AppDestination.AddOperation) {
                    onCreateOperationClick()
                } else {
                    selectedDestination = it
                }
            },
            authBusy = authBusy,
            onAccountClick = onAccountClick,
            snackbarHostState = snackbarHostState,
            user = user,
            onCategoriesClick = onCategoriesClick,
            onFamilyAccessClick = onFamilyAccessClick,
            onCloudSyncClick = onCloudSyncClick,
            onCreateOperationClick = onCreateOperationClick,
            onOperationClick = onOperationClick,
            onPlannedOperationEditorClick = onPlannedOperationEditorClick,
        )
    }
}

@Composable
private fun MainContent(
    selectedDestination: AppDestination,
    onSelectDestination: (AppDestination) -> Unit,
    authBusy: Boolean,
    onAccountClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
    user: AuthUser?,
    onCategoriesClick: () -> Unit,
    onFamilyAccessClick: () -> Unit,
    onCloudSyncClick: () -> Unit,
    onCreateOperationClick: () -> Unit,
    onOperationClick: (String, String) -> Unit,
    onPlannedOperationEditorClick: (PlannedOperationType) -> Unit,
) {
    Scaffold(
        modifier = Modifier.testTag("main_screen"),
        snackbarHost = {
            com.firkat.fillin.ui.component
                .SaldomiSnackbarHost(snackbarHostState)
        },
        floatingActionButtonPosition = FabPosition.Center,
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
        val provider =
            entryProvider<NavKey> {
                entry(AppDestination.Dashboard) { DashboardRoute() }
                entry(
                    AppDestination.History,
                ) { OperationHistoryRoute(snackbarHostState = snackbarHostState, onOperationClick = onOperationClick) }
                entry(AppDestination.Planned) {
                    PlannedOperationsRoute(onOpenEditor = onPlannedOperationEditorClick)
                }
                entry(AppDestination.Profile) {
                    ProfileRoute(
                        user = user,
                        authBusy = authBusy,
                        onAccountClick = onAccountClick,
                        onCategoriesClick = onCategoriesClick,
                        onFamilyAccessClick = onFamilyAccessClick,
                        onCloudSyncClick = onCloudSyncClick,
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
            modifier = Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding),
        )
    }
}

// Leave room for the shared FAB below each tab's own creation action.
private val MainFabClearance = 72.dp

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
    AddOperation(
        labelResource = R.string.notice,
        iconResource = R.drawable.ic_add_24,
    ),
    Planned(
        labelResource = R.string.navigation_planned,
        iconResource = R.drawable.ic_calendar,
    ),
    Profile(
        labelResource = R.string.navigation_profile,
        iconResource = R.drawable.ic_account_box,
    ),
}
