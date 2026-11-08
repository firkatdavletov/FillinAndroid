package com.firkat.fillin.feature.budgetmanagement.domain

fun validBudgetName(value: String): Boolean = value.trim().let {
    it.length in 1..80 && it.none(Char::isISOControl)
}
