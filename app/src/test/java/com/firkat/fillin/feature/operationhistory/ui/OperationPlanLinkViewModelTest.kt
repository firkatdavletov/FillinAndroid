package com.firkat.fillin.feature.operationhistory.ui

import androidx.lifecycle.ViewModelStore
import com.firkat.fillin.feature.operationhistory.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class OperationPlanLinkViewModelTest {
    private val models = ViewModelStore()

    @Before fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After fun cleanup() {
        models.clear()
        Dispatchers.resetMain()
    }
    private val plan =
        SuggestedPlan(
            PlanLinkTarget("EXPENSE", ruleId = "rule", dueOn = "2026-10-30"),
            "expense-category",
            "Продукты",
            "100.00",
            "80.00",
            0
        )
    private val history = FakeOfflineHistory()
    private fun model(
        store: Links
    ) = OperationPlanLinkViewModel("first", history, store).also { models.put("model", it) }

    @Test fun `confirmation blocks repeated clicks and dismissal until saved`() = runTest {
        val store = Links()
        val model = model(store)
        runCurrent()
        assertEquals(plan, model.state.value.selected)
        model.confirm()
        model.confirm()
        assertFalse(model.requestDismiss())
        runCurrent()
        assertEquals(1, store.sets)
        assertEquals(0, store.dismisses)
        assertTrue(model.state.value.saving)
        store.gate.complete(Unit)
        runCurrent()
        assertEquals(OperationPlanLinkEffect.Close, model.effects.first())
        assertNull(history.created)
    }

    @Test fun `gesture dismissal persists refusal without creating a link`() = runTest {
        val store = Links()
        val model = model(store)
        runCurrent()
        model.requestDismiss()
        runCurrent()
        assertEquals(1, store.dismisses)
        assertEquals(0, store.sets)
        assertEquals(OperationPlanLinkEffect.Close, model.effects.first())
    }

    @Test fun `failed confirmation keeps operation and permits retry`() = runTest {
        val store = Links().apply {
            failure = IllegalStateException("disk")
            gate.complete(Unit)
        }
        val model = model(store)
        runCurrent()
        model.confirm()
        runCurrent()
        assertFalse(model.state.value.saving)
        assertEquals("link_failed", model.state.value.error)
        assertNotNull(model.state.value.operation)
        store.failure = null
        model.confirm()
        runCurrent()
        assertEquals(2, store.sets)
        assertEquals(OperationPlanLinkEffect.Close, model.effects.first())
        assertNull(history.created)
    }

    @Test fun `stale target requires new selection and never silently changes version`() = runTest {
        val store = Links()
        val model = model(store)
        runCurrent()
        store.links.value = OperationPlanLinks(listOf(plan.copy(version = 1)))
        runCurrent()
        assertEquals("plan_changed", model.state.value.error)
        assertEquals(0, model.state.value.selected!!.version)
        model.chooseAnother()
        runCurrent()
        assertEquals(OperationPlanLinkEffect.ChooseAnother, model.effects.first())
        assertEquals(1, store.dismisses)
    }

    private inner class Links : OperationPlanLinkStore {
        val links = MutableStateFlow(OperationPlanLinks(listOf(plan)))
        override fun observeLinks(
            operationId: String
        ) = links
        override val pendingProposal = MutableStateFlow<String?>("first")
        var sets = 0
        var dismisses = 0
        var failure: Exception? = null
        val gate = CompletableDeferred<Unit>()
        override suspend fun proposal(
            operationId: String
        ) = plan
        override suspend fun selectProposal(
            operationId: String,
            plan: SuggestedPlan
        ) = Unit
        override suspend fun dismissProposal(
            operationId: String
        ) {
            dismisses++
            pendingProposal.value = null
        }
        override suspend fun setLink(
            operationId: String,
            plan: SuggestedPlan,
            operationVersion: Long
        ) {
            sets++
            gate.await()
            failure?.let { throw it }
        }
        override suspend fun removeLink(
            operationId: String,
            operationVersion: Long
        ) = Unit
    }
}
