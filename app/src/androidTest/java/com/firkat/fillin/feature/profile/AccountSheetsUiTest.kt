package com.firkat.fillin.feature.profile

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import com.firkat.fillin.feature.profile.ui.DeleteAccountBottomSheet
import com.firkat.fillin.feature.profile.ui.SignOutAccountBottomSheet
import com.firkat.fillin.ui.theme.FillinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class AccountSheetsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun backDismissesWithoutSigningOut() {
        val open = mutableStateOf(true)
        var calls = 0
        compose.setContent { FillinTheme {
            if (open.value) SignOutAccountBottomSheet({ calls++ }, { open.value = false })
        } }
        compose.onNodeWithTag("sign_out_sheet").assertIsDisplayed()
        pressBack()
        compose.onNodeWithTag("sign_out_sheet").assertDoesNotExist()
        assertEquals(0, calls)
    }

    @Test fun cancelDismissesDeletionWithoutSubmitting() {
        val open = mutableStateOf(true)
        var calls = 0
        compose.setContent { FillinTheme {
            if (open.value) DeleteAccountBottomSheet("УДАЛИТЬ", {}, { calls++ }, { open.value = false })
        } }
        compose.onNodeWithText("Отмена").performScrollTo().performClick()
        compose.onNodeWithTag("delete_account_sheet").assertDoesNotExist()
        assertEquals(0, calls)
    }

    @Test fun largeFontAndKeyboardKeepDeleteActionReachable() {
        val input = mutableStateOf("")
        var calls = 0
        compose.setContent { FillinTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                DeleteAccountBottomSheet(input.value, { input.value = it }, { calls++ }, {})
            }
        } }
        compose.onNodeWithTag("delete_confirmation_input").performScrollTo().performTextInput("УДАЛИТЬ")
        compose.onNodeWithTag("delete_confirm").performScrollTo().assertIsDisplayed().assertIsEnabled()
        snapshot("delete-large-font-keyboard")
        compose.onNodeWithTag("delete_confirm").performClick()
        assertEquals(1, calls)
    }

    @Test fun darkSignOutShowsBothActions() {
        compose.setContent { FillinTheme(darkTheme = true) { SignOutAccountBottomSheet({}, {}) } }
        compose.onNodeWithTag("sign_out_confirm").assertIsDisplayed()
        compose.onNodeWithText("Отмена").assertIsDisplayed()
        snapshot("sign-out-dark")
    }

    @Test fun lightDeleteShowsWarningAndConfirmation() {
        compose.setContent { FillinTheme(darkTheme = false) { DeleteAccountBottomSheet("", {}, {}, {}) } }
        compose.onNodeWithTag("delete_confirm").performScrollTo().assertIsNotEnabled()
        snapshot("delete-light")
    }

    private fun snapshot(name: String) {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = (InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File)
            ?: File(context.getExternalFilesDir(null), "account-sheets")).apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
