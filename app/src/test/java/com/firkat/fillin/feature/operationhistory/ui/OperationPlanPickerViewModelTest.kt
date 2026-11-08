package com.firkat.fillin.feature.operationhistory.ui

import androidx.lifecycle.ViewModelStore
import com.firkat.fillin.feature.operationhistory.domain.repository.*
import com.firkat.fillin.feature.plannedoperations.domain.repository.PlanningRulesRepository
import com.firkat.fillin.feature.plannedoperations.domain.model.PlanningRule
import com.firkat.fillin.feature.plannedoperations.domain.usecase.ObservePlanningRules
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class OperationPlanPickerViewModelTest {
    private val models = ViewModelStore()
    private val plan = SuggestedPlan(PlanLinkTarget("EXPENSE", ruleId = "r", dueOn = "2026-11-04"), "expense-category", "Еда", "100", "50", 1)
    private val candidates = MutableStateFlow(OperationPlanLinks())
    private var saved = 0
    private var fail = false
    private val gate = CompletableDeferred<Unit>()
    private val links = object : OperationPlanLinkStore {
        override val pendingProposal = MutableStateFlow<String?>(null)
        override fun observeLinks(operationId: String) = candidates
        override suspend fun proposal(operationId: String): SuggestedPlan? = null
        override suspend fun selectProposal(operationId: String, plan: SuggestedPlan) = Unit
        override suspend fun dismissProposal(operationId: String) = Unit
        override suspend fun removeLink(operationId: String, operationVersion: Long) = Unit
        override suspend fun setLink(operationId: String, plan: SuggestedPlan, operationVersion: Long) {
            saved++; gate.await(); if (fail) error("offline error")
        }
    }
    @Before fun setup() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun cleanup() { models.clear(); Dispatchers.resetMain() }
    private fun model(): OperationPlanPickerViewModel {
        val rules = ObservePlanningRules(object : PlanningRulesRepository {
            override fun observeRules() = flowOf(emptyList<PlanningRule>())
            override fun requestSync() = Unit
        })
        return OperationPlanPickerViewModel("first", FakeOfflineHistory(), links, rules).also { models.put("picker", it) }
    }
    @Test fun `groups recurring payments and standalone incomes in candidate order`() {
        val second = plan.copy(target = plan.target.copy(dueOn = "2026-12-04"))
        val income = plan.copy(target = PlanLinkTarget("INCOME", planId = "income", dueOn = "2026-11-04"))
        val groups = pickerGroups(listOf(plan, income, second), emptyList())
        assertEquals(2, groups.size)
        assertEquals(listOf(plan, second), groups[0].payments)
        assertEquals(listOf(income), groups[1].payments)
        assertEquals("Еда", groups[0].name)
    }
    @Test fun `selection is explicit and back cancels it before closing`() = runTest {
        candidates.value = OperationPlanLinks(candidates = listOf(plan))
        val model = model(); runCurrent()
        model.group(model.state.value.groups.first().key)
        assertNull(model.state.value.selected)
        model.select(plan); model.back()
        assertNull(model.state.value.group); assertNull(model.state.value.selected)
        assertFalse(model.state.value.closed); assertEquals(0, saved)
        model.back(); assertTrue(model.state.value.closed)
    }
    @Test fun `changed payment clears selection and prevents saving stale version`() = runTest {
        candidates.value = OperationPlanLinks(candidates = listOf(plan))
        val model = model(); runCurrent(); model.group("EXPENSE:r"); model.select(plan)
        candidates.value = OperationPlanLinks(candidates = listOf(plan.copy(version = 2)))
        runCurrent(); assertNull(model.state.value.selected); assertTrue(model.state.value.error)
        model.save(); assertEquals(0, saved)
    }
    @Test fun `save blocks duplicate writes and back then returns to details`() = runTest {
        candidates.value = OperationPlanLinks(candidates = listOf(plan))
        val model = model(); runCurrent(); model.group("EXPENSE:r"); model.select(plan)
        model.save(); model.save(); model.back(); runCurrent()
        assertEquals(1, saved); assertTrue(model.state.value.saving); assertFalse(model.state.value.closed)
        gate.complete(Unit); runCurrent(); assertTrue(model.state.value.closed)
    }
    @Test fun `failure retains selection and retry reopens save`() = runTest {
        candidates.value = OperationPlanLinks(candidates = listOf(plan))
        val model = model(); runCurrent(); model.group("EXPENSE:r"); model.select(plan)
        fail = true; gate.complete(Unit); model.save(); runCurrent()
        assertTrue(model.state.value.error); assertFalse(model.state.value.closed)
        fail = false; model.load(); runCurrent(); model.save(); runCurrent()
        assertTrue(model.state.value.closed)
    }
}
