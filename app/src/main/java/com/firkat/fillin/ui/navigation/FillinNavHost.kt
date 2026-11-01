package com.firkat.fillin.ui.navigation

import android.app.Activity
import com.firkat.fillin.feature.operationhistory.ui.OperationPlanLinkBottomSheet
import com.firkat.fillin.feature.operationhistory.ui.PendingPlanLinkRecovery
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.result.LocalResultEventBus
import androidx.navigation3.runtime.result.rememberResultEventBusNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.firkat.fillin.feature.auth.domain.AuthSession
import com.firkat.fillin.feature.auth.ui.AuthScreen
import com.firkat.fillin.feature.auth.ui.AuthViewModel
import com.firkat.fillin.feature.auth.ui.GoogleSignInLauncher
import com.firkat.fillin.feature.auth.ui.messageResource
import com.firkat.fillin.feature.operationhistory.ui.EditOperationBottomSheet
import com.firkat.fillin.feature.plannedoperations.ui.PlannedOperationBottomSheet
import com.firkat.fillin.feature.budgetprofile.ui.ActiveBudgetContent
import com.firkat.fillin.feature.budgetprofile.ui.MainBudgetViewModelStore
import com.firkat.fillin.feature.budgetprofile.data.ActiveBudgetManager
import com.firkat.fillin.feature.budgetsetup.ui.BudgetSetupRoute
import com.firkat.fillin.feature.categories.ui.CategoriesRoute
import com.firkat.fillin.feature.editcategory.ui.EditCategoryRoute
import com.firkat.fillin.feature.launch.presentation.LaunchScreen
import com.firkat.fillin.feature.main.ui.MainScreen
import com.firkat.fillin.feature.operationhistory.ui.OperationCreateRoute
import com.firkat.fillin.feature.budgetsharing.ui.FamilyAccessRoute
import com.firkat.fillin.feature.sync.ui.CloudSyncRoute
import com.firkat.fillin.ui.component.SnackbarType
import com.firkat.fillin.ui.component.showSaldomiSnackbar
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
@OptIn(ExperimentalMaterial3Api::class)
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
    val operationCreateModels: MainBudgetViewModelStore = viewModel(key = "operation-create-models")
    mainModels.bind(runtime.takeIf { AppRoute.Main in navigation.backStack })
    operationCreateModels.bind(runtime.takeIf { AppRoute.OperationCreate in navigation.backStack })
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val sheet = navigation.backStack.lastOrNull() as? AppRoute.EditOperationBottomSheet
    val linkSheet = navigation.backStack.lastOrNull() as? AppRoute.OperationPlanLinkBottomSheet
    LaunchedEffect(runtime?.scope?.id, linkSheet) {
        if (linkSheet != null && linkSheet.budgetScopeId != runtime?.scope?.id) navigation.dismissOperationPlanLink(linkSheet)
    }
    val plannedSheet = navigation.backStack.lastOrNull() as? AppRoute.PlannedOperationBottomSheet
    LaunchedEffect(runtime?.scope?.id, sheet) {
        if (sheet != null && sheet.budgetScopeId != runtime?.scope?.id) {
            navigation.dismissEditOperationBottomSheet(sheet)
        }
    }
    LaunchedEffect(runtime?.scope?.id, plannedSheet) {
        if (plannedSheet != null && plannedSheet.budgetScopeId != runtime?.scope?.id) {
            navigation.dismissPlannedOperationBottomSheet(plannedSheet)
        }
    }
    DisposableEffect(mainModels, operationCreateModels, context) {
        onDispose {
            if ((context as? Activity)?.isChangingConfigurations != true) {
                mainModels.bind(null)
                operationCreateModels.bind(null)
            }
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbar.showSaldomiSnackbar(
                message = resources.getString(error.messageResource()),
                type = SnackbarType.Info
            )
            auth.errorShown(error)
        }
    }
    val sheetDismissController = remember { BottomSheetDismissController() }
    NavDisplay(
        modifier = Modifier.fillMaxSize(),
        backStack = navigation.backStack,
        onBack = { if ((sheet == null && plannedSheet == null && linkSheet == null) || sheetDismissController.canDismiss()) navigation.back() },
        sceneStrategies = listOf(remember { BottomSheetSceneStrategy<AppRoute>(sheetDismissController) }),
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
            rememberResultEventBusNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<AppRoute.Launch> {
                LaunchScreen(launchFailed, navigation::onLaunchShown, navigation::retryLaunch)
            }
            entry<AppRoute.Main> {
                ActiveBudgetContent(viewModelStoreOwner = mainModels) {
                    PendingPlanLinkRecovery(onPending = { id -> runtime?.scope?.id?.let { navigation.openOperationPlanLink(id, it) } }, isActive = navigation.backStack.lastOrNull() == AppRoute.Main)
                }
                MainScreen(
                    authBusy = state.busy,
                    onAccountClick = {
                        if (state.session is AuthSession.Authenticated) auth.signOut()
                        else navigation.openAuthentication()
                    },
                    snackbarHostState = snackbar,
                    budgetViewModelStoreOwner = mainModels,
                    user = (state.session as? AuthSession.Authenticated)?.user,
                    onCategoriesClick = navigation::openCategories,
                    onFamilyAccessClick = navigation::openFamilyAccess,
                    onCloudSyncClick = navigation::openCloudSync,
                    onCreateOperationClick = navigation::openOperationCreate,
                    onOperationClick = { operationId, resultKey ->
                        runtime?.scope?.id?.let { scopeId ->
                            navigation.openEditOperationBottomSheet(operationId, resultKey, scopeId)
                        }
                    },
                    onPlannedOperationEditorClick = { type ->
                        runtime?.scope?.id?.let { scopeId ->
                            navigation.openPlannedOperationBottomSheet(type, scopeId)
                        }
                    },
                )
            }
            entry<AppRoute.PlannedOperationBottomSheet>(metadata = BottomSheetSceneStrategy.bottomSheet()) { route ->
                // The selected editor is initialized by the Plans tab and retained across rotation.
                ActiveBudgetContent(viewModelStoreOwner = mainModels) {
                    if (route.budgetScopeId == runtime?.scope?.id) {
                        PlannedOperationBottomSheet(
                            type = route.type,
                            onClose = { navigation.dismissPlannedOperationBottomSheet(route) },
                        )
                    }
                }
            }
            entry<AppRoute.OperationPlanLinkBottomSheet>(metadata = BottomSheetSceneStrategy.bottomSheet()) { route ->
                val linkModels: MainBudgetViewModelStore = viewModel(key = "plan-link-${route.requestId}")
                linkModels.bind(runtime.takeIf { it?.scope?.id == route.budgetScopeId })
                ActiveBudgetContent(viewModelStoreOwner = linkModels) {
                    if (route.budgetScopeId == runtime?.scope?.id) {
                        OperationPlanLinkBottomSheet(route.operationId, onClose = { navigation.dismissOperationPlanLink(route) }, onChooseAnother = { navigation.chooseAnotherPlan(route) })
                    }
                }
            }
            entry<AppRoute.EditOperationBottomSheet>(metadata = BottomSheetSceneStrategy.bottomSheet()) { route ->
                val resultBus = LocalResultEventBus.current
                val sheetModels: MainBudgetViewModelStore = viewModel(key = "edit-operation-${route.requestId}")
                sheetModels.bind(runtime.takeIf { it?.scope?.id == route.budgetScopeId })
                ActiveBudgetContent(viewModelStoreOwner = sheetModels) {
                    EditOperationBottomSheet(
                        operationId = route.operationId,
                        onLinkSelected = { navigation.replaceEditorWithPlanLink(route) },
                        onClose = { navigation.dismissEditOperationBottomSheet(route) },
                        onResult = { result ->
                            if (route.budgetScopeId == runtime?.scope?.id && navigation.dismissEditOperationBottomSheet(route)) {
                                resultBus.sendResult(route.resultKey, result)
                            }
                        },
                    )
                }
            }
            entry<AppRoute.OperationCreate> {
                ActiveBudgetContent(viewModelStoreOwner = operationCreateModels) {
                    OperationCreateRoute(onBack = navigation::back, onCreated = { result -> runtime?.scope?.id?.let { navigation.operationCreated(result, it) } })
                }
            }
            entry<AppRoute.Categories> {
                ActiveBudgetContent {
                    CategoriesRoute(
                        onBack = navigation::back,
                        onCreateCategory = { navigation.openCategoryEditor() },
                        onEditCategory = { categoryId -> navigation.openCategoryEditor(categoryId) },
                    )
                }
            }
            entry<AppRoute.EditCategoryBottomSheet>(metadata = BottomSheetSceneStrategy.bottomSheet()) { route ->
                ActiveBudgetContent {
                    EditCategoryRoute(
                        categoryId = route.categoryId,
                        onClose = navigation::back,
                    )
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
