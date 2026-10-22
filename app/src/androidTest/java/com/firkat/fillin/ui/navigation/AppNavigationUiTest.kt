package com.firkat.fillin.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModelStore
import com.firkat.fillin.feature.auth.domain.*
import com.firkat.fillin.feature.auth.ui.AuthViewModel
import com.firkat.fillin.feature.auth.ui.GoogleSignInLauncher
import com.firkat.fillin.feature.budgetconnection.BudgetConnectionDeviceTest
import com.firkat.fillin.feature.budgetconnection.ui.BudgetConnectionViewModel
import com.firkat.fillin.feature.budgetprofile.data.BudgetDatabaseFactory
import com.firkat.fillin.feature.launch.data.RoomLocalBudgetRestorer
import com.firkat.fillin.feature.sync.data.CloudBudgetSession
import com.firkat.fillin.ui.theme.FillinTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.koin.compose.scope.UnboundKoinScope
import org.koin.core.annotation.KoinDelicateAPI
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

@OptIn(KoinExperimentalAPI::class, KoinDelicateAPI::class, KoinInternalApi::class)
class AppNavigationUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val env = BudgetConnectionDeviceTest.Environment()
    private val models = ViewModelStore()
    private var pickerCalls = 0
    private var launcher = GoogleSignInLauncher { pickerCalls++; "synthetic-credential" }
    private val auth = object : AuthRepository by env.auth {
        override suspend fun signInWithGoogle(idToken: String) { env.auth.login("owner") }
    }
    private lateinit var navigation: AppNavigationViewModel
    private lateinit var authModel: AuthViewModel

    private fun show(
        createdBudget: Boolean = false,
        restoration: StateRestorationTester? = null,
    ) {
        runBlocking {
            env.manager.restore()
            if (createdBudget) {
                env.manager.active.value!!.scope.get<CloudBudgetSession>()
                    .createLocal("321.00".toBigDecimal(), java.time.LocalDate.now().minusDays(1))
            }
        }
        env.koin.loadModules(listOf(module {
            viewModel { AuthViewModel(auth) }
            single<GoogleSignInLauncher> { launcher }
            viewModel { parameters -> BudgetConnectionViewModel(auth, env.connection, env.creation, parameters.getOrNull<Boolean>() ?: false) }
        }))
        authModel = AuthViewModel(auth).also { models.put("auth", it) }
        navigation = AppNavigationViewModel(auth, RoomLocalBudgetRestorer(env.registry, BudgetDatabaseFactory(env.context), env.manager))
            .also { models.put("navigation", it) }
        val content: @Composable () -> Unit = {
            FillinTheme {
                UnboundKoinScope(env.koin.scopeRegistry.rootScope) {
                    FillinNavHost(navigation, authModel, launcher)
                }
            }
        }
        if (restoration == null) compose.setContent(content) else restoration.setContent(content)
        if (auth.session.value != AuthSession.Loading) {
            compose.waitUntil(5000) {
                compose.onAllNodes(if (createdBudget) hasText("Облако") else hasTestTag("auth_screen"))
                    .fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    @After fun cleanup() {
        compose.activityRule.scenario.onActivity { it.setContentView(android.widget.FrameLayout(it)) }
        compose.waitForIdle()
        models.clear()
        env.close()
    }

    @Test fun coldLaunchWithLocalBudgetOpensMainAndGuestAuthReturnsToCloud() {
        show(createdBudget = true)
        compose.onNodeWithText("Облако").performClick()
        compose.onNodeWithText("Авторизация").performClick()
        compose.onNodeWithTag("auth_screen").assertIsDisplayed()
        compose.onNodeWithText("Облако").assertDoesNotExist()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Авторизация").assertIsDisplayed()
        compose.onNodeWithText("Облако").assertIsSelected()
    }

    @Test fun launchWaitsForFirebaseBeforeOpeningMainWithoutAnotherPicker() {
        env.auth.session.value = AuthSession.Loading
        show(createdBudget = true)
        compose.onNodeWithTag("launch_screen").assertIsDisplayed()
        compose.runOnIdle { env.auth.login("owner") }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Облако").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(listOf(AppRoute.Main), navigation.backStack)
        assertEquals(0, pickerCalls)
    }

    @Test fun GoogleSignInReturnsToTheSelectedCloudTab() {
        show(createdBudget = true)
        compose.onNodeWithText("Облако").performClick()
        compose.onNodeWithText("Авторизация").performClick()
        compose.onNodeWithText("Продолжить с Google").performClick()
        compose.onNodeWithText("Выйти из аккаунта").assertIsDisplayed()
        compose.onNodeWithText("Облако").assertIsSelected()
        assertEquals(1, pickerCalls)
        assertEquals(listOf(AppRoute.Main), navigation.backStack)
    }

    @Test fun logoutClearsMainHistoryEvenWhenTheLocalBudgetStillExists() {
        env.auth.login("owner")
        show(createdBudget = true)
        compose.onNodeWithText("Облако").performClick()
        compose.onNodeWithText("Выйти из аккаунта").performClick()
        compose.onNodeWithTag("auth_screen").assertIsDisplayed()
        compose.runOnIdle { navigation.back() }
        assertEquals(listOf(AppRoute.Auth), navigation.backStack)
        assertEquals(AuthSession.Anonymous, auth.session.value)
        assertEquals(0, pickerCalls)
    }

    @Test fun emptyDeviceShowsAuthAndEmailExplainsItsAvailability() {
        show()
        compose.onNodeWithTag("auth_screen").assertIsDisplayed()
        compose.onNodeWithText("Облако").assertDoesNotExist()
        compose.onNodeWithText("Продолжить с email").performClick()
        compose.onNodeWithText("Вход по email пока недоступен").assertIsDisplayed()
    }

    @Test fun missingFirebaseConfigurationStillAllowsGuestSetup() {
        env.auth.session.value = AuthSession.Unavailable
        show()
        compose.onNodeWithText("Продолжить с Google").assertIsNotEnabled()
        compose.onNodeWithText("Продолжить без авторизации").performClick()
        compose.onNodeWithTag("auth_screen").assertDoesNotExist()
        compose.onNodeWithText("Начало работы").assertIsDisplayed()
        assertEquals(0, pickerCalls)
    }

    @Test fun pendingGooglePickerBlocksRepeatedActionsAndCancellationPermitsRetry() {
        val result = CompletableDeferred<String>()
        launcher = GoogleSignInLauncher { pickerCalls++; result.await() }
        show()
        compose.onNodeWithText("Продолжить с Google").performClick()
        compose.waitUntil { pickerCalls == 1 }
        compose.onNodeWithText("Продолжить с email").assertIsNotEnabled()
        compose.onNodeWithText("Продолжить без авторизации").assertIsNotEnabled()
        compose.runOnIdle { result.completeExceptionally(GetCredentialCancellationException("Synthetic cancellation")) }
        compose.waitUntil { !authModel.state.value.busy }
        compose.onNodeWithText("Продолжить с Google").assertIsEnabled()
        compose.onNodeWithTag("auth_screen").assertIsDisplayed()
        assertNull(authModel.state.value.error)
        assertEquals(1, pickerCalls)
    }

    @Test fun GoogleFailureShowsSnackbarAndRetryReturnsToMain() {
        launcher = GoogleSignInLauncher {
            if (++pickerCalls == 1) throw AuthFailure(AuthError.Network)
            "synthetic-credential"
        }
        show(createdBudget = true)
        compose.onNodeWithText("Облако").performClick()
        compose.onNodeWithText("Авторизация").performClick()
        compose.onNodeWithText("Продолжить с Google").performClick()
        compose.onNodeWithText("Не удалось выполнить вход. Проверьте интернет и повторите попытку.").assertIsDisplayed()
        compose.onNodeWithTag("auth_screen").assertIsDisplayed()
        compose.onNodeWithText("Продолжить с Google").performClick()
        compose.onNodeWithText("Выйти из аккаунта").assertIsDisplayed()
        assertEquals(2, pickerCalls)
        assertEquals(listOf(AppRoute.Main), navigation.backStack)
    }

    @Test fun guestAuthStackAndSelectedTabSurviveSavedStateRestoration() {
        val restoration = StateRestorationTester(compose)
        show(createdBudget = true, restoration = restoration)
        compose.onNodeWithText("Облако").performClick()
        compose.onNodeWithText("Авторизация").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("auth_screen").assertIsDisplayed()
        compose.runOnIdle { navigation.back() }
        compose.onNodeWithText("Облако").assertIsSelected()
        assertEquals(0, pickerCalls)
    }
}
