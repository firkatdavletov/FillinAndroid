package com.firkat.fillin.feature.budgetsetup

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.test.espresso.Espresso.closeSoftKeyboard
import com.firkat.fillin.feature.budgetconnection.BudgetConnectionDeviceTest
import com.firkat.fillin.feature.budgetconnection.ui.BudgetConnectionViewModel
import com.firkat.fillin.feature.budgetprofile.domain.BudgetProfileKind
import com.firkat.fillin.feature.budgetsetup.domain.BudgetSetupMode
import com.firkat.fillin.feature.budgetsetup.domain.BudgetSetupRepository
import com.firkat.fillin.feature.budgetsetup.ui.BudgetSetupScreen
import com.firkat.fillin.feature.budgetsetup.ui.BudgetSetupViewModel
import com.firkat.fillin.feature.budgetsetup.ui.CloudBudgetSetupScreen
import com.firkat.fillin.ui.theme.FillinTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.math.BigDecimal
import java.time.LocalDate

class BudgetSetupUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val server = BudgetConnectionDeviceTest.Server().apply { budgetExists = false }
    private val env = BudgetConnectionDeviceTest.Environment(server)
    private val models = ViewModelStore()
    private lateinit var localModel: BudgetSetupViewModel
    private var cloudModel by mutableStateOf<BudgetConnectionViewModel?>(null)
    private var backs = 0
    private var commit: CompletableDeferred<Unit>? = null

    private fun newCloudModel() = BudgetConnectionViewModel(env.auth, env.connection, env.creation, autoStartCreation = true)
        .also { models.put("cloud-${models.keys().size}", it) }

    private fun show(
        mode: BudgetSetupMode = BudgetSetupMode.Local,
        compact: Boolean = false,
        restoration: StateRestorationTester? = null,
    ) {
        if (mode == BudgetSetupMode.Cloud) env.auth.login("owner")
        runBlocking { env.manager.restore() }
        val repository = env.manager.active.value!!.scope.get<BudgetSetupRepository>()
        localModel = BudgetSetupViewModel(object : BudgetSetupRepository by repository {
            override suspend fun createLocal(openingBalance: BigDecimal, startedOn: LocalDate) {
                commit?.await()
                repository.createLocal(openingBalance, startedOn)
            }
        }).also { models.put("local", it) }
        cloudModel = newCloudModel()
        val content: @Composable () -> Unit = {
            FillinTheme(dynamicColor = false) {
                val form: @Composable () -> Unit = {
                    when (mode) {
                        BudgetSetupMode.Local -> BudgetSetupScreen(localModel, onBack = { backs++ })
                        BudgetSetupMode.Cloud -> CloudBudgetSetupScreen(cloudModel!!, onBack = { backs++ })
                    }
                }
                if (compact) {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                        Box(Modifier.fillMaxWidth().height(220.dp)) { form() }
                    }
                } else form()
            }
        }
        if (restoration == null) compose.setContent(content) else restoration.setContent(content)
    }

    @After fun cleanup() {
        compose.activityRule.scenario.onActivity { it.setContentView(android.widget.FrameLayout(it)) }
        compose.waitForIdle()
        models.clear()
        env.close()
    }

    @Test fun localFormCreatesBudgetWithoutAnyAuthenticationActions() {
        show()
        compose.onNodeWithText("Создание бюджета").assertIsDisplayed()
        compose.onNodeWithText("Продолжить без авторизации").assertDoesNotExist()
        compose.onNodeWithText("Начальный баланс, ₸").performTextReplacement("123,45")
        closeSoftKeyboard()
        compose.onNodeWithText("Создать бюджет").performScrollTo().performClick()
        compose.waitUntil(5000) { localModel.ready.value == true }
        assertTrue(server.creations.isEmpty())
    }

    @Test fun invalidInputShowsErrorAndCanBeCorrected() {
        show()
        compose.onNodeWithText("Начальный баланс, ₸").performTextReplacement("1.234")
        closeSoftKeyboard()
        compose.onNodeWithText("Создать бюджет").performScrollTo().performClick()
        compose.onNodeWithText("Проверьте баланс и дату начала учёта.").assertIsDisplayed()
        compose.onNodeWithText("Начальный баланс, ₸").performTextReplacement("10")
        closeSoftKeyboard()
        compose.onNodeWithText("Создать бюджет").performScrollTo().performClick()
        compose.waitUntil(5000) { localModel.ready.value == true }
    }

    @Test fun savingBlocksButtonAndSystemBackUntilCommit() {
        commit = CompletableDeferred()
        show()
        compose.onNodeWithText("Создать бюджет").performClick()
        compose.onNodeWithText("Создать бюджет").assertIsNotEnabled()
        compose.onNodeWithText("Назад").assertIsNotEnabled()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertEquals(0, backs)
        assertEquals(false, localModel.ready.value)
        compose.runOnIdle { commit!!.complete(Unit) }
        compose.waitUntil(5000) { localModel.ready.value == true }
    }

    @Test fun localFormRetainsBalanceAndDateAfterSavedStateRestoration() {
        val restoration = StateRestorationTester(compose)
        show(restoration = restoration)
        compose.onNodeWithText("Начальный баланс, ₸").performTextReplacement("345,67")
        compose.onNodeWithText("Дата начала учёта (ГГГГ-ММ-ДД)").performTextReplacement("2026-10-01")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("345,67").assertIsDisplayed()
        compose.onNodeWithText("2026-10-01").assertIsDisplayed()
        closeSoftKeyboard()
        compose.onNodeWithText("Назад").performScrollTo().performClick()
        assertEquals(1, backs)
    }

    @Test fun cloudFormCreatesFirstBudgetDirectly() {
        show(mode = BudgetSetupMode.Cloud)
        compose.waitUntil(5000) { cloudModel!!.state.value.draft != null }
        compose.onNodeWithText("Название бюджета").performTextReplacement("Семья")
        compose.onNodeWithText("Начальный баланс, ₸").performTextReplacement("125,50")
        closeSoftKeyboard()
        compose.onNodeWithText("Создать облачный бюджет").performScrollTo().performClick()
        compose.waitUntil(5000) { env.manager.active.value?.profile?.kind == BudgetProfileKind.CLOUD }
        assertEquals(1, server.creations.size)
        assertEquals("125.50", server.openingBalance)
    }

    @Test fun existingCloudBudgetRequiresExplicitSelection() {
        server.budgetExists = true
        show(mode = BudgetSetupMode.Cloud)
        compose.waitUntil(5000) { cloudModel!!.state.value.loaded }
        compose.onNodeWithText("Название бюджета").assertDoesNotExist()
        assertNotEquals(BudgetProfileKind.CLOUD, env.manager.active.value!!.profile.kind)
        compose.onNodeWithText("Тестовый бюджет · Владелец").performClick()
        compose.waitUntil(5000) { env.manager.active.value?.profile?.kind == BudgetProfileKind.CLOUD }
        assertTrue(server.creations.isEmpty())
    }

    @Test fun cloudDraftSurvivesSavedStateRestorationAndANewViewModel() {
        val restoration = StateRestorationTester(compose)
        show(mode = BudgetSetupMode.Cloud, restoration = restoration)
        compose.waitUntil(5000) { cloudModel!!.state.value.draft != null }
        compose.onNodeWithText("Название бюджета").performTextReplacement("Семейный")
        compose.onNodeWithText("Начальный баланс, ₸").performTextReplacement("800,25")
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { cloudModel = newCloudModel() }
        compose.waitUntil(5000) { cloudModel!!.state.value.draft?.name == "Семейный" }
        compose.onNodeWithText("Семейный").assertIsDisplayed()
        compose.onNodeWithText("800,25").assertIsDisplayed()
    }

    @Test fun pendingCreationResumesSameRequestAfterANewViewModel() {
        server.loseCreationResponse = true
        show(mode = BudgetSetupMode.Cloud)
        compose.waitUntil(5000) { cloudModel!!.state.value.draft != null }
        compose.onNodeWithText("Название бюджета").performTextReplacement("Повтор")
        closeSoftKeyboard()
        compose.onNodeWithText("Создать облачный бюджет").performScrollTo().performClick()
        compose.waitUntil(5000) { cloudModel!!.state.value.pending != null && !cloudModel!!.state.value.busy }
        compose.runOnIdle { cloudModel = newCloudModel() }
        compose.waitUntil(5000) { cloudModel!!.state.value.pending != null && !cloudModel!!.state.value.busy }
        compose.onNodeWithText("Название бюджета").assertIsNotEnabled()
        compose.onNodeWithText("Продолжить создание").performScrollTo().performClick()
        compose.waitUntil(5000) { env.manager.active.value?.profile?.kind == BudgetProfileKind.CLOUD }
        assertEquals(1, server.creations.size)
    }

    @Test fun largeTextOnShortScreenKeepsCreationAndBackReachable() {
        show(compact = true)
        compose.onNodeWithText("Создать бюджет").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Назад").performScrollTo().performClick()
        assertEquals(1, backs)
    }
}
