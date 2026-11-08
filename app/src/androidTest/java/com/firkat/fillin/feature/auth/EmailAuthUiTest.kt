package com.firkat.fillin.feature.auth

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.firkat.fillin.feature.auth.domain.*
import com.firkat.fillin.feature.auth.ui.components.EmailAuthContent
import com.firkat.fillin.feature.auth.ui.interactor.*
import com.firkat.fillin.feature.auth.ui.viewmodel.AuthViewModel
import com.firkat.fillin.ui.theme.FillinTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class EmailAuthUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val repo = object : AuthRepository {
        override val session = MutableStateFlow<AuthSession>(AuthSession.Anonymous)
        override suspend fun signInWithGoogle(idToken: String) {}
        override suspend fun signOut() { session.value = AuthSession.Anonymous }
        override suspend fun resetPassword(email: String) {}
        override suspend fun register(email: String, password: String) { session.value = AuthSession.EmailVerificationRequired(AuthUser("test", null, email, setOf("password"), false)) }
        override suspend fun sendVerification() {}
    }
    private lateinit var model: AuthViewModel
    private fun show(page: EmailPage) {
        compose.runOnUiThread { model = AuthViewModel(AuthState(page = page), AuthReducer(), AuthInteractor(repo, null, Dispatchers.Main.immediate)) }
        compose.setContent {
            val s by model.state.collectAsState()
            FillinTheme { EmailAuthContent(s.page, s.email, s.password, s.confirmation, s.busy, s.error, s.notice, s.session.userOrNull, false,
                { model.onAction(AuthAction.EmailChanged(it)) }, { model.onAction(AuthAction.PasswordChanged(it)) }, { model.onAction(AuthAction.ConfirmationChanged(it)) },
                { model.onAction(AuthAction.Page(it)) }, { model.onAction(AuthAction.SubmitEmail(it)) }, {}, {}, {}, { model.onAction(AuthAction.Page(EmailPage.Login)) }) }
        }
    }
    @Test fun formsNavigateAndResetUsesNeutralMessage() {
        show(EmailPage.Login)
        compose.onNodeWithText("Забыли пароль?").performScrollTo().performClick()
        compose.onNodeWithTag("email_input").performTextInput("nobody@example.test")
        compose.onNodeWithTag("email_submit").performScrollTo().performClick()
        compose.onNodeWithText("Если для адреса доступно восстановление, вы получите письмо").assertExists()
    }
    @Test fun registrationOpensVerificationAndClearsPasswords() {
        show(EmailPage.Register)
        compose.onNodeWithTag("email_input").performTextInput("test@example.test")
        compose.onNodeWithTag("password_input").performTextInput("a long password")
        compose.onNodeWithTag("confirmation_input").performTextInput("a long password")
        compose.onNodeWithTag("email_submit").performScrollTo().performClick()
        compose.onNodeWithText("Я подтвердил email").assertExists()
        compose.onNodeWithText("Повторная отправка доступна через минуту").assertIsNotEnabled()
        compose.runOnIdle { assertEquals("", model.state.value.password); assertEquals("", model.state.value.confirmation) }
    }
    @Test fun invalidAddressRemainsAnInlineError() {
        show(EmailPage.Login)
        compose.onNodeWithTag("email_input").performTextInput("wrong")
        compose.onNodeWithTag("password_input").performTextInput("old")
        compose.onNodeWithTag("email_submit").performScrollTo().performClick()
        compose.onNodeWithTag("email_error").assertTextEquals("Введите корректный email")
    }
    @Test fun leavingFormClearsPasswordAndNewModelDoesNotRestoreIt() {
        show(EmailPage.Login)
        compose.onNodeWithTag("password_input").performTextInput("sensitive value")
        compose.onNodeWithText("Регистрация").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("", model.state.value.password) }
        val fresh = AuthState()
        assertEquals("", fresh.password)
    }
}
