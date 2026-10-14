package com.firkat.fillin

import android.os.Bundle
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
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
import com.firkat.fillin.feature.categories.ui.CategoriesRoute
import com.firkat.fillin.feature.dashboard.ui.DashboardRoute
import com.firkat.fillin.feature.plannedexpenses.ui.PlannedExpensesRoute
import com.firkat.fillin.feature.plannedincomes.ui.PlannedIncomesRoute
import com.firkat.fillin.feature.purchasehistory.ui.PurchaseHistoryRoute
import com.firkat.fillin.ui.theme.FillinTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FillinTheme {
                FillinApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FillinApp() {
    var selectedDestination by rememberSaveable { mutableStateOf(AppDestination.Dashboard) }

    Scaffold(
        topBar = {
            if (selectedDestination != AppDestination.Dashboard) {
                CenterAlignedTopAppBar(
                    title = { Text(stringResource(selectedDestination.titleResource)) },
                )
            }
        },
        bottomBar = {
            NavigationBar {
                AppDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = selectedDestination == destination,
                        onClick = { selectedDestination = destination },
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
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (selectedDestination) {
                AppDestination.Dashboard -> DashboardRoute(
                    onOpenPlannedExpenses = {
                        selectedDestination = AppDestination.Planned
                    },
                    onOpenCategories = {
                        selectedDestination = AppDestination.Categories
                    },
                    onAddOperation = {
                        selectedDestination = AppDestination.History
                    },
                )
                AppDestination.History -> PurchaseHistoryRoute()
                AppDestination.Planned -> PlannedExpensesRoute()
                AppDestination.Incomes -> PlannedIncomesRoute()
                AppDestination.Categories -> CategoriesRoute()
            }
        }
    }
}

private enum class AppDestination(
    @StringRes val labelResource: Int,
    @DrawableRes val iconResource: Int,
    @StringRes val titleResource: Int,
) {
    Dashboard(
        labelResource = R.string.navigation_dashboard,
        iconResource = R.drawable.ic_home,
        titleResource = R.string.dashboard_title,
    ),
    History(
        labelResource = R.string.navigation_history,
        iconResource = R.drawable.ic_receipt,
        titleResource = R.string.purchase_history_title,
    ),
    Planned(
        labelResource = R.string.navigation_planned,
        iconResource = R.drawable.ic_calendar,
        titleResource = R.string.planned_expenses_title,
    ),
    Incomes(
        labelResource = R.string.navigation_planned_incomes,
        iconResource = R.drawable.ic_income,
        titleResource = R.string.planned_incomes_title,
    ),
    Categories(
        labelResource = R.string.navigation_categories,
        iconResource = R.drawable.ic_categories,
        titleResource = R.string.categories_title,
    ),
}
