package com.firkat.fillin.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.closeSoftKeyboard
import com.firkat.fillin.feature.appaccess.domain.AppAccess
import com.firkat.fillin.feature.appaccess.domain.AppMode
import com.firkat.fillin.feature.appaccess.ui.AboutAppViewModel
import com.firkat.fillin.feature.auth.domain.*
import com.firkat.fillin.feature.auth.ui.GoogleSignInLauncher
import com.firkat.fillin.feature.auth.ui.interactor.AuthAction
import com.firkat.fillin.feature.auth.ui.interactor.AuthInteractor
import com.firkat.fillin.feature.auth.ui.interactor.AuthReducer
import com.firkat.fillin.feature.auth.ui.interactor.AuthState
import com.firkat.fillin.feature.auth.ui.viewmodel.AuthViewModel
import com.firkat.fillin.feature.budgetconnection.BudgetConnectionDeviceTest
import com.firkat.fillin.feature.budgetconnection.ui.BudgetConnectionViewModel
import com.firkat.fillin.feature.budgetprofile.data.BudgetDatabaseFactory
import com.firkat.fillin.feature.budgetsetup.data.RoomActiveBudgetReadiness
import com.firkat.fillin.feature.budgetsetup.domain.BudgetSetupMode
import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import com.firkat.fillin.feature.launch.data.RoomLocalBudgetRestorer
import com.firkat.fillin.feature.operationhistory.domain.model.Operation
import com.firkat.fillin.feature.operationhistory.domain.model.OperationHistoryFilter
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.feature.operationhistory.domain.repository.NewOperation
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryStore
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpenseFrequency
import com.firkat.fillin.feature.plannedexpenses.domain.repository.PlannedExpensesRepository
import com.firkat.fillin.feature.plannedincomes.domain.repository.PlannedIncomesRepository
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperation
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationType
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationsFilter
import com.firkat.fillin.feature.plannedoperations.domain.repository.PlannedOperationsRepository
import com.firkat.fillin.feature.sync.data.CloudBudgetSession
import com.firkat.fillin.ui.theme.FillinTheme
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.koin.compose.scope.UnboundKoinScope
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinDelicateAPI
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

@OptIn(KoinExperimentalAPI::class, KoinDelicateAPI::class, KoinInternalApi::class)
class AppNavigationUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val env = BudgetConnectionDeviceTest.Environment()
    private val access = object : AppAccess {
        override val mode = MutableStateFlow(AppMode.InternalFull)
        override val checking = MutableStateFlow(false)
        override fun installationId() = "a625153f-4be5-47b1-adfa-2c404603c034"
        override suspend fun refresh(
            manual: Boolean
        ) = true
        override fun revoke() {
            mode.value = AppMode.OfflineFree
        }
    }
    private var pickerCalls = 0
    private var authModelsCreated = 0
    private var profileCalls = 0
    private var signInAction: suspend () -> Unit = { env.auth.login("owner") }
    private var launcher = GoogleSignInLauncher {
        pickerCalls++
        "synthetic-credential"
    }
    private val auth = object : AuthRepository by env.auth {
        override suspend fun signInWithGoogle(
            idToken: String
        ) = signInAction()
        override suspend fun reloadUser() {}
    }
    private lateinit var navigation: AppNavigationViewModel
    private lateinit var authModel: AuthViewModel
    private lateinit var hostContent: @Composable () -> Unit

    @Test fun existingCloudSetupOffersFamilyCodeWithoutCreatingAnotherBudget() {
        env.auth.login("owner")
        show()
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("Вступить в семейный бюджет").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Вступить в семейный бюджет").performScrollTo().performClick()
        compose.onNodeWithText("Код приглашения").assertExists()
        assertTrue(env.server.creations.isEmpty())
        compose.onNodeWithTag("setup_back").performScrollTo().performClick()
        compose.onNodeWithText("Код приглашения").assertDoesNotExist()
    }

    @Test fun setupGoogleLoginReturnsToDraftInsteadOfReadyAccountBudget() {
        runBlocking {
            env.auth.login("owner")
            env.manager.restore()
            env.connection.connect("owner", env.connection.budgets("owner").single())
            env.auth.logout()
            env.manager.restore()
        }
        show()
        compose.onNodeWithTag("budget_setup_name").performScrollTo().performTextReplacement("Поездка")
        closeSoftKeyboard()
        compose.onNodeWithTag("setup_cloud").performScrollTo().performClick()
        compose.onNodeWithTag("setup_create").performScrollTo().performClick()
        compose.onNodeWithTag("auth_screen").assertIsDisplayed()
        compose.onNodeWithText("Продолжить с Google").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("budget_setup_name").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Поездка").assertExists()
        compose.onNodeWithTag("main_screen").assertDoesNotExist()
        assertTrue(env.server.creations.isEmpty())
    }

    @Test fun cancelingAuthenticationFromSetupKeepsDraftAndDoesNotReopenAuth() {
        show()
        compose.onNodeWithTag("budget_setup_name").performScrollTo().performTextReplacement("Поездка")
        closeSoftKeyboard()
        compose.onNodeWithTag("setup_cloud").performScrollTo().performClick()
        compose.onNodeWithTag("setup_create").performScrollTo().performClick()
        compose.onNodeWithTag("auth_screen").assertIsDisplayed()
        compose.runOnIdle { navigation.back() }
        compose.onNodeWithText("Поездка").assertExists()
        compose.onNodeWithTag("auth_screen").assertDoesNotExist()
    }

    @Test fun guestJoinAuthenticatesAndOpensTheInvitationPage() {
        show()
        compose.onNodeWithTag("setup_open_join").performScrollTo().performClick()
        compose.onNodeWithTag("auth_screen").assertIsDisplayed()
        compose.onNodeWithText("Продолжить с Google").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("setup_code").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(AppRoute.BudgetSetupJoin, navigation.backStack.last())
        assertTrue(env.server.creations.isEmpty())
    }

    @Test fun categoriesOpenFromProfileAndTheEditorSavesToTheOfflineList() {
        show(createdBudget = true)
        compose.onNodeWithContentDescription("Profile").performClick()
        compose.onNodeWithTag("profile_screen").assertIsDisplayed()
        compose.onNodeWithText("Категории").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf(AppRoute.Main, AppRoute.Profile, AppRoute.Categories), navigation.backStack)
        }
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("Новая категория", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Новая категория", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Новая категория").assertIsDisplayed()
        compose.runOnIdle { assertNull((navigation.backStack.last() as AppRoute.EditCategory).categoryId) }
        compose.onNodeWithText("Удалить").assertDoesNotExist()
        compose.onNodeWithText("Сохранить").assertIsNotEnabled()
        compose.onNodeWithText("Расход").assertIsSelected()
        compose.onNodeWithText("Доход").performClick().assertIsSelected()
        compose.onNode(hasSetTextAction()).performTextInput("Возврат за товар")
        closeSoftKeyboard()
        compose.onNodeWithText("Сохранить").assertIsEnabled().performClick()
        compose.waitUntil(5000) { navigation.backStack.lastOrNull() == AppRoute.Categories }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Возврат за товар"))
        compose.onNodeWithText("Возврат за товар").performClick()
        compose.onNodeWithText("Редактирование").assertIsDisplayed()
        compose.onNodeWithText("Доход").assertIsSelected().assertIsNotEnabled()
        compose.onNodeWithText("Сохранить").assertIsNotEnabled()
        compose.onNodeWithText("Удалить").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performTextReplacement("Возврат за билет")
        closeSoftKeyboard()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Продолжить редактирование").performClick()
        compose.onNodeWithText("Возврат за билет").assertExists()
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithText("Выйти без сохранения").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Возврат за товар"))
        compose.onNodeWithText("Возврат за товар").assertIsDisplayed()
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithTag("profile_screen").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf(AppRoute.Main, AppRoute.Profile), navigation.backStack) }
    }

    @Test fun categoryDraftSurvivesRecreationAndClosesOnBudgetSwitch() {
        show(createdBudget = true, fontScale = 2f)
        val runtime = requireNotNull(env.manager.active.value)
        val category = runBlocking {
            runtime.scope.get<com.firkat.fillin.feature.categories.domain.usecase.CreateCategory>()(
                "Поездки",
                CategoryKind.Expense,
                "CAR"
            )
        }
        compose.runOnIdle {
            navigation.openCategories()
            navigation.openCategoryEditor(runtime.scope.id, category.id)
        }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Сохранить").fetchSemanticsNodes().isNotEmpty() }
        val delete = compose.onNodeWithText("Удалить").assertIsDisplayed()
        val save = compose.onNodeWithText("Сохранить").assertIsDisplayed()
        assertTrue(delete.getUnclippedBoundsInRoot().bottom <= save.getUnclippedBoundsInRoot().top)
        compose.onNode(hasSetTextAction()).performTextReplacement("Черновик поездок")
        closeSoftKeyboard()
        save.assertIsEnabled()
        compose.activityRule.scenario.recreate()
        compose.setContent(hostContent)
        compose.onNodeWithText("Черновик поездок").assertExists()
        save.assertIsEnabled()

        runBlocking {
            env.manager.select(env.registry.createLocal().id)
            val session = env.manager.active.value!!.scope.get<CloudBudgetSession>()
            session.createLocal("900.00".toBigDecimal(), session.today)
        }
        compose.waitUntil(5000) { navigation.backStack.none { it is AppRoute.EditCategory } }
        compose.onNodeWithText("Черновик поездок").assertDoesNotExist()
    }

    private fun show(
        createdBudget: Boolean = false,
        restoration: StateRestorationTester? = null,
        fontScale: Float = 1f,
        expectMain: Boolean = createdBudget
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
        env.koin.loadModules(
            listOf(
                module {
                    single<AppAccess> { access }
                    viewModel { AboutAppViewModel(get()) }
                    viewModel {
                        authModelsCreated++
                        AuthViewModel(
                            AuthState(session = auth.session.value),
                            AuthReducer(),
                            AuthInteractor(
                                auth,
                                object : ServerAuthRepository {
                                    override suspend fun authenticate(
                                        uid: String
                                    ): ServerUser {
                                        profileCalls++
                                        return ServerUser("server-$uid", uid, null, null)
                                    }
                                },
                                Dispatchers.Main.immediate
                            )
                        )
                    }
                    viewModel {
                        AppNavigationViewModel(
                            auth,
                            RoomLocalBudgetRestorer(env.registry, BudgetDatabaseFactory(env.context), env.manager),
                            RoomActiveBudgetReadiness(env.manager),
                            get(),
                            access = access,
                            activeBudget = env.manager
                        )
                    }
                    viewModel {
                        com.firkat.fillin.feature.budgetsetup.ui.BudgetSetupViewModel(
                            auth, env.connection, env.creation,
                            com.firkat.fillin.feature.budgetsetup.data.RoomLocalBudgetCreator(
                                auth,
                                env.registry,
                                env.manager,
                                BudgetDatabaseFactory(env.context),
                                env.koin.get()
                            ),
                            env.sharing, get(), dispatcher = Dispatchers.IO, access = access, localCount = env.registry.observeLocalCount()
                        )
                    }
                    viewModel {
                        com.firkat.fillin.feature.budgetsharing.ui.FamilyBudgetJoinViewModel(auth, env.sharing)
                    }
                    viewModel { com.firkat.fillin.feature.budgetsharing.ui.FamilyMembersViewModel(env.sharing) }
                    viewModel {
                        com.firkat.fillin.feature.budgetinvitation.ui.BudgetInvitationViewModel(object : com.firkat.fillin.feature.budgetinvitation.domain.BudgetInvitationRepository {
                            override val target = kotlinx.coroutines.flow.flowOf<com.firkat.fillin.feature.budgetinvitation.domain.InvitationTarget?>(
                                null
                            )
                            override fun isCurrent(
                                target: com.firkat.fillin.feature.budgetinvitation.domain.InvitationTarget
                            ) = false
                            override suspend fun current(
                                target: com.firkat.fillin.feature.budgetinvitation.domain.InvitationTarget
                            ): com.firkat.fillin.feature.budgetinvitation.domain.BudgetInvitation? = error("Unused")
                            override suspend fun create(
                                target: com.firkat.fillin.feature.budgetinvitation.domain.InvitationTarget
                            ): com.firkat.fillin.feature.budgetinvitation.domain.CreatedInvitation = error("Unused")
                            override suspend fun revoke(
                                target: com.firkat.fillin.feature.budgetinvitation.domain.InvitationTarget,
                                invitation: com.firkat.fillin.feature.budgetinvitation.domain.BudgetInvitation
                            ) = Unit
                        })
                    }
                    viewModel {
                        com.firkat.fillin.feature.budgetupload.ui.LocalBudgetUploadViewModel(auth, env.uploadConnection)
                    }
                    single<com.firkat.fillin.feature.profile.domain.ProfileBudgetsRepository> {
                        com.firkat.fillin.feature.profile.data.RoomProfileBudgetsRepository(
                            auth,
                            env.registry,
                            env.manager,
                            BudgetDatabaseFactory(env.context),
                            env.connection,
                            get(),
                            env.uploads,
                            access = access
                        )
                    }
                    viewModel { com.firkat.fillin.feature.profile.ui.ProfileViewModel(get(), auth, access) }
                    viewModel {
                        val store = object : com.firkat.fillin.feature.profile.domain.DeletionStore {
                            override fun operations() =
                                emptyList<com.firkat.fillin.feature.profile.domain.DeletionOperation>()
                            override fun save(
                                operation: com.firkat.fillin.feature.profile.domain.DeletionOperation
                            ) = Unit
                            override fun blocked(
                                uid: String
                            ) = false
                            override fun block(
                                uid: String
                            ) = Unit
                            override fun finish(
                                uid: String
                            ) = Unit
                        }
                        com.firkat.fillin.feature.profile.ui.ProfileDeletionViewModel(
                            com.firkat.fillin.feature.profile.data.ProfileDeletionCoordinator(
                                store,
                                null,
                                auth,
                                token = { error("Unused") },
                                pause = {},
                                erase = {}
                            ),
                            auth
                        )
                    }
                    viewModel {
                        com.firkat.fillin.feature.budgetmanagement.ui.BudgetManagementViewModel(
                            com.firkat.fillin.feature.budgetmanagement.data.BudgetManagement(
                                env.context, env.registryDb, env.registry, env.manager, auth,
                                env.requests, env.uploads, get(), enqueue = {}
                            ),
                            auth
                        )
                    }
                    single<GoogleSignInLauncher> { launcher }
                    viewModel { parameters ->
                        BudgetConnectionViewModel(
                            auth,
                            env.connection,
                            env.creation,
                            parameters.getOrNull<Boolean>() ?: false
                        )
                    }
                }
            )
        )
        hostContent = {
            FillinTheme {
                UnboundKoinScope(env.koin.scopeRegistry.rootScope) {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                        authModel = koinViewModel(viewModelStoreOwner = compose.activity)
                        navigation = koinViewModel(viewModelStoreOwner = compose.activity)
                        FillinNavHost()
                    }
                }
            }
        }
        if (restoration == null) compose.setContent(hostContent) else restoration.setContent(hostContent)
        compose.waitUntil(5000) {
            compose.onAllNodesWithTag(if (expectMain) "main_screen" else "budget_setup_screen")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @After fun cleanup() {
        compose.activityRule.scenario.onActivity { it.setContentView(android.widget.FrameLayout(it)) }
        compose.waitForIdle()
        compose.activityRule.scenario.onActivity { it.viewModelStore.clear() }
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

    @Test fun localLaunchOpensMainWhileFirebaseRestores() {
        env.auth.session.value = AuthSession.Loading
        show(createdBudget = true)
        compose.onNodeWithTag("main_screen").assertIsDisplayed()
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
        compose.onNodeWithTag("sign_out_confirm").performClick()
        compose.onNodeWithTag("main_screen").assertIsDisplayed()
        compose.runOnIdle { navigation.back() }
        assertEquals(listOf(AppRoute.Main), navigation.backStack)
        assertEquals(AuthSession.Anonymous, auth.session.value)
        assertEquals(0, pickerCalls)
    }

    @Test fun explicitCloudCreationOpensAuthAndEmailOpensLogin() {
        show()
        openSetupAuth()
        compose.onNodeWithTag("auth_screen").assertIsDisplayed()
        compose.onNodeWithText("Профиль").assertDoesNotExist()
        compose.onNodeWithText("Продолжить с email").performClick()
        compose.onNodeWithTag("email_input").assertIsDisplayed()
    }

    @Test fun pendingEmailCanReturnToLocalBudgetThroughProfile() {
        env.auth.session.value =
            AuthSession.EmailVerificationRequired(
                AuthUser("pending", null, "pending@example.test", setOf("password"), false)
            )
        show(createdBudget = true)
        compose.onNodeWithTag("tab_Profile").performClick()
        compose.onNodeWithText("Подтвердите email").performScrollTo().performClick()
        compose.onNodeWithText("Я подтвердил email").assertExists()
        compose.onNodeWithText("Продолжить локально").performScrollTo().performClick()
        compose.onNodeWithTag("main_screen").assertIsDisplayed()
        assertTrue(auth.session.value is AuthSession.EmailVerificationRequired)
        assertEquals(listOf(AppRoute.Main), navigation.backStack)
    }

    @Test fun missingFirebaseConfigurationStillAllowsGuestSetup() {
        env.auth.session.value = AuthSession.Unavailable
        show()
        compose.onNodeWithTag("auth_screen").assertDoesNotExist()
        compose.onNodeWithText("Настройте свой бюджет").assertIsDisplayed()
        compose.onNodeWithText("Продолжить без авторизации").assertDoesNotExist()
        compose.onNodeWithTag("main_screen").assertDoesNotExist()
        assertEquals(0, pickerCalls)
    }

    @Test fun pendingGooglePickerBlocksRepeatedActionsAndCancellationPermitsRetry() {
        val result = CompletableDeferred<String>()
        launcher = GoogleSignInLauncher {
            pickerCalls++
            result.await()
        }
        show()
        openSetupAuth()
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

    @Test fun activityRecreationRetainsAuthModelAndDoesNotReloadRestoredProfile() {
        env.auth.login("owner")
        show()
        compose.waitUntil { profileCalls == 1 }
        val originalModel = authModel
        compose.activityRule.scenario.recreate()
        compose.setContent(hostContent)
        compose.onNodeWithTag("budget_setup_screen").assertIsDisplayed()
        assertSame(originalModel, authModel)
        assertEquals(1, authModelsCreated)
        assertEquals(1, profileCalls)
    }

    @Test fun stoppingAuthCancelsOnlyPickerAndRestartAllowsOneNewRequest() {
        var cancellations = 0
        launcher = GoogleSignInLauncher {
            pickerCalls++
            try {
                awaitCancellation()
            } finally {
                cancellations++
            }
        }
        show()
        compose.onNodeWithText("Продолжить с Google").performClick()
        compose.waitUntil { pickerCalls == 1 }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.waitUntil { !authModel.state.value.busy && cancellations == 1 }
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithText("Продолжить с Google").assertIsEnabled().performClick()
        compose.waitUntil { pickerCalls == 2 }
        assertEquals(1, authModelsCreated)
    }

    @Test fun leavingAuthCancelsPickerWithoutCancellingSharedModel() {
        var cancelled = false
        launcher = GoogleSignInLauncher {
            pickerCalls++
            try {
                awaitCancellation()
            } finally {
                cancelled = true
            }
        }
        show(createdBudget = true)
        compose.onNodeWithTag("tab_Profile").performClick()
        compose.onNodeWithText("Авторизоваться").performClick()
        compose.onNodeWithText("Продолжить с Google").performClick()
        compose.waitUntil { pickerCalls == 1 }
        compose.runOnIdle { navigation.back() }
        compose.onNodeWithTag("main_screen").assertIsDisplayed()
        compose.waitUntil { cancelled && !authModel.state.value.busy }
        compose.onNodeWithText("Авторизоваться").performClick()
        compose.onNodeWithText("Продолжить с Google").assertIsEnabled()
        assertEquals(1, authModelsCreated)
    }

    @Test fun FirebaseExchangeFinishesAfterNavigationWithOneSharedModelAndProfileLoad() {
        val completed = CompletableDeferred<Unit>()
        var cancelled = false
        signInAction = {
            env.auth.login("owner")
            try {
                completed.await()
            } finally {
                cancelled = !completed.isCompleted
            }
        }
        show(createdBudget = true)
        compose.onNodeWithTag("tab_Profile").performClick()
        compose.onNodeWithText("Авторизоваться").performClick()
        compose.onNodeWithText("Продолжить с Google").performClick()
        // Synchronize with composition before reading SnapshotStateList from the test thread.
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithTag("main_screen").fetchSemanticsNodes().isNotEmpty()
        }
        compose.runOnIdle { assertEquals(listOf(AppRoute.Main), navigation.backStack) }
        assertTrue(authModel.state.value.busy)
        assertFalse(cancelled)
        compose.runOnIdle { completed.complete(Unit) }
        compose.waitUntil(timeoutMillis = 10_000) { !authModel.state.value.busy && profileCalls == 1 }
        compose.onNodeWithContentDescription("Выйти из аккаунта").assertIsDisplayed()
        assertEquals(1, authModelsCreated)
    }

    @Test fun errorWhileAuthStoppedIsShownOnceAfterRestart() {
        show()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.runOnUiThread {
            authModel.onAction(AuthAction.GoogleClicked)
            authModel.onAction(AuthAction.CredentialFailed(authModel.state.value.operationId, AuthError.Network))
        }
        assertEquals(AuthError.Network, authModel.state.value.error)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithText("Не удалось выполнить вход. Проверьте интернет и повторите попытку.").assertIsDisplayed()
        compose.waitUntil(10000) { authModel.state.value.error == null }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithText(
            "Не удалось выполнить вход. Проверьте интернет и повторите попытку."
        ).assertDoesNotExist()
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
        compose.onNodeWithText("Настройте свой бюджет").assertIsDisplayed()
        compose.onNodeWithText("Продолжить без авторизации").assertDoesNotExist()
        compose.onNodeWithText("Профиль").assertDoesNotExist()
        assertEquals(AppRoute.BudgetSetup(BudgetSetupMode.Local), navigation.backStack.last())
        compose.onNodeWithTag("budget_setup_balance").performScrollTo().performTextReplacement("123,45")
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

    @Test fun offlineProfileOpensSharedSetupAndBackKeepsSelectedBudget() {
        access.revoke()
        show(createdBudget = true)
        val original = env.manager.active.value!!.profile.id
        openProfileBudgetCreation()
        compose.onNodeWithTag("setup_cloud").assertDoesNotExist()
        compose.onNodeWithTag("budget_setup_name").performScrollTo().performTextReplacement("Черновик")
        closeSoftKeyboard()
        compose.onNodeWithTag("setup_back").performScrollTo().performClick()
        compose.onNodeWithTag("tab_Profile").assertIsSelected()
        compose.onNodeWithTag("budget_setup_screen").assertDoesNotExist()
        assertEquals(original, env.manager.active.value!!.profile.id)
        compose.onNodeWithText("Создать новый").performScrollTo().performClick()
        compose.onNodeWithTag("budget_setup_name").assertTextContains("Личный бюджет")
    }

    @Test fun creatingFromProfileSelectsTheNewBudgetAndOpensDashboard() {
        show(createdBudget = true)
        openProfileBudgetCreation()
        compose.onNodeWithTag("budget_setup_name").performScrollTo().performTextReplacement("Отпуск")
        closeSoftKeyboard()
        compose.onNodeWithTag("setup_create").performScrollTo().performClick()
        compose.waitUntil(5000) { navigation.backStack.toList() == listOf(AppRoute.Main) }
        compose.onNodeWithTag("tab_Dashboard").assertIsSelected()
        assertEquals("Отпуск", env.manager.active.value!!.profile.name)
        compose.runOnIdle { navigation.back() }
        compose.onNodeWithTag("budget_setup_screen").assertDoesNotExist()
        assertTrue(env.server.creations.isEmpty())
    }

    @Test fun profileCloudCreationSurvivesAuthenticationAndActivityRecreation() {
        env.server.budgetExists = false
        show(createdBudget = true)
        openProfileBudgetCreation()
        compose.onNodeWithTag("budget_setup_name").performScrollTo().performTextReplacement("Поездка")
        closeSoftKeyboard()
        openSetupAuth()
        compose.onNodeWithText("Продолжить с Google").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("budget_setup_name").fetchSemanticsNodes().isNotEmpty() }
        compose.activityRule.scenario.recreate()
        compose.setContent(hostContent)
        compose.onNodeWithTag("budget_setup_name").assertTextContains("Поездка")
        compose.waitUntil(5000) {
            compose.onAllNodes(hasTestTag("setup_create") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("setup_create").performScrollTo().performClick()
        compose.waitUntil(5000) { navigation.backStack.toList() == listOf(AppRoute.Main) }
        compose.onNodeWithTag("tab_Dashboard").assertIsSelected()
        val (request, createdBudget) = env.server.creations.values.single()
        assertEquals("Поездка", request.name)
        assertEquals(createdBudget.id, env.manager.active.value!!.profile.budgetId)
    }

    private fun openProfileBudgetCreation() {
        compose.onNodeWithTag("tab_Profile").performClick()
        compose.onNodeWithText("Создать новый").performScrollTo().performClick()
        compose.onNodeWithTag("budget_setup_screen").assertIsDisplayed()
        compose.onNodeWithTag("budget_setup_date").assertDoesNotExist()
    }

    @Test fun localSetupRotationRetainsInput() {
        val restoration = StateRestorationTester(compose)
        show(restoration = restoration)
        compose.onNodeWithTag("budget_setup_balance").performScrollTo().performTextReplacement("456,78")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("456,78").assertIsDisplayed()
        closeSoftKeyboard()
        compose.onNodeWithTag("auth_screen").assertDoesNotExist()
        compose.onNodeWithTag("main_screen").assertDoesNotExist()
    }

    @Test fun GoogleLoginWithoutReadyBudgetOpensCloudSetupAndCreatesBudget() {
        env.server.budgetExists = false
        show()
        openSetupAuth()
        compose.onNodeWithText("Продолжить с Google").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Название бюджета").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("main_screen").assertDoesNotExist()
        compose.onNodeWithTag("auth_screen").assertDoesNotExist()
        assertEquals(AppRoute.BudgetSetup(BudgetSetupMode.Cloud), navigation.backStack.last())
        compose.onNodeWithText("Название бюджета").performTextReplacement("Семейный")
        closeSoftKeyboard()
        compose.onNodeWithText("Создать бюджет").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("main_screen").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(listOf(AppRoute.Main), navigation.backStack)
        assertEquals(1, env.server.creations.size)
        assertEquals(1, pickerCalls)
    }

    @Test fun restoredGoogleAccountSelectsExistingCloudBudgetBeforeMain() {
        env.auth.login("owner")
        show()
        compose.onNodeWithText("Выберите бюджет").performScrollTo().performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("Тестовый бюджет · Владелец").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("main_screen").assertDoesNotExist()
        compose.onNodeWithText("Тестовый бюджет · Владелец").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("main_screen").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(listOf(AppRoute.Main), navigation.backStack)
        assertEquals(0, pickerCalls)
    }

    @Test fun offlineSetupIsImmediateAndGrantPreservesDraft() {
        access.revoke()
        show()
        compose.onNodeWithTag("auth_screen").assertDoesNotExist()
        compose.onNodeWithTag("setup_cloud").assertDoesNotExist()
        compose.onNodeWithTag("budget_setup_name").performScrollTo().performTextReplacement("Поездка")
        closeSoftKeyboard()
        compose.runOnIdle { access.mode.value = AppMode.InternalFull }
        compose.onNodeWithTag("setup_cloud").assertExists()
        compose.onNodeWithText("Поездка").assertExists()
        compose.onNodeWithTag("auth_screen").assertDoesNotExist()
    }

    @Test fun revocationClosesCloudRouteAndHidesProfileActions() {
        show(createdBudget = true)
        compose.onNodeWithTag("tab_Profile").performClick()
        compose.runOnIdle { navigation.openCloudSync() }
        assertEquals(AppRoute.CloudSync, navigation.backStack.last())
        compose.runOnIdle { access.revoke() }
        compose.waitUntil(5000) { navigation.backStack.toList() == listOf(AppRoute.Main) }
        compose.onNodeWithText("Авторизоваться").assertDoesNotExist()
        compose.onNodeWithText("О приложении").assertExists()
        compose.runOnIdle {
            navigation.openAuthentication()
            navigation.openCloudSync()
            navigation.openFamilyAccess()
            assertEquals(listOf(AppRoute.Main), navigation.backStack)
        }
    }

    @Test fun revokedCloudBudgetStaysVisibleAndRejectsWrites() {
        env.koin.loadModules(listOf(module { single<AppAccess> { access } }))
        runBlocking {
            env.auth.login("owner")
            env.manager.restore()
            env.connection.connect("owner", env.connection.budgets("owner").single())
        }
        show(createdBudget = false, expectMain = true)
        compose.runOnIdle { access.revoke() }
        compose.onNodeWithTag("tab_AddOperation").assertDoesNotExist()
        compose.onNodeWithTag("tab_Profile").performClick()
        compose.onNodeWithText("Облачный бюджет доступен только для чтения").assertExists()
        compose.onNodeWithText("Тестовый бюджет").assertExists()
        runBlocking {
            val runtime = env.manager.active.value!!
            val lease = env.manager.acquire(runtime.profile.syncIdentity)!!
            lease.use {
                val store = lease.scope.get<com.firkat.fillin.feature.sync.data.BudgetStore>()
                val before = store.pending()
                val error = runCatching { store.enqueue("CREATE_CATEGORY") }.exceptionOrNull()
                assertEquals("access_denied", (error as? com.firkat.fillin.feature.sync.data.BudgetLocalFailure)?.code)
                assertEquals(before, store.pending())
            }
        }
    }

    private fun openSetupAuth() {
        compose.onNodeWithTag("setup_cloud").performScrollTo().performClick()
        compose.onNodeWithTag("setup_create").performScrollTo().performClick()
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

    @Test fun forecastDetailsOpenFromDashboardAndRetainTabAcrossRestoration() {
        val restoration = StateRestorationTester(compose)
        show(createdBudget = true, restoration = restoration)
        compose.waitUntil(5000) { compose.onAllNodesWithTag("dashboard-grid").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("dashboard-grid").performScrollToNode(hasTestTag("dashboard-widget-balance-forecast"))
        val details = compose.onNodeWithText("Подробный прогноз").performScrollTo()
        scrollAboveMainNavigation("dashboard-grid", details)
        details.performClick()
        compose.waitUntil(5000) { navigation.backStack.lastOrNull() is AppRoute.ForecastDetails }
        compose.onNodeWithText("Параметры").performClick().assertIsSelected()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Параметры").assertIsSelected()
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.waitUntil(5000) { navigation.backStack.toList() == listOf(AppRoute.Main) }
        compose.onNodeWithTag("tab_Dashboard").assertIsSelected()
    }

    @Test fun dashboardScrollSurvivesTabSwitchAndSavedStateRestoration() {
        val restoration = StateRestorationTester(compose)
        show(createdBudget = true, restoration = restoration, fontScale = 2f)
        compose.waitUntil(5000) { compose.onAllNodesWithTag("dashboard-grid").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("dashboard-grid").performScrollToNode(hasTestTag("dashboard-widget-balance-forecast"))
        val top = compose.onNodeWithTag("dashboard-widget-balance-forecast").getUnclippedBoundsInRoot().top
        compose.onNodeWithTag("tab_History").performClick()
        compose.onNodeWithTag("tab_Dashboard").performClick()
        assertEquals(top, compose.onNodeWithTag("dashboard-widget-balance-forecast").getUnclippedBoundsInRoot().top)
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("tab_Dashboard").assertIsSelected()
        compose.waitUntil(5000) {
            compose.onAllNodesWithTag("dashboard-widget-balance-forecast").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(top, compose.onNodeWithTag("dashboard-widget-balance-forecast").getUnclippedBoundsInRoot().top)
    }

    @Test fun historySheetSupportsEveryDismissMethodAndKeepsTheFilter() {
        show(createdBudget = true)
        val operation = seedSheetOperation()
        compose.onNodeWithTag("tab_History").performClick()
        compose.onNodeWithTag("history-type-EXPENSE").performClick()
        val dismissals: List<() -> Unit> = listOf(
            { compose.onNodeWithTag("edit_operation_close").performClick() },
            { androidx.test.espresso.Espresso.pressBack() }
        )
        dismissals.forEach { dismiss ->
            openSheet(operation)
            dismiss()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("operation_details").fetchSemanticsNodes().isEmpty() }
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
            closeSoftKeyboard()
            compose.onNodeWithTag("edit_operation_save").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("operation_details").fetchSemanticsNodes().isEmpty() }
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
        val row = compose.onNodeWithTag("operation-${operation.id}")
        scrollAboveMainNavigation("operation_history_list", row)
        val top = row.getUnclippedBoundsInRoot().top
        openSheet(operation)
        val route = navigation.backStack.last()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("operation_details").assertIsDisplayed()
        assertEquals(route, navigation.backStack.last())
        compose.onNodeWithTag("edit_operation_close").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("operation_details").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("tab_History").assertIsSelected()
        assertEquals(top, row.getUnclippedBoundsInRoot().top)
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
        compose.waitUntil(5000) { compose.onAllNodesWithTag("operation_details").fetchSemanticsNodes().isEmpty() }
        assertFalse(navigation.backStack.any { it is AppRoute.OperationDetails })
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
        compose.onNodeWithTag("operation_details").assertIsDisplayed()
        compose.onNodeWithTag("tab_History").assertDoesNotExist()
        assertEquals(route, navigation.backStack.last())
        compose.onNodeWithTag("edit_operation_input").performTextReplacement("55.00")
        compose.onNodeWithTag("edit_operation_save").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("operation_details").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Изменения сохранены").assertIsDisplayed()
        compose.onNodeWithTag("history-type-EXPENSE").assertIsSelected()
    }

    @Test fun plannedEditorsSaveAboveTheListAndKeepTheSelectedFilter() {
        show(createdBudget = true)
        val operations = seedPlannedOperations()
        compose.onNodeWithTag("tab_Planned").performClick()
        for (operation in operations) {
            compose.onNodeWithTag("planning-filter-${operation.type.name}").performClick()
            compose.onNodeWithText(plannedCategoryName(operation)).performClick()
            compose.onNodeWithTag("planning-rule-editor").assertIsDisplayed()
            assertEquals(operation.type, (navigation.backStack.last() as AppRoute.PlanningRuleEditor).type)
            compose.onNodeWithTag("planned-editor-amount").performTextReplacement("450.00")
            closeSoftKeyboard()
            compose.onNodeWithTag("planned-editor-save").performClick()
            waitForPlannedSheetToClose()
            compose.onNodeWithTag("planning-filter-selected").assertIsDisplayed()
            compose.onNodeWithTag("planning-filter-${operation.type.name}").assertDoesNotExist()
            compose.onNodeWithText(plannedCategoryName(operation)).assertIsDisplayed()
            assertEquals(listOf(AppRoute.Main), navigation.backStack)
            val saved = runBlocking {
                env.manager.active.value!!.scope.get<PlannedOperationsRepository>()
                    .getPlannedOperations(PlannedOperationsFilter.currentMonth(operation.dueOn))
                    .items.first { it.key == operation.key }
            }
            val amount = when (saved) {
                is PlannedOperation.Expense -> saved.expense.plannedAmount
                is PlannedOperation.Income -> saved.income.plannedAmount
            }
            assertEquals(0, "450.00".toBigDecimal().compareTo(amount))
            compose.onNodeWithTag("planning-filter-reset").performClick()
        }
    }

    @Test fun plannedSheetKeepsUnsavedInputAcrossActivityRecreationAndCloseReturnsToTheList() {
        show(createdBudget = true)
        val operation = seedPlannedOperations().first { it.type == PlannedOperationType.Income }
        compose.onNodeWithTag("tab_Planned").performClick()
        compose.onNodeWithTag("planning-filter-Income").performClick()
        compose.onNodeWithText(plannedCategoryName(operation)).performClick()
        compose.onNodeWithTag("planned-editor-amount").performTextReplacement("999.00")
        closeSoftKeyboard()
        val route = navigation.backStack.last()
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { activity ->
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        compose.setContent(hostContent)
        compose.onNodeWithTag("planning-rule-editor").assertIsDisplayed()
        compose.onNodeWithTag("planned-editor-amount").assertTextContains("999.00")
        assertEquals(route, navigation.backStack.last())
        compose.onNodeWithTag("planned-editor-close").performClick()
        compose.onNodeWithText("Подтвердить").performClick()
        waitForPlannedSheetToClose()
        compose.onNodeWithTag("planning-filter-selected").assertIsDisplayed()
        compose.onNodeWithText(plannedCategoryName(operation)).performClick()
        compose.onNodeWithTag("planned-editor-amount").assertTextContains("200.00")
        androidx.test.espresso.Espresso.pressBack()
        waitForPlannedSheetToClose()
        compose.onNodeWithTag("planning-filter-selected").assertIsDisplayed()
    }

    @Test fun plannedCreationUsesSheetsAndBudgetSwitchDismissesTheEditor() {
        show(createdBudget = true)
        compose.onNodeWithTag("tab_Planned").performClick()
        compose.onNodeWithTag("planned-create").performClick()
        compose.onNodeWithTag("planning-rule-editor").assertIsDisplayed()
        assertEquals(PlannedOperationType.Expense, (navigation.backStack.last() as AppRoute.PlanningRuleEditor).type)
        compose.onNodeWithTag("planned-editor-close").performClick()
        waitForPlannedSheetToClose()

        compose.onNodeWithTag("planning-filter-Income").performClick()
        compose.onNodeWithTag("planned-create").performClick()
        compose.onNodeWithTag("planning-rule-editor").assertIsDisplayed()
        assertEquals(PlannedOperationType.Income, (navigation.backStack.last() as AppRoute.PlanningRuleEditor).type)
        compose.onNodeWithTag("planned-editor-close").performClick()
        waitForPlannedSheetToClose()
        compose.onNodeWithTag("planning-filter-reset").performClick()

        val operation = seedPlannedOperations().first { it.type == PlannedOperationType.Expense }
        compose.onNodeWithText(plannedCategoryName(operation)).performClick()
        compose.onNodeWithTag("planning-rule-editor").assertIsDisplayed()
        runBlocking {
            env.manager.select(env.registry.createLocal().id)
            val session = env.manager.active.value!!.scope.get<CloudBudgetSession>()
            session.createLocal("900.00".toBigDecimal(), session.today)
        }
        waitForPlannedSheetToClose()
        assertFalse(navigation.backStack.any { it is AppRoute.PlanningRuleEditor })
    }

    private fun waitForPlannedSheetToClose() {
        compose.waitUntil(5000) {
            compose.onAllNodesWithTag("planning-rule-editor").fetchSemanticsNodes().isEmpty()
        }
    }

    private fun plannedCategoryName(
        operation: PlannedOperation
    ): String = when (operation) {
        is PlannedOperation.Expense -> operation.expense.categoryName
        is PlannedOperation.Income -> operation.income.categoryName
    }

    private fun seedPlannedOperations(): List<PlannedOperation> = runBlocking {
        val scope = env.manager.active.value!!.scope
        val store = scope.get<OperationHistoryStore>()
        val context = store.context.first { it.initialSyncComplete && it.categories.isNotEmpty() }
        val today = LocalDate.now(java.time.ZoneId.of("Asia/Almaty"))
        scope.get<PlannedExpensesRepository>().createRule(
            context.categories.first { it.kind == CategoryKind.Expense }.id,
            "100.00".toBigDecimal(),
            PlannedExpenseFrequency.Monthly,
            today.dayOfMonth,
            today
        )
        scope.get<PlannedIncomesRepository>().createPlannedIncome(
            context.categories.first { it.kind == CategoryKind.Income }.id,
            "200.00".toBigDecimal(),
            today
        )
        scope.get<PlannedOperationsRepository>().getPlannedOperations(PlannedOperationsFilter.currentMonth(today)).items
    }

    private fun seedSheetOperation(
        description: String = "Тестовая операция"
    ): Operation = runBlocking {
        val store = env.manager.active.value!!.scope.get<OperationHistoryStore>()
        val context = store.context.first { it.initialSyncComplete && it.categories.isNotEmpty() }
        val category = context.categories.first { it.kind == CategoryKind.Expense }
        val today = LocalDate.now(java.time.ZoneId.of("Asia/Almaty"))
        store.create(NewOperation(OperationType.Expense, category.id, "42.00".toBigDecimal(), today, description))
        store.observeHistory(OperationHistoryFilter.currentMonth(today), 100).first { page ->
            page.items.any { it.description == description }
        }.items.first { it.description == description }
    }

    private fun scrollAboveMainNavigation(
        scrollTag: String,
        item: SemanticsNodeInteraction
    ) {
        val barTop = compose.onNodeWithTag("tab_AddOperation").getUnclippedBoundsInRoot().top
        // Compose's scroll helpers use the full viewport, including the area under the bar.
        val coveredHeight = item.getUnclippedBoundsInRoot().bottom - barTop
        if (coveredHeight >= 0.dp) {
            val distance = with(compose.density) { (coveredHeight + 1.dp).toPx() }
            compose.onNodeWithTag(scrollTag).performSemanticsAction(SemanticsActions.ScrollBy) {
                it(0f, distance)
            }
        }
        assertTrue(item.getUnclippedBoundsInRoot().bottom <= barTop)
    }

    private fun openSheet(
        operation: Operation
    ) {
        compose.waitUntil(5000) {
            compose.onAllNodesWithTag("operation-${operation.id}").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("operation-${operation.id}").performClick()
        compose.onNodeWithTag("operation_details").assertIsDisplayed()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("edit_operation_input").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(2, navigation.backStack.size)
    }

    @Test fun switchingBudgetKeepsProfileScrollPosition() {
        show(createdBudget = true)
        val other = runBlocking {
            val originalId = env.manager.active.value!!.profile.id
            val profile = env.registry.createLocal("Резервный бюджет")
            env.manager.select(profile.id)
            val session = env.manager.active.value!!.scope.get<CloudBudgetSession>()
            session.createLocal("900.00".toBigDecimal(), session.today)
            env.manager.select(originalId)
            profile
        }
        compose.onNodeWithTag("tab_Profile").performClick()
        val card = compose.onNodeWithText("Резервный бюджет")
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("Резервный бюджет").fetchSemanticsNodes().isNotEmpty()
        }
        val sync = compose.onNodeWithText("Облачная синхронизация").performScrollTo()
        val position = sync.fetchSemanticsNode().boundsInRoot
        runBlocking { env.manager.select(other.id) }
        compose.waitUntil(5000) {
            compose.onAllNodes(hasText("Резервный бюджет") and isSelected()).fetchSemanticsNodes().isNotEmpty()
        }
        card.assertIsSelected()
        compose.onNodeWithTag("tab_Profile").assertIsSelected()
        sync.assertIsDisplayed()
        assertEquals(position, sync.fetchSemanticsNode().boundsInRoot)
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
