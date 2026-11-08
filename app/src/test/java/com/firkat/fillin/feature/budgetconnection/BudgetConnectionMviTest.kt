package com.firkat.fillin.feature.budgetconnection

import androidx.lifecycle.ViewModelStore
import com.firkat.fillin.feature.auth.domain.*
import com.firkat.fillin.feature.budgetconnection.domain.*
import com.firkat.fillin.feature.budgetconnection.ui.BudgetConnectionViewModel
import com.firkat.fillin.feature.budgetconnection.ui.interactor.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetConnectionMviTest {
    private val models = ViewModelStore()
    private val auth = Auth()
    private val repository = Repository()
    private val creation = Creation()
    private val draft = BudgetCreationDraft("Семья", "1,25", "2026-10-21")
    @Before fun setup() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun cleanup() { models.clear(); Dispatchers.resetMain() }

    @Test fun `reducer rejects every old result after A B A and cancellation cannot clear a new request`() {
        val reducer = BudgetConnectionReducer(true)
        var state = BudgetConnectionState()
        state = reducer.reduceEvent(state, BudgetConnectionEvent.SessionChanged(session("A")))
        val old = state.request()!!
        state = reducer.reduceEvent(state, BudgetConnectionEvent.SessionChanged(session("B")))
        state = reducer.reduceEvent(state, BudgetConnectionEvent.SessionChanged(session("A")))
        for (event in listOf(
            BudgetConnectionEvent.Cached(old, listOf(budget)), BudgetConnectionEvent.Pending(old, PendingBudgetCreation("old", draft)),
            BudgetConnectionEvent.Loaded(old, listOf(budget), "2026-10-22"), BudgetConnectionEvent.Created(old),
            BudgetConnectionEvent.CreationFailed(old, draft, null, "old"), BudgetConnectionEvent.Finished(old, "old"),
        )) assertEquals(state, reducer.reduceEvent(state, event))
        assertEquals(state, reducer.reduceAction(state, BudgetConnectionAction.Cancelled(old)))
        val request = state.request()!!
        state = reducer.reduceEvent(state, BudgetConnectionEvent.Loaded(request, emptyList(), "2026-10-22"))
        state = reducer.reduceEvent(state, BudgetConnectionEvent.Finished(request))
        assertNotNull(state.draft)
        val pending = PendingBudgetCreation("saved", draft)
        val loading = reducer.reduceAction(state, BudgetConnectionAction.Refresh)
        val withPending = reducer.reduceEvent(loading, BudgetConnectionEvent.Pending(loading.request()!!, pending))
        val idle = reducer.reduceEvent(withPending, BudgetConnectionEvent.Finished(loading.request()!!))
        for (action in listOf(BudgetConnectionAction.EditDraft(draft.copy(name = "other")), BudgetConnectionAction.ChooseCreate(), BudgetConnectionAction.CancelCreate, BudgetConnectionAction.Connect(budget))) {
            assertEquals(idle, reducer.reduceAction(idle, action))
        }
        assertEquals(pending.draft, reducer.reduceAction(idle, BudgetConnectionAction.Restore("A", draft.copy(name = "restored"))).draft)
        assertEquals(state, reducer.reduceAction(state, BudgetConnectionAction.Restore("other", draft)))
    }

    @Test fun `section restoration is independent of loading while drafts wait for loaded idle state`() {
        val reducer = BudgetConnectionReducer(true)
        val loading = reducer.reduceEvent(BudgetConnectionState(), BudgetConnectionEvent.SessionChanged(session("A")))
        val restored = reducer.reduceAction(loading, BudgetConnectionAction.RestoreSection("A", true))
        assertTrue(restored.joiningFamily)
        assertTrue(restored.loading)
        assertEquals(loading, reducer.reduceAction(loading, BudgetConnectionAction.Restore("A", draft)))
        assertEquals(restored, reducer.reduceAction(restored, BudgetConnectionAction.RestoreSection("other", false)))
    }

    @Test fun `load emits cache then pending then network and refresh taps are synchronously guarded`() = runTest {
        repository.cached = listOf(budget)
        creation.saved = PendingBudgetCreation("saved", draft)
        val events = BudgetConnectionInteractor(auth, repository, creation, StandardTestDispatcher(testScheduler))
            .execute(BudgetConnectionCommand.Load(ConnectionRequest("A", 1, 1))).toList()
        assertEquals(listOf("cache", "pending", "network"), repository.calls)
        assertTrue(events[0] is BudgetConnectionEvent.Cached)
        assertTrue(events[1] is BudgetConnectionEvent.Pending)
        assertTrue(events[2] is BudgetConnectionEvent.Loaded)
        assertTrue(events[3] is BudgetConnectionEvent.Finished)
        val model = model(); runCurrent()
        val reads = repository.reads
        repository.gate = CompletableDeferred()
        model.onAction(BudgetConnectionAction.Refresh); model.onAction(BudgetConnectionAction.Refresh)
        assertTrue(model.state.value.loading)
        runCurrent()
        assertEquals(reads + 1, repository.reads)
        repository.selected.value = "other-budget"; runCurrent()
        assertEquals("other-budget", model.state.value.activeBudgetId)
        assertEquals(1, auth.session.subscriptionCount.value)
        assertEquals(1, repository.selected.subscriptionCount.value)
        repository.gate!!.complete(Unit); runCurrent()
        models.clear(); runCurrent()
        assertEquals(0, auth.session.subscriptionCount.value)
        assertEquals(0, repository.selected.subscriptionCount.value)
    }

    @Test fun `create failure recovers pending resume is distinct and failed pending read keeps original`() = runTest {
        val model = model(); runCurrent()
        model.onAction(BudgetConnectionAction.ChooseCreate())
        model.onAction(BudgetConnectionAction.EditDraft(draft))
        creation.failure = IllegalStateException("response lost")
        creation.saveOnCreate = true
        model.onAction(BudgetConnectionAction.Create); model.onAction(BudgetConnectionAction.Create); runCurrent()
        assertEquals(1, creation.creates)
        assertEquals(creation.saved, model.state.value.pending)
        assertNotNull(model.state.value.error)
        creation.failPending = true
        model.onAction(BudgetConnectionAction.Create); runCurrent()
        assertEquals(1, creation.resumes)
        assertEquals(creation.saved, model.state.value.pending)
        assertFalse(model.state.value.busy)
        creation.failure = null; creation.failPending = false
        model.onAction(BudgetConnectionAction.Create); runCurrent()
        assertEquals(2, creation.resumes)
        assertNull(model.state.value.pending)
        assertNull(model.state.value.error)
        assertFalse(model.state.value.busy)
    }

    @Test fun `account change cancels load connect and create including uncancellable late failures`() = runTest {
        for (operation in listOf("load", "connect", "create")) {
            auth.session.value = session("A")
            repository.gate = null; creation.gate = null; repository.lateFailure = false
            val model = model(); runCurrent()
            val gate = CompletableDeferred<Unit>()
            when (operation) {
                "load" -> { repository.gate = gate; model.onAction(BudgetConnectionAction.Refresh) }
                "connect" -> { repository.gate = gate; model.onAction(BudgetConnectionAction.Connect(budget)) }
                "create" -> {
                    creation.gate = gate
                    model.onAction(BudgetConnectionAction.ChooseCreate())
                    model.onAction(BudgetConnectionAction.EditDraft(draft))
                    model.onAction(BudgetConnectionAction.Create)
                }
            }
            runCurrent()
            repository.gate = null; creation.gate = null
            auth.session.value = session("B"); runCurrent()
            auth.session.value = session("A"); runCurrent()
            repository.lateFailure = true
            gate.complete(Unit); runCurrent()
            assertNull("$operation late error", model.state.value.error)
            assertFalse("$operation stuck busy", model.state.value.busy)
            assertEquals(listOf(budget), model.state.value.budgets)
            models.clear(); runCurrent()
        }
    }

    @Test fun `cancellation permits retry while both observations remain alive and back emits only when idle`() = runTest {
        val model = model(); runCurrent()
        val effects = mutableListOf<BudgetConnectionEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.effects.collect { effects += it } }
        repository.cancel = true
        model.onAction(BudgetConnectionAction.Refresh)
        model.onAction(BudgetConnectionAction.Back); runCurrent()
        assertFalse(model.state.value.busy)
        assertNull(model.state.value.error)
        assertTrue(effects.isEmpty())
        repository.selected.value = "changed"; runCurrent()
        assertEquals("changed", model.state.value.activeBudgetId)
        repository.cancel = false
        model.onAction(BudgetConnectionAction.Refresh); runCurrent()
        model.onAction(BudgetConnectionAction.Back); runCurrent()
        assertEquals(listOf(BudgetConnectionEffect.Back), effects)
        model.onAction(BudgetConnectionAction.ShowJoin(true))
        model.onAction(BudgetConnectionAction.ShowJoin(false))
        assertFalse(model.state.value.joiningFamily)
        models.clear(); runCurrent()
        assertEquals(0, repository.selected.subscriptionCount.value)
        assertEquals(0, auth.session.subscriptionCount.value)
    }

    @Test fun `clearing the model cancels command publication and both observations while repository commit can finish`() = runTest {
        val model = model(); runCurrent()
        val gate = CompletableDeferred<Unit>()
        creation.gate = gate
        model.onAction(BudgetConnectionAction.ChooseCreate())
        model.onAction(BudgetConnectionAction.EditDraft(draft))
        model.onAction(BudgetConnectionAction.Create); runCurrent()
        val beforeClear = model.state.value
        models.clear(); runCurrent()
        assertEquals(0, auth.session.subscriptionCount.value)
        assertEquals(0, repository.selected.subscriptionCount.value)
        gate.complete(Unit); runCurrent()
        assertEquals(1, creation.finished)
        assertEquals(beforeClear, model.state.value)
    }

    private fun model() = BudgetConnectionViewModel(auth, repository, creation).also { models.put("model", it) }
    private fun session(uid: String) = AuthSession.Authenticated(AuthUser(uid, null, null))
    private val budget = AvailableBudget("budget", "Семья", "owner", "OWNER")
    private inner class Auth : AuthRepository {
        override val session = MutableStateFlow<AuthSession>(session("A"))
        override suspend fun signOut() { session.value = AuthSession.Anonymous }
        override suspend fun signInWithGoogle(idToken: String) = Unit
    }
    private inner class Repository : BudgetConnectionRepository {
        val selected = MutableStateFlow<String?>(null)
        override val activeBudgetId = selected
        var reads = 0
        val calls = mutableListOf<String>()
        var cached = emptyList<AvailableBudget>()
        var gate: CompletableDeferred<Unit>? = null
        var lateFailure = false
        var cancel = false
        override suspend fun cachedBudgets(uid: String): List<AvailableBudget> { calls += "cache"; return cached }
        override suspend fun budgets(uid: String): List<AvailableBudget> {
            reads++; calls += "network"
            if (cancel) throw CancellationException("cancelled")
            val wait = gate
            withContext(NonCancellable) { wait?.await() }
            if (wait != null && lateFailure) error("late")
            return listOf(budget)
        }
        override suspend fun connect(uid: String, budget: AvailableBudget) {
            val wait = gate
            withContext(NonCancellable) { wait?.await() }
            if (wait != null && lateFailure) error("late")
        }
    }
    private inner class Creation : BudgetCreationRepository {
        var saved: PendingBudgetCreation? = null
        var failure: Exception? = null
        var failPending = false
        var saveOnCreate = false
        var gate: CompletableDeferred<Unit>? = null
        var creates = 0; var resumes = 0; var finished = 0
        override suspend fun pending(uid: String): PendingBudgetCreation? {
            repository.calls += "pending"
            if (failPending) error("pending unavailable")
            return saved
        }
        override suspend fun create(uid: String, draft: BudgetCreationDraft) {
            creates++
            if (saveOnCreate) saved = PendingBudgetCreation("request", draft)
            val wait = gate
            withContext(NonCancellable) { wait?.await() }
            failure?.let { throw it }
            if (wait != null && repository.lateFailure) error("late")
            saved = null
            finished++
        }
        override suspend fun resume(uid: String) { resumes++; failure?.let { throw it }; saved = null }
    }
}
