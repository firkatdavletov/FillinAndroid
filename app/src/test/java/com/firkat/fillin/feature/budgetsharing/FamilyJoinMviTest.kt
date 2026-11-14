package com.firkat.fillin.feature.budgetsharing

import androidx.lifecycle.ViewModelStore
import com.firkat.fillin.feature.auth.domain.*
import com.firkat.fillin.feature.budgetconnection.domain.AvailableBudget
import com.firkat.fillin.feature.budgetinvitation.domain.InvitationTarget
import com.firkat.fillin.feature.budgetsharing.domain.*
import com.firkat.fillin.feature.budgetsharing.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class FamilyJoinMviTest {
    private val models = ViewModelStore()
    private val auth = Auth()
    private val repository = Repository()
    private val code = "ABCDE-23456-789AB-CDEFG"

    @Before fun setup() = Dispatchers.setMain(StandardTestDispatcher())

    @After fun cleanup() {
        models.clear()
        Dispatchers.resetMain()
    }

    @Test fun `reducer preserves input on failure guards busy and rejects old A B A events`() {
        val reducer = FamilyJoinReducer()
        var state = reducer.reduceEvent(FamilyJoinState(), FamilyJoinEvent.SessionChanged(session("A")))
        state = reducer.reduceAction(state, FamilyJoinAction.EditCode(code))
        state = reducer.reduceAction(state, FamilyJoinAction.Join)
        val request = state.request()!!
        assertEquals(state, reducer.reduceAction(state, FamilyJoinAction.Join))
        assertEquals(state, reducer.reduceAction(state, FamilyJoinAction.EditCode("other")))
        val failed = reducer.reduceEvent(state, FamilyJoinEvent.Finished(request, error = "network"))
        assertEquals(code, failed.code)
        assertFalse(failed.busy)
        assertNull(reducer.reduceAction(failed, FamilyJoinAction.Join).error)
        state = reducer.reduceEvent(state, FamilyJoinEvent.SessionChanged(session("B")))
        state = reducer.reduceEvent(state, FamilyJoinEvent.SessionChanged(session("A")))
        state = reducer.reduceAction(state, FamilyJoinAction.Join)
        assertEquals(state, reducer.reduceEvent(state, FamilyJoinEvent.Finished(request, name = "old")))
        assertEquals(state, reducer.reduceEvent(state, FamilyJoinEvent.Finished(request, error = "old")))
        assertEquals(state, reducer.reduceAction(state, FamilyJoinAction.Cancelled(request)))
        val joined = reducer.reduceEvent(state, FamilyJoinEvent.Finished(state.request()!!, name = "Семья"))
        assertEquals("", joined.code)
        assertEquals("Семья", joined.joinedName)
        assertFalse(joined.busy)
    }

    @Test fun `interactor validates normalizes reports errors and propagates cancellation`() = runTest {
        val interactor = FamilyJoinInteractor(auth, repository, StandardTestDispatcher(testScheduler))
        val request = JoinRequest("A", 1, 1)
        val invalid = interactor.execute(FamilyJoinCommand.Join(request, "bad")).single() as FamilyJoinEvent.Finished
        assertNotNull(invalid.error)
        assertEquals(0, repository.calls)
        val joined = interactor.execute(
            FamilyJoinCommand.Join(request, code.lowercase())
        ).single() as FamilyJoinEvent.Finished
        assertEquals("Семья", joined.name)
        assertEquals(code, repository.code)
        repository.failure = IllegalStateException("network")
        assertNotNull(
            (interactor.execute(FamilyJoinCommand.Join(request, code)).single() as FamilyJoinEvent.Finished).error
        )
        repository.failure = CancellationException("cancelled")
        try {
            interactor.execute(FamilyJoinCommand.Join(request, code)).toList()
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }

    @Test fun `cancellation releases busy observer survives retry and clear removes subscription`() = runTest {
        val model = FamilyBudgetJoinViewModel(auth, repository).also { models.put("join", it) }
        runCurrent()
        repository.failure = CancellationException("cancelled")
        model.onAction(FamilyJoinAction.EditCode(code))
        model.onAction(FamilyJoinAction.Join)
        model.onAction(FamilyJoinAction.Join)
        runCurrent()
        assertEquals(1, repository.calls)
        assertFalse(model.state.value.busy)
        assertNull(model.state.value.error)
        repository.failure = null
        model.onAction(FamilyJoinAction.Join)
        runCurrent()
        assertEquals("Семья", model.state.value.joinedName)
        assertEquals(1, auth.session.subscriptionCount.value)
        models.clear()
        runCurrent()
        assertEquals(0, auth.session.subscriptionCount.value)
    }

    @Test fun `uncancellable old response cannot affect the same account after A B A`() = runTest {
        val model = FamilyBudgetJoinViewModel(auth, repository).also { models.put("join", it) }
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        model.onAction(FamilyJoinAction.EditCode(code))
        model.onAction(FamilyJoinAction.Join)
        runCurrent()
        auth.session.value = session("B")
        runCurrent()
        auth.session.value = session("A")
        runCurrent()
        repository.gate = null
        model.onAction(FamilyJoinAction.EditCode("new input"))
        gate.complete(Unit)
        runCurrent()
        assertEquals("new input", model.state.value.code)
        assertNull(model.state.value.joinedName)
        assertNull(model.state.value.error)
        assertFalse(model.state.value.busy)
    }

    @Test fun `clearing the model stops observation and never publishes a late committed join`() = runTest {
        val model = FamilyBudgetJoinViewModel(auth, repository).also { models.put("join", it) }
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        model.onAction(FamilyJoinAction.EditCode(code))
        model.onAction(FamilyJoinAction.Join)
        runCurrent()
        val beforeClear = model.state.value
        models.clear()
        runCurrent()
        assertEquals(0, auth.session.subscriptionCount.value)
        gate.complete(Unit)
        runCurrent()
        assertEquals(1, repository.finished)
        assertEquals(beforeClear, model.state.value)
    }

    private fun session(
        uid: String
    ) = AuthSession.Authenticated(AuthUser(uid, null, null))
    private inner class Auth : AuthRepository {
        override val session = MutableStateFlow<AuthSession>(session("A"))
        override suspend fun signOut() {
            session.value = AuthSession.Anonymous
        }
        override suspend fun signInWithGoogle(
            idToken: String
        ) = Unit
    }
    private class Repository : FamilyBudgetRepository {
        override val target = MutableStateFlow<InvitationTarget?>(null)
        override fun isCurrent(
            target: InvitationTarget
        ) = false
        var calls = 0
        var finished = 0
        var code: String? = null
        var failure: Exception? = null
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun join(
            uid: String,
            code: String
        ): AvailableBudget {
            calls++
            this.code = code
            val wait = gate
            withContext(NonCancellable) { wait?.await() }
            failure?.let { throw it }
            finished++
            return AvailableBudget("family", "Семья", "member", "MEMBER")
        }
        override suspend fun members(
            target: InvitationTarget
        ) = emptyList<FamilyBudgetMember>()
        override suspend fun revoke(
            target: InvitationTarget,
            member: FamilyBudgetMember
        ) = Unit
    }
}
