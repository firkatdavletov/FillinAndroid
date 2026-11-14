package com.firkat.fillin.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateUtils {
    fun getCurrentDate(): String {
        val locale = Locale.forLanguageTag("ru-RU")
        val formatter = SimpleDateFormat("EEEE, d MMMM", locale)

        return formatter.format(Date())
            .replaceFirstChar { it.titlecase(locale) }
    }
}