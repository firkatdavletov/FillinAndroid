package com.firkat.fillin.feature.categories.domain.model

private val categoryNamePattern =
    Regex("[A-Za-zА-Яа-яЁё]+(?:-[A-Za-zА-Яа-яЁё]+)*(?: [A-Za-zА-Яа-яЁё]+(?:-[A-Za-zА-Яа-яЁё]+)*)*")
private val reservedWords = setOf("сегодня", "вчера", "завтра", "купить", "покупать", "покупку", "покупке", "покупки")

internal fun isValidCategoryName(name: String): Boolean =
    name.length <= 50 &&
        categoryNamePattern.matches(name) &&
        name.split(' ').none { it.lowercase() in reservedWords }
