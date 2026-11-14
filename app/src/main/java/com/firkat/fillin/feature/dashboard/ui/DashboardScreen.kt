package com.firkat.fillin.feature.dashboard.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.firkat.fillin.R
import com.firkat.fillin.feature.dashboard.domain.model.Money
import com.firkat.fillin.feature.dashboard.widgets.availabletospend.ui.AvailableToSpendUiState
import com.firkat.fillin.feature.dashboard.widgets.availabletospend.ui.AvailableToSpendWidget
import com.firkat.fillin.feature.dashboard.widgets.balanceforecast.ui.BalanceForecastWidget
import com.firkat.fillin.feature.dashboard.widgets.balanceforecast.ui.forecastPreviewState
import com.firkat.fillin.feature.dashboard.widgets.dailylimit.ui.DailyLimitUiState
import com.firkat.fillin.feature.dashboard.widgets.dailylimit.ui.DailyLimitWidget
import com.firkat.fillin.feature.dashboard.widgets.totalbalance.ui.TotalBalanceUiState
import com.firkat.fillin.feature.dashboard.widgets.totalbalance.ui.TotalBalanceWidget
import com.firkat.fillin.feature.plannedoperationswidget.ui.PlannedOperationsWidget
import com.firkat.fillin.ui.theme.FillinTheme
import com.firkat.fillin.ui.theme.saldomiDesignColors
import com.firkat.fillin.utils.DateUtils

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    title: String?,
    bottomBarOverlap: Dp = 0.dp,
    onProfileClick: () -> Unit = {},
    widgetContent: @Composable (DashboardWidget) -> Unit
) {
    Column(
        modifier = modifier
    ) {
        MainScreenHeader(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.background.copy(alpha = 0.8f)
                    )
                    .padding(start = 16.dp, bottom = 8.dp, end = 8.dp)
                    .statusBarsPadding(),
            title = title ?: "",
            subtitle = DateUtils.getCurrentDate(),
            textColor = MaterialTheme.colorScheme.onBackground,
            onProfileClick = onProfileClick
        )
        DashboardGrid(
            widgetContent = widgetContent,
            bottomBarOverlap = bottomBarOverlap
        )
    }
}

@Composable
fun DashboardGrid(
    modifier: Modifier = Modifier,
    widgets: List<DashboardWidget> = DefaultDashboardWidgets,
    bottomBarOverlap: Dp = 0.dp,
    widgetContent: @Composable (DashboardWidget) -> Unit
) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val cellWidth = (maxWidth - 32.dp - 12.dp) / 2
        val cellHeight = cellWidth * 0.5f
        // Keep two columns while allowing enlarged labels to wrap above the values.
        val rowHeight = cellHeight + (cellWidth + 32.dp) * (fontScale - 1f)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().testTag("dashboard-grid"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomBarOverlap),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = widgets,
                key = { it.id },
                span = { GridItemSpan(it.size.columns) }
            ) { widget ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .testTag("dashboard-widget-${widget.id}"),
                    propagateMinConstraints = true
                ) { widgetContent(widget) }
            }
        }
    }
}

@Composable
fun MainScreenHeader(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
    textColor: Color = MaterialTheme.colorScheme.onBackground,
    onProfileClick: () -> Unit = {}
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )

            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = textColor
                )
            }
        }
        IconButton(
            onClick = onProfileClick
        ) {
            Icon(
                modifier = Modifier.size(24.dp),
                imageVector = ImageVector.vectorResource(R.drawable.ic_profile_24),
                contentDescription = "Profile"
            )
        }
    }
}

@Preview(name = "Compact grid", widthDp = 320, heightDp = 640)
@Preview(name = "Dark grid", widthDp = 320, heightDp = 640, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Large text grid", widthDp = 320, heightDp = 840, fontScale = 2f)
@Preview(
    name = "Dark large text grid",
    widthDp = 320,
    heightDp = 840,
    fontScale = 2f,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Preview(name = "Expanded grid", widthDp = 840, heightDp = 960)
@Composable
private fun MainScreenGridPreview() {
    FillinTheme {
        DashboardGrid(
            widgets =
                DefaultDashboardWidgets +
                    listOf(
                        DashboardWidget("wide", DashboardWidgetKind.TotalBalance, DashboardWidgetSize.TwoByOne),
                        DashboardWidget("large", DashboardWidgetKind.TotalBalance, DashboardWidgetSize.TwoByTwo)
                    )
        ) { widget ->
            when (widget.kind) {
                DashboardWidgetKind.TotalBalance -> {
                    TotalBalanceWidget(
                        TotalBalanceUiState(Money("12345678.90".toBigDecimal(), "KZT"))
                    )
                }

                DashboardWidgetKind.AvailableToSpend -> {
                    AvailableToSpendWidget(
                        AvailableToSpendUiState(Money("-12345.67".toBigDecimal(), "KZT"))
                    )
                }

                DashboardWidgetKind.DailyLimit -> {
                    DailyLimitWidget(
                        DailyLimitUiState(
                            amount = Money("123456.00".toBigDecimal(), "KZT"),
                            spentToday = Money("30864.00".toBigDecimal(), "KZT"),
                            remainingToday = Money("92592.00".toBigDecimal(), "KZT"),
                            spentPercent = "25.0".toBigDecimal(),
                            remainingPercent = "75.0".toBigDecimal(),
                            progress = 0.25f
                        )
                    )
                }

                DashboardWidgetKind.BalanceForecast -> {
                    BalanceForecastWidget(
                        forecastPreviewState
                    )
                }

                DashboardWidgetKind.PlannedOperations -> {
                    PlannedOperationsWidget(
                        expenses = emptyList(),
                        income = null
                    )
                }
            }
        }
    }
}
