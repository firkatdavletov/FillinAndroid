package com.firkat.fillin.feature.main.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import com.firkat.fillin.feature.dashboard.ui.MainScreenHeader
import com.firkat.fillin.feature.operationhistory.ui.OperationHistoryRoute
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationType
import com.firkat.fillin.feature.plannedoperations.ui.PlannedOperationsEvent
import com.firkat.fillin.feature.plannedoperations.ui.PlannedOperationsRoute
import com.firkat.fillin.feature.plannedoperations.ui.planningRulesViewModel
import com.firkat.fillin.feature.profile.ui.ProfileRoute
import com.firkat.fillin.ui.component.SaldomiSnackbarHost
import com.firkat.fillin.ui.theme.saldomiDesignColors
import kotlinx.serialization.Serializable

@Composable
fun MainScreen(
    user: AuthUser?,
    authBusy: Boolean,
    onAccountClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
    budgetViewModelStoreOwner: MainBudgetViewModelStore,
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
    var selectedDestination by rememberSaveable { mutableStateOf(AppDestination.Dashboard) }

    MainContent(
        modifier = modifier,
        readOnly = readOnly,
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
        onMethodsClick = onMethodsClick,
        snackbarHostState = snackbarHostState,
        user = user,
        budgetViewModelStoreOwner = budgetViewModelStoreOwner,
        onCategoriesClick = onCategoriesClick,
        onFamilyAccessClick = onFamilyAccessClick,
        onCloudSyncClick = onCloudSyncClick,
        onOperationClick = onOperationClick,
        onForecastClick = onForecastClick,
        onPlannedOperationEditorClick = onPlannedOperationEditorClick,
    )
}