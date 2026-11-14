package com.firkat.fillin.feature.budgetsharing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import com.firkat.fillin.feature.budgetconnection.BudgetConnectionDeviceTest
import com.firkat.fillin.feature.budgetsharing.data.AcceptInvitationDto
import com.firkat.fillin.feature.budgetsharing.ui.*
import com.firkat.fillin.ui.theme.FillinTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class FamilyBudgetUiTest {
    @get:Rule val compose = createComposeRule()
    private val server = FamilyBudgetDeviceTest.Server()
    private val env = BudgetConnectionDeviceTest.Environment(apiOverride = server, familyApi = server)
    private val models = ViewModelStore()

    @After fun cleanup() {
        models.clear()
        env.close()
    }

    @Test fun joiningByCodeShowsTheFamilyBudgetAndMemberCannotRevokeParticipants() {
        runBlocking {
            env.auth.login("other")
            env.manager.restore()
        }
        val join = FamilyBudgetJoinViewModel(env.auth, env.sharing).also { models.put("join", it) }
        val members = FamilyMembersViewModel(env.sharing).also { models.put("members", it) }
        compose.setContent {
            FillinTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    FamilyBudgetJoinSection(join)
                    FamilyMembersSection(members)
                }
            }
        }
        compose.waitUntil(5000) {
            join.state.value.session is com.firkat.fillin.feature.auth.domain.AuthSession.Authenticated
        }
        compose.onNodeWithText("Код приглашения").performTextReplacement("bad")
        compose.onNodeWithText("Присоединиться по коду").performClick()
        assertEquals(0, server.newMemberships)
        compose.onNodeWithText("Код приглашения").performTextReplacement(server.issue().lowercase())
        compose.onNodeWithText("Присоединиться по коду").performClick()
        compose.waitUntil(5000) {
            !join.state.value.busy &&
                join.state.value.joinedName != null &&
                members.state.value.members.size == 2
        }
        compose.onNodeWithText("Подключён бюджет «Семейный»").assertExists()
        compose.onNodeWithText("Guest · Участник · Вы").performScrollTo().assertIsDisplayed()
        compose.onAllNodes(hasText("Отозвать доступ:", substring = true)).assertCountEquals(0)
        assertEquals("MEMBER", env.manager.active.value!!.profile.role)
        assertEquals(1, server.newMemberships)
    }

    @Test fun ownerSeesMembersAndRevokesOnlyTheSelectedMember() {
        runBlocking {
            server.join("Bearer synthetic-other", AcceptInvitationDto(server.issue()))
            env.auth.login("owner")
            env.manager.restore()
            env.connection.connect("owner", env.connection.budgets("owner").single())
        }
        val members = FamilyMembersViewModel(env.sharing).also { models.put("members", it) }
        compose.setContent {
            FillinTheme { Column(Modifier.verticalScroll(rememberScrollState())) { FamilyMembersSection(members) } }
        }
        compose.waitUntil(5000) { !members.state.value.busy && members.state.value.members.size == 2 }
        compose.onNodeWithText("Owner · Владелец · Вы").assertExists()
        compose.onNodeWithText("Отозвать доступ: Guest").performScrollTo().performClick()
        compose.waitUntil(5000) { !members.state.value.busy && members.state.value.members.size == 1 }
        compose.onNodeWithText("Отозвать доступ: Guest").assertDoesNotExist()
        assertFalse(server.guestActive)
    }
}
