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
        if (category.isOther) throw CategoriesException.InvalidRequest

        val normalizedName = name.trim()
        val normalizedIcon = iconCode.trim().uppercase()
        if (normalizedName.isEmpty() || normalizedIcon.isEmpty()) {
            throw CategoriesException.InvalidRequest
        }

        val changedName = normalizedName.takeIf { it != category.name }
        val changedIcon = normalizedIcon.takeIf { it != category.icon.code }
        if (changedName == null && changedIcon == null) return category

        return repository.updateCategory(
            id = category.id,
            name = changedName,
            iconCode = changedIcon,
        )
    }
}
