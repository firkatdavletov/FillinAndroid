package com.firkat.fillin.feature.categories.domain.usecase

import com.firkat.fillin.feature.categories.domain.model.Category
import com.firkat.fillin.feature.categories.domain.repository.CategoriesRepository

class GetCategories(
    private val repository: CategoriesRepository,
) {
    fun observe() = repository.observeCategories()

    fun requestSync() = repository.requestSync()

    suspend operator fun invoke(): List<Category> = repository.getCategories()
}
