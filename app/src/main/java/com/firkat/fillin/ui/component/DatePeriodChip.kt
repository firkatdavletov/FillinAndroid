package com.firkat.fillin.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.firkat.fillin.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DatePeriodChip(
    dateFrom: LocalDate,
    dateTo: LocalDate,
    isDefaultPeriod: Boolean,
    onClick: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = if (isDefaultPeriod) {
        dateFrom.format(MONTH_FORMAT).replaceFirstChar { it.titlecase(RUSSIAN_LOCALE) }
    } else {
        val months = stringArrayResource(R.array.date_period_chip_short_months)
        val includeYear = dateFrom.year != dateTo.year
        val dates = listOf(dateFrom, dateTo).map { date ->
            buildString {
                append("${date.dayOfMonth} ${months[date.monthValue - 1]}")
                if (includeYear) append(" ${date.year}")
            }
        }
        stringResource(R.string.date_period_chip_range, dates[0], dates[1])
    }
    InputChip(
        selected = !isDefaultPeriod,
        onClick = onClick,
        label = { Text(label) },
        trailingIcon = {
            if (isDefaultPeriod) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_drop_down),
                    contentDescription = stringResource(R.string.date_period_chip_choose_period),
                    modifier = Modifier.size(InputChipDefaults.IconSize).testTag("date-period-dropdown")
                )
            } else {
                IconButton(
                    onClick = onReset,
                    modifier = Modifier.size(24.dp).testTag("date-period-reset")
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.date_period_chip_reset),
                        modifier = Modifier.size(InputChipDefaults.IconSize)
                    )
                }
            }
        },
        modifier = modifier
    )
}

private val RUSSIAN_LOCALE = Locale.forLanguageTag("ru")
private val MONTH_FORMAT = DateTimeFormatter.ofPattern("LLLL", RUSSIAN_LOCALE)
