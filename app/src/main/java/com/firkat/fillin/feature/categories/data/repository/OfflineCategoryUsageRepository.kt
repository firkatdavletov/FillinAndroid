package com.firkat.fillin.feature.categories.data.repository

import com.firkat.fillin.feature.categories.data.local.CategoryUsageDao
import com.firkat.fillin.feature.categories.data.local.CategoryUsageTotal
import com.firkat.fillin.feature.categories.domain.model.Category
import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import com.firkat.fillin.feature.categories.domain.repository.CategoriesRepository
import com.firkat.fillin.feature.categories.domain.repository.CategoryUsageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

class OfflineCategoryUsageRepository(private val dao: CategoryUsageDao, private val categories: CategoriesRepository) :
    CategoryUsageRepository {
    override fun observeFrequentlyUsed(
        limit: Int,
        kind: CategoryKind?
    ) = observe(limit, kind, FREQUENT_ORDER)

    override fun observeRecentlyUsed(
        limit: Int,
        kind: CategoryKind?
    ) = observe(limit, kind, RECENT_ORDER)

    private fun observe(
        limit: Int,
        kind: CategoryKind?,
        order: Comparator<CategoryUsageTotal>
    ): Flow<List<Category>> {
        require(limit >= 0)
        if (limit == 0) return flowOf(emptyList())
        return combine(dao.observeTotals(), categories.observeCategories()) { totals, available ->
            rankUsedCategories(totals, available, limit, kind, order)
        }.distinctUntilChanged()
    }
}

internal val FREQUENT_ORDER = compareByDescending<CategoryUsageTotal> { it.uses }
    .thenByDescending { it.lastUsedAt }.thenBy { it.categoryId }

internal val RECENT_ORDER = compareByDescending<CategoryUsageTotal> { it.lastUsedAt }
    .thenByDescending { it.lastEventId }.thenBy { it.categoryId }

internal fun rankUsedCategories(
    totals: List<CategoryUsageTotal>,
    available: List<Category>,
    limit: Int,
    kind: CategoryKind?,
    order: Comparator<CategoryUsageTotal>
): List<Category> {
    val eligible = available.filter { kind == null || it.kind == kind }.associateBy { it.id }
    return totals.sortedWith(order).asSequence().mapNotNull { eligible[it.categoryId] }.take(limit).toList()
}
