package com.firkat.fillin.feature.categories.domain.repository

import com.firkat.fillin.feature.categories.domain.model.Category
import com.firkat.fillin.feature.categories.domain.model.CategoryKind

interface CategoriesRepository {
    fun observeCategories(): kotlinx.coroutines.flow.Flow<List<Category>> =
        kotlinx.coroutines.flow.flow {
            emit(getCategories())
        }

    fun requestSync() = Unit

    suspend fun getCategories(): List<Category>

    suspend fun createCategory(
        name: String,
        kind: CategoryKind,
        iconCode: String,
    ): Category

    suspend fun updateCategory(
        id: String,
        name: String?,
        iconCode: String?,
    ): Category

    suspend fun updateCategory(category: Category, name: String?, iconCode: String?): Category =
        updateCategory(category.id, name, iconCode)

    suspend fun deleteCategory(category: Category) = deleteCategory(category.id)

    suspend fun deleteCategory(id: String)
}

sealed class CategoriesException : Exception() {
    data object MissingApiToken : CategoriesException()

    data object Unauthorized : CategoriesException()

    data object Forbidden : CategoriesException()

    data object InvalidRequest : CategoriesException()

    data object NotFound : CategoriesException()

    data object Conflict : CategoriesException()

    data object Network : CategoriesException()

    data object InvalidResponse : CategoriesException()

    data object Unknown : CategoriesException()
}
