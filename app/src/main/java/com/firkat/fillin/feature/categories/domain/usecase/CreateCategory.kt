package com.firkat.fillin.feature.categories.domain.usecase

import com.firkat.fillin.feature.categories.domain.model.Category
import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import com.firkat.fillin.feature.categories.domain.repository.CategoriesException
import com.firkat.fillin.feature.categories.domain.repository.CategoriesRepository

class CreateCategory(
    private val repository: CategoriesRepository,
) {
    suspend operator fun invoke(
        name: String,
        kind: CategoryKind,
        iconCode: String,
    ): Category {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty() || iconCode.isBlank()) {
            throw CategoriesException.InvalidRequest
        }
        return repository.createCategory(
            name = normalizedName,
            kind = kind,
            iconCode = iconCode,
        )
    }
}
