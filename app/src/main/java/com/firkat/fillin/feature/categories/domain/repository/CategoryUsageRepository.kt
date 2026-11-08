package com.firkat.fillin.feature.categories.domain.repository

import com.firkat.fillin.feature.categories.domain.model.Category
import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import kotlinx.coroutines.flow.Flow

interface CategoryUsageRepository {
    fun observeFrequentlyUsed(limit: Int, kind: CategoryKind?): Flow<List<Category>>
    fun observeRecentlyUsed(limit: Int, kind: CategoryKind?): Flow<List<Category>>
}
