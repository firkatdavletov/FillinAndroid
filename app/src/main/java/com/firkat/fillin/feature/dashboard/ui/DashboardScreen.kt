package com.firkat.fillin.feature.dashboard.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firkat.fillin.R
import com.firkat.fillin.feature.dashboard.domain.model.DashboardStatus
import com.firkat.fillin.feature.dashboard.domain.model.Money
import com.firkat.fillin.feature.dashboard.widgets.availabletospend.ui.AvailableToSpendRoute
import com.firkat.fillin.feature.dashboard.widgets.availabletospend.ui.AvailableToSpendUiState
import com.firkat.fillin.feature.dashboard.widgets.availabletospend.ui.AvailableToSpendWidget
import com.firkat.fillin.feature.dashboard.widgets.balanceforecast.ui.BalanceForecastRoute
import com.firkat.fillin.feature.dashboard.widgets.balanceforecast.ui.BalanceForecastWidget
import com.firkat.fillin.feature.dashboard.widgets.balanceforecast.ui.forecastPreviewState
import com.firkat.fillin.feature.dashboard.widgets.dailylimit.ui.DailyLimitRoute
import com.firkat.fillin.feature.dashboard.widgets.dailylimit.ui.DailyLimitUiState
import com.firkat.fillin.feature.dashboard.widgets.dailylimit.ui.DailyLimitWidget
import com.firkat.fillin.feature.dashboard.widgets.totalbalance.ui.TotalBalanceRoute
import com.firkat.fillin.feature.dashboard.widgets.totalbalance.ui.TotalBalanceUiState
import com.firkat.fillin.feature.dashboard.widgets.totalbalance.ui.TotalBalanceWidget
import com.firkat.fillin.ui.component.RefreshableContent
import com.firkat.fillin.ui.theme.FillinTheme
import com.firkat.fillin.ui.theme.saldomiDesignColors
import org.koin.compose.viewmodel.koinViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DashboardRoute(
    viewModel: DashboardViewModel = koinViewModel(),
    onForecastClick: () -> Unit = {},
    onPaymentsClick: (LocalDate, LocalDate) -> Unit = { _, _ -> },
    bottomBarOverlap: Dp = 0.dp,
    topBarPadding: Dp = 0.dp,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(DashboardEvent.Resume) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            DashboardScreen(
                state = state,
                onEvent = viewModel::onEvent,
                bottomBarOverlap = bottomBarOverlap,
                topBarPadding = topBarPadding,
            ) { widget ->
                when (widget.kind) {
                    DashboardWidgetKind.TotalBalance -> TotalBalanceRoute()
                    DashboardWidgetKind.AvailableToSpend -> AvailableToSpendRoute()
                    DashboardWidgetKind.DailyLimit -> DailyLimitRoute(onPaymentsClick = onPaymentsClick)
                    DashboardWidgetKind.BalanceForecast -> BalanceForecastRoute(onDetailsClick = onForecastClick)
                }
            }
        }
    }
}

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onEvent: (DashboardEvent) -> Unit,
    bottomBarOverlap: Dp = 0.dp,
    topBarPadding: Dp = 0.dp,
    widgetContent: @Composable (DashboardWidget) -> Unit,
) {
    val isReady = state.status == DashboardStatus.Ready
    DashboardGrid(
        header = {
            DashboardHeader(
                topBarPadding = topBarPadding,
            )
        },
        widgetContent = widgetContent,
        bottomBarOverlap = bottomBarOverlap,
    )
}

@Composable
fun DashboardGrid(
    modifier: Modifier = Modifier,
    widgets: List<DashboardWidget> = DefaultDashboardWidgets,
    header: (@Composable () -> Unit)? = null,
    bottomBarOverlap: Dp = 0.dp,
    widgetContent: @Composable (DashboardWidget) -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
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
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (header != null) {
                stickyHeader {
                    header()
                }
//                item(key = "dashboard-header", span = { GridItemSpan(2) }) { header() }
            }
            items(widgets, key = { it.id }, span = { GridItemSpan(it.size.columns) }) { widget ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (widget.kind in setOf(DashboardWidgetKind.DailyLimit, DashboardWidgetKind.BalanceForecast)) {
                                Modifier
                            } else {
                                Modifier.height(rowHeight * widget.size.rows + 12.dp * (widget.size.rows - 1))
                            },
                        ).testTag("dashboard-widget-${widget.id}"),
                    propagateMinConstraints = true,
                ) { widgetContent(widget) }
            }
            item {
                Spacer(Modifier.height(54.dp))
            }
        }
    }
}

@Composable
private fun DashboardHeader(
    date: LocalDate? = null,
    topBarPadding: Dp = 0.dp,
) {
    Column(
        modifier = Modifier
            .background(
            brush = Brush.verticalGradient(
                0f to MaterialTheme.saldomiDesignColors.background,
                0.7f to MaterialTheme.saldomiDesignColors.background,
                1f to MaterialTheme.saldomiDesignColors.background.copy(alpha = 0f)
            )
        )
            .padding(bottom = 16.dp, top = topBarPadding),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        date?.let {
            val locale = Locale.forLanguageTag("ru-KZ")
            Text(
                it.format(DateTimeFormatter.ofPattern("dd LLLL yyyy", locale)),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(name = "Compact grid", widthDp = 320, heightDp = 640)
@Preview(name = "Dark grid", widthDp = 320, heightDp = 640, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Large text grid", widthDp = 320, heightDp = 840, fontScale = 2f)
@Preview(name = "Dark large text grid", widthDp = 320, heightDp = 840, fontScale = 2f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Expanded grid", widthDp = 840, heightDp = 960)
@Composable
private fun DashboardGridPreview() {
    FillinTheme {
        DashboardGrid(
            widgets =
                DefaultDashboardWidgets +
                    listOf(
                        DashboardWidget("wide", DashboardWidgetKind.TotalBalance, DashboardWidgetSize.TwoByOne),
                        DashboardWidget("large", DashboardWidgetKind.TotalBalance, DashboardWidgetSize.TwoByTwo),
                    ),
            header = { DashboardHeader(LocalDate.of(2026, 10, 25)) },
        ) { widget ->
            when (widget.kind) {
                DashboardWidgetKind.TotalBalance -> {
                    TotalBalanceWidget(
                        TotalBalanceUiState(Money("12345678.90".toBigDecimal(), "KZT")),
                    )
                }

                DashboardWidgetKind.AvailableToSpend -> {
                    AvailableToSpendWidget(
                        AvailableToSpendUiState(Money("-12345.67".toBigDecimal(), "KZT")),
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
                            progress = 0.25f,
                        ),
                    )
                }

                DashboardWidgetKind.BalanceForecast -> {
                    BalanceForecastWidget(
                        forecastPreviewState,
                    )
                }
            }
        }
    }
}
