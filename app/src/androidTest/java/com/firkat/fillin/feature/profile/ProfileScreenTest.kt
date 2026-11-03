package com.firkat.fillin.feature.profile

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.closeSoftKeyboard
import com.firkat.fillin.feature.auth.domain.AuthSession
import com.firkat.fillin.feature.auth.domain.AuthUser
import com.firkat.fillin.feature.budgetinvitation.domain.BudgetInvitation
import com.firkat.fillin.feature.budgetinvitation.domain.InvitationTarget
import com.firkat.fillin.feature.budgetinvitation.ui.BudgetInvitationState
import com.firkat.fillin.feature.budgetsharing.ui.FamilyAccessScreen
import com.firkat.fillin.feature.budgetsharing.ui.FamilyJoinState
import com.firkat.fillin.feature.profile.domain.ProfileBudget
import com.firkat.fillin.feature.profile.ui.ProfileScreen
import com.firkat.fillin.feature.profile.ui.ProfileUiState
import com.firkat.fillin.feature.sync.domain.BudgetSyncContext
import com.firkat.fillin.feature.sync.ui.CloudSyncScreen
import com.firkat.fillin.ui.theme.FillinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class ProfileScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun budgetActionsDoNotSelectAndDispatchBothForms() {
        var selected = 0
        val actions = mutableListOf<Boolean>()
        val state = mutableStateOf(ProfileUiState(loading = false, budgets = listOf(
            ProfileBudget("one", "Дом", false, true),
            ProfileBudget("two", "Поездка", false, false),
            ProfileBudget("shared", "Общий", true, false, canManage = false),
        )))
        compose.setContent { FillinTheme {
            ProfileScreen(state.value, null, false, {}, { selected++ }, {}, {}, {}, {}, {},
                onBudgetActionsClick = { id -> state.value = state.value.copy(expandedBudgetId = id.takeUnless { state.value.expandedBudgetId == id }) },
                onManageBudget = { _, _, deleting -> actions.add(deleting) })
        } }
        compose.onAllNodesWithContentDescription("Действия с бюджетом").assertCountEquals(2)
        compose.onAllNodesWithContentDescription("Действия с бюджетом")[0].performClick()
        compose.onNodeWithText("Редактировать").performClick()
        compose.onNodeWithText("Удалить").performClick()
        assertEquals(listOf(false, true), actions)
        assertEquals(0, selected)
        compose.onAllNodesWithContentDescription("Действия с бюджетом")[1].performClick()
        compose.onAllNodesWithText("Редактировать").assertCountEquals(1)
        compose.onAllNodesWithContentDescription("Действия с бюджетом")[1].performClick()
        compose.onNodeWithText("Редактировать").assertDoesNotExist()
    }

    @Test fun profileDisplaysAccountAndSelectionAndDispatchesActions() {
        var selected: String? = null
        var family = 0
        var cloud = 0
        compose.setContent {
            FillinTheme {
                ProfileScreen(profileState(), USER, false, {}, { selected = it }, {}, {}, { family++ }, { cloud++ }, {})
            }
        }
        compose.onNodeWithText("Анна Иванова").assertIsDisplayed()
        compose.onNodeWithText("anna@example.com").assertIsDisplayed()
        snapshot("profile-account")
        compose.onNodeWithText("Основной бюджет").assertIsSelected()
        compose.onNodeWithText("Накопления").assertIsNotSelected().performClick()
        assertEquals("local", selected)
        compose.onNodeWithText("Семейный доступ").performScrollTo().performClick()
        compose.onNodeWithText("Облачная синхронизация").performScrollTo().performClick()
        assertEquals(1, family)
        assertEquals(1, cloud)
    }

    @Test fun profileMethodsAndAccountActionsDispatch() {
        var methods = 0
        var signOut = 0
        var deletion = 0
        compose.setContent { FillinTheme(darkTheme = true) {
            ProfileScreen(profileState(), USER, false, { signOut++ }, {}, {}, {}, {}, {}, {},
                onMethodsClick = { methods++ }, onDeleteClick = { deletion++ })
        } }
        compose.onNodeWithText("Способы входа").performClick()
        assertEquals(1, methods)
        snapshot("profile-account-dark")
        compose.onNodeWithText("Выйти из аккаунта").performScrollTo().performClick()
        compose.onNodeWithTag("delete_profile").performScrollTo().performClick()
        assertEquals(1, signOut)
        assertEquals(1, deletion)
        snapshot("profile-management-dark")
    }

    @Test fun narrowLargeFontKeepsProfileActionsReachable() {
        var signOut = 0
        compose.setContent { FillinTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                ProfileScreen(profileState(), USER.copy(displayName = "Анна Александровна Иванова", email = "anna.alexandrovna@example.com"), false,
                    { signOut++ }, {}, {}, {}, {}, {}, {}, modifier = Modifier.requiredWidth(320.dp))
            }
        } }
        snapshot("profile-large-font-light")
        compose.onNodeWithText("Выйти из аккаунта").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, signOut)
    }

    @Test fun guestCanSignInAndHasNoSignOutAction() {
        var signIn = 0
        compose.setContent { FillinTheme { ProfileScreen(profileState(), null, false, { signIn++ }, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("Выйти из аккаунта").assertDoesNotExist()
        compose.onNodeWithText("Управление аккаунтом").assertDoesNotExist()
        compose.onNodeWithTag("delete_profile").assertDoesNotExist()
        compose.onNodeWithText("Авторизоваться").performClick()
        assertEquals(1, signIn)
        snapshot("profile-guest")
    }

    @Test fun cloudSwitchChangesStateAndDisabledSyncHasNoManualAction() {
        val enabled = mutableStateOf(false)
        var syncs = 0
        compose.setContent {
            FillinTheme {
                CloudSyncScreen(BudgetSyncContext(ready = true, cloudEnabled = enabled.value, lastSyncedAt = "2026-10-24T04:41:00Z"), false, null, {}, { enabled.value = it }, { syncs++ })
            }
        }
        compose.onNodeWithText("Синхронизировать").assertDoesNotExist()
        snapshot("cloud-disabled")
        compose.onNodeWithTag("cloud_sync_toggle").performClick().assertIsOn()
        compose.onNodeWithText("Синхронизировать").performClick()
        assertEquals(1, syncs)
        snapshot("cloud-enabled")
    }

    @Test fun familyInvitationCanBeSharedRevokedAndJoined() {
        val code = mutableStateOf("")
        var shared = 0
        var revoked = 0
        var joined = 0
        compose.setContent {
            FillinTheme {
                FamilyAccessScreen(
                    BudgetInvitationState(target = InvitationTarget("profile", "user", "family", "OWNER"), current = BudgetInvitation("invitation", "family", "2026-10-31T18:59:00Z"), code = "ABCDE-FGHJK-MNPQR-STVW0"),
                    FamilyJoinState(AuthSession.Authenticated(USER), code.value), {}, {}, {}, { shared++ }, { revoked++ }, {}, { code.value = it }, { joined++ },
                )
            }
        }
        snapshot("family-access")
        compose.onNodeWithText("Поделиться").performClick()
        compose.onNodeWithText("Отозвать").performClick()
        compose.onNodeWithText("Вступить").assertIsNotEnabled()
        compose.onNodeWithText("Код приглашения").performTextInput("ABCDE-FGHJK-MNPQR-STVW0")
        closeSoftKeyboard()
        compose.onNodeWithText("Вступить").performScrollTo().performClick()
        assertEquals(1, shared)
        assertEquals(1, revoked)
        assertEquals(1, joined)
    }

    private fun snapshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File)
            ?: context.getExternalFilesDir("profile-redesign")!!
        directory.mkdirs()
        val file = File(directory, "$name.png")
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun profileState() = ProfileUiState(loading = false, budgets = listOf(
        ProfileBudget("family", "Основной бюджет", true, true), ProfileBudget("local", "Накопления", false, false),
    ))

    private companion object { val USER = AuthUser("user", "Анна Иванова", "anna@example.com") }
}
