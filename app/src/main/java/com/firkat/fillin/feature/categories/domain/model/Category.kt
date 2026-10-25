package com.firkat.fillin.feature.categories.domain.model

data class Category(
    val id: String,
    val name: String,
    val kind: CategoryKind,
    val icon: CategoryIcon,
    val dictionaryKey: String? = null,
    val revision: Long = 0,
)

enum class CategoryKind {
    Expense,
    Income,
}

data class CategoryIcon(
    val code: String,
    val emoji: String = "📦",
)
