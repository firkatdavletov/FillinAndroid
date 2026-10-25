package com.firkat.fillin.feature.bottomsheets.presentation

import kotlinx.serialization.Serializable

@Serializable
enum class TestBottomSheetAction { None, Edit, Correct }

data class TestBottomSheetUiState(
    val operationId: String,
    val action: TestBottomSheetAction,
)

sealed interface TestBottomSheetEvent {
    data object CloseClicked : TestBottomSheetEvent
    data object ActionClicked : TestBottomSheetEvent
}

sealed interface TestBottomSheetEffect {
    data object Close : TestBottomSheetEffect
    data object PerformOperationAction : TestBottomSheetEffect
}

/** Presentation result; the history feature revalidates the operation before opening its editor. */
data class TestBottomSheetResult(val operationId: String)
