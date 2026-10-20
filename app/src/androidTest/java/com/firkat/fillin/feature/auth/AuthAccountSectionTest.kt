package com.firkat.fillin.feature.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModelStore
import com.firkat.fillin.feature.auth.domain.AuthRepository
import com.firkat.fillin.feature.auth.domain.AuthSession
import com.firkat.fillin.feature.auth.domain.AuthUser
import com.firkat.fillin.feature.auth.ui.AuthAccountSection
import com.firkat.fillin.feature.auth.ui.AuthViewModel
import com.firkat.fillin.feature.auth.ui.GoogleSignInLauncher
import com.firkat.fillin.ui.theme.FillinTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AuthAccountSectionTest {
    @get:Rule val compose = createComposeRule()
    private val models = ViewModelStore()
    @After fun cleanup() = models.clear()

    private fun model(repository: FakeAuthRepository) = AuthViewModel(repository).also { models.put("auth", it) }

    @Test fun signInShowsProfileAndSignOutReturnsToLocalMode() {
        val repository = FakeAuthRepository()
        val model = model(repository)
        val launcher = GoogleSignInLauncher { "synthetic-google-credential" }
        compose.setContent { FillinTheme { AuthAccountSection(model, launcher) } }
        compose.onNodeWithText("Локальный бюджет доступен без входа.").assertIsDisplayed()
        compose.onNodeWithText("Войти через Google").performClick()
        compose.onNodeWithText("Тестовый пользователь").assertIsDisplayed()
        compose.onNodeWithText("user@example.test").assertIsDisplayed()
        compose.onNodeWithText("Выйти из аккаунта").performClick()
        compose.onNodeWithText("Вы не вошли в аккаунт").assertIsDisplayed()
        compose.onNodeWithText("user@example.test").assertDoesNotExist()
        assertEquals(1, repository.signIns)
        assertEquals(1, repository.signOuts)
    }

    @Test fun cancellingGooglePickerIsNotAnErrorAndCanBeRetried() {
        val repository = FakeAuthRepository()
        val model = model(repository)
        var requests = 0
        val launcher = GoogleSignInLauncher {
            requests++
            throw GetCredentialCancellationException("Synthetic cancellation")
        }
        compose.setContent { FillinTheme { AuthAccountSection(model, launcher) } }
        repeat(2) {
            compose.onNodeWithText("Войти через Google").performClick()
            compose.waitUntil { !model.state.value.busy && requests == it + 1 }
        }
        compose.onNodeWithText("Вы не вошли в аккаунт").assertIsDisplayed()
        assertNull(model.state.value.error)
        assertEquals(0, repository.signIns)
    }

    @Test fun leavingScreenCancelsPickerWithoutReopeningItOnReturn() {
        val repository = FakeAuthRepository()
        val model = model(repository)
        val credential = CompletableDeferred<String>()
        var requests = 0
        var visible by mutableStateOf(true)
        val launcher = GoogleSignInLauncher { requests++; credential.await() }
        compose.setContent { FillinTheme { if (visible) AuthAccountSection(model, launcher) } }
        compose.onNodeWithText("Войти через Google").performClick()
        compose.waitUntil { requests == 1 }
        compose.runOnIdle { visible = false }
        compose.waitUntil { !model.state.value.busy }
        compose.runOnIdle { visible = true }
        compose.onNodeWithText("Войти через Google").assertIsEnabled()
        assertEquals(1, requests)
        assertEquals(0, repository.signIns)
        compose.runOnIdle { credential.complete("synthetic-google-credential") }
        compose.onNodeWithText("Войти через Google").performClick()
        compose.onNodeWithText("Тестовый пользователь").assertIsDisplayed()
        assertEquals(2, requests)
    }

    @Test fun restoredSessionShowsProfileWithoutOpeningPicker() {
        val repository = FakeAuthRepository(AuthSession.Authenticated(USER))
        val launcher = GoogleSignInLauncher { error("Restored user must not reopen Google Sign-In") }
        val model = model(repository)
        compose.setContent { FillinTheme { AuthAccountSection(model, launcher) } }
        compose.onNodeWithText("Тестовый пользователь").assertIsDisplayed()
        compose.onNodeWithText("Выйти из аккаунта").assertIsEnabled()
        assertEquals(0, repository.signIns)
    }

    @Test fun missingConfigurationKeepsLocalModeAvailable() {
        val model = model(FakeAuthRepository(AuthSession.Unavailable))
        val launcher = GoogleSignInLauncher { error("Google Sign-In is not configured") }
        compose.setContent { FillinTheme { AuthAccountSection(model, launcher) } }
        compose.onNodeWithText("Войти через Google").assertIsNotEnabled()
        compose.onNodeWithText("Локальный бюджет доступен без входа.").assertIsDisplayed()
    }

    private class FakeAuthRepository(initial: AuthSession = AuthSession.Anonymous) : AuthRepository {
        override val session = MutableStateFlow(initial)
        var signIns = 0
        var signOuts = 0
        override suspend fun signInWithGoogle(idToken: String) {
            signIns++
            session.value = AuthSession.Authenticated(USER)
        }
        override suspend fun signOut() {
            signOuts++
            session.value = AuthSession.Anonymous
        }
    }

    private companion object {
        val USER = AuthUser("synthetic-uid", "Тестовый пользователь", "user@example.test")
    }
}
