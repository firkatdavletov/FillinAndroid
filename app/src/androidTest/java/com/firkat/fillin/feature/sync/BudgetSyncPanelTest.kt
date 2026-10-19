package com.firkat.fillin.feature.sync

import androidx.compose.runtime.remember
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.firkat.fillin.feature.sync.domain.*
import com.firkat.fillin.feature.sync.ui.*
import com.firkat.fillin.ui.theme.FillinTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BudgetSyncPanelTest {
    @get:Rule val compose = createComposeRule()

    @Test fun conflictOffersBothVersionsAndExplicitResolution() {
        val control =
            FakeControl(
                BudgetSyncContext(
                    ready = true,
                    canWrite = true,
                    issues =
                        listOf(
                            BudgetSyncIssue(
                                "request",
                                "Категория",
                                "Изменение · Моя правка",
                                "Серверная версия",
                                "CONFLICT",
                                true,
                            ),
                        ),
                ),
            )
        compose.setContent { FillinTheme { BudgetSyncPanel(remember { BudgetSyncViewModel(control) }) } }
        compose.onNodeWithText("Категория: конфликт").performClick()
        compose.onNodeWithText("На устройстве: Изменение · Моя правка").assertIsDisplayed()
        compose.onNodeWithText("На сервере: Серверная версия").assertIsDisplayed()
        compose.onNodeWithText("Применить мою правку").performClick()
        compose.runOnIdle { assertEquals("request" to true, control.resolved) }
        compose.onNodeWithText("Категория: конфликт").assertDoesNotExist()
    }

    @Test fun pendingDeletionRemainsVisibleAndInitialSyncIsExplained() {
        val control = FakeControl(BudgetSyncContext())
        compose.setContent { FillinTheme { BudgetSyncPanel(remember { BudgetSyncViewModel(control) }) } }
        compose.onNodeWithText("Для работы офлайн нужна первая полная синхронизация").assertIsDisplayed()
        compose.runOnIdle {
            control.context.value =
                BudgetSyncContext(
                    ready = true,
                    canWrite = true,
                    pending = 1,
                    pendingActions = listOf("Категория: Удаление · Тестовая"),
                )
        }
        compose.onNodeWithText("Показать ожидающие изменения").performClick()
        compose.onNodeWithText("Категория: Удаление · Тестовая").assertIsDisplayed()
    }

    private class FakeControl(
        initial: BudgetSyncContext,
    ) : BudgetSyncControl {
        override val context = MutableStateFlow(initial)
        var resolved: Pair<String, Boolean>? = null

        override fun requestSync() = Unit

        override suspend fun resolve(
            id: String,
            reapply: Boolean,
        ) {
            resolved = id to reapply
            context.value =
                context.value.copy(issues = emptyList())
        }
    }
}
