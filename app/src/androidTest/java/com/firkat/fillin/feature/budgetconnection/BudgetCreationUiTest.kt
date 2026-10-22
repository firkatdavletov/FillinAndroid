package com.firkat.fillin.feature.budgetconnection

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import com.firkat.fillin.feature.budgetconnection.ui.BudgetConnectionSection
import com.firkat.fillin.feature.budgetconnection.ui.BudgetConnectionViewModel
import com.firkat.fillin.feature.budgetprofile.domain.BudgetProfileKind
import com.firkat.fillin.ui.theme.FillinTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class BudgetCreationUiTest {
    @get:Rule val compose = createComposeRule()
    private val server = BudgetConnectionDeviceTest.Server().apply { budgetExists = false }
    private val env = BudgetConnectionDeviceTest.Environment(server)
    private val models = ViewModelStore()
    private fun show(): BudgetConnectionViewModel {
        runBlocking { env.auth.login("owner"); env.manager.restore() }
        val model = BudgetConnectionViewModel(env.auth, env.connection, env.creation)
        models.put("creation", model)
        compose.setContent { FillinTheme { Column(Modifier.verticalScroll(rememberScrollState())) { BudgetConnectionSection(model) } } }
        compose.waitUntil(5000) { model.state.value.loaded }
        return model
    }
    @After fun cleanup() { models.clear(); env.close() }

    @Test fun firstBudgetFormValidatesInputAndCreatesOwnerBudget() {
        val model = show()
        compose.onNodeWithText("Создать облачный бюджет").performScrollTo().performClick()
        compose.onNodeWithText("Название бюджета").performTextReplacement("Новый бюджет")
        compose.onNodeWithText("Начальный баланс, ₸").performTextReplacement("1.001")
        compose.onNodeWithText("Создать облачный бюджет").performScrollTo().performClick()
        compose.waitUntil(5000) { model.state.value.error != null }
        assertTrue(server.creations.isEmpty())
        compose.onNodeWithText("Начальный баланс, ₸").performScrollTo().performTextReplacement("125,50")
        compose.onNodeWithText("Создать облачный бюджет").performScrollTo().performClick()
        compose.waitUntil(5000) { env.manager.active.value?.profile?.kind == BudgetProfileKind.CLOUD && !model.state.value.creating }
        assertEquals("125.50", server.openingBalance)
        assertEquals("Новый бюджет", server.budgetName)
        assertNull(model.state.value.draft)
        assertEquals(1, server.creations.size)
    }

    @Test fun lostResponseShowsReadonlyRetryAndOpensTheSameBudget() {
        server.loseCreationResponse = true
        val model = show()
        compose.onNodeWithText("Создать облачный бюджет").performScrollTo().performClick()
        compose.onNodeWithText("Название бюджета").performTextReplacement("Повтор")
        compose.onNodeWithText("Создать облачный бюджет").performScrollTo().performClick()
        compose.waitUntil(5000) { model.state.value.pending != null && !model.state.value.creating }
        compose.onNodeWithText("Название бюджета").assertIsNotEnabled()
        compose.onNodeWithText("Продолжить создание").performScrollTo().performClick()
        compose.waitUntil(5000) { env.manager.active.value?.profile?.kind == BudgetProfileKind.CLOUD && !model.state.value.creating }
        assertEquals(1, server.creations.size)
        runBlocking { assertNull(env.creation.pending("owner")) }
    }

    @Test fun switchingGoogleAccountHidesPendingCreationAndReturningRestoresIt() {
        server.loseCreationResponse = true
        val model = show()
        compose.onNodeWithText("Создать облачный бюджет").performScrollTo().performClick()
        compose.onNodeWithText("Название бюджета").performTextReplacement("Только владельцу")
        compose.onNodeWithText("Создать облачный бюджет").performScrollTo().performClick()
        compose.waitUntil(5000) { model.state.value.pending != null && !model.state.value.creating }
        env.auth.login("other")
        compose.waitUntil(5000) { model.state.value.loaded && model.state.value.pending == null }
        compose.onNodeWithText("Только владельцу").assertDoesNotExist()
        env.auth.login("owner")
        compose.waitUntil(5000) { model.state.value.pending != null }
        compose.onNodeWithText("Только владельцу").assertExists()
        assertEquals(1, server.creations.size)
    }
}
