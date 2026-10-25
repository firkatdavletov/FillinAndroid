package com.firkat.fillin.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModelStore
import androidx.test.espresso.Espresso.closeSoftKeyboard
import com.firkat.fillin.feature.auth.domain.*
import com.firkat.fillin.feature.auth.ui.AuthViewModel
import com.firkat.fillin.feature.auth.ui.GoogleSignInLauncher
import com.firkat.fillin.feature.budgetconnection.BudgetConnectionDeviceTest
import com.firkat.fillin.feature.budgetconnection.ui.BudgetConnectionViewModel
import com.firkat.fillin.feature.budgetprofile.data.BudgetDatabaseFactory
import com.firkat.fillin.feature.budgetsetup.data.RoomActiveBudgetReadiness
import com.firkat.fillin.feature.budgetsetup.domain.BudgetSetupMode
import com.firkat.fillin.feature.launch.data.RoomLocalBudgetRestorer
import com.firkat.fillin.feature.sync.data.CloudBudgetSession
import com.firkat.fillin.ui.theme.FillinTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import com.firkat.fillin.feature.operationhistory.domain.model.Operation
import com.firkat.fillin.feature.operationhistory.domain.model.OperationHistoryFilter
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.feature.operationhistory.domain.repository.NewOperation
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryStore
import java.time.LocalDate
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
    private lateinit var hostContent: @Composable () -> Unit

    @Test fun existingCloudSetupOffersFamilyCodeWithoutCreatingAnotherBudget() {
        env.auth.login("owner")
        show()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Вступить в семейный бюджет").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Вступить в семейный бюджет").performScrollTo().performClick()
        compose.onNodeWithText("Код приглашения").assertExists()
        assertTrue(env.server.creations.isEmpty())
        compose.onNodeWithText("К списку бюджетов").performScrollTo().performClick()
        compose.onNodeWithText("Код приглашения").assertDoesNotExist()
    }

    private fun show(
        createdBudget: Boolean = false,
        restoration: StateRestorationTester? = null,
        fontScale: Float = 1f,
    ) {
        compose.runOnUiThread {
            compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        runBlocking {
            env.manager.restore()
            if (createdBudget) {
                env.manager.active.value!!.scope.get<CloudBudgetSession>()
                    .createLocal("321.00".toBigDecimal(), java.time.LocalDate.now().minusDays(1))
            }
        }
        env.koin.loadModules(listOf(module {
            viewModel { AuthViewModel(auth) }
            viewModel { com.firkat.fillin.feature.budgetsharing.ui.FamilyBudgetJoinViewModel(auth, env.sharing) }
            viewModel { com.firkat.fillin.feature.budgetsharing.ui.FamilyMembersViewModel(env.sharing) }
            viewModel {
                com.firkat.fillin.feature.budgetinvitation.ui.BudgetInvitationViewModel(object : com.firkat.fillin.feature.budgetinvitation.domain.BudgetInvitationRepository {
                    override val target = kotlinx.coroutines.flow.flowOf<com.firkat.fillin.feature.budgetinvitation.domain.InvitationTarget?>(null)
                    override fun isCurrent(target: com.firkat.fillin.feature.budgetinvitation.domain.InvitationTarget) = false
                    override suspend fun current(target: com.firkat.fillin.feature.budgetinvitation.domain.InvitationTarget): com.firkat.fillin.feature.budgetinvitation.domain.BudgetInvitation? = error("Unused")
                    override suspend fun create(target: com.firkat.fillin.feature.budgetinvitation.domain.InvitationTarget): com.firkat.fillin.feature.budgetinvitation.domain.CreatedInvitation = error("Unused")
                    override suspend fun revoke(target: com.firkat.fillin.feature.budgetinvitation.domain.InvitationTarget, invitation: com.firkat.fillin.feature.budgetinvitation.domain.BudgetInvitation) = Unit
                })
            }
            viewModel { com.firkat.fillin.feature.budgetupload.ui.LocalBudgetUploadViewModel(auth, env.uploadConnection) }
            single<com.firkat.fillin.feature.profile.domain.ProfileBudgetsRepository> {
                com.firkat.fillin.feature.profile.data.RoomProfileBudgetsRepository(auth, env.registry, env.manager, BudgetDatabaseFactory(env.context), env.connection, get(), env.uploads)
            }
            viewModel { com.firkat.fillin.feature.profile.ui.ProfileViewModel(get(), auth, env.creation) }
            single<GoogleSignInLauncher> { launcher }
            viewModel { parameters -> BudgetConnectionViewModel(auth, env.connection, env.creation, parameters.getOrNull<Boolean>() ?: false) }
        }))
        authModel = AuthViewModel(auth).also { models.put("auth", it) }
        navigation = AppNavigationViewModel(auth, RoomLocalBudgetRestorer(env.registry, BudgetDatabaseFactory(env.context), env.manager), RoomActiveBudgetReadiness(env.manager))
            .also { models.put("navigation", it) }
        hostContent = {
            FillinTheme {
                UnboundKoinScope(env.koin.scopeRegistry.rootScope) {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                        FillinNavHost(navigation, authModel, launcher)
                    }
                }
            }
        }
        if (restoration == null) compose.setContent(hostContent) else restoration.setContent(hostContent)
        if (auth.session.value != AuthSession.Loading) {
            compose.waitUntil(5000) {
                compose.onAllNodes(if (createdBudget) hasTestTag("main_screen") else if (auth.session.value is AuthSession.Authenticated) hasTestTag("budget_setup_screen") else hasTestTag("auth_screen"))
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
        compose.onNodeWithTag("tab_Profile").performClick()
        compose.onNodeWithText("Авторизоваться").performClick()
        compose.onNodeWithTag("auth_screen").assertIsDisplayed()
        compose.onNodeWithText("Профиль").assertDoesNotExist()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Авторизоваться").assertIsDisplayed()
        compose.onNodeWithTag("tab_Profile").assertIsSelected()
    }

    @Test fun launchWaitsForFirebaseBeforeOpeningMainWithoutAnotherPicker() {
        env.auth.session.value = AuthSession.Loading
        show(createdBudget = true)
        compose.onNodeWithTag("launch_screen").assertIsDisplayed()
        compose.runOnIdle { env.auth.login("owner") }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Профиль").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(listOf(AppRoute.Main), navigation.backStack)
        assertEquals(0, pickerCalls)
    }

    @Test fun GoogleSignInReturnsToTheSelectedCloudTab() {
        show(createdBudget = true)
        compose.onNodeWithTag("tab_Profile").performClick()
        compose.onNodeWithText("Авторизоваться").performClick()
        compose.onNodeWithText("Продолжить с Google").performClick()
        compose.onNodeWithContentDescription("Выйти из аккаунта").assertIsDisplayed()
        compose.onNodeWithTag("tab_Profile").assertIsSelected()
        assertEquals(1, pickerCalls)
        assertEquals(listOf(AppRoute.Main), navigation.backStack)
    }

    @Test fun logoutClearsMainHistoryEvenWhenTheLocalBudgetStillExists() {
        env.auth.login("owner")
        show(createdBudget = true)
        compose.onNodeWithTag("tab_Profile").performClick()
        compose.onNodeWithContentDescription("Выйти из аккаунта").performClick()
        compose.onNodeWithTag("auth_screen").assertIsDisplayed()
        compose.runOnIdle { navigation.back() }
        assertEquals(listOf(AppRoute.Auth), navigation.backStack)
        assertEquals(AuthSession.Anonymous, auth.session.value)
        assertEquals(0, pickerCalls)
    }

    @Test fun emptyDeviceShowsAuthAndEmailExplainsItsAvailability() {
        show()
        compose.onNodeWithTag("auth_screen").assertIsDisplayed()
        compose.onNodeWithText("Профиль").assertDoesNotExist()
        compose.onNodeWithText("Продолжить с email").performClick()
        compose.onNodeWithText("Вход по email пока недоступен").assertIsDisplayed()
    }

    @Test fun missingFirebaseConfigurationStillAllowsGuestSetup() {
        env.auth.session.value = AuthSession.Unavailable
        show()
        compose.onNodeWithText("Продолжить с Google").assertIsNotEnabled()
        compose.onNodeWithText("Продолжить без авторизации").performClick()
        compose.onNodeWithTag("auth_screen").assertDoesNotExist()
        compose.onNodeWithText("Создание бюджета").assertIsDisplayed()
        compose.onNodeWithText("Продолжить без авторизации").assertDoesNotExist()
        compose.onNodeWithTag("main_screen").assertDoesNotExist()
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
        compose.onNodeWithTag("tab_Profile").performClick()
        compose.onNodeWithText("Авторизоваться").performClick()
        compose.onNodeWithText("Продолжить с Google").performClick()
        compose.onNodeWithText("Не удалось выполнить вход. Проверьте интернет и повторите попытку.").assertIsDisplayed()
        compose.onNodeWithTag("auth_screen").assertIsDisplayed()
        compose.onNodeWithText("Продолжить с Google").performClick()
        compose.onNodeWithContentDescription("Выйти из аккаунта").assertIsDisplayed()
        assertEquals(2, pickerCalls)
        assertEquals(listOf(AppRoute.Main), navigation.backStack)
    }

    @Test fun guestAuthStackAndSelectedTabSurviveSavedStateRestoration() {
        val restoration = StateRestorationTester(compose)
        show(createdBudget = true, restoration = restoration)
        compose.onNodeWithTag("tab_Profile").performClick()
        compose.onNodeWithText("Авторизоваться").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("auth_screen").assertIsDisplayed()
        compose.runOnIdle { navigation.back() }
        compose.onNodeWithTag("tab_Profile").assertIsSelected()
        assertEquals(0, pickerCalls)
    }
    @Test fun guestCreatesBudgetAfterOneClickAndBackCannotReopenSetup() {
        show()
        compose.onNodeWithText("Продолжить без авторизации").performClick()
        compose.onNodeWithText("Создание бюджета").assertIsDisplayed()
        compose.onNodeWithText("Продолжить без авторизации").assertDoesNotExist()
        compose.onNodeWithText("Профиль").assertDoesNotExist()
        assertEquals(AppRoute.BudgetSetup(BudgetSetupMode.Local), navigation.backStack.last())
        compose.onNodeWithText("Начальный баланс, ₸").performTextReplacement("123,45")
        closeSoftKeyboard()
        compose.onNodeWithText("Создать бюджет").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("main_screen").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tab_Dashboard").assertIsSelected()
        compose.onNodeWithTag("budget_setup_screen").assertDoesNotExist()
        assertEquals(listOf(AppRoute.Main), navigation.backStack)
        compose.runOnIdle { navigation.back() }
        compose.onNodeWithTag("main_screen").assertIsDisplayed()
        assertEquals(0, pickerCalls)
        assertTrue(env.server.creations.isEmpty())
    }

    @Test fun localSetupBackReturnsToAuthAndRotationRetainsInput() {
        val restoration = StateRestorationTester(compose)
        show(restoration = restoration)
        compose.onNodeWithText("Продолжить без авторизации").performClick()
        compose.onNodeWithText("Начальный баланс, ₸").performTextReplacement("456,78")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("456,78").assertIsDisplayed()
        closeSoftKeyboard()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("auth_screen").assertIsDisplayed()
        compose.onNodeWithTag("main_screen").assertDoesNotExist()
    }

    @Test fun GoogleLoginWithoutReadyBudgetOpensCloudSetupAndCreatesBudget() {
        env.server.budgetExists = false
        show()
        compose.onNodeWithText("Продолжить с Google").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Название бюджета").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("main_screen").assertDoesNotExist()
        compose.onNodeWithTag("auth_screen").assertDoesNotExist()
        assertEquals(AppRoute.BudgetSetup(BudgetSetupMode.Cloud), navigation.backStack.last())
        compose.onNodeWithText("Название бюджета").performTextReplacement("Семейный")
        closeSoftKeyboard()
        compose.onNodeWithText("Создать облачный бюджет").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("main_screen").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(listOf(AppRoute.Main), navigation.backStack)
        assertEquals(1, env.server.creations.size)
        assertEquals(1, pickerCalls)
    }

    @Test fun restoredGoogleAccountSelectsExistingCloudBudgetBeforeMain() {
        env.auth.login("owner")
        show()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Тестовый бюджет · Владелец").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("main_screen").assertDoesNotExist()
        compose.onNodeWithText("Тестовый бюджет · Владелец").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("main_screen").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(listOf(AppRoute.Main), navigation.backStack)
        assertEquals(0, pickerCalls)
    }

    @Test fun cloudSetupBackReturnsToAuthAndResumesWithoutAnotherPicker() {
        env.server.budgetExists = false
        env.auth.login("owner")
        show()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Название бюджета").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Назад").performScrollTo().performClick()
        compose.onNodeWithTag("auth_screen").assertIsDisplayed()
        compose.onNodeWithText("Продолжить с Google").performClick()
        compose.onNodeWithTag("budget_setup_screen").assertIsDisplayed()
        assertEquals(0, pickerCalls)
    }

    @Test fun guestButtonFromCloudReturnsToExistingBudget() {
        show(createdBudget = true)
        compose.onNodeWithTag("tab_Profile").performClick()
        compose.onNodeWithText("Авторизоваться").performClick()
        compose.onNodeWithText("Продолжить без авторизации").performClick()
        compose.onNodeWithTag("tab_Profile").assertIsSelected()
        compose.onNodeWithTag("budget_setup_screen").assertDoesNotExist()
        assertEquals(listOf(AppRoute.Main), navigation.backStack)
    }

    @Test fun allTabsNavigateAndSystemBackReturnsThroughDashboard() {
        show(createdBudget = true)
        for (tab in listOf("History", "Planned", "Profile")) {
            compose.onNodeWithTag("tab_$tab").performClick().assertIsSelected()
            compose.onNodeWithTag("tab_$tab").performClick().assertIsSelected()
            compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithTag("tab_Dashboard").assertIsSelected()
        }
        compose.onNodeWithTag("auth_screen").assertDoesNotExist()
    }

    @Test fun profileDetailsOpenOutsideMainAndBackRetainsProfileTab() {
        show(createdBudget = true)
        compose.onNodeWithTag("tab_Profile").performClick()
        compose.onNodeWithText("Семейный доступ").performScrollTo().performClick()
        compose.onNodeWithTag("family_access_screen").assertIsDisplayed()
        compose.onNodeWithTag("main_screen").assertDoesNotExist()
        assertEquals(listOf(AppRoute.Main, AppRoute.FamilyAccess), navigation.backStack)
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithTag("tab_Profile").assertIsSelected()
        compose.onNodeWithText("Облачная синхронизация").performScrollTo().performClick()
        compose.onNodeWithTag("cloud_sync_screen").assertIsDisplayed()
        compose.onNodeWithTag("main_screen").assertDoesNotExist()
        assertEquals(listOf(AppRoute.Main, AppRoute.CloudSync), navigation.backStack)
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("tab_Profile").assertIsSelected()
        compose.onNodeWithTag("profile_screen").assertIsDisplayed()
    }

    @Test fun familyGuestAuthenticationBackReturnsToFamilyScreen() {
        show(createdBudget = true)
        compose.onNodeWithTag("tab_Profile").performClick()
        compose.onNodeWithText("Семейный доступ").performScrollTo().performClick()
        compose.onNodeWithText("Авторизоваться").performClick()
        compose.onNodeWithTag("auth_screen").assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("family_access_screen").assertIsDisplayed()
        compose.onNodeWithTag("main_screen").assertDoesNotExist()
    }

    @Test fun createOperationOpensFromEveryTabAndBackReturnsToThatTab() {
        show(createdBudget = true)
        listOf("Dashboard", "History", "Planned", "Profile").forEach { tab ->
            compose.onNodeWithTag("tab_$tab").performClick()
            compose.onNodeWithTag("tab_AddOperation").assertIsDisplayed().performClick()
            assertEquals(listOf(AppRoute.Main, AppRoute.OperationCreate), navigation.backStack)
            compose.onNodeWithTag("operation_create_screen").assertIsDisplayed()
            compose.onNodeWithTag("main_screen").assertDoesNotExist()
            compose.onNodeWithTag("operation-create-input").assertIsDisplayed()
            compose.onNodeWithTag("tab_AddOperation").assertDoesNotExist()
            compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
            compose.waitUntil(5000) { navigation.backStack.toList() == listOf(AppRoute.Main) }
            assertEquals(listOf(AppRoute.Main), navigation.backStack)
            compose.onNodeWithTag("tab_$tab").assertIsSelected()
            compose.onNodeWithTag("tab_AddOperation").assertIsDisplayed()
        }
    }

    @Test fun savingOperationFromDashboardReturnsToDashboardAndUpdatesHistory() {
        show(createdBudget = true)
        compose.onNodeWithTag("tab_AddOperation").performClick()
        compose.onNodeWithTag("operation-create-input").performTextInput("Покупка из общей кнопки 42.50")
        compose.onNodeWithText("Продукты").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("tab_Dashboard").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tab_Dashboard").assertIsSelected()
        compose.onNodeWithTag("tab_AddOperation").assertIsDisplayed()
        compose.onNodeWithTag("tab_History").performClick()
        compose.onNodeWithText("Покупка из общей кнопки").assertIsDisplayed()
    }

    @Test fun cancellingOperationCreationKeepsHistoryFilter() {
        show(createdBudget = true)
        compose.onNodeWithTag("tab_History").performClick()
        compose.onNodeWithTag("history-type-INCOME").performClick().assertIsSelected()
        compose.onNodeWithTag("tab_AddOperation").performClick()
        compose.onNodeWithTag("operation-create-input").performTextInput("42")
        closeSoftKeyboard()
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithTag("tab_History").assertIsSelected()
        compose.onNodeWithTag("history-type-INCOME").assertIsSelected()
    }

    @Test fun historyFilterSurvivesSwitchingTabsAndAuthentication() {
        show(createdBudget = true)
        compose.onNodeWithTag("tab_History").performClick()
        compose.onNodeWithTag("history-type-INCOME").performClick().assertIsSelected()
        compose.onNodeWithTag("tab_Planned").performClick()
        compose.onNodeWithTag("tab_History").performClick()
        compose.onNodeWithTag("history-type-INCOME").assertIsSelected()
        compose.onNodeWithTag("tab_Profile").performClick()
        compose.onNodeWithText("Авторизоваться").performClick()
        compose.onNodeWithText("Продолжить без авторизации").performClick()
        compose.onNodeWithTag("tab_History").performClick()
        compose.onNodeWithTag("history-type-INCOME").assertIsSelected()
    }

    @Test fun dashboardScrollSurvivesTabSwitchAndSavedStateRestoration() {
        val restoration = StateRestorationTester(compose)
        show(createdBudget = true, restoration = restoration, fontScale = 2f)
        compose.waitUntil(5000) { compose.onAllNodesWithTag("dashboard-grid").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("dashboard-grid").performScrollToNode(hasTestTag("dashboard-widget-monthly-budget"))
        val top = compose.onNodeWithTag("dashboard-widget-monthly-budget").getUnclippedBoundsInRoot().top
        compose.onNodeWithTag("tab_History").performClick()
        compose.onNodeWithTag("tab_Dashboard").performClick()
        assertEquals(top, compose.onNodeWithTag("dashboard-widget-monthly-budget").getUnclippedBoundsInRoot().top)
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("tab_Dashboard").assertIsSelected()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("dashboard-widget-monthly-budget").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(top, compose.onNodeWithTag("dashboard-widget-monthly-budget").getUnclippedBoundsInRoot().top)
    }

    @Test fun historySheetSupportsEveryDismissMethodAndKeepsTheFilter() {
        show(createdBudget = true)
        val operation = seedSheetOperation()
        compose.onNodeWithTag("tab_History").performClick()
        compose.onNodeWithTag("history-type-EXPENSE").performClick()
        val dismissals: List<() -> Unit> = listOf(
            { compose.onNodeWithTag("edit_operation_close").performClick() },
            { androidx.test.espresso.Espresso.pressBack() },
            { compose.onNode(isDialog()).performTouchInput { click(Offset(centerX, 1f)) } },
            { compose.onNodeWithTag("edit_operation_bottom_sheet").performTouchInput { swipeDown() } },
        )
        dismissals.forEach { dismiss ->
            openSheet(operation)
            dismiss()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("edit_operation_bottom_sheet").fetchSemanticsNodes().isEmpty() }
            assertEquals(listOf(AppRoute.Main), navigation.backStack)
            compose.onNodeWithTag("tab_History").assertIsSelected()
            compose.onNodeWithTag("history-type-EXPENSE").assertIsSelected()
        }
    }

    @Test fun sheetSavesLocallyAndShowsSuccessOnHistoryOnEveryInvocation() {
        show(createdBudget = true)
        val operation = seedSheetOperation()
        compose.onNodeWithTag("tab_History").performClick()
        repeat(2) { attempt ->
            openSheet(operation)
            compose.onNodeWithTag("edit_operation_input").performTextReplacement("${50 + attempt}.00")
            compose.onNodeWithTag("edit_operation_save").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("edit_operation_bottom_sheet").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithText("Изменения сохранены").assertIsDisplayed()
            assertEquals(listOf(AppRoute.Main), navigation.backStack)
            compose.onNodeWithTag("operation-${operation.id}").assertExists()
            compose.onNodeWithContentDescription("Закрыть").performClick()
        }
    }

    @Test fun sheetAndHistoryScrollSurviveSavedStateRestoration() {
        val restoration = StateRestorationTester(compose)
        show(createdBudget = true, restoration = restoration)
        val operations = (0..23).map { seedSheetOperation("Тестовая операция $it") }
        val operation = operations[12]
        compose.onNodeWithTag("tab_History").performClick()
        compose.onNodeWithTag("operation_history_list").performScrollToNode(hasTestTag("operation-${operation.id}"))
        val top = compose.onNodeWithTag("operation-${operation.id}").getUnclippedBoundsInRoot().top
        openSheet(operation)
        val route = navigation.backStack.last()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("edit_operation_bottom_sheet").assertIsDisplayed()
        assertEquals(route, navigation.backStack.last())
        compose.onNodeWithTag("edit_operation_close").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("edit_operation_bottom_sheet").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("tab_History").assertIsSelected()
        assertEquals(top, compose.onNodeWithTag("operation-${operation.id}").getUnclippedBoundsInRoot().top)
    }

    @Test fun switchingBudgetDismissesAnOpenSheetWithoutOpeningAnEditorInTheNewBudget() {
        show(createdBudget = true)
        val operation = seedSheetOperation()
        compose.onNodeWithTag("tab_History").performClick()
        openSheet(operation)
        runBlocking {
            env.manager.select(env.registry.createLocal().id)
            val session = env.manager.active.value!!.scope.get<CloudBudgetSession>()
            session.createLocal("900.00".toBigDecimal(), session.today)
        }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("edit_operation_bottom_sheet").fetchSemanticsNodes().isEmpty() }
        assertFalse(navigation.backStack.any { it is AppRoute.EditOperationBottomSheet })
        compose.onNodeWithText("Изменение расхода").assertDoesNotExist()
    }

    @Test fun sheetSurvivesActivityRecreationAndReturnsItsAction() {
        show(createdBudget = true)
        val operation = seedSheetOperation()
        compose.onNodeWithTag("tab_History").performClick()
        compose.onNodeWithTag("history-type-EXPENSE").performClick()
        openSheet(operation)
        val route = navigation.backStack.last()
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { activity ->
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        compose.setContent(hostContent)
        compose.onNodeWithTag("edit_operation_bottom_sheet").assertIsDisplayed()
        compose.onNodeWithTag("tab_History").assertIsSelected()
        assertEquals(route, navigation.backStack.last())
        compose.onNodeWithTag("edit_operation_input").performTextReplacement("55.00")
        compose.onNodeWithTag("edit_operation_save").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("edit_operation_bottom_sheet").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Изменения сохранены").assertIsDisplayed()
        compose.onNodeWithTag("history-type-EXPENSE").assertIsSelected()
    }

    private fun seedSheetOperation(description: String = "Тестовая операция"): Operation = runBlocking {
        val store = env.manager.active.value!!.scope.get<OperationHistoryStore>()
        val context = store.context.first { it.initialSyncComplete && it.categories.isNotEmpty() }
        val category = context.categories.first { it.kind == CategoryKind.Expense }
        val today = LocalDate.now(java.time.ZoneId.of("Asia/Almaty"))
        store.create(NewOperation(OperationType.Expense, category.id, "42.00".toBigDecimal(), today, description))
        store.observeHistory(OperationHistoryFilter.currentMonth(today), 100).first { page ->
            page.items.any { it.description == description }
        }.items.first { it.description == description }
    }

    private fun openSheet(operation: Operation) {
        compose.waitUntil(5000) { compose.onAllNodesWithTag("operation-${operation.id}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("operation-${operation.id}").performClick()
        compose.onNodeWithTag("edit_operation_bottom_sheet").assertIsDisplayed()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("edit_operation_input").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(2, navigation.backStack.size)
    }

    @Test fun switchingBudgetClearsMainModelsWithoutLeakingThePreviousFilter() {
        show(createdBudget = true)
        compose.onNodeWithTag("tab_History").performClick()
        compose.onNodeWithTag("history-type-INCOME").performClick().assertIsSelected()
        runBlocking {
            env.manager.select(env.registry.createLocal().id)
            val session = env.manager.active.value!!.scope.get<CloudBudgetSession>()
            session.createLocal("900.00".toBigDecimal(), session.today)
        }
        compose.waitUntil(5000) {
            compose.onAllNodesWithTag("main_screen").fetchSemanticsNodes().isNotEmpty() &&
                !navigation.resolvingBudget.value
        }
        compose.onNodeWithTag("tab_History").performClick()
        compose.onNodeWithTag("history-type-INCOME").assertIsNotSelected()
        compose.onNodeWithTag("history-type-EXPENSE").assertIsNotSelected()
    }

}
