package com.firkat.fillin.feature.bottomsheets.presentation

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TestBottomSheetViewModelTest {
    @Test fun `action returns exactly one effect even after repeated clicks`() = runTest {
        for (action in listOf(TestBottomSheetAction.Edit, TestBottomSheetAction.Correct)) {
            val model = TestBottomSheetViewModel("operation-42", action)
            val effects = mutableListOf<TestBottomSheetEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.effects.toList(effects) }
            model.onEvent(TestBottomSheetEvent.ActionClicked)
            model.onEvent(TestBottomSheetEvent.ActionClicked)
            model.onEvent(TestBottomSheetEvent.CloseClicked)
            assertEquals(listOf(TestBottomSheetEffect.PerformOperationAction), effects)
            assertEquals(TestBottomSheetUiState("operation-42", action), model.uiState.value)
        }
    }

    @Test fun `unavailable action cannot edit but can still close`() = runTest {
        val model = TestBottomSheetViewModel("read-only", TestBottomSheetAction.None)
        val effects = mutableListOf<TestBottomSheetEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.effects.toList(effects) }
        model.onEvent(TestBottomSheetEvent.ActionClicked)
        assertTrue(effects.isEmpty())
        model.onEvent(TestBottomSheetEvent.CloseClicked)
        model.onEvent(TestBottomSheetEvent.CloseClicked)
        assertEquals(listOf(TestBottomSheetEffect.Close), effects)
    }
}
