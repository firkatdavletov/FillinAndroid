package com.firkat.fillin.feature.categories.domain.usecase

import com.firkat.fillin.feature.categories.domain.model.Category
import com.firkat.fillin.feature.categories.domain.repository.CategoriesException
import com.firkat.fillin.feature.categories.domain.repository.CategoriesRepository

class UpdateCategory(
    private val repository: CategoriesRepository,
) {
    suspend operator fun invoke(
        category: Category,
        name: String,
        iconCode: String,
    ): Category {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty() || iconCode.isBlank()) {
            throw CategoriesException.InvalidRequest
        }

        val changedName = normalizedName.takeIf { it != category.name }
        val changedIcon = iconCode.takeIf { it != category.icon.code }
        if (changedName == null && changedIcon == null) return category

        return repository.updateCategory(
            category = category,
            name = changedName,
            iconCode = changedIcon,
        )
    }
}
