package com.firkat.fillin.feature.categories.ui

import com.firkat.fillin.feature.categories.domain.model.Category

data class CategoriesUiState(
    val items: List<Category> = emptyList(),
    val isLoading: Boolean = false,
    val error: CategoriesError? = null,
)

enum class CategoriesError {
    MissingApiToken,
    Unauthorized,
    Forbidden,
    Network,
    InvalidResponse,
    Unknown,
}

sealed interface CategoriesEvent {
    data object Retry : CategoriesEvent
}
