package com.firkat.fillin.feature.operationhistory.ui

import com.firkat.fillin.feature.bottomsheets.presentation.TestBottomSheetAction
import com.firkat.fillin.feature.operationhistory.domain.model.Operation
import com.firkat.fillin.feature.operationhistory.domain.model.OperationStatus
import com.firkat.fillin.feature.operationhistory.domain.model.OperationSyncStatus
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType

internal fun Operation.bottomSheetAction(): TestBottomSheetAction = when {
    syncStatus == OperationSyncStatus.Rejected -> TestBottomSheetAction.Correct
    type == OperationType.Expense && status == OperationStatus.Posted && originalCurrency == "KZT" ->
        TestBottomSheetAction.Edit
    else -> TestBottomSheetAction.None
}
