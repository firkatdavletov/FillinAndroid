package com.firkat.fillin.feature.dashboard.widgets.common.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.firkat.fillin.feature.dashboard.domain.model.Money
import com.firkat.fillin.ui.format.formatMoney
import com.firkat.fillin.ui.theme.saldomiColors
import com.firkat.fillin.ui.theme.saldomiDesignColors

@Composable
fun WidgetCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.saldomiDesignColors.border),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), content = content)
    }
}

@Composable
fun WidgetHeading(
    @StringRes title: Int,
    @DrawableRes icon: Int,
    stacked: Boolean = false
) {
    if (stacked) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BasicText(
                stringResource(title),
                Modifier.weight(1f),
                style =
                MaterialTheme.typography.labelLarge.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = TextUnit.Unspecified
                ),
                maxLines = 1,
                softWrap = false,
                autoSize =
                TextAutoSize.StepBased(
                    minFontSize = 10.sp,
                    maxFontSize = MaterialTheme.typography.labelLarge.fontSize,
                    stepSize = 1.sp
                )
            )
        }
    }
}

@Composable
fun MoneyAmount(
    money: Money?,
    modifier: Modifier = Modifier
) {
    BasicText(
        text = money?.let { formatMoney(it.amount, it.currency) } ?: "—",
        modifier = modifier.fillMaxWidth(),
        style =
        MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.SemiBold,
            lineHeight = TextUnit.Unspecified,
            color =
            if (money != null && money.amount.signum() < 0) {
                MaterialTheme.saldomiColors.expense
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        ),
        maxLines = 1,
        softWrap = false,
        autoSize =
        TextAutoSize.StepBased(
            minFontSize = (8f / LocalDensity.current.fontScale).sp,
            maxFontSize = 28.sp,
            stepSize = 1.sp
        )
    )
}

@Composable
fun MetricWidget(
    @StringRes title: Int,
    @DrawableRes icon: Int,
    amount: Money?,
    modifier: Modifier = Modifier,
    supportingText: String? = null
) {
    WidgetCard(modifier) {
        Spacer(Modifier.height(8.dp))
        WidgetHeading(title, icon, stacked = LocalDensity.current.fontScale > 1.3f)
        Spacer(Modifier.weight(1f))
        if (amount != null || supportingText == null) {
            MoneyAmount(amount)
        }
        if (supportingText != null) {
            if (amount != null) Spacer(Modifier.height(4.dp))
            Text(
                supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
