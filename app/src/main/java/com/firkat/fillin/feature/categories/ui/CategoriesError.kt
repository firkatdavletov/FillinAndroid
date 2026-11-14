package com.firkat.fillin.feature.categories.ui

import androidx.annotation.StringRes
import com.firkat.fillin.R
import com.firkat.fillin.feature.categories.domain.repository.CategoriesException

enum class CategoriesError {
    MissingApiToken,
    Unauthorized,
    Forbidden,
    InvalidRequest,
    NotFound,
    Conflict,
    Network,
    InvalidResponse,
    Unknown
}

internal fun CategoriesException.toUiError(): CategoriesError = when (this) {
    CategoriesException.MissingApiToken -> CategoriesError.MissingApiToken
    CategoriesException.Unauthorized -> CategoriesError.Unauthorized
    CategoriesException.Forbidden -> CategoriesError.Forbidden
    CategoriesException.InvalidRequest -> CategoriesError.InvalidRequest
    CategoriesException.NotFound -> CategoriesError.NotFound
    CategoriesException.Conflict -> CategoriesError.Conflict
    CategoriesException.Network -> CategoriesError.Network
    CategoriesException.InvalidResponse -> CategoriesError.InvalidResponse
    CategoriesException.Unknown -> CategoriesError.Unknown
}

internal val CategoriesError.messageResource: Int
    @StringRes get() =
        when (this) {
            CategoriesError.MissingApiToken,
            CategoriesError.Unauthorized
            -> R.string.categories_error_unauthorized

            CategoriesError.Forbidden -> R.string.categories_error_forbidden

            CategoriesError.InvalidRequest -> R.string.categories_error_invalid_request

            CategoriesError.NotFound -> R.string.categories_error_not_found

            CategoriesError.Conflict -> R.string.categories_error_conflict

            CategoriesError.Network -> R.string.categories_error_network

            CategoriesError.InvalidResponse -> R.string.categories_error_invalid_response

            CategoriesError.Unknown -> R.string.categories_error_generic
        }
