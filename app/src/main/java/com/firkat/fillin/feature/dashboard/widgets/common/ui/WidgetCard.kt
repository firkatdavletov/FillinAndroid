package com.firkat.fillin.feature.dashboard.widgets.common.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.saldomiDesignColors.border),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            content = content
        )
    }
}

@Composable
fun WidgetHeading(
    @StringRes title: Int,
    stacked: Boolean = false
) {
    if (stacked) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BasicText(
                text = stringResource(title),
                modifier = Modifier.weight(1f),
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        color = MaterialTheme.colorScheme.onBackground,
                        lineHeight = TextUnit.Unspecified
                    ),
                maxLines = 1,
                softWrap = false,
                autoSize =
                    TextAutoSize.StepBased(
                        minFontSize = 14.sp,
                        maxFontSize = MaterialTheme.typography.titleMedium.fontSize,
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
        modifier = modifier,
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
    amount: Money?,
    modifier: Modifier = Modifier,
    supportingText: String? = null
) {
    WidgetCard(modifier) {
        Spacer(Modifier.height(8.dp))
        WidgetHeading(title, stacked = LocalDensity.current.fontScale > 1.3f)
        Spacer(Modifier.height(8.dp))
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
