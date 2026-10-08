package com.firkat.fillin.feature.categories.domain.model

data class Category(
    val id: String,
    val name: String,
    val kind: CategoryKind,
    val icon: CategoryIcon,
) {
    val isOther: Boolean
        get() = icon.code == OTHER_CATEGORY_ICON_CODE
}

const val OTHER_CATEGORY_ICON_CODE = "OTHER"

enum class CategoryKind {
    Expense,
    Income,
}

data class CategoryIcon(
    val code: String,
    val emoji: String,
)
