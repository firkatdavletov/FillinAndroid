package com.firkat.fillin.feature.budgetsetup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.firkat.fillin.feature.budgetconnection.domain.*
import com.firkat.fillin.feature.budgetsetup.domain.BudgetSetupMode
import com.firkat.fillin.feature.budgetsetup.ui.BudgetSetupViewModel
import com.firkat.fillin.feature.budgetsetup.ui.interactor.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetSetupViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()
    private val env = SetupTestEnvironment()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { models.clear(); Dispatchers.resetMain() }
    private fun model(saved: SavedStateHandle = SavedStateHandle(), clock: Clock = Clock.systemUTC()) = BudgetSetupViewModel(env.auth, env.connection, env.creation, env.local, env.family, saved, dispatcher, clock = clock).also {
        models.put("model-${models.keys().size}", it); it.onAction(BudgetSetupAction.Start)
    }
    @Test fun guestDraftSurvivesLoginAndSavedState() = runTest(dispatcher) {
        val saved = SavedStateHandle(); val m = model(saved); runCurrent()
        m.onAction(BudgetSetupAction.NameChanged("Поездка")); m.onAction(BudgetSetupAction.BalanceChanged("125,50"))
        m.onAction(BudgetSetupAction.StorageChanged(BudgetSetupMode.Cloud)); m.onAction(BudgetSetupAction.Create)
        env.available = listOf(AvailableBudget("existing", "Семья", "member", "OWNER")); env.login(); runCurrent()
        assertEquals("Поездка", m.state.value.name); assertEquals(SetupPage.Create, m.state.value.page)
        assertEquals(0, env.creates)
        val restored = model(SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })); runCurrent()
        assertEquals("125,50", restored.state.value.balance)
    }

    @Test fun localCreationUsesAlmatyDateAtSubmitAndIgnoresLegacyDraftDate() = runTest(dispatcher) {
        val clock = TestClock("2026-11-02T18:59:59Z")
        val saved = SavedStateHandle(mapOf("budget_setup_draft" to arrayListOf(
            "Поездка", "0", "Local", "Create", "", "", "true", "", "false", "RUB", "false", "2026-10-01",
        )))
        val m = model(saved, clock)
        runCurrent()
        clock.now = Instant.parse("2026-11-02T19:00:00Z")
        m.onAction(BudgetSetupAction.Create)
        clock.now = Instant.parse("2026-11-03T19:00:00Z")
        runCurrent()
        assertEquals("Поездка", env.submitted!!.name)
        assertEquals("2026-11-03", env.submitted!!.accountingStartedOn)
    }
    @Test fun repeatTapsDoNotRepeatLocalCreation() = runTest(dispatcher) {
        env.gate = CompletableDeferred(); val m = model(); runCurrent()
        m.onAction(BudgetSetupAction.Create); m.onAction(BudgetSetupAction.Create); runCurrent()
        assertEquals(1, env.localCreates); assertTrue(m.state.value.busy)
        m.onAction(BudgetSetupAction.NameChanged("Другой")); assertEquals("Личный бюджет", m.state.value.name)
        env.gate!!.complete(Unit); runCurrent(); assertTrue(m.state.value.completed)
    }
    @Test fun pendingRequestSurvivesModelRecreationAndResumes() = runTest(dispatcher) {
        env.login(); env.fail = true
        val clock = TestClock("2026-11-02T18:59:59Z")
        val m = model(clock = clock); runCurrent()
        m.onAction(BudgetSetupAction.StorageChanged(BudgetSetupMode.Cloud))
        m.onAction(BudgetSetupAction.Create); runCurrent(); assertNotNull(m.state.value.pending)
        val pending = requireNotNull(env.saved)
        assertEquals("2026-11-02", pending.draft.accountingStartedOn)
        clock.now = Instant.parse("2026-11-02T19:00:00Z")
        val restored = model(clock = clock); runCurrent(); assertNotNull(restored.state.value.pending)
        restored.onAction(BudgetSetupAction.Create); runCurrent()
        assertTrue(restored.state.value.completed); assertEquals(1, env.creates); assertEquals(1, env.resumes)
        assertEquals(pending, env.resumed)
    }
    @Test fun accountChangeCancelsOldOperationAndResetClearsSavedDraft() = runTest(dispatcher) {
        env.gate = CompletableDeferred(); val saved = SavedStateHandle(); val m = model(saved); runCurrent()
        m.onAction(BudgetSetupAction.NameChanged("Старый бюджет")); m.onAction(BudgetSetupAction.Create); runCurrent()
        env.login("other"); runCurrent()
        assertFalse(m.state.value.busy); assertFalse(m.state.value.completed)
        assertEquals("Личный бюджет", m.state.value.name)
        m.onAction(BudgetSetupAction.Reset)
        assertFalse(saved.contains("budget_setup_draft"))
    }

    @Test fun existingCatalogShowsListAndBackReturnsToIt() = runTest(dispatcher) {
        env.login(); env.available = listOf(AvailableBudget("budget", "Семья", "member", "OWNER"))
        val m = model(); runCurrent(); assertEquals(SetupPage.List, m.state.value.page)
        m.onAction(BudgetSetupAction.OpenCreate); m.onAction(BudgetSetupAction.Back)
        assertEquals(SetupPage.List, m.state.value.page)
    }

    private class TestClock(initial: String) : Clock() {
        var now: Instant = Instant.parse(initial)
        override fun instant() = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = fixed(now, zone)
    }
}
