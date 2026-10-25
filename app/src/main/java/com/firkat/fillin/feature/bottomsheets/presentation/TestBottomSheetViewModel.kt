package com.firkat.fillin.feature.bottomsheets.presentation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

class TestBottomSheetViewModel(operationId: String, action: TestBottomSheetAction) : ViewModel() {
    val uiState = MutableStateFlow(TestBottomSheetUiState(operationId, action)).asStateFlow()
    private val effectChannel = Channel<TestBottomSheetEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()
    private var completed = false

    fun onEvent(event: TestBottomSheetEvent) {
        if (completed) return
        val effect = when (event) {
            TestBottomSheetEvent.CloseClicked -> TestBottomSheetEffect.Close
            TestBottomSheetEvent.ActionClicked -> {
                if (uiState.value.action == TestBottomSheetAction.None) return
                TestBottomSheetEffect.PerformOperationAction
            }
        }
        completed = true
        effectChannel.trySend(effect)
    }

    override fun onCleared() {
        effectChannel.close()
    }
}
