package com.firkat.fillin.feature.operationhistory.ui

import androidx.lifecycle.ViewModelStore
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationPlanLinkContext
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationPlanLinks
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationPlanOffer
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationPlanOfferStore
import com.firkat.fillin.feature.operationhistory.domain.repository.PlanLinkOperation
import com.firkat.fillin.feature.operationhistory.domain.repository.PlanLinkTarget
import com.firkat.fillin.feature.operationhistory.domain.repository.PlanSuggestion
import com.firkat.fillin.feature.operationhistory.domain.repository.SuggestedPlan
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OperationPlanLinkViewModelTest {
    private val models = ViewModelStore()
    private val plan = SuggestedPlan(
        PlanLinkTarget("EXPENSE", ruleId = "rule", dueOn = "2026-10-30"),
        "category",
        "Связь",
        "100",
        "80",
        0,
        currency = "KZT"
    )
    private val operation = PlanLinkOperation(
        0, "EXPENSE", "category", "Связь", "COMMUNICATION", "KZT", "2026-10-30", "80", "80"
    )

    @Before fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After fun cleanup() {
        models.clear()
        Dispatchers.resetMain()
    }

    @Test fun `confirmation blocks duplicate clicks and dismissal until saved`() = runTest {
        val store = Offers()
        val model = model(store)
        runCurrent()
        model.proceed()
        model.proceed()
        assertFalse(model.requestDismiss())
        runCurrent()
        assertEquals(1, store.confirms)
        assertEquals(0, store.dismisses)
        assertTrue(model.state.value.saving)
        store.gate.complete(Unit)
        runCurrent()
        assertEquals("Связь", model.effects.first().planName)
    }

    @Test fun `gesture persists dismissal before closing without linking`() = runTest {
        val store = Offers()
        val model = model(store)
        runCurrent()
        model.requestDismiss()
        runCurrent()
        assertEquals(1, store.dismisses)
        assertEquals(0, store.confirms)
        assertNull(model.effects.first().planName)
    }

    @Test fun `an ambiguous choice is explicit and continue only starts review`() = runTest {
        val store = Offers(PlanSuggestion.Choice(listOf(plan), false))
        val model = model(store)
        runCurrent()
        assertFalse(model.state.value.canProceed)
        model.proceed()
        assertEquals(PlanOfferPage.Suggestions, model.state.value.page)
        model.select(plan)
        model.proceed()
        assertEquals(PlanOfferPage.Review, model.state.value.page)
        assertEquals(0, store.confirms)
    }

    @Test fun `changing the remainder without changing version requires a fresh manual choice`() = runTest {
        val store = Offers()
        val model = model(store)
        runCurrent()
        val updated = plan.copy(remainingAmount = "60")
        store.context.value = OperationPlanLinkContext(operation, OperationPlanLinks(listOf(updated)))
        runCurrent()
        assertEquals(PlanOfferError.Changed, model.state.value.error)
        model.proceed()
        assertEquals(0, store.confirms)
        model.chooseAnother()
        assertEquals(PlanOfferPage.Manual, model.state.value.page)
        model.group(model.state.value.groups.single().key)
        model.select(updated)
        model.proceed()
        assertEquals(updated, model.state.value.selected?.plan)
        assertTrue(model.state.value.canProceed)
        assertEquals(0, store.dismisses)
    }

    @Test fun `failed confirmation keeps selection and allows retry`() = runTest {
        val store = Offers().apply {
            fail = true
            gate.complete(Unit)
        }
        val model = model(store)
        runCurrent()
        model.proceed()
        runCurrent()
        assertEquals(PlanOfferError.Save, model.state.value.error)
        assertEquals(plan, model.state.value.selected?.plan)
        store.fail = false
        model.proceed()
        runCurrent()
        assertEquals(2, store.confirms)
        assertEquals("Связь", model.effects.first().planName)
    }

    private fun model(
        store: Offers
    ) = OperationPlanLinkViewModel("op", store).also { models.put("model", it) }

    private inner class Offers(private val suggestion: PlanSuggestion = PlanSuggestion.Recommended(plan)) :
        OperationPlanOfferStore {
        val context = MutableStateFlow(OperationPlanLinkContext(operation, OperationPlanLinks(listOf(plan))))
        val gate = CompletableDeferred<Unit>()
        var confirms = 0
        var dismisses = 0
        var fail = false
        override suspend fun prepareOffer(
            operationId: String
        ) = true
        override suspend fun proposal(
            operationId: String
        ) = OperationPlanOffer(operation, suggestion)
        override fun observeOfferContext(
            operationId: String
        ) = context
        override suspend fun confirmOffer(
            operationId: String,
            operation: PlanLinkOperation,
            plan: SuggestedPlan
        ) {
            confirms++
            gate.await()
            check(!fail)
        }
        override suspend fun dismissProposal(
            operationId: String
        ) {
            dismisses++
        }
    }
}
