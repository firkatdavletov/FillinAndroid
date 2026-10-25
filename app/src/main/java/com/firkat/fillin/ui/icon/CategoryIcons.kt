package com.firkat.fillin.ui.icon

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.firkat.fillin.R

internal data class CategoryIconOption(
    val code: String,
    @DrawableRes val drawableResource: Int,
    @StringRes val labelResource: Int,
)

/** Presentation catalogue only: persisted and synchronized codes remain opaque strings. */
internal object CategoryIcons {
    val options = listOf(
        CategoryIconOption("GROCERIES", R.drawable.ic_category_groceries, R.string.category_icon_groceries),
        CategoryIconOption("CAFE_RESTAURANTS", R.drawable.ic_category_cafe_restaurants, R.string.category_icon_cafe_restaurants),
        CategoryIconOption("CAR", R.drawable.ic_category_car, R.string.category_icon_car),
        CategoryIconOption("BICYCLE", R.drawable.ic_category_bicycle, R.string.category_icon_bicycle),
        CategoryIconOption("HOME", R.drawable.ic_category_home, R.string.category_icon_home),
        CategoryIconOption("MORTGAGE", R.drawable.ic_category_mortgage, R.string.category_icon_mortgage),
        CategoryIconOption("HOUSEHOLD_GOODS", R.drawable.ic_category_household_goods, R.string.category_icon_household_goods),
        CategoryIconOption("CLOTHING", R.drawable.ic_category_clothing, R.string.category_icon_clothing),
        CategoryIconOption("HEALTH", R.drawable.ic_category_health, R.string.category_icon_health),
        CategoryIconOption("PERSONAL_CARE", R.drawable.ic_category_personal_care, R.string.category_icon_personal_care),
        CategoryIconOption("CHILDREN", R.drawable.ic_category_children, R.string.category_icon_children),
        CategoryIconOption("PETS", R.drawable.ic_category_pets, R.string.category_icon_pets),
        CategoryIconOption("BOOKS_EDUCATION", R.drawable.ic_category_books_education, R.string.category_icon_books_education),
        CategoryIconOption("ENTERTAINMENT", R.drawable.ic_category_entertainment, R.string.category_icon_entertainment),
        CategoryIconOption("SPORT", R.drawable.ic_category_sport, R.string.category_icon_sport),
        CategoryIconOption("COMMUNICATION", R.drawable.ic_category_communication, R.string.category_icon_communication),
        CategoryIconOption("SUBSCRIPTIONS", R.drawable.ic_category_subscriptions, R.string.category_icon_subscriptions),
        CategoryIconOption("GIFT", R.drawable.ic_category_gift, R.string.category_icon_gift),
        CategoryIconOption("LOAN", R.drawable.ic_category_loan, R.string.category_icon_loan),
        CategoryIconOption("SERVICE_FEES", R.drawable.ic_category_service_fees, R.string.category_icon_service_fees),
        CategoryIconOption("SALARY", R.drawable.ic_category_salary, R.string.category_icon_salary),
        CategoryIconOption("BONUS", R.drawable.ic_category_bonus, R.string.category_icon_bonus),
        CategoryIconOption("SOCIAL_BENEFITS", R.drawable.ic_category_social_benefits, R.string.category_icon_social_benefits),
    )

    private val byCode = options.associateBy { it.code }
    private val legacyCodes = mapOf(
        "FOOD" to "GROCERIES",
        "TRANSPORT" to "CAR",
        "CLOTHES" to "CLOTHING",
        "EDUCATION" to "BOOKS_EDUCATION",
        "INCOME" to "SALARY",
    )

    fun find(code: String): CategoryIconOption? {
        val lookupCode = code.trim().uppercase()
        return byCode[legacyCodes[lookupCode] ?: lookupCode]
    }

    @DrawableRes
    fun drawableResource(code: String): Int =
        find(code)?.drawableResource ?: R.drawable.ic_category_household_goods
}
