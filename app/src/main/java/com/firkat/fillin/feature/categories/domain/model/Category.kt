package com.firkat.fillin.feature.categories.domain.model

data class Category(
    val id: String,
    val name: String,
    val kind: CategoryKind,
    val icon: CategoryIcon,
)

enum class CategoryKind {
    Expense,
    Income,
}

data class CategoryIcon(
    val code: String,
    val emoji: String,
)

enum class CategoryIconType(
    val emoji: String,
) {
    FOOD("🍔"),
    TRANSPORT("🚕"),
    HOME("🏠"),
    CLOTHES("👕"),
    HEALTH("💊"),
    ENTERTAINMENT("🎬"),
    SPORT("⚽"),
    EDUCATION("🎓"),
    TRAVEL("✈️"),
    PETS("🐾"),
    INCOME("💰"),
    OTHER("📦"),
    ;

    companion object {
        fun fromCode(code: String): CategoryIconType? = entries.firstOrNull {
            it.name == code.trim().uppercase()
        }
    }
}
