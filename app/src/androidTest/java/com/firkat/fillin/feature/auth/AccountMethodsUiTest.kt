package com.firkat.fillin.feature.auth

import android.graphics.Bitmap
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.firkat.fillin.feature.auth.domain.AuthError
import com.firkat.fillin.feature.auth.domain.AuthUser
import com.firkat.fillin.feature.auth.ui.components.AccountMethodsContent
import com.firkat.fillin.ui.theme.FillinTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AccountMethodsUiTest {
    @get:Rule val compose = createComposeRule()
    private var googleClicks = 0
    private var passwordClicks = 0
    private var backs = 0
    private fun show(
        providers: Set<String>,
        dark: Boolean = false,
        busy: Boolean = false,
        error: AuthError? = null
    ) {
        compose.setContent {
            FillinTheme(darkTheme = dark) {
                AccountMethodsContent(
                    AuthUser("test", null, "anna@example.com", providers, true),
                    busy,
                    error,
                    null,
                    { googleClicks++ },
                    { passwordClicks++ },
                    {},
                    { backs++ }
                )
            }
        }
    }

    @Test fun passwordAccountCanConnectGoogleFromCardAndButton() {
        show(setOf("password"))
        compose.onNodeWithTag("method_password").assertHasNoClickAction()
        compose.onNodeWithTag("method_google").performClick()
        compose.onNodeWithText("Подключить Google с тем же email").performScrollTo().performClick()
        assertEquals(2, googleClicks)
        compose.onNodeWithText("Повторить вход").assertDoesNotExist()
        compose.onNodeWithText("Выйти из аккаунта").assertDoesNotExist()
        compose.onAllNodesWithText("anna@example.com").assertCountEquals(1)
        snapshot("methods-password-light")
    }

    @Test fun googleAccountCanAddPassword() {
        show(setOf("google.com"), dark = true)
        compose.onNodeWithTag("method_google").assertHasNoClickAction()
        compose.onNodeWithTag("method_password").performScrollTo().performClick()
        compose.onNodeWithText("Добавить пароль").performScrollTo().performClick()
        assertEquals(2, passwordClicks)
        snapshot("methods-google-dark")
    }

    @Test fun connectedMethodsAreStatusOnlyAndBackWorks() {
        show(setOf("google.com", "password"))
        compose.onNodeWithTag("method_google").assertHasNoClickAction()
        compose.onNodeWithTag("method_password").assertHasNoClickAction()
        compose.onNodeWithText("Назад").performClick()
        assertEquals(1, backs)
        snapshot("methods-both-light")
    }

    @Test fun busyBlocksBothCardAndButton() {
        show(setOf("password"), busy = true)
        compose.onNodeWithTag("method_google").assertIsNotEnabled()
        compose.onNodeWithText("Подключить Google с тем же email").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("methods_loading").assertExists()
    }

    @Test fun linkErrorRemainsVisible() {
        show(setOf("password"), error = AuthError.Network)
        compose.onNodeWithTag("email_error").assertIsDisplayed()
        compose.onNodeWithTag("method_google").assertIsEnabled()
    }

    @Test fun passwordAccountDarkSnapshot() {
        show(setOf("password"), dark = true)
        snapshot("methods-password-dark")
    }

    @Test fun narrowLargeFontKeepsConnectActionReachable() {
        compose.setContent {
            FillinTheme(darkTheme = true) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                    AccountMethodsContent(
                        AuthUser(
                            "test",
                            null,
                            "anna.alexandrovna.ivanova@example.com",
                            setOf("password"),
                            true
                        ),
                        false, null, null,
                        { googleClicks++ }, {}, {}, {}, Modifier.requiredWidth(320.dp)
                    )
                }
            }
        }
        compose.onNodeWithText("Подключить Google с тем же email").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, googleClicks)
        snapshot("methods-large-font-dark")
    }
    private fun snapshot(
        name: String
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File)
            ?: context.getExternalFilesDir("profile-redesign")!!
        directory.mkdirs()
        File(directory, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
