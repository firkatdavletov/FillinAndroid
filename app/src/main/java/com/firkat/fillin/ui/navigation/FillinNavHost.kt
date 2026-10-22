package com.firkat.fillin.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.firkat.fillin.feature.auth.domain.AuthSession
import com.firkat.fillin.feature.auth.ui.AuthScreen
import com.firkat.fillin.feature.auth.ui.AuthViewModel
import com.firkat.fillin.feature.auth.ui.GoogleSignInLauncher
import com.firkat.fillin.feature.auth.ui.messageResource
import com.firkat.fillin.feature.launch.presentation.LaunchScreen
import com.firkat.fillin.feature.main.ui.MainScreen
import com.firkat.fillin.ui.component.SnackbarType
import com.firkat.fillin.ui.component.showSaldomiSnackbar
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun FillinNavHost(
    navigation: AppNavigationViewModel = koinViewModel(),
    auth: AuthViewModel = koinViewModel(),
    googleSignIn: GoogleSignInLauncher = koinInject(),
) {
    val state by auth.state.collectAsStateWithLifecycle()
    val launchFailed by navigation.launchFailed.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
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
                    isAuthenticated = state.session is AuthSession.Authenticated,
                    authBusy = state.busy,
                    onAccountClick = {
                        if (state.session is AuthSession.Authenticated) auth.signOut()
                        else navigation.openAuthentication()
                    },
                    snackbarHostState = snackbar,
                )
            }
            entry<AppRoute.Auth> {
                AuthScreen(state, auth, googleSignIn, snackbar, navigation::continueAsGuest)
            }
        },
    )
}
