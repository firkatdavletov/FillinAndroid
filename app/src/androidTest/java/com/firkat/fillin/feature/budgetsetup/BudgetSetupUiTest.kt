package com.firkat.fillin.feature.budgetsetup

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.firkat.fillin.feature.budgetsetup.ui.BudgetSetupScreen
import com.firkat.fillin.feature.budgetsetup.ui.BudgetSetupViewModel
import com.firkat.fillin.feature.operationhistory.data.local.OperationDatabase
import com.firkat.fillin.feature.operationhistory.data.sync.OperationSyncScheduler
import com.firkat.fillin.feature.sync.data.CloudBudgetSession
import com.firkat.fillin.ui.theme.FillinTheme
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.*
import org.junit.Assert.*
import java.time.*

class BudgetSetupUiTest {
    @get:Rule val compose = createComposeRule()
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), OperationDatabase::class.java).build()
    private val api = TestCloudApi()
    private val session = CloudBudgetSession(database, api, "synthetic-token", Json, OperationSyncScheduler {}, Clock.fixed(Instant.parse("2026-10-18T05:00:00Z"), ZoneId.of("Asia/Almaty")))

    @After fun cleanup() {
        database.close()
    }

    @Test fun createChoiceStoresLocalBudgetWithoutNetwork() {
        val model = BudgetSetupViewModel(session)
        compose.setContent { FillinTheme { BudgetSetupScreen(model) } }
        compose.onNodeWithText("Создать бюджет").performClick()
        compose.onNodeWithText("Начальный баланс, ₸").performTextReplacement("123.45")
        compose.onNodeWithText("Создать бюджет").performClick()
        compose.waitUntil(5000) { model.ready.value == true }
        assertEquals(0, api.calls)
        runBlocking { assertFalse(database.operations().state()!!.cloudEnabled) }
    }

    @Test fun missingBudgetShowsErrorAndReturnsToInitialChoices() {
        val model = BudgetSetupViewModel(session)
        compose.setContent { FillinTheme { BudgetSetupScreen(model) } }
        compose.onNodeWithText("Войти в облако").performClick()
        compose.waitUntil(5000) { model.error != null }
        compose.onNodeWithText("Бюджет по этому токену не найден. Создайте бюджет или повторите подключение.").assertIsDisplayed()
        compose.onNodeWithText("Создать бюджет").assertIsEnabled()
        compose.onNodeWithText("Войти в облако").assertIsEnabled()
        assertFalse(model.ready.value == true)
        assertEquals(0, api.creates)
    }
}
