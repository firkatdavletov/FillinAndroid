package com.firkat.fillin.ui.navigation

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.result.LocalResultEventBus
import androidx.navigation3.runtime.result.rememberResultEventBusNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.firkat.fillin.feature.auth.domain.AuthSession
import com.firkat.fillin.feature.auth.domain.userOrNull
import com.firkat.fillin.feature.auth.ui.AuthScreen
import com.firkat.fillin.feature.auth.ui.components.messageRes
import com.firkat.fillin.feature.auth.ui.interactor.AuthAction
import com.firkat.fillin.feature.auth.ui.viewmodel.AuthViewModel
import com.firkat.fillin.feature.budgetprofile.data.ActiveBudgetManager
import com.firkat.fillin.feature.budgetprofile.ui.ActiveBudgetContent
import com.firkat.fillin.feature.budgetprofile.ui.MainBudgetViewModelStore
import com.firkat.fillin.feature.budgetsetup.ui.BudgetSetupRoute
import com.firkat.fillin.feature.budgetsetup.ui.BudgetSetupViewModel
import com.firkat.fillin.feature.budgetsetup.ui.interactor.BudgetSetupAction
import com.firkat.fillin.feature.budgetsetup.ui.interactor.SetupPage
import com.firkat.fillin.feature.budgetsharing.ui.FamilyAccessRoute
import com.firkat.fillin.feature.categories.ui.CategoriesRoute
import com.firkat.fillin.feature.dailylimit.ui.DailyLimitDetailsRoute
import com.firkat.fillin.feature.dashboard.widgets.balanceforecast.ui.ForecastDetailsRoute
import com.firkat.fillin.feature.editcategory.ui.EditCategoryRoute
import com.firkat.fillin.feature.launch.presentation.LaunchScreen
import com.firkat.fillin.feature.main.ui.MainScreen
import com.firkat.fillin.feature.operationhistory.ui.OperationCreateRoute
import com.firkat.fillin.feature.operationhistory.ui.OperationDetailsRoute
import com.firkat.fillin.feature.operationhistory.ui.OperationPlanLinkBottomSheet
import com.firkat.fillin.feature.operationhistory.ui.PendingPlanLinkRecovery
import com.firkat.fillin.feature.plannedoperations.ui.PlannedOperationBottomSheet
import com.firkat.fillin.feature.profile.ui.ProfileRoute
import com.firkat.fillin.feature.sync.ui.CloudSyncRoute
import com.firkat.fillin.ui.component.SnackbarType
import com.firkat.fillin.ui.component.showSaldomiSnackbar
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private object SetupPageKey : NavMetadataKey<SetupPage>

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun FillinNavHost(
    navigation: AppNavigationViewModel =
        koinViewModel(
            viewModelStoreOwner = checkNotNull(LocalActivity.current as? ComponentActivity)
        ),
    auth: AuthViewModel =
        koinViewModel(
            viewModelStoreOwner = checkNotNull(LocalActivity.current as? ComponentActivity)
        ),
    setup: BudgetSetupViewModel =
        koinViewModel(
            viewModelStoreOwner = checkNotNull(LocalActivity.current as? ComponentActivity)
        ),
    budgets: ActiveBudgetManager = koinInject()
) {
    val setupState by setup.state.collectAsStateWithLifecycle()
    val state by auth.state.collectAsStateWithLifecycle()
    LaunchedEffect(navigation.backStack.lastOrNull()) {
        if (navigation.backStack.lastOrNull() == AppRoute.Main && setup.state.value.initialized) {
            setup.onAction(BudgetSetupAction.Reset)
        }
    }

    LaunchedEffect(
        navigation.backStack.lastOrNull(),
        setupState.uid,
        state.session
    ) {
        if (navigation.backStack.lastOrNull() == AppRoute.Auth && !navigation.configuringBudget) {
            setup.onAction(
                BudgetSetupAction.Reset
            )
        }
        if (navigation.backStack.lastOrNull()?.isBudgetSetup() == true &&
            setupState.authTarget != null &&
            (setupState.uid != null || state.session !is AuthSession.Authenticated)
        ) {
            setup.onAction(BudgetSetupAction.AuthReturned)
        }
    }
    val launchFailed by navigation.launchFailed.collectAsStateWithLifecycle()
    val runtime by budgets.active.collectAsStateWithLifecycle()
    val access: com.firkat.fillin.feature.appaccess.domain.AppAccess = koinInject()
    val mode by access.mode.collectAsStateWithLifecycle()
    val fullAccess = mode == com.firkat.fillin.feature.appaccess.domain.AppMode.InternalFull
    val readOnly = !fullAccess && runtime?.profile?.firebaseUid != null
    LaunchedEffect(fullAccess, readOnly) { navigation.closeRestrictedRoutes() }
    val mainModels: MainBudgetViewModelStore = viewModel(key = "main-budget-models")
    mainModels.bind(runtime.takeIf { AppRoute.Main in navigation.backStack })
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val sheet = navigation.backStack.lastOrNull() as? AppRoute.OperationDetails
    val linkSheet = navigation.backStack.lastOrNull() as? AppRoute.OperationPlanLinkBottomSheet
    LaunchedEffect(
        runtime?.scope?.id,
        linkSheet
    ) {
        if (linkSheet != null && linkSheet.budgetScopeId != runtime?.scope?.id) {
            navigation.dismissOperationPlanLink(
                linkSheet
            )
        }
    }
    val editorRoute = navigation.backStack.lastOrNull() as? AppRoute.PlanningRuleEditor
    LaunchedEffect(
        runtime?.scope?.id,
        editorRoute
    ) {
        if (editorRoute != null && editorRoute.budgetScopeId != runtime?.scope?.id) {
            navigation.dismissPlanningRuleEditor(
                editorRoute
            )
        }
    }
    val categoryEditor = navigation.backStack.lastOrNull() as? AppRoute.EditCategory
    LaunchedEffect(runtime?.scope?.id, categoryEditor) {
        if (categoryEditor != null && categoryEditor.budgetScopeId != runtime?.scope?.id) {
            navigation.dismissCategoryEditor(categoryEditor)
        }
    }
    val plannedSheet = navigation.backStack.lastOrNull() as? AppRoute.PlannedOperationBottomSheet
    LaunchedEffect(
        runtime?.scope?.id,
        sheet
    ) {
        if (sheet != null && sheet.budgetScopeId != runtime?.scope?.id) {
            navigation.dismissOperationDetails(sheet)
        }
    }
    LaunchedEffect(
        runtime?.scope?.id,
        plannedSheet
    ) {
        if (plannedSheet != null && plannedSheet.budgetScopeId != runtime?.scope?.id) {
            navigation.dismissPlannedOperationBottomSheet(plannedSheet)
        }
    }
    DisposableEffect(
        mainModels,
        context
    ) {
        onDispose {
            if ((context as? Activity)?.isChangingConfigurations != true) {
                mainModels.bind(null)
            }
        }
    }
    val authVisible = navigation.backStack.lastOrNull() in listOf(AppRoute.Auth, AppRoute.AccountMethods)
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(
        authVisible,
        state.errorId,
        state.error,
        lifecycleOwner,
        resources
    ) {
        if (!authVisible) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                state.error?.let { error ->
                    snackbar.showSaldomiSnackbar(resources.getString(error.messageRes), type = SnackbarType.Info)
                    auth.onAction(AuthAction.ErrorShown(state.errorId))
                }
            }
        }
    }
    val sheetDismissController = remember { BottomSheetDismissController() }
    val setupContent: @Composable (AppRoute, SetupPage) -> Unit = { route, page ->
        BudgetSetupRoute(
            viewModel = setup,
            onBack = { if (navigation.exitSetup()) (context as? Activity)?.finish() },
            onSignIn = navigation::authenticateForSetup,
            onComplete = navigation::finishSetup,
            onPageChange = navigation::showSetupPage,
            fromProfile = navigation.setupFromProfile,
            page = page,
            isActive = navigation.backStack.lastOrNull() == route
        )
    }

    NavDisplay(
        modifier = Modifier.fillMaxSize().clipToBounds(),
        backStack = navigation.backStack,
        transitionSpec = {
            val from = initialState.metadata[SetupPageKey]
            val to = targetState.metadata[SetupPageKey]
            // Setup pages replace the top entry, so NavDisplay treats those changes as forward.
            val returnsWithinSetup = when (from) {
                SetupPage.Join -> to == SetupPage.Create
                SetupPage.List -> setupState.localFirst && to == SetupPage.Create
                SetupPage.Create -> !setupState.localFirst && to == SetupPage.List
                null -> false
            }
            slideNavigation(if (returnsWithinSetup) SlideDirection.End else SlideDirection.Start)
        },
        popTransitionSpec = { slideNavigation(SlideDirection.End) },
        predictivePopTransitionSpec = { slideNavigation(SlideDirection.End) },
        onBack = {
            if (navigation.backStack.lastOrNull()?.isBudgetSetup() ==
                true
            ) {
                setup.onAction(BudgetSetupAction.Back)
            } else if ((plannedSheet == null && linkSheet == null) ||
                sheetDismissController.canDismiss()
            ) {
                navigation.back()
            }
        },
        sceneStrategies = listOf(remember { BottomSheetSceneStrategy<AppRoute>(sheetDismissController) }),
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
                rememberResultEventBusNavEntryDecorator()
            ),
        entryProvider =
            entryProvider {
                entry<AppRoute.Launch> {
                    LaunchScreen(
                        failed = launchFailed,
                        onShown = navigation::onLaunchShown,
                        onRetry = navigation::retryLaunch
                    )
                }
                entry<AppRoute.DailyLimitDetails> { route ->
                    val models: MainBudgetViewModelStore = viewModel(key = "daily-limit-details")
                    models.bind(runtime.takeIf { it?.scope?.id == route.budgetScopeId })
                    LaunchedEffect(runtime?.scope?.id) {
                        if (runtime?.scope?.id != route.budgetScopeId) navigation.back()
                    }
                    ActiveBudgetContent(viewModelStoreOwner = models) {
                        DailyLimitDetailsRoute(onBack = navigation::back)
                    }
                }
                entry<AppRoute.ForecastDetails> { route ->
                    val models: MainBudgetViewModelStore = viewModel(key = "forecast-details")
                    models.bind(runtime.takeIf { it?.scope?.id == route.budgetScopeId })
                    LaunchedEffect(runtime?.scope?.id) {
                        if (runtime?.scope?.id != route.budgetScopeId) navigation.back()
                    }
                    ActiveBudgetContent(viewModelStoreOwner = models) {
                        ForecastDetailsRoute(onBack = navigation::back)
                    }
                }
                entry<AppRoute.Main> {
                    if (!readOnly) {
                        ActiveBudgetContent(viewModelStoreOwner = mainModels) {
                            PendingPlanLinkRecovery(
                                onPending = { id ->
                                    runtime?.scope?.id?.let { navigation.openOperationPlanLink(id, it) }
                                },
                                isActive =
                                    navigation.backStack.lastOrNull() == AppRoute.Main
                            )
                        }
                    }
                    MainScreen(
                        modifier = Modifier.fillMaxSize(),
                        readOnly = readOnly,
                        onAccountClick = {
                            navigation.openProfile()
                        },
                        snackbarHostState = snackbar,
                        budgetViewModelStoreOwner = mainModels,
                        selectedDestination = navigation.mainDestination,
                        onSelectDestination = navigation::selectMainDestination,
                        onForecastClick = { runtime?.scope?.id?.let(navigation::openForecast) },
                        onCreateOperationClick = navigation::openOperationCreate,
                        onOperationClick = { operationId, resultKey ->
                            runtime?.scope?.id?.let { scopeId ->
                                navigation.openOperationDetails(operationId, resultKey, scopeId)
                            }
                        },
                        onPlannedOperationEditorClick = { type, ruleId ->
                            runtime?.scope?.id?.let { scopeId ->
                                navigation.openPlanningRuleEditor(type, scopeId, ruleId)
                            }
                        }
                    )
                }
                entry<AppRoute.PlanningRuleEditor> { route ->
                    val editorModels: MainBudgetViewModelStore = viewModel(key = "rule-editor-${route.requestId}")
                    editorModels.bind(runtime.takeIf { it?.scope?.id == route.budgetScopeId })
                    ActiveBudgetContent(viewModelStoreOwner = editorModels) {
                        if (route.budgetScopeId == runtime?.scope?.id) {
                            com.firkat.fillin.feature.plannedoperations.ui
                                .PlanningRuleEditorRoute(route.type, route.ruleId, route.requestId, onClose = {
                                    navigation.dismissPlanningRuleEditor(route)
                                })
                        }
                    }
                }
                entry<AppRoute.PlannedOperationBottomSheet>(
                    metadata = BottomSheetSceneStrategy.bottomSheet()
                ) { route ->
                    // The selected editor is initialized by the Plans tab and retained across rotation.
                    ActiveBudgetContent(viewModelStoreOwner = mainModels) {
                        if (route.budgetScopeId == runtime?.scope?.id) {
                            PlannedOperationBottomSheet(
                                type = route.type,
                                onClose = { navigation.dismissPlannedOperationBottomSheet(route) }
                            )
                        }
                    }
                }
                entry<AppRoute.OperationPlanLinkBottomSheet>(
                    metadata = BottomSheetSceneStrategy.bottomSheet()
                ) { route ->
                    val linkModels: MainBudgetViewModelStore = viewModel(key = "plan-link-${route.requestId}")
                    linkModels.bind(runtime.takeIf { it?.scope?.id == route.budgetScopeId })
                    ActiveBudgetContent(viewModelStoreOwner = linkModels) {
                        if (route.budgetScopeId == runtime?.scope?.id) {
                            OperationPlanLinkBottomSheet(route.operationId, onClose = {
                                navigation.dismissOperationPlanLink(route)
                            }, onChooseAnother = { navigation.chooseAnotherPlan(route) })
                        }
                    }
                }
                entry<AppRoute.OperationPlanPicker> { route ->
                    val models: MainBudgetViewModelStore = viewModel(key = "plan-picker-${route.operationId}")
                    models.bind(runtime.takeIf { it?.scope?.id == route.budgetScopeId })
                    LaunchedEffect(runtime?.scope?.id) {
                        if (runtime?.scope?.id !=
                            route.budgetScopeId
                        ) {
                            navigation.back()
                        }
                    }
                    ActiveBudgetContent(viewModelStoreOwner = models) {
                        com.firkat.fillin.feature.operationhistory.ui
                            .OperationPlanPickerRoute(route.operationId, navigation::back)
                    }
                }
                entry<AppRoute.OperationDetails> { route ->
                    val resultBus = LocalResultEventBus.current
                    val sheetModels: MainBudgetViewModelStore = viewModel(key = "edit-operation-${route.requestId}")
                    sheetModels.bind(runtime.takeIf { it?.scope?.id == route.budgetScopeId })
                    ActiveBudgetContent(viewModelStoreOwner = sheetModels) {
                        OperationDetailsRoute(
                            readOnly = readOnly,
                            operationId = route.operationId,
                            onLinkSelected = { navigation.openOperationPlanPicker(route) },
                            onOpenPlan = { type, id -> navigation.openDetailsPlanningRule(route, type, id) },
                            onClose = { navigation.dismissOperationDetails(route) },
                            onResult = { result ->
                                if (route.budgetScopeId == runtime?.scope?.id &&
                                    navigation.dismissOperationDetails(
                                        route
                                    )
                                ) {
                                    resultBus.sendResult(route.resultKey, result)
                                }
                            }
                        )
                    }
                }
                entry<AppRoute.OperationCreate> {
                    val models: MainBudgetViewModelStore = viewModel()
                    models.bind(runtime)
                    ActiveBudgetContent(viewModelStoreOwner = models) {
                        OperationCreateRoute(
                            onBack = {
                                if (navigation.backStack.lastOrNull() == AppRoute.OperationCreate) navigation.back()
                            },
                            onCreated = { result ->
                                runtime?.scope?.id?.let { navigation.operationCreated(result, it) }
                            }
                        )
                    }
                }
                entry<AppRoute.Categories> {
                    ActiveBudgetContent {
                        CategoriesRoute(
                            readOnly = readOnly,
                            onBack = navigation::back,
                            onCreateCategory = { runtime?.scope?.id?.let { navigation.openCategoryEditor(it) } },
                            onEditCategory = { categoryId ->
                                runtime?.scope?.id?.let { navigation.openCategoryEditor(it, categoryId) }
                            }
                        )
                    }
                }
                entry<AppRoute.EditCategory> { route ->
                    val models: MainBudgetViewModelStore = viewModel(key = "category-editor")
                    models.bind(runtime.takeIf { it?.scope?.id == route.budgetScopeId })
                    if (route.budgetScopeId == runtime?.scope?.id) {
                        ActiveBudgetContent(viewModelStoreOwner = models) {
                            EditCategoryRoute(
                                categoryId = route.categoryId,
                                readOnly = readOnly,
                                onClose = { navigation.dismissCategoryEditor(route) }
                            )
                        }
                    }
                }
                entry<AppRoute.FamilyAccess> {
                    FamilyAccessRoute(onBack = navigation::back, onSignIn = navigation::openAuthentication)
                }
                entry<AppRoute.CloudSync> {
                    ActiveBudgetContent {
                        CloudSyncRoute(onBack = navigation::back, onSignIn = navigation::openAuthentication)
                    }
                }
                entry<AppRoute.AccountMethods> {
                    AuthScreen(
                        onContinueAsGuest = navigation::back,
                        onContinueWithAccount = navigation::back,
                        methods = true,
                        onBack = navigation::back
                    )
                }
                entry<AppRoute.Auth> {
                    AuthScreen(
                        onContinueAsGuest = navigation::continueAsGuest,
                        onContinueWithAccount = navigation::continueWithAccount
                    )
                }
                entry<AppRoute.BudgetSetup>(metadata = metadata { put(SetupPageKey, SetupPage.Create) }) { route ->
                    setupContent(route, SetupPage.Create)
                }
                entry<AppRoute.BudgetSetupList>(metadata = metadata { put(SetupPageKey, SetupPage.List) }) { route ->
                    setupContent(route, SetupPage.List)
                }
                entry<AppRoute.BudgetSetupJoin>(metadata = metadata { put(SetupPageKey, SetupPage.Join) }) { route ->
                    setupContent(route, SetupPage.Join)
                }
                entry(AppRoute.Profile) {
                    ProfileRoute(
                        user = state.session.userOrNull,
                        authBusy = state.busy,
                        onAccountClick = {
                            if (state.session.userOrNull != null) {
                                auth.onAction(AuthAction.SignOut)
                            } else {
                                navigation.openAuthentication()
                            }
                        },
                        onMethodsClick = navigation::openAccountMethods,
                        onCategoriesClick = navigation::openCategories,
                        budgetReadOnly = readOnly,
                        onFamilyAccessClick = navigation::openFamilyAccess,
                        onCloudSyncClick = navigation::openCloudSync,
                        onCreateBudgetClick = navigation::openBudgetCreation
                    )
                }
            }
    )
}
