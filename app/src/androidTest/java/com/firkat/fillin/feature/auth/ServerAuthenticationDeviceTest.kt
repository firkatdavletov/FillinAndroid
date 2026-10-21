package com.firkat.fillin.feature.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import com.firkat.fillin.feature.auth.data.FirebaseAuthApi
import com.firkat.fillin.feature.auth.data.FirebaseServerAuthRepository
import com.firkat.fillin.feature.auth.domain.*
import com.firkat.fillin.feature.auth.ui.AuthAccountSection
import com.firkat.fillin.feature.auth.ui.AuthViewModel
import com.firkat.fillin.feature.auth.ui.GoogleSignInLauncher
import com.firkat.fillin.ui.theme.FillinTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.atomic.AtomicInteger

/** Controlled HTTP responses exercise the production retry path without sending real credentials. */
class ServerAuthenticationDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val models = ViewModelStore()
    @After fun cleanup() = models.clear()
    private val calls = AtomicInteger()
    private val refreshes = mutableListOf<Boolean>()

    @Test fun first401RefreshesTokenAndShowsServerConfirmation() {
        val model = show { call -> if (call == 1) 401 else 200 }
        compose.waitUntil { model.state.value.serverProfile is ServerProfileState.Ready }
        compose.onNodeWithText("Аккаунт подтверждён сервером").assertIsDisplayed()
        assertEquals(2, calls.get())
        assertEquals(listOf(false, true), refreshes)
        compose.onNodeWithText("Выйти из аккаунта").performClick()
        compose.onNodeWithText("Аккаунт подтверждён сервером").assertDoesNotExist()
        compose.onNodeWithText("Вы не вошли в аккаунт").assertIsDisplayed()
    }

    @Test fun unavailableServerShowsRecoveryAndRetrySucceeds() {
        val model = show { call -> if (call == 1) 503 else 200 }
        compose.waitUntil { model.state.value.serverProfile is ServerProfileState.Failed }
        compose.onNodeWithText("Сервер входа временно недоступен. Повторите проверку позже.").assertIsDisplayed()
        compose.onNodeWithText("Повторить проверку").performClick()
        compose.waitUntil { model.state.value.serverProfile is ServerProfileState.Ready }
        compose.onNodeWithText("Аккаунт подтверждён сервером").assertIsDisplayed()
        assertEquals(listOf(false, false), refreshes)
    }

    @Test fun repeated401StopsAndAsksForSignInAgain() {
        val model = show { 401 }
        compose.waitUntil { model.state.value.serverProfile is ServerProfileState.Failed }
        compose.onNodeWithText("Сервер не подтвердил сессию. Выйдите из аккаунта и войдите снова.").assertIsDisplayed()
        assertEquals(2, calls.get())
        assertEquals(listOf(false, true), refreshes)
    }

    private fun show(status: (Int) -> Int): AuthViewModel {
        val auth = object : AuthRepository {
            override val session = MutableStateFlow<AuthSession>(AuthSession.Authenticated(USER))
            override suspend fun signInWithGoogle(idToken: String) = Unit
            override suspend fun signOut() { session.value = AuthSession.Anonymous }
        }
        val tokens = object : FirebaseIdTokenProvider {
            override fun isCurrentUser(uid: String) = (auth.session.value as? AuthSession.Authenticated)?.user?.uid == uid
            override suspend fun idToken(uid: String, forceRefresh: Boolean): String {
                refreshes += forceRefresh
                return if (forceRefresh) "synthetic-new-token" else "synthetic-first-token"
            }
        }
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val code = status(calls.incrementAndGet())
            val body = if (code == 200) """{"id":"synthetic-server-id","firebaseUid":"synthetic-uid"}""" else "{}"
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code)
                .message("Synthetic response").body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val api = Retrofit.Builder().baseUrl("https://synthetic.invalid/").client(client)
            .addConverterFactory(Json.asConverterFactory("application/json".toMediaType())).build()
            .create(FirebaseAuthApi::class.java)
        val model = AuthViewModel(auth, FirebaseServerAuthRepository(api, tokens))
        models.put("auth", model)
        compose.setContent { FillinTheme { AuthAccountSection(model, GoogleSignInLauncher { error("No picker expected") }) } }
        return model
    }

    private companion object { val USER = AuthUser("synthetic-uid", "Synthetic User", "user@example.test") }
}
