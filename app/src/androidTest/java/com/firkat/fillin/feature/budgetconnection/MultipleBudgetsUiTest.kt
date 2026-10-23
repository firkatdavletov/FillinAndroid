package com.firkat.fillin.feature.budgetconnection

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import com.firkat.fillin.feature.budgetconnection.domain.BudgetCreationDraft
import com.firkat.fillin.feature.budgetconnection.ui.BudgetConnectionSection
import com.firkat.fillin.feature.budgetconnection.ui.BudgetConnectionViewModel
import com.firkat.fillin.ui.theme.FillinTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.time.LocalDate

class MultipleBudgetsUiTest {
    @get:Rule val compose = createComposeRule()
    private val server = MultipleBudgetsDeviceTest.MultiServer()
    private val env = BudgetConnectionDeviceTest.Environment(apiOverride = server)
    private val models = ViewModelStore()
    @After fun cleanup() { models.clear(); env.close() }

    @Test fun createsAnAdditionalBudgetAndSelectsTheFirstBudgetFromTheSavedOfflineList() {
        runBlocking {
            env.auth.login("owner"); env.manager.restore()
            env.creation.create("owner", BudgetCreationDraft("Первый", "100.00", LocalDate.now().minusDays(1).toString()))
        }
        val original = env.manager.active.value!!.profile
        val model = BudgetConnectionViewModel(env.auth, env.connection, env.creation)
        models.put("connection", model)
        compose.setContent { FillinTheme { Column(Modifier.verticalScroll(rememberScrollState())) { BudgetConnectionSection(model) } } }
        compose.waitUntil(5000) { model.state.value.loaded && !model.state.value.busy }
        compose.onNodeWithText("Первый · Владелец").assertIsSelected()
        compose.onNodeWithText("Создать ещё бюджет").performScrollTo().performClick()
        compose.onNodeWithText("Название бюджета").performTextReplacement("Второй")
        compose.onNodeWithText("Начальный баланс, ₸").performTextReplacement("900.00")
        compose.onNodeWithText("Создать облачный бюджет").performScrollTo().performClick()
        compose.waitUntil(5000) { !model.state.value.busy && model.state.value.budgets.size == 2 }
        compose.onNodeWithText("Второй · Владелец").assertIsSelected()
        assertEquals(2, server.budgets.size)
        server.offline = true
        compose.onNodeWithText("Обновить список бюджетов").performScrollTo().performClick()
        compose.waitUntil(5000) { !model.state.value.busy && model.state.value.error != null }
        compose.onNodeWithText("Первый · Владелец").performScrollTo().performClick()
        compose.waitUntil(5000) { model.state.value.activeBudgetId == original.budgetId && !model.state.value.busy }
        compose.onNodeWithText("Первый · Владелец").assertIsSelected()
        compose.onNodeWithText("Второй · Владелец").assertIsNotSelected()
        assertEquals(original, env.manager.active.value!!.profile)
        assertTrue(model.state.value.showingSaved)
    }
}
