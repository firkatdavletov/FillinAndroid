package com.firkat.fillin.feature.budgetsetup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.firkat.fillin.feature.auth.domain.AuthSession
import com.firkat.fillin.feature.auth.domain.AuthUser
import com.firkat.fillin.feature.budgetsetup.domain.BudgetSetupRepository
import com.firkat.fillin.feature.budgetsetup.ui.BudgetSetupStep
import com.firkat.fillin.feature.budgetsetup.ui.BudgetSetupViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.math.BigDecimal
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetSetupViewModelTest {
    private val models = ViewModelStore()
    @Before fun setup() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun cleanup() { models.clear(); Dispatchers.resetMain() }

    @Test fun `successful sign in opens cloud and back does not sign out or immediately reopen it`() = runTest {
        val model = model()
        model.authSessionChanged(AuthSession.Anonymous)
        assertEquals(BudgetSetupStep.Welcome, model.step)
        model.authSessionChanged(user())
        assertEquals(BudgetSetupStep.CloudSetup, model.step)
        model.back()
        model.authSessionChanged(user())
        assertEquals(BudgetSetupStep.Welcome, model.step)
        model.chooseCloud()
        assertEquals(BudgetSetupStep.CloudSetup, model.step)
    }

    @Test fun `saved welcome and local steps survive session restoration`() = runTest {
        val saved = SavedStateHandle()
        val first = model(saved)
        first.authSessionChanged(user())
        first.back()
        val restoredWelcome = model(saved)
        restoredWelcome.authSessionChanged(user())
        assertEquals(BudgetSetupStep.Welcome, restoredWelcome.step)
        restoredWelcome.chooseCreate()
        val restoredLocal = model(saved)
        restoredLocal.authSessionChanged(user())
        assertEquals(BudgetSetupStep.LocalSetup, restoredLocal.step)
    }

    @Test fun `restored google session opens cloud while sign out returns to welcome`() = runTest {
        val model = model()
        model.authSessionChanged(AuthSession.Loading)
        model.authSessionChanged(user())
        assertEquals(BudgetSetupStep.CloudSetup, model.step)
        model.authSessionChanged(AuthSession.Anonymous)
        assertEquals(BudgetSetupStep.Welcome, model.step)
    }

    private fun user() = AuthSession.Authenticated(AuthUser("owner", null, null))
    private fun model(saved: SavedStateHandle = SavedStateHandle()) = BudgetSetupViewModel(object : BudgetSetupRepository {
        override val ready = MutableStateFlow(false)
        override val today = LocalDate.of(2026, 10, 22)
        override suspend fun createLocal(openingBalance: BigDecimal, startedOn: LocalDate) = error("Unused")
        override suspend fun connectExisting() = error("Unused")
    }, savedState = saved).also { models.put("setup-${models.keys().size}", it) }
}
