package com.firkat.fillin.ui.icon

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.firkat.fillin.R

internal data class CategoryIconOption(
    val code: String,
    @DrawableRes val drawableResource: Int,
    @StringRes val labelResource: Int
)

/** Presentation catalogue only: persisted and synchronized codes remain opaque strings. */
internal object CategoryIcons {
    val options = listOf(
        CategoryIconOption("GROCERIES", R.drawable.ic_category_groceries, R.string.category_icon_groceries),
        CategoryIconOption("CAFE_RESTAURANTS", R.drawable.ic_category_cafe, R.string.category_icon_cafe_restaurants),
        CategoryIconOption("HOME", R.drawable.ic_category_housing, R.string.category_icon_home),
        CategoryIconOption("CAR", R.drawable.ic_category_car, R.string.category_icon_car),
        CategoryIconOption("COMMUNICATION", R.drawable.ic_category_communication, R.string.category_icon_communication),
        CategoryIconOption("HEALTH", R.drawable.ic_category_health, R.string.category_icon_health),
        CategoryIconOption("CLOTHING", R.drawable.ic_category_clothing, R.string.category_icon_clothing),
        CategoryIconOption(
            "HOUSEHOLD_GOODS",
            R.drawable.ic_category_household,
            R.string.category_icon_household_goods
        ),
        CategoryIconOption("PERSONAL_CARE", R.drawable.ic_category_personal_care, R.string.category_icon_personal_care),
        CategoryIconOption("CHILDREN", R.drawable.ic_category_children, R.string.category_icon_children),
        CategoryIconOption(
            "BOOKS_EDUCATION",
            R.drawable.ic_category_education,
            R.string.category_icon_books_education
        ),
        CategoryIconOption("BICYCLE", R.drawable.ic_category_bicycle, R.string.category_icon_entertainment),
        CategoryIconOption("TRAVEL", R.drawable.ic_category_leisure, R.string.category_icon_travel),
        CategoryIconOption("GIFT", R.drawable.ic_category_gifts, R.string.category_icon_gift),
        CategoryIconOption("SUBSCRIPTIONS", R.drawable.ic_category_subscriptions, R.string.category_icon_other_expense),
        CategoryIconOption("SALARY", R.drawable.ic_category_salary, R.string.category_icon_salary),
        CategoryIconOption("MORTGAGE", R.drawable.ic_category_mortgage, R.string.category_icon_side_job),
        CategoryIconOption(
            "SOCIAL_BENEFITS",
            R.drawable.ic_category_bonus,
            R.string.category_icon_social_benefits
        ),
        CategoryIconOption("SPORT", R.drawable.ic_category_sport, R.string.category_icon_other_income),
        CategoryIconOption("PETS", R.drawable.ic_category_pets, R.string.category_icon_pets),
        CategoryIconOption("LOAN", R.drawable.ic_category_credit, R.string.category_icon_loan),
        CategoryIconOption("TAXES", R.drawable.ic_category_fees, R.string.category_icon_taxes)
    )

    private val byCode = options.associateBy { it.code }
    private val legacyCodes = mapOf(
        "FOOD" to "GROCERIES",
        "TRANSPORT" to "CAR",
        "CLOTHES" to "CLOTHING",
        "EDUCATION" to "BOOKS_EDUCATION",
        "INCOME" to "SALARY"
    )

    fun find(
        code: String
    ): CategoryIconOption? {
        val lookupCode = code.trim().uppercase()
        return byCode[legacyCodes[lookupCode] ?: lookupCode]
    }
}
