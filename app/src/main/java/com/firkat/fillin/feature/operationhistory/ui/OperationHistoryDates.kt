package com.firkat.fillin.feature.operationhistory.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// Material date pickers represent calendar days as UTC midnight, independently of the device zone.
internal fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

internal fun pickerMillisToDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
