package com.firkat.fillin.feature.categories.domain.usecase

import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import com.firkat.fillin.feature.categories.domain.repository.CategoryUsageRepository

const val DEFAULT_USED_CATEGORY_LIMIT = 4

class GetFrequentlyUsedCategories(private val repository: CategoryUsageRepository) {
    operator fun invoke(
        limit: Int = DEFAULT_USED_CATEGORY_LIMIT,
        kind: CategoryKind? = null
    ) = repository.observeFrequentlyUsed(limit, kind)
}

class GetRecentlyUsedCategories(private val repository: CategoryUsageRepository) {
    operator fun invoke(
        limit: Int = DEFAULT_USED_CATEGORY_LIMIT,
        kind: CategoryKind? = null
    ) = repository.observeRecentlyUsed(limit, kind)
}
