package com.firkat.fillin.feature.operationhistory.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.firkat.fillin.R
import com.firkat.fillin.feature.operationhistory.data.OfflineTestEnvironment
import com.firkat.fillin.feature.operationhistory.data.SyntheticSyncApi
import com.firkat.fillin.feature.operationhistory.data.local.OperationDatabase
import com.firkat.fillin.feature.operationhistory.domain.model.OperationSyncStatus
import com.firkat.fillin.feature.operationhistory.domain.usecase.RefundPurchase
import com.firkat.fillin.feature.operationhistory.domain.usecase.UpdatePurchaseAmount
import com.firkat.fillin.ui.component.FeatureScaffold
import com.firkat.fillin.ui.theme.FillinTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineOperationEntryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun createIncomeAndExpenseOfflineThenSynchronizeWithoutDuplicateRows() {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), OperationDatabase::class.java).build()
        val api = SyntheticSyncApi()
        val env = OfflineTestEnvironment(db, api)
        runBlocking { env.synchronizer.synchronize() }
        env.connectivity.online.value = false
        val model =
            OperationHistoryViewModel(
                env.repository,
                UpdatePurchaseAmount(env.repository),
                RefundPurchase(env.repository),
                OfflineTestEnvironment.CLOCK,
            )
        val store = ViewModelStore().apply { put("history", model) }
        try {
            compose.setContent {
                val state by model.uiState.collectAsStateWithLifecycle()
                FillinTheme { FeatureScaffold(R.string.operation_history_title) { OperationHistoryScreen(state, model::onEvent) } }
            }
            compose.waitUntil { model.uiState.value.offline.initialSyncComplete }
            compose.onNodeWithText("Добавить операцию").performClick()
            compose.onNodeWithTag("operation-create-amount").performTextInput("10.01")
            compose.onNodeWithTag("operation-create-category").performClick()
            compose.onNodeWithText("🍔 Продукты").performClick()
            compose.onNodeWithTag("operation-create-save").performScrollTo().performClick()
            compose.waitUntil { model.uiState.value.createEditor == null && model.uiState.value.operations.size == 1 }
            compose.onNodeWithText("Ожидает отправки").assertIsDisplayed()
            compose.onNodeWithText("Добавить операцию").performClick()
            compose.onNodeWithText("Доход").performClick()
            compose.onNodeWithTag("operation-create-amount").performTextInput("100.02")
            compose.onNodeWithTag("operation-create-category").performClick()
            compose.onNodeWithText("💰 Зарплата").performClick()
            compose.onNodeWithTag("operation-create-save").performScrollTo().performClick()
            compose.waitUntil { model.uiState.value.createEditor == null && model.uiState.value.operations.size == 2 }
            assertEquals(0, api.postCount)
            compose.onAllNodesWithText("Ожидает отправки").assertCountEquals(2)
            env.connectivity.online.value = true
            runBlocking { env.synchronizer.synchronize() }
            compose.waitUntil {
                model.uiState.value.operations
                    .all { it.syncStatus == OperationSyncStatus.Synced }
            }
            compose.onAllNodesWithText("Ожидает отправки").assertCountEquals(0)
            assertEquals(2, model.uiState.value.operations.size)
            assertEquals(2, api.received.size)
        } finally {
            compose.runOnIdle { store.clear() }
            db.close()
        }
    }
}
