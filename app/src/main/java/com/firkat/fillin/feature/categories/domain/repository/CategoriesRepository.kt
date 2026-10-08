package com.firkat.fillin.feature.categories.domain.repository

import com.firkat.fillin.feature.categories.domain.model.Category
import com.firkat.fillin.feature.categories.domain.model.CategoryKind

interface CategoriesRepository {
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
