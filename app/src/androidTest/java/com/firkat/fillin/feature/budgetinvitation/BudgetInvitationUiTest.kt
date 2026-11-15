package com.firkat.fillin.feature.budgetinvitation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import com.firkat.fillin.feature.budgetconnection.BudgetConnectionDeviceTest
import com.firkat.fillin.feature.budgetinvitation.ui.BudgetInvitationSection
import com.firkat.fillin.feature.budgetinvitation.ui.BudgetInvitationViewModel
import com.firkat.fillin.ui.theme.FillinTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class BudgetInvitationUiTest {
    @get:Rule val compose = createComposeRule()
    private val env = BudgetConnectionDeviceTest.Environment()
    private val api = BudgetInvitationDeviceTest.Api()
    private val models = ViewModelStore()
    private var copied: String? = null

    @After fun cleanup() {
        models.clear()
        env.close()
    }

    private fun show(
        role: String = "OWNER"
    ): BudgetInvitationViewModel {
        runBlocking {
            env.auth.login("owner")
            env.manager.restore()
            env.server.scope = env.server.scope.copy(role = role)
            env.connection.connect("owner", env.connection.budgets("owner").single())
        }
        val model = BudgetInvitationViewModel(BudgetInvitationDeviceTest.repository(env, api))
        models.put("invitation", model)
        compose.setContent {
            FillinTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    BudgetInvitationSection(model) {
                        copied =
                            it
                    }
                }
            }
        }
        compose.waitUntil(5000) { model.state.value.target != null && !model.state.value.busy }
        return model
    }

    @Test fun ownerCreatesCopiesReplacesAndRevokesTheCodeOfTheSelectedBudget() {
        val model = show()
        compose.onNodeWithText("Создать новый код").performScrollTo().performClick()
        compose.waitUntil(5000) { model.state.value.code != null && !model.state.value.busy }
        val original = model.state.value.current!!
        val code = model.state.value.code
        compose.onNodeWithText(code!!).assertExists()
        compose.onNodeWithText("Скопировать код").performScrollTo().performClick()
        assertEquals(code, copied)
        compose.onNodeWithText("Код скопирован").assertExists()
        compose.onNodeWithText("Создать новый код").performScrollTo().performClick()
        compose.waitUntil(5000) {
            model.state.value.current != null &&
                model.state.value.current!!.id != original.id &&
                !model.state.value.busy
        }
        compose.onNodeWithText(code).assertDoesNotExist()
        compose.onNodeWithText("Отозвать приглашение").performScrollTo().performClick()
        compose.waitUntil(5000) { model.state.value.current == null && !model.state.value.busy }
        compose.onNodeWithText("Скопировать код").assertDoesNotExist()
        assertTrue(api.saved.isEmpty())
        assertEquals(2, api.creations)
    }

    @Test fun memberSeesOwnerRestrictionWithoutCodeOrCreationControls() {
        show("MEMBER")
        compose.onNodeWithText("Только владелец бюджета может создавать и отзывать приглашения.").assertExists()
        compose.onNodeWithText("Создать новый код").assertDoesNotExist()
        compose.onNodeWithText("Отозвать приглашение").assertDoesNotExist()
        assertEquals(0, api.calls)
    }
}
