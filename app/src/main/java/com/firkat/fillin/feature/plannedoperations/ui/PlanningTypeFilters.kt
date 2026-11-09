package com.firkat.fillin.feature.plannedoperations.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.firkat.fillin.R
import com.firkat.fillin.feature.plannedoperations.domain.model.PlannedOperationType

private val FilterMinHeight = 88.dp
private const val FadeOutMillis = 150
private const val FadeInMillis = 200

@Composable
internal fun PlanningTypeFilters(
    expenseCount: Int,
    incomeCount: Int,
    selectedType: PlannedOperationType?,
    isLoading: Boolean,
    onTypeSelected: (PlannedOperationType?) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = selectedType,
        modifier = modifier.fillMaxWidth().heightIn(min = FilterMinHeight),
        contentAlignment = Alignment.CenterStart,
        transitionSpec = {
            (fadeIn(tween(FadeInMillis, delayMillis = FadeOutMillis)) togetherWith
                fadeOut(tween(FadeOutMillis))).using(
                SizeTransform { _, _ -> tween(FadeOutMillis + FadeInMillis) },
            )
        },
        label = "Planning type filter",
    ) { type ->
        if (type == null) {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlannedOperationType.entries.forEach { cardType ->
                    val colors = MaterialTheme.colorScheme
                    Card(
                        onClick = { onTypeSelected(cardType) },
                        enabled = selectedType == null,
                        modifier = Modifier.weight(1f).fillMaxHeight().testTag("planning-filter-${cardType.name}"),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = colors.surfaceContainer,
                            contentColor = colors.onSurfaceVariant,
                            disabledContainerColor = colors.surfaceContainer,
                            disabledContentColor = colors.onSurfaceVariant,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.heightIn(min = FilterMinHeight).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(stringResource(cardType.filterLabel), style = MaterialTheme.typography.labelLarge)
                            PlanningTypeCount(
                                count = if (cardType == PlannedOperationType.Expense) expenseCount else incomeCount,
                                isLoading = isLoading,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = FilterMinHeight).testTag("planning-filter-selected"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(vertical = 12.dp)) {
                    PlanningTypeCount(
                        count = if (type == PlannedOperationType.Expense) expenseCount else incomeCount,
                        isLoading = isLoading,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = stringResource(type.filterLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { onTypeSelected(null) },
                    enabled = selectedType == type,
                    modifier = Modifier.testTag("planning-filter-reset"),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.operation_totals_reset_type),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanningTypeCount(
    count: Int,
    isLoading: Boolean,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    if (isLoading) {
        CircularProgressIndicator(modifier = modifier.size(24.dp), strokeWidth = 2.dp)
    } else {
        Text(
            text = pluralStringResource(R.plurals.planning_plan_count, count, count),
            modifier = modifier,
            style = style,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private val PlannedOperationType.filterLabel: Int
    get() = when (this) {
        PlannedOperationType.Expense -> R.string.planned_operations_expenses
        PlannedOperationType.Income -> R.string.planned_operations_incomes
    }
