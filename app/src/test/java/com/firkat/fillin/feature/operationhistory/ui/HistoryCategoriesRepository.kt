package com.firkat.fillin.feature.operationhistory.ui

import com.firkat.fillin.feature.categories.domain.model.Category
import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import com.firkat.fillin.feature.categories.domain.repository.CategoriesRepository

internal class HistoryCategoriesRepository(
    var load: suspend () -> List<Category> = { emptyList() },
) : CategoriesRepository {
    override suspend fun getCategories(): List<Category> = load()
    override suspend fun createCategory(name: String, kind: CategoryKind, iconCode: String): Category =
        error("Unexpected category mutation")
    override suspend fun updateCategory(id: String, name: String?, iconCode: String?): Category =
        error("Unexpected category mutation")
    override suspend fun deleteCategory(id: String) = error("Unexpected category mutation")
}
