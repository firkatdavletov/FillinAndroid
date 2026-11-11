package com.firkat.fillin.feature.budgetsetup

import com.firkat.fillin.feature.budgetsetup.ui.interactor.*
import com.firkat.fillin.feature.auth.domain.AuthSession
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BudgetSetupInteractorTest {
    @Test fun enteredDateIsUsedAndLocalDoesNotCallCloud() = runTest {
        val env = SetupTestEnvironment()
        val clock = Clock.fixed(Instant.parse("2026-11-02T23:30:00Z"), ZoneOffset.UTC)
        val interactor = BudgetSetupInteractor(env.auth, env.connection, env.creation, env.local, env.family, StandardTestDispatcher(testScheduler), clock)
        val result = interactor.execute(BudgetSetupCommand.Submit(BudgetSetupState(session = AuthSession.Anonymous, name = " Поездка ", balance = "-1,50", accountingStartedOn = "2026-10-20"), SubmitKind.Local)).toList()
        assertEquals("2026-10-20", env.submitted!!.accountingStartedOn)
        assertEquals("Поездка", env.submitted!!.name); assertEquals("-1.50", env.submitted!!.openingBalance)
        assertEquals(0, env.creates); assertEquals(1, env.localCreates)
        assertNull((result.single() as BudgetSetupEvent.Finished).error)
    }
}
