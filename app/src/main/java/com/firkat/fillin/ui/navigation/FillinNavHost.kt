package com.firkat.fillin.ui.navigation

import android.app.Activity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.firkat.fillin.feature.auth.domain.AuthSession
import com.firkat.fillin.feature.auth.ui.AuthScreen
import com.firkat.fillin.feature.auth.ui.AuthViewModel
import com.firkat.fillin.feature.auth.ui.GoogleSignInLauncher
import com.firkat.fillin.feature.auth.ui.messageResource
import com.firkat.fillin.feature.budgetprofile.ui.ActiveBudgetContent
import com.firkat.fillin.feature.budgetprofile.ui.MainBudgetViewModelStore
import com.firkat.fillin.feature.budgetprofile.data.ActiveBudgetManager
import com.firkat.fillin.feature.budgetsetup.ui.BudgetSetupRoute
import com.firkat.fillin.feature.launch.presentation.LaunchScreen
import com.firkat.fillin.feature.main.ui.MainScreen
import com.firkat.fillin.feature.budgetsharing.ui.FamilyAccessRoute
import com.firkat.fillin.feature.sync.ui.CloudSyncRoute
import com.firkat.fillin.ui.component.SnackbarType
import com.firkat.fillin.ui.component.showSaldomiSnackbar
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun FillinNavHost(
    navigation: AppNavigationViewModel = koinViewModel(),
    auth: AuthViewModel = koinViewModel(),
    googleSignIn: GoogleSignInLauncher = koinInject(),
    budgets: ActiveBudgetManager = koinInject(),
) {
    val state by auth.state.collectAsStateWithLifecycle()
    val launchFailed by navigation.launchFailed.collectAsStateWithLifecycle()
    val resolvingBudget by navigation.resolvingBudget.collectAsStateWithLifecycle()
    val runtime by budgets.active.collectAsStateWithLifecycle()
    val mainModels: MainBudgetViewModelStore = viewModel(key = "main-budget-models")
    mainModels.bind(runtime.takeIf { AppRoute.Main in navigation.backStack })
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    DisposableEffect(mainModels, context) {
        onDispose {
            if ((context as? Activity)?.isChangingConfigurations != true) mainModels.bind(null)
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbar.showSaldomiSnackbar(
                message = context.getString(error.messageResource()),
                type = SnackbarType.Info
            )
            auth.errorShown(error)
        }
    }
    NavDisplay(
        modifier = Modifier.fillMaxSize(),
        backStack = navigation.backStack,
        onBack = { navigation.back() },
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
        entryProvider = entryProvider {
            entry<AppRoute.Launch> {
                LaunchScreen(launchFailed, navigation::onLaunchShown, navigation::retryLaunch)
            }
            entry<AppRoute.Main> {
                MainScreen(
                    authBusy = state.busy,
                    onAccountClick = {
                        if (state.session is AuthSession.Authenticated) auth.signOut()
                        else navigation.openAuthentication()
                    },
                    snackbarHostState = snackbar,
                    budgetViewModelStoreOwner = mainModels,
                    user = (state.session as? AuthSession.Authenticated)?.user,
                    onFamilyAccessClick = navigation::openFamilyAccess,
                    onCloudSyncClick = navigation::openCloudSync,
                )
            }
            entry<AppRoute.FamilyAccess> {
                FamilyAccessRoute(onBack = navigation::back, onSignIn = navigation::openAuthentication)
            }
            entry<AppRoute.CloudSync> {
                ActiveBudgetContent {
                    CloudSyncRoute(onBack = navigation::back, onSignIn = navigation::openAuthentication)
                }
            }
            entry<AppRoute.Auth> {
                AuthScreen(
                    state.copy(busy = state.busy || resolvingBudget), auth, googleSignIn, snackbar,
                    navigation::continueAsGuest, navigation::continueWithAccount,
                )
            }
            entry<AppRoute.BudgetSetup> { route ->
                ActiveBudgetContent {
                    BudgetSetupRoute(route.mode, navigation::back)
                }
            }
        },
    )
}
