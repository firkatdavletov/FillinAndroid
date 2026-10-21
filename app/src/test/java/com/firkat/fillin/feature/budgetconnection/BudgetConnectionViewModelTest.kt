package com.firkat.fillin.feature.budgetconnection

import androidx.lifecycle.ViewModelStore
import com.firkat.fillin.feature.auth.domain.*
import com.firkat.fillin.feature.budgetconnection.domain.*
import com.firkat.fillin.feature.budgetconnection.ui.BudgetConnectionViewModel
import com.firkat.fillin.feature.sync.domain.BudgetConnectionFailure
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetConnectionViewModelTest {
    private val models = ViewModelStore()
    @Before fun setup() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun cleanup() { models.clear(); Dispatchers.resetMain() }

    @Test fun `switching account discards the old pending list and logout hides budgets`() = runTest {
        val first = CompletableDeferred<List<AvailableBudget>>()
        val auth = Auth()
        val repository = object : BudgetConnectionRepository {
            override suspend fun budgets(uid: String) = if (uid == "owner") first.await() else listOf(budget("other"))
            override suspend fun connect(uid: String, budget: AvailableBudget) = Unit
        }
        val model = model(auth, repository)
        runCurrent()
        auth.session.value = AuthSession.Authenticated(AuthUser("other", null, null))
        runCurrent()
        first.complete(listOf(budget("owner")))
        runCurrent()
        assertEquals(listOf(budget("other")), model.state.value.budgets)
        auth.signOut(); runCurrent()
        assertTrue(model.state.value.budgets.isEmpty())
        assertFalse(model.state.value.loading)
    }

    @Test fun `server error can be retried and repeated connect taps do not start another exchange`() = runTest {
        var reads = 0
        var connects = 0
        val finish = CompletableDeferred<Unit>()
        val model = model(Auth(), object : BudgetConnectionRepository {
            override suspend fun budgets(uid: String): List<AvailableBudget> {
                if (++reads == 1) throw BudgetConnectionFailure("network")
                return listOf(budget(uid))
            }
            override suspend fun connect(uid: String, budget: AvailableBudget) { connects++; finish.await() }
        })
        runCurrent()
        assertNotNull(model.state.value.error)
        model.refresh(); runCurrent()
        assertEquals(listOf(budget("owner")), model.state.value.budgets)
        assertNull(model.state.value.error)
        model.connect(budget("owner")); model.connect(budget("owner")); runCurrent()
        assertEquals(1, connects)
        finish.complete(Unit); runCurrent()
        assertNull(model.state.value.connecting)
    }

    private fun model(auth: AuthRepository, repository: BudgetConnectionRepository) = BudgetConnectionViewModel(auth, repository).also { models.put("connection", it) }
    private fun budget(uid: String) = AvailableBudget("budget-$uid", "Бюджет", "member-$uid", "OWNER")
    private class Auth : AuthRepository {
        override val session = MutableStateFlow<AuthSession>(AuthSession.Authenticated(AuthUser("owner", null, null)))
        override suspend fun signOut() { session.value = AuthSession.Anonymous }
        override suspend fun signInWithGoogle(idToken: String) = Unit
    }
}
