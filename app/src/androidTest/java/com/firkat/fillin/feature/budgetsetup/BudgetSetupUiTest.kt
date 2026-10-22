package com.firkat.fillin.feature.budgetsetup

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModelStore
import androidx.test.espresso.Espresso.closeSoftKeyboard
import com.firkat.fillin.feature.auth.domain.*
import com.firkat.fillin.feature.auth.ui.AuthViewModel
import com.firkat.fillin.feature.auth.ui.GoogleSignInLauncher
import com.firkat.fillin.feature.budgetconnection.BudgetConnectionDeviceTest
import com.firkat.fillin.feature.budgetconnection.ui.BudgetConnectionViewModel
import com.firkat.fillin.feature.budgetprofile.domain.BudgetProfileKind
import com.firkat.fillin.feature.budgetprofile.ui.ActiveBudgetContent
import com.firkat.fillin.feature.budgetsetup.domain.BudgetSetupRepository
import com.firkat.fillin.feature.budgetsetup.ui.BudgetSetupScreen
import com.firkat.fillin.feature.budgetsetup.ui.BudgetSetupGate
import com.firkat.fillin.feature.budgetsetup.ui.BudgetSetupViewModel
import com.firkat.fillin.ui.theme.FillinTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

class BudgetSetupUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val server = BudgetConnectionDeviceTest.Server().apply { budgetExists = false }
    private val env = BudgetConnectionDeviceTest.Environment(server)
    private val models = ViewModelStore()
    private var pickerCalls = 0
    private var launcher = GoogleSignInLauncher { pickerCalls++; "synthetic-credential" }
    private val auth = object : AuthRepository by env.auth {
        override suspend fun signInWithGoogle(idToken: String) { env.auth.login("owner") }
    }
    private lateinit var model: BudgetSetupViewModel
    private lateinit var authModel: AuthViewModel
    private lateinit var cloudModel: BudgetConnectionViewModel
    private var background = Color.Unspecified

    private fun showGate(restoration: StateRestorationTester? = null) {
        runBlocking { env.manager.restore() }
        env.koin.loadModules(listOf(module {
            viewModel { AuthViewModel(auth) }
            single<GoogleSignInLauncher> { launcher }
            viewModel { parameters -> BudgetConnectionViewModel(auth, env.connection, env.creation, parameters.getOrNull<Boolean>() ?: false) }
        }))
        val content: @Composable () -> Unit = {
            FillinTheme {
                ActiveBudgetContent(env.manager, auth) {
                    BudgetSetupGate { Text("Бюджет готов") }
                }
            }
        }
        if (restoration == null) compose.setContent(content) else restoration.setContent(content)
        // The gate resolves Room readiness asynchronously before showing the welcome actions.
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("Продолжить без авторизации").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun show(dark: Boolean = false, compact: Boolean = false, restoration: StateRestorationTester? = null) {
        runBlocking { env.manager.restore() }
        model = BudgetSetupViewModel(env.manager.active.value!!.scope.get<BudgetSetupRepository>())
        authModel = AuthViewModel(auth)
        cloudModel = BudgetConnectionViewModel(auth, env.connection, env.creation, autoStartCreation = true)
        models.put("setup", model); models.put("auth", authModel); models.put("cloud", cloudModel)
        val content: @Composable () -> Unit = {
            FillinTheme(darkTheme = dark, dynamicColor = false) {
                val color = MaterialTheme.colorScheme.background
                SideEffect { background = color }
                if (compact) {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                        Box(Modifier.fillMaxWidth().height(220.dp)) { BudgetSetupScreen(model, authModel, launcher, cloudModel) }
                    }
                } else BudgetSetupScreen(model, authModel, launcher, cloudModel)
            }
        }
        if (restoration == null) compose.setContent(content) else restoration.setContent(content)
    }

    @After fun cleanup() {
        compose.activityRule.scenario.onActivity { it.setContentView(android.widget.FrameLayout(it)) }
        compose.waitForIdle()
        models.clear(); env.close()
    }

    @Test fun gateOpensAppAfterLocalSetup() {
        showGate()
        compose.onNodeWithText("Продолжить без авторизации").performClick()
        compose.onNodeWithText("Начальный баланс, ₸").performTextReplacement("123.45")
        closeSoftKeyboard()
        compose.onNodeWithText("Создать бюджет").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Бюджет готов").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Бюджет готов").assertIsDisplayed()
        assertEquals(0, pickerCalls)
        assertTrue(server.creations.isEmpty())
    }

    @Test fun gateOpensAppAfterCloudProfileReplacesItsViewModelStore() {
        showGate()
        compose.onNodeWithText("Войти через Google").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Название бюджета").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Название бюджета").performTextReplacement("Общий")
        closeSoftKeyboard()
        compose.onNodeWithText("Создать облачный бюджет").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Бюджет готов").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, server.creations.size)
    }

    @Test fun gateRestoresStepAndInputWithANewBudgetViewModelStore() {
        val restoration = StateRestorationTester(compose)
        showGate(restoration)
        compose.onNodeWithText("Продолжить без авторизации").performClick()
        compose.onNodeWithText("Начальный баланс, ₸").performTextReplacement("456,78")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("456,78").assertIsDisplayed()
        compose.onNodeWithText("Создать бюджет").performScrollTo().assertIsDisplayed()
    }

    @Test fun createChoiceStoresLocalBudgetWithoutGoogleOrCloudCreation() {
        show()
        compose.onNodeWithText("Продолжить без авторизации").performClick()
        compose.onNodeWithText("Начальный баланс, ₸").performTextReplacement("123.45")
        closeSoftKeyboard()
        compose.onNodeWithText("Создать бюджет").performScrollTo().performClick()
        try {
            compose.waitUntil(5000) { model.ready.value == true }
        } catch (error: ComposeTimeoutException) {
            throw AssertionError("Local setup: ready=${model.ready.value}, busy=${model.busy}, error=${model.error}\n${compose.onRoot().printToString()}", error)
        }
        assertEquals(0, pickerCalls)
        assertTrue(server.creations.isEmpty())
    }

    @Test fun googleButtonDirectlyOpensFirstCloudBudgetFormAndCreatesIt() {
        show()
        compose.onNodeWithText("Войти через Google").performClick()
        compose.waitUntil(5000) { cloudModel.state.value.draft != null }
        compose.onNodeWithText("Аккаунт").assertDoesNotExist()
        compose.onNodeWithText("Название бюджета").performTextReplacement("Семья")
        compose.onNodeWithText("Начальный баланс, ₸").performTextReplacement("125,50")
        closeSoftKeyboard()
        compose.onNodeWithText("Создать облачный бюджет").performScrollTo().performClick()
        compose.waitUntil(5000) { env.manager.active.value?.profile?.kind == BudgetProfileKind.CLOUD }
        assertEquals(1, pickerCalls)
        assertEquals(1, server.creations.size)
        assertEquals("125.50", server.openingBalance)
    }

    @Test fun existingBudgetRequiresExplicitSelection() {
        server.budgetExists = true
        show()
        compose.onNodeWithText("Войти через Google").performClick()
        compose.waitUntil(5000) { cloudModel.state.value.loaded }
        compose.onNodeWithText("Название бюджета").assertDoesNotExist()
        assertNotEquals(BudgetProfileKind.CLOUD, env.manager.active.value!!.profile.kind)
        compose.onNodeWithText("Тестовый бюджет · Владелец").performClick()
        compose.waitUntil(5000) { env.manager.active.value?.profile?.kind == BudgetProfileKind.CLOUD }
        assertTrue(server.creations.isEmpty())
    }

    @Test fun cancellationAndNetworkErrorCanBeRetriedFromWelcome() {
        launcher = GoogleSignInLauncher {
            when (++pickerCalls) {
                1 -> throw GetCredentialCancellationException("Synthetic cancellation")
                2 -> throw AuthFailure(AuthError.Network)
                else -> "synthetic-credential"
            }
        }
        show()
        compose.onNodeWithText("Войти через Google").performClick()
        compose.waitUntil { pickerCalls == 1 && !authModel.state.value.busy }
        assertNull(authModel.state.value.error)
        compose.onNodeWithText("Войти через Google").performClick()
        compose.onNodeWithText("Не удалось выполнить вход. Проверьте интернет и повторите попытку.").assertIsDisplayed()
        compose.onNodeWithText("Продолжить без авторизации").assertIsEnabled()
        compose.onNodeWithText("Войти через Google").performClick()
        compose.waitUntil(5000) { cloudModel.state.value.draft != null }
        assertEquals(3, pickerCalls)
    }

    @Test fun unavailableGoogleStillAllowsLocalSetup() {
        env.auth.session.value = AuthSession.Unavailable
        show()
        compose.onNodeWithText("Войти через Google").assertIsNotEnabled()
        compose.onNodeWithText("Продолжить без авторизации").performClick()
        compose.onNodeWithText("Начальный баланс, ₸").assertIsDisplayed()
        assertEquals(0, pickerCalls)
    }

    @Test fun restoredGoogleSessionAndBackResumeWithoutAnotherPicker() {
        env.auth.login("owner")
        show()
        compose.waitUntil(5000) { cloudModel.state.value.draft != null }
        compose.onNodeWithText("Название бюджета").performTextReplacement("Сохранённый")
        closeSoftKeyboard()
        compose.onNodeWithText("Назад").performScrollTo().performClick()
        compose.onNodeWithText("Начало работы").assertIsDisplayed()
        compose.onNodeWithText("Войти через Google").performClick()
        compose.onNodeWithText("Сохранённый").assertIsDisplayed()
        assertEquals(0, pickerCalls)
        assertTrue(auth.session.value is AuthSession.Authenticated)
    }

    @Test fun localFormSurvivesStateRestorationAndBack() {
        val restoration = StateRestorationTester(compose)
        show(restoration = restoration)
        compose.onNodeWithText("Продолжить без авторизации").performClick()
        compose.onNodeWithText("Начальный баланс, ₸").performTextReplacement("345,67")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("345,67").assertIsDisplayed()
        compose.onNodeWithText("Назад").performScrollTo().performClick()
        compose.onNodeWithText("Продолжить без авторизации").performClick()
        compose.onNodeWithText("345,67").assertIsDisplayed()
    }

    @Test fun cloudDraftSurvivesStateRestoration() {
        env.auth.login("owner")
        val restoration = StateRestorationTester(compose)
        show(restoration = restoration)
        compose.waitUntil(5000) { cloudModel.state.value.draft != null }
        compose.onNodeWithText("Название бюджета").performTextReplacement("Семейный")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Семейный").assertIsDisplayed()
        assertEquals(0, pickerCalls)
    }

    @Test fun pendingCreationResumesTheSameBudgetAfterReturningToWelcome() {
        server.loseCreationResponse = true
        env.auth.login("owner")
        show()
        compose.waitUntil(5000) { cloudModel.state.value.draft != null }
        compose.onNodeWithText("Название бюджета").performTextReplacement("Повтор")
        closeSoftKeyboard()
        compose.onNodeWithText("Создать облачный бюджет").performScrollTo().performClick()
        try {
            compose.waitUntil(5000) { cloudModel.state.value.pending != null && !cloudModel.state.value.busy }
        } catch (error: ComposeTimeoutException) {
            throw AssertionError("Pending state: ${cloudModel.state.value}; created budgets: ${server.creations.size}", error)
        }
        compose.onNodeWithText("Назад").performScrollTo().performClick()
        compose.onNodeWithText("Войти через Google").performClick()
        compose.onNodeWithText("Название бюджета").assertIsNotEnabled()
        compose.onNodeWithText("Продолжить создание").performScrollTo().performClick()
        compose.waitUntil(5000) { env.manager.active.value?.profile?.kind == BudgetProfileKind.CLOUD }
        assertEquals(1, server.creations.size)
    }

    @Test fun lightWelcomeUsesThemeAndCenteredFullWidthButtons() = checkWelcome(dark = false)
    @Test fun darkWelcomeUsesThemeAndCenteredFullWidthButtons() = checkWelcome(dark = true)

    private fun checkWelcome(dark: Boolean) {
        show(dark = dark)
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        val google = compose.onNodeWithText("Войти через Google").getUnclippedBoundsInRoot()
        val local = compose.onNodeWithText("Продолжить без авторизации").getUnclippedBoundsInRoot()
        val title = compose.onNodeWithText("Fillin").getUnclippedBoundsInRoot()
        assertEquals(16f, google.left.value, 0.5f)
        assertEquals((root.right - root.left).value - 32f, (google.right - google.left).value, 0.5f)
        assertEquals((google.right - google.left).value, (local.right - local.left).value, 0.5f)
        assertEquals((root.top.value + root.bottom.value) / 2f, (title.top.value + local.bottom.value) / 2f, 24f)
        val theme = if (dark) "dark" else "light"
        captureScreen("auth-$theme")
        compose.onNodeWithText("Продолжить без авторизации").performClick()
        compose.onNodeWithText("Создать бюджет").performScrollTo().assertIsDisplayed()
        captureScreen("local-setup-$theme")
        compose.onNodeWithText("Назад").performScrollTo().performClick()
        compose.onNodeWithText("Войти через Google").performClick()
        compose.waitUntil(5000) { cloudModel.state.value.draft != null }
        compose.onNodeWithText("Название бюджета").assertIsDisplayed()
        captureScreen("cloud-setup-$theme")
    }

    private fun captureScreen(name: String) {
        val image = compose.onRoot().captureToImage()
        assertEquals(background, image.toPixelMap()[1, 1])
        val file = File(env.context.getExternalFilesDir("onboarding"), "$name.png")
        file.outputStream().use { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun largeTextOnShortScreenKeepsBothActionsReachable() {
        show(compact = true)
        compose.onNodeWithText("Войти через Google").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Продолжить без авторизации").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Создать бюджет").performScrollTo().assertIsDisplayed()
    }
}
