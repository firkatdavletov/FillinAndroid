package com.firkat.fillin.feature.profile

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.firkat.fillin.feature.auth.domain.AuthUser
import com.firkat.fillin.feature.profile.domain.*
import com.firkat.fillin.feature.profile.ui.*
import com.firkat.fillin.feature.profile.ui.bottomsheets.DeleteAccountBottomSheet
import com.firkat.fillin.feature.profile.ui.bottomsheets.SignOutAccountBottomSheet
import com.firkat.fillin.ui.theme.FillinTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ProfileDeletionUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun unverifiedUserCanReachDeleteAtBottomOfProfile() {
        var opened = false
        compose.setContent {
            FillinTheme {
                ProfileScreen(
                    ProfileUiState(loading = false), AuthUser("uid", "Анна", "a@b.com", emailVerified = false), false,
                    {}, {}, {}, {}, {}, {}, {}, onDeleteClick = { opened = true }
                )
            }
        }
        compose.onNodeWithTag("delete_profile").performScrollTo().performClick()
        assertTrue(opened)
    }

    @Test fun guestHasNoDeletionButton() {
        compose.setContent {
            FillinTheme { ProfileScreen(ProfileUiState(loading = false), null, false, {}, {}, {}, {}, {}, {}, {}) }
        }
        compose.onNodeWithTag("delete_profile").assertDoesNotExist()
        compose.onNodeWithTag("sign_out_profile").assertDoesNotExist()
    }

    @Test fun confirmationRequiresExactWordAndAllowsCancel() {
        val input = mutableStateOf("")
        var submitted = false
        var cancelled = false
        compose.setContent {
            FillinTheme {
                DeleteAccountBottomSheet(input.value, { input.value = it }, {
                    submitted =
                        true
                }, { cancelled = true })
            }
        }
        compose.onNodeWithTag("delete_confirm").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("delete_confirmation_input").performScrollTo().performTextInput("удалить")
        compose.onNodeWithTag("delete_confirm").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("delete_confirmation_input").performScrollTo().performTextReplacement(" УДАЛИТЬ ")
        compose.onNodeWithTag("delete_confirm").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Отмена").performScrollTo().performClick()
        assertTrue(cancelled)
        assertFalse(submitted)
        compose.onNodeWithTag("delete_confirm").performScrollTo().performClick()
        assertTrue(submitted)
    }

    @Test fun signOutRequiresConfirmationAndAllowsCancel() {
        var submitted = false
        var cancelled = false
        compose.setContent {
            FillinTheme {
                SignOutAccountBottomSheet({ submitted = true }, { cancelled = true })
            }
        }
        assertFalse(submitted)
        compose.onNodeWithText("Отмена").performScrollTo().performClick()
        assertTrue(cancelled)
        assertFalse(submitted)
        compose.onNodeWithTag("sign_out_confirm").performScrollTo().performClick()
        assertTrue(submitted)
    }

    @Test fun pendingStateDoesNotClaimCompletionAndOffersNoCancellation() {
        compose.setContent { FillinTheme { DeletionStatusContent(ProfileDeletionState(DeletionPhase.Pending), {}) } }
        compose.onNodeWithText("Удаление выполняется. Автономными бюджетами можно пользоваться.").assertIsDisplayed()
        compose.onNodeWithText("Профиль удалён").assertDoesNotExist()
        compose.onNodeWithText("Отмена").assertDoesNotExist()
    }
}
