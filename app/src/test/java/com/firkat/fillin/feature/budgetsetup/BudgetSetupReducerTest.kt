package com.firkat.fillin.feature.budgetsetup

import com.firkat.fillin.feature.auth.domain.*
import com.firkat.fillin.feature.budgetconnection.domain.*
import com.firkat.fillin.feature.budgetsetup.domain.BudgetSetupMode
import com.firkat.fillin.feature.budgetsetup.ui.interactor.*
import org.junit.Assert.*
import org.junit.Test

class BudgetSetupReducerTest {
    private val reducer = BudgetSetupReducer()
    private val guest = BudgetSetupState(session = AuthSession.Anonymous, initialized = true)
    @Test fun storageSwitchKeepsDraft() {
        val original = guest.copy(name = "Поездка", balance = "-10,50")
        val cloud = reducer.reduceAction(original, BudgetSetupAction.StorageChanged(BudgetSetupMode.Cloud))
        assertEquals(original.name, cloud.name); assertEquals(original.balance, cloud.balance)
        assertEquals(SetupAuthTarget.Create, reducer.reduceAction(cloud, BudgetSetupAction.Create).authTarget)
    }
    @Test fun validatesNameAndBalanceBeforeRequestingAuthentication() {
        val invalid = reducer.reduceAction(guest.copy(name = " ", balance = "1.234", mode = BudgetSetupMode.Cloud), BudgetSetupAction.Create)
        assertTrue(invalid.nameInvalid); assertTrue(invalid.balanceInvalid); assertNull(invalid.authTarget)
        assertFalse(invalid.busy)
    }
    @Test fun pendingRequestLocksFormAndOverridesDraft() {
        val pending = PendingBudgetCreation("request", BudgetCreationDraft("Сохранённый", "20", "2026-10-01"))
        val restored = reducer.reduceEvent(guest, BudgetSetupEvent.Catalog(0, emptyList(), pending, true))
        assertEquals(pending.draft.name, restored.name)
        assertEquals(restored, reducer.reduceAction(restored, BudgetSetupAction.NameChanged("Другой")))
        assertEquals(restored, reducer.reduceAction(restored, BudgetSetupAction.StorageChanged(BudgetSetupMode.Local)))
    }
    @Test fun accountChangeClearsDraftExceptExplicitGuestAuthentication() {
        val login = BudgetSetupEvent.Session(AuthSession.Authenticated(AuthUser("new", null, null)))
        val draft = guest.copy(name = "Поездка", authTarget = SetupAuthTarget.Join)
        val transferred = reducer.reduceEvent(draft, login)
        assertEquals("Поездка", transferred.name); assertEquals(SetupPage.Join, transferred.page)
        val replaced = reducer.reduceEvent(transferred, BudgetSetupEvent.Session(AuthSession.Authenticated(AuthUser("other", null, null))))
        assertEquals("Личный бюджет", replaced.name); assertEquals("", replaced.code)
    }
    @Test fun updatingAccountMetadataDoesNotCancelAnOperation() {
        val current = guest.copy(session = AuthSession.Authenticated(AuthUser("owner", null, null)), ownerUid = "owner", busy = true)
        val updated = reducer.reduceEvent(current, BudgetSetupEvent.Session(AuthSession.Authenticated(AuthUser("owner", "Анна", null))))
        assertTrue(updated.busy); assertEquals(current.generation, updated.generation)
    }
    @Test fun staleAndDuplicateEventsCannotFinishNewOperation() {
        val busy = reducer.reduceAction(guest, BudgetSetupAction.Create)
        assertEquals(busy, reducer.reduceAction(busy, BudgetSetupAction.Create))
        assertEquals(busy, reducer.reduceEvent(busy, BudgetSetupEvent.Finished(999, busy.operationId)))
        val done = reducer.reduceEvent(busy, BudgetSetupEvent.Finished(busy.generation, busy.operationId))
        assertTrue(done.completed); assertEquals(done, reducer.reduceAction(done, BudgetSetupAction.Create))
    }
    @Test fun resetKeepsOfflineAccessUntilSessionIsObservedAgain() {
        val offline = reducer.reduceEvent(BudgetSetupState(), BudgetSetupEvent.Session(AuthSession.Anonymous, cloudAllowed = false, localFirst = true))
        val completed = reducer.reduceEvent(
            reducer.reduceAction(offline.copy(name = "Поездка"), BudgetSetupAction.Create),
            BudgetSetupEvent.Finished(offline.generation, offline.operationId + 1),
        )
        assertTrue(completed.completed)

        val reset = reducer.reduceAction(completed, BudgetSetupAction.Reset)
        assertFalse(reset.cloudAllowed)
        assertTrue(reset.localFirst)
        assertFalse(reset.completed)
        assertFalse(reset.initialized)
        assertEquals("Личный бюджет", reset.name)
        assertEquals(offline.generation + 1, reset.generation)

        val restarted = reducer.reduceEvent(reset, BudgetSetupEvent.Session(AuthSession.Anonymous, cloudAllowed = false, localFirst = true))
        assertTrue(restarted.initialized)
        assertFalse(restarted.cloudAllowed)
    }
}
