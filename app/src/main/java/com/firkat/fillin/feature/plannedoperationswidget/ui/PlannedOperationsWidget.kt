package com.firkat.fillin.feature.plannedoperationswidget.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.firkat.fillin.R
import com.firkat.fillin.feature.dashboard.domain.model.Money
import com.firkat.fillin.feature.dashboard.widgets.common.ui.MoneyAmount
import com.firkat.fillin.feature.dashboard.widgets.common.ui.WidgetCard
import com.firkat.fillin.feature.dashboard.widgets.common.ui.WidgetHeading
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpense
import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncome
import com.firkat.fillin.ui.theme.saldomiDesignColors
import com.firkat.fillin.utils.DateUtils
import com.firkat.fillin.utils.toRelativeDateString
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun PlannedOperationsWidget(
    expenses: List<PlannedExpense>,
    income: PlannedIncome?,
    modifier: Modifier = Modifier
) {
    WidgetCard(
        modifier = modifier
    ) {
        WidgetHeading(
            title = R.string.closest_payments
        )
        Spacer(Modifier.height(16.dp))
        expenses.forEach {
            ExpenseRow(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .fillMaxWidth(),
                expense = it
            )
        }
        income?.let {
            Spacer(Modifier.height(8.dp))
            IncomeCard(it)
        }
    }
}

@Composable
private fun ExpenseRow(
    expense: PlannedExpense,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DateColumn(
            modifier = Modifier.padding(end = 8.dp),
            date = expense.dueOn
        )
        Column(
            modifier = Modifier
        ) {
            Text(
                text = expense.categoryName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = expense.dueOn.toRelativeDateString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Spacer(Modifier.weight(1f))
        MoneyAmount(
            money = Money(
                amount = expense.plannedAmount,
                currency = expense.currency
            )
        )
    }
}

@Composable
private fun DateColumn(
    date: LocalDate,
    modifier: Modifier = Modifier
) {
    val locale = Locale.forLanguageTag("ru-RU")
    val month = date.month
        .getDisplayName(TextStyle.FULL_STANDALONE, locale)
        .take(3)
        .lowercase(locale)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.saldomiDesignColors.primaryContainer)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = month,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun IncomeCard(
    income: PlannedIncome,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(12.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "Ожидаемый доход ${DateUtils.getShortDate(income.dueOn)}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Row {
            Text(
                text = income.categoryName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.weight(1f))
            MoneyAmount(
                money = Money(
                    amount = income.plannedAmount,
                    currency = income.currency
                )
            )
        }
    }
}
