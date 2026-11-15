package com.firkat.fillin.feature.budgetupload

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import com.firkat.fillin.feature.budgetconnection.BudgetConnectionDeviceTest
import com.firkat.fillin.feature.budgetupload.domain.*
import com.firkat.fillin.feature.budgetupload.ui.*
import com.firkat.fillin.ui.theme.FillinTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*

class LocalBudgetUploadUiTest {
    @get:Rule val compose = createComposeRule()
    private val models = ViewModelStore()

    @After fun cleanup() {
        models.clear()
    }

    @Test fun localBudgetOffersGoogleLoginThenConnectsOnceWithEnteredName() {
        val auth = BudgetConnectionDeviceTest.TestAuth()
        val repository = Uploads()
        val model = LocalBudgetUploadViewModel(auth, repository).also { models.put("upload", it) }
        var signIns = 0
        compose.setContent {
            FillinTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    LocalBudgetUploadSection({ signIns++ }, model)
                }
            }
        }
        compose.onNodeWithText("Подключить к аккаунту").performScrollTo().performClick()
        assertEquals(1, signIns)
        assertEquals(0, repository.calls.size)
        auth.login("owner")
        compose.waitUntil(5000) {
            model.state.value.session is com.firkat.fillin.feature.auth.domain.AuthSession.Authenticated
        }
        compose.onNodeWithText("Название бюджета").performTextReplacement("Офлайн-бюджет")
        compose.onNodeWithText("Подключить к аккаунту").performScrollTo().performClick()
        compose.waitUntil(5000) { repository.calls.isNotEmpty() }
        compose.onNodeWithText("Подключить к аккаунту").assertIsNotEnabled()
        assertEquals(listOf(Triple("owner", "local-profile", "Офлайн-бюджет")), repository.calls)
        repository.finish.complete(Unit)
        compose.waitUntil(5000) { !model.state.value.busy }
    }

    @Test fun pendingUploadIsHiddenImmediatelyForAnotherAccountAndReturnsForItsOwner() {
        val auth = BudgetConnectionDeviceTest.TestAuth().apply { login("owner") }
        val repository = Uploads().apply {
            candidates.value =
                listOf(LocalBudgetCandidate("local-profile", "Приватный", true, firebaseUid = "owner"))
        }
        val model = LocalBudgetUploadViewModel(auth, repository).also { models.put("upload", it) }
        compose.setContent { FillinTheme { LocalBudgetUploadSection({}, model) } }
        compose.onNodeWithText("Продолжить подключение").assertExists()
        auth.login("other")
        compose.waitUntil(5000) {
            (model.state.value.session as? com.firkat.fillin.feature.auth.domain.AuthSession.Authenticated)?.user?.uid ==
                "other"
        }
        // Keep stale repository rows deliberately: the UI also enforces the captured UID.
        compose.onNodeWithText("Продолжить подключение").assertDoesNotExist()
        auth.login("owner")
        compose.onNodeWithText("Продолжить подключение").assertExists()
        assertTrue(repository.calls.isEmpty())
    }

    private class Uploads : LocalBudgetUploadRepository {
        override val candidates = MutableStateFlow(listOf(LocalBudgetCandidate("local-profile", "Мой бюджет", false)))
        val calls = mutableListOf<Triple<String, String, String>>()
        val finish = CompletableDeferred<Unit>()
        override suspend fun connect(
            uid: String,
            profileId: String,
            name: String
        ) {
            calls +=
                Triple(uid, profileId, name)
            finish.await()
        }
    }
}
