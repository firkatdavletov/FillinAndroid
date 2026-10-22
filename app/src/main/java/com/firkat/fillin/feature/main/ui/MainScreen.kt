package com.firkat.fillin.feature.main.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.firkat.fillin.R
import com.firkat.fillin.feature.budgetprofile.ui.ActiveBudgetContent
import com.firkat.fillin.feature.budgetsetup.ui.BudgetSetupGate
import com.firkat.fillin.feature.categories.ui.CategoriesRoute
import com.firkat.fillin.feature.dashboard.ui.DashboardRoute
import com.firkat.fillin.feature.operationhistory.ui.OperationHistoryRoute
import com.firkat.fillin.feature.plannedoperations.ui.PlannedOperationsRoute

@Composable
fun MainScreen(
    isAuthenticated: Boolean,
    authBusy: Boolean,
    onAccountClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    var selectedDestination by rememberSaveable { mutableStateOf(AppDestination.Dashboard) }
    ActiveBudgetContent {
        BudgetSetupGate {
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                AppDestination.entries.forEach { destination ->
                    NavigationBarItem(
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
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
        ) {
            when (selectedDestination) {
                AppDestination.Dashboard -> {
                    DashboardRoute()
                }

                AppDestination.History -> {
                    OperationHistoryRoute()
                }

                AppDestination.Planned -> {
                    PlannedOperationsRoute()
                }

                AppDestination.Categories -> {
                    CategoriesRoute()
                }

                AppDestination.Cloud -> {
                    com.firkat.fillin.feature.sync.ui
                        .BudgetSyncScreen(
                            isAuthenticated = isAuthenticated,
                            authBusy = authBusy,
                            onAccountClick = onAccountClick,
                        )
                }
            }
        }
    }
}

private enum class AppDestination(
    @StringRes val labelResource: Int,
    @DrawableRes val iconResource: Int,
) {
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
    Cloud(
        labelResource = R.string.navigation_cloud,
        iconResource = R.drawable.ic_cloud,
    ),
}
