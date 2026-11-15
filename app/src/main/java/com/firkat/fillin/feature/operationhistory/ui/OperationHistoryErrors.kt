package com.firkat.fillin.feature.operationhistory.ui

import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryException

internal fun OperationHistoryException.toUiError(): OperationHistoryError = when (this) {
    OperationHistoryException.MissingApiToken -> OperationHistoryError.MissingApiToken
    OperationHistoryException.Unauthorized -> OperationHistoryError.Unauthorized
    OperationHistoryException.Forbidden -> OperationHistoryError.Forbidden
    OperationHistoryException.InvalidRequest -> OperationHistoryError.InvalidRequest
    OperationHistoryException.NotFound -> OperationHistoryError.NotFound
    OperationHistoryException.Conflict -> OperationHistoryError.Conflict
    OperationHistoryException.Network -> OperationHistoryError.Network
    OperationHistoryException.InvalidResponse -> OperationHistoryError.InvalidResponse
    OperationHistoryException.UnlinkBeforeCategoryChange -> OperationHistoryError.UnlinkBeforeCategoryChange
    OperationHistoryException.Unknown -> OperationHistoryError.Unknown
}
