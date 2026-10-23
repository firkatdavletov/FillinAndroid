package com.firkat.fillin.feature.sync

import androidx.compose.runtime.remember
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.firkat.fillin.feature.sync.domain.*
import com.firkat.fillin.feature.sync.ui.*
import com.firkat.fillin.ui.theme.FillinTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class BudgetSyncPanelTest {
    @get:Rule val compose = createComposeRule()

    @Test fun localModeDoesNotRequireInitialServerSyncOrOfferConflictChooser() {
        val control = FakeControl(BudgetSyncContext(ready = true, canWrite = true))
        compose.setContent { FillinTheme { BudgetSyncPanel(remember { BudgetSyncViewModel(control) }) } }
        compose.onNodeWithText("Для работы офлайн нужна первая полная синхронизация").assertDoesNotExist()
        compose.onNodeWithText("Принять серверную").assertDoesNotExist()
    }

    @Test fun cloudScreenShowsControlsAndLastSuccessfulSync() {
        val control = FakeControl(BudgetSyncContext(ready = true, canWrite = true, cloudEnabled = true, lastSyncedAt = "2026-10-18T05:00:00Z", pending = 2))
        compose.setContent { FillinTheme { BudgetSyncScreen(remember { BudgetSyncViewModel(control) }, isAuthenticated = false, authBusy = false, onAccountClick = {}, uploadContent = {}, connectionContent = {}, invitationContent = {}, joinContent = {}, membersContent = {}) } }
        compose.onNodeWithText("Синхронизация").assertIsDisplayed()
        compose.onNodeWithText("Включить облачную синхронизацию").assertIsNotEnabled()
        compose.onNodeWithText("Отключить облачную синхронизацию").assertIsEnabled()
        compose.onNodeWithText("Синхронизировать").assertIsEnabled()
        compose.onNodeWithText("Последняя успешная синхронизация: 18.10.2026 10:00").assertExists()
        compose.runOnIdle { control.context.value = control.context.value.copy(syncing = true) }
        compose.onNodeWithText("Синхронизировать").assertIsNotEnabled()
    }

    private class FakeControl(
        initial: BudgetSyncContext,
    ) : BudgetSyncControl {
        override val context = MutableStateFlow(initial)

        override fun requestSync() = Unit

        override suspend fun resolve(
            id: String,
            reapply: Boolean,
        ) = error("Manual resolution is not part of the UI")
    }
}
