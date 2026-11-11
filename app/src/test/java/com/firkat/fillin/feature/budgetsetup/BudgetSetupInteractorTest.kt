package com.firkat.fillin.feature.budgetsetup

import com.firkat.fillin.feature.budgetsetup.ui.interactor.*
import com.firkat.fillin.feature.auth.domain.AuthSession
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BudgetSetupInteractorTest {
    @Test fun localCreationUsesSubmissionDateAndDoesNotCallCloud() = runTest {
        val env = SetupTestEnvironment()
        val interactor = BudgetSetupInteractor(env.auth, env.connection, env.creation, env.local, env.family, StandardTestDispatcher(testScheduler))
        val result = interactor.execute(BudgetSetupCommand.Submit(
            BudgetSetupState(session = AuthSession.Anonymous, name = " Поездка ", balance = "-1,50"),
            SubmitKind.Local,
            startedOn = LocalDate.of(2026, 11, 3),
        )).toList()
        assertEquals("2026-11-03", env.submitted!!.accountingStartedOn)
        assertEquals("Поездка", env.submitted!!.name); assertEquals("-1.50", env.submitted!!.openingBalance)
        assertEquals(0, env.creates); assertEquals(1, env.localCreates)
        assertNull((result.single() as BudgetSetupEvent.Finished).error)
    }
}
