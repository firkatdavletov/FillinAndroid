package com.firkat.fillin.feature.dashboard.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.firkat.fillin.feature.budgetprofile.domain.BudgetCurrency
import com.firkat.fillin.feature.dashboard.domain.DashboardDataSource
import com.firkat.fillin.feature.dashboard.domain.model.Dashboard
import com.firkat.fillin.feature.dashboard.domain.repository.DashboardRepository
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpenseFrequency
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpenses
import com.firkat.fillin.feature.plannedexpenses.domain.repository.PlannedExpensesException
import com.firkat.fillin.feature.plannedexpenses.domain.repository.PlannedExpensesRepository
import com.firkat.fillin.feature.plannedexpenses.domain.usecase.GetPlannedExpenses
import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncome
import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncomeStatus
import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncomes
import com.firkat.fillin.feature.plannedincomes.domain.repository.PlannedIncomesException
import com.firkat.fillin.feature.plannedincomes.domain.repository.PlannedIncomesRepository
import com.firkat.fillin.feature.plannedincomes.domain.usecase.GetPlannedIncomes
import com.firkat.fillin.feature.profile.domain.ProfileBudget
import com.firkat.fillin.feature.profile.domain.ProfileBudgetsRepository
import com.firkat.fillin.feature.sync.domain.BudgetSyncContext
import com.firkat.fillin.feature.sync.domain.BudgetSyncControl
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    private val store = ViewModelStore()
    private val dispatcher = StandardTestDispatcher()
    private var expenses = CompletableDeferred<PlannedExpenses>()
    private var incomes = CompletableDeferred<PlannedIncomes>()
    private var expenseReads = 0
    private var incomeReads = 0

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun emptyResultsHideSkeletonAfterBothReadsWithoutStartingOverlappingRequests() = runTest {
        val model = model()
        assertTrue(model.plannedOperations.value.isLoading)
        assertTrue(model.plannedOperations.value.isVisible)

        model.onEvent(DashboardEvent.Resume)
        model.onEvent(DashboardEvent.Resume)
        runCurrent()
        assertEquals(1, expenseReads)
        assertEquals(1, incomeReads)

        expenses.complete(emptyExpenses)
        runCurrent()
        assertTrue(model.plannedOperations.value.isLoading)

        incomes.complete(PlannedIncomes(date, emptyList()))
        runCurrent()
        assertFalse(model.plannedOperations.value.isLoading)
        assertFalse(model.plannedOperations.value.isVisible)
    }

    @Test
    fun partialFailureDisplaysSuccessfulIncomeAndRefreshKeepsItUntilAnotherSuccess() = runTest {
        val model = model()
        model.onEvent(DashboardEvent.Resume)
        runCurrent()

        expenses.completeExceptionally(PlannedExpensesException.InvalidRequest)
        incomes.complete(PlannedIncomes(date, listOf(income)))
        runCurrent()
        assertFalse(model.plannedOperations.value.isLoading)
        assertEquals(income, model.plannedOperations.value.income)

        expenses = CompletableDeferred()
        incomes = CompletableDeferred()
        model.onEvent(DashboardEvent.Resume)
        runCurrent()
        assertEquals(2, incomeReads)
        assertFalse(model.plannedOperations.value.isLoading)
        assertEquals(income, model.plannedOperations.value.income)

        expenses.complete(emptyExpenses)
        incomes.completeExceptionally(PlannedIncomesException.InvalidRequest)
        runCurrent()
        assertEquals(income, model.plannedOperations.value.income)
        assertTrue(model.plannedOperations.value.isVisible)

        incomes = CompletableDeferred(PlannedIncomes(date, emptyList()))
        model.onEvent(DashboardEvent.Resume)
        runCurrent()
        assertFalse(model.plannedOperations.value.isVisible)
    }

    private fun TestScope.model(): DashboardViewModel {
        val source = DashboardDataSource(
            sync = object : BudgetSyncControl {
                override val context = flowOf(BudgetSyncContext())
                override fun requestSync() = Unit
                override suspend fun resolve(
                    id: String,
                    reapply: Boolean
                ) = Unit
            },
            repository = object : DashboardRepository {
                override fun observeDashboard() = emptyFlow<Dashboard>()
                override fun requestSync() = Unit
            },
            scope = backgroundScope
        )
        val expensesReader = object : ExpensesReader() {
            override suspend fun getPlannedExpenses(): PlannedExpenses {
                expenseReads++
                return expenses.await()
            }
        }
        val incomesReader = object : IncomesReader() {
            override suspend fun getPlannedIncomes(): PlannedIncomes {
                incomeReads++
                return incomes.await()
            }
        }
        val factory = viewModelFactory {
            initializer {
                DashboardViewModel(
                    budgets = UnusedBudgets,
                    source = source,
                    getPlannedExpenses = GetPlannedExpenses(expensesReader),
                    getPlannedIncomes = GetPlannedIncomes(incomesReader),
                    ioDispatcher = dispatcher
                )
            }
        }
        return ViewModelProvider.create(store, factory)[DashboardViewModel::class]
    }

    private companion object {
        val date: LocalDate = LocalDate.of(2026, 11, 15)
        val emptyExpenses = PlannedExpenses(date, date, emptyList())
        val income = PlannedIncome(
            id = "salary",
            categoryId = "salary",
            categoryName = "Salary",
            plannedAmount = BigDecimal("50000"),
            currency = "KZT",
            dueOn = date,
            overdue = false,
            status = PlannedIncomeStatus.Scheduled,
            version = 1,
            createdAt = Instant.EPOCH,
            modifiedAt = Instant.EPOCH
        )
    }
}

private object UnusedBudgets : ProfileBudgetsRepository {
    override fun observe() = flowOf(emptyList<ProfileBudget>())
    override suspend fun refresh() = error("Unused")
    override suspend fun select(
        id: String
    ) = error("Unused")

    override suspend fun createLocal(
        balance: BigDecimal,
        startedOn: LocalDate
    ) = error("Unused")

    override suspend fun createLocal(
        name: String,
        balance: BigDecimal,
        startedOn: LocalDate
    ) = error("Unused")

    override suspend fun createLocal(
        name: String,
        balance: BigDecimal,
        startedOn: LocalDate,
        currency: BudgetCurrency
    ) = error("Unused")
}

private abstract class ExpensesReader : PlannedExpensesRepository {
    override suspend fun createRule(
        categoryId: String,
        amount: BigDecimal,
        frequency: PlannedExpenseFrequency,
        scheduleValue: Int?,
        startsOn: LocalDate
    ) = error("Unused")

    override suspend fun updateRule(
        id: String,
        version: Long,
        amount: BigDecimal
    ) = error("Unused")

    override suspend fun deleteRule(
        id: String,
        version: Long
    ) = error("Unused")

    override suspend fun closeExpense(
        id: String,
        version: Long
    ) = error("Unused")
}

private abstract class IncomesReader : PlannedIncomesRepository {
    override suspend fun createPlannedIncome(
        categoryId: String,
        amount: BigDecimal,
        dueOn: LocalDate
    ) = error("Unused")

    override suspend fun updatePlannedIncome(
        id: String,
        version: Long,
        categoryId: String?,
        amount: BigDecimal?,
        dueOn: LocalDate?
    ) = error("Unused")

    override suspend fun deletePlannedIncome(
        id: String,
        version: Long
    ) = error("Unused")
}
