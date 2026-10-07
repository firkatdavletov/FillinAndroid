package com.firkat.fillin.feature.categories.domain.repository

import com.firkat.fillin.feature.categories.domain.model.Category

interface CategoriesRepository {
    suspend fun getCategories(): List<Category>
}

sealed class CategoriesException : Exception() {
    data object MissingApiToken : CategoriesException()
    data object Unauthorized : CategoriesException()
    data object Forbidden : CategoriesException()
    data object Network : CategoriesException()
    data object InvalidResponse : CategoriesException()
    data object Unknown : CategoriesException()
}
