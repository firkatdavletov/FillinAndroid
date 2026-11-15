package com.firkat.fillin.feature.profile

import androidx.lifecycle.ViewModelStore
import com.firkat.fillin.feature.auth.domain.*
import com.firkat.fillin.feature.profile.data.ProfileDeletionCoordinator
import com.firkat.fillin.feature.profile.domain.*
import com.firkat.fillin.feature.profile.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileAccountConfirmationTest {
    private val store = ViewModelStore()
    private val auth = Auth()

    @Before fun setup() = Dispatchers.setMain(StandardTestDispatcher())

    @After fun cleanup() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test fun `sign out only dispatches once after explicit confirmation`() = runTest {
        val model = model()
        runCurrent()
        var dispatched = 0
        model.openSignOut()
        assertEquals(0, dispatched)
        model.confirmSignOut { dispatched++ }
        model.confirmSignOut { dispatched++ }
        assertEquals(1, dispatched)
        assertNull(model.confirmation.value)
    }

    @Test fun `cancel resets input and prevents both operations`() = runTest {
        val model = model()
        runCurrent()
        model.open()
        model.changeConfirmation("УДАЛИТЬ")
        model.close()
        model.submit()
        model.confirmSignOut { fail("Cancelled confirmation dispatched") }
        model.open()
        assertEquals("", model.confirmation.value?.value)
    }

    @Test fun `only one confirmation can be open`() = runTest {
        val model = model()
        runCurrent()
        model.openSignOut()
        model.open()
        assertEquals(AccountConfirmationKind.SignOut, model.confirmation.value?.kind)
        model.submit()
        assertNotNull(model.confirmation.value)
    }

    @Test fun `invalid deletion word is rejected in view model`() = runTest {
        val model = model()
        runCurrent()
        model.open()
        for (value in listOf("", "удалить", "УДАЛИТЬ!")) {
            model.changeConfirmation(value)
            model.submit()
            assertEquals(value, model.confirmation.value?.value)
        }
    }

    @Test fun `account change clears confirmation and cannot sign out the new account`() = runTest {
        val model = model()
        runCurrent()
        model.openSignOut()
        auth.session.value = AuthSession.Authenticated(AuthUser("other", null, null))
        model.confirmSignOut { fail("Different account signed out") }
        model.open()
        auth.session.value = AuthSession.Anonymous
        runCurrent()
        assertNull(model.confirmation.value)
        model.openSignOut()
        model.open()
        assertNull(model.confirmation.value)
    }

    @Test fun `account change rejects deletion before session collector runs`() = runTest {
        val model = model()
        runCurrent()
        model.open()
        model.changeConfirmation(" УДАЛИТЬ ")
        auth.session.value = AuthSession.Authenticated(AuthUser("other", null, null))
        model.submit()
        assertNull(model.confirmation.value)
    }

    private fun model(): ProfileDeletionViewModel {
        val deletionStore = object : DeletionStore {
            override fun operations() = emptyList<DeletionOperation>()
            override fun blocked(
                uid: String
            ) = false
            override fun save(
                operation: DeletionOperation
            ) {
                error("Unexpected deletion")
            }
            override fun block(
                uid: String
            ) = Unit
            override fun finish(
                uid: String
            ) = Unit
        }
        return ProfileDeletionViewModel(
            ProfileDeletionCoordinator(deletionStore, null, auth, {
                "token"
            }, {}, {}),
            auth
        )
            .also { store.put("confirmation", it) }
    }

    private class Auth : AuthRepository {
        override val session = MutableStateFlow<AuthSession>(AuthSession.Authenticated(AuthUser("uid", null, null)))
        override suspend fun signInWithGoogle(
            idToken: String
        ) = Unit
        override suspend fun signOut() = Unit
    }
}
