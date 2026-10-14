package com.firkat.fillin.feature.categories.domain.usecase

import com.firkat.fillin.feature.categories.domain.model.Category
import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import com.firkat.fillin.feature.categories.domain.repository.CategoriesException
import com.firkat.fillin.feature.categories.domain.repository.CategoriesRepository

class DeleteCategory(
    private val repository: CategoriesRepository,
) {
    suspend operator fun invoke(category: Category) {
        if (category.kind != CategoryKind.Expense) {
            throw CategoriesException.InvalidRequest
        }
        repository.deleteCategory(category.id)
    }
}
