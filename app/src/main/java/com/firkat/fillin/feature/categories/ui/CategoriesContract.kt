package com.firkat.fillin.feature.categories.ui

import com.firkat.fillin.feature.categories.domain.model.Category

data class CategoriesUiState(
    val items: List<Category> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: CategoriesError? = null
)

sealed interface CategoriesEvent {
    data object Refresh : CategoriesEvent
    data object Retry : CategoriesEvent
}
