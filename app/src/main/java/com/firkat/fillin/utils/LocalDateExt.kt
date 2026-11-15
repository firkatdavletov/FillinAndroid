package com.firkat.fillin.utils

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import kotlin.math.abs

fun LocalDate.toRelativeDateString(): String {
    val days = ChronoUnit.DAYS.between(LocalDate.now(), this)
    val count = abs(days)

    val dayWord = when {
        count % 100 in 11L..14L -> "дней"
        count % 10 == 1L -> "день"
        count % 10 in 2L..4L -> "дня"
        else -> "дней"
    }

    return when {
        days > 0 -> "Через $count $dayWord"
        days < 0 -> "$count $dayWord назад"
        else -> "Сегодня"
    }
}

fun LocalDate.toDate(): Date = Date.from(
    atStartOfDay(ZoneId.systemDefault()).toInstant()
)
