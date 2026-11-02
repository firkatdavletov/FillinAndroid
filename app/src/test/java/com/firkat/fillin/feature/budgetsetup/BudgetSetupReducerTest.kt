package com.firkat.fillin.feature.budgetsetup

import com.firkat.fillin.feature.budgetsetup.ui.interactor.*
import org.junit.Assert.*
import org.junit.Test

class BudgetSetupReducerTest {
    private val reducer = BudgetSetupReducer()
    private val initial = BudgetSetupState("0", "2026-10-22")

    @Test fun `editing and restoring belong to state and busy protects the accepted form`() {
        val restored = reducer.reduceAction(initial, BudgetSetupAction.RestoreForm("123,45", "2026-10-21"))
        assertEquals("123,45", restored.balance)
        assertEquals("2026-10-21", restored.date)
        val busy = reducer.reduceAction(restored.copy(error = "old"), BudgetSetupAction.Create)
        assertTrue(busy.busy)
        assertNull(busy.error)
        assertEquals(1L, busy.operationId)
        for (action in listOf(BudgetSetupAction.Create, BudgetSetupAction.BalanceChanged("9"), BudgetSetupAction.DateChanged("bad"), BudgetSetupAction.RestoreForm("9", "bad"))) {
            assertEquals(busy, reducer.reduceAction(busy, action))
        }
    }
    @Test fun `readiness comes only from observation and stale completion cannot release a new operation`() {
        val busy = reducer.reduceAction(initial, BudgetSetupAction.Create)
        assertEquals(busy, reducer.reduceEvent(busy, BudgetSetupEvent.Created(0, "old")))
        assertEquals(busy, reducer.reduceAction(busy, BudgetSetupAction.OperationCancelled(0)))
        val finished = reducer.reduceEvent(busy, BudgetSetupEvent.Created(1))
        assertFalse(finished.busy)
        assertNull(finished.ready)
        val ready = reducer.reduceEvent(finished, BudgetSetupEvent.ReadinessChanged(true))
        assertEquals(ready, reducer.reduceAction(ready, BudgetSetupAction.Create))
        assertEquals(false, reducer.reduceEvent(ready, BudgetSetupEvent.ReadinessChanged(false)).ready)
    }
    @Test fun `error and cancellation permit retry without erasing form`() {
        val busy = reducer.reduceAction(initial, BudgetSetupAction.Create)
        val failed = reducer.reduceEvent(busy, BudgetSetupEvent.Created(1, "storage"))
        assertEquals("storage", failed.error)
        val retry = reducer.reduceAction(failed, BudgetSetupAction.Create)
        assertNull(retry.error)
        assertEquals(2L, retry.operationId)
        val cancelled = reducer.reduceAction(retry, BudgetSetupAction.OperationCancelled(2))
        assertFalse(cancelled.busy)
        assertEquals(initial.balance, cancelled.balance)
        assertEquals(cancelled, reducer.reduceAction(cancelled, BudgetSetupAction.Back))
    }
}
