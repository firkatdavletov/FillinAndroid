package com.firkat.fillin.feature.main.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.firkat.fillin.ui.theme.FillinTheme

@Composable
internal fun MainBottomNavigation(
    selectedDestination: AppDestination,
    onSelectDestination: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal)),
//            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        NavigationBar(
            modifier = Modifier
                .padding(top = 24.dp)
                .clip(MainNavigationShape)
                .background(
                    Brush.verticalGradient(listOf(colors.surfaceContainer, colors.surfaceContainerLowest)),
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(colors.primary.copy(alpha = 0.65f), colors.outlineVariant.copy(alpha = 0.15f)),
                    ),
                    shape = MainNavigationShape,
                ),
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom),
        ) {
            // Own the spacing so Material's inter-item gaps do not squeeze the labels.
            Row(modifier = Modifier.fillMaxWidth()) {
                AppDestination.entries.forEach { destination ->
                    if (destination == AppDestination.AddOperation) {
                        // Reserve the cradle; creation is an action, not a selectable destination.
                        Spacer(Modifier.width(80.dp))
                    } else {
                        val selected = selectedDestination == destination
                        NavigationBarItem(
                            modifier = Modifier.testTag("tab_${destination.name}"),
                            selected = selected,
                            onClick = { onSelectDestination(destination) },
                            icon = {
                                Box(
                                    modifier = Modifier
                                        .size(width = 48.dp, height = 28.dp)
                                        .background(
                                            color = if (selected) colors.primary.copy(alpha = 0.18f) else Color.Transparent,
                                            shape = CircleShape,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(destination.iconResource),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            },
                            label = {
                                Text(
                                    text = stringResource(destination.labelResource),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontSize = 11.sp,
                                    letterSpacing = 0.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = colors.primary,
                                selectedTextColor = colors.onSurface,
                                indicatorColor = Color.Transparent,
                                unselectedIconColor = colors.onSurfaceVariant,
                                unselectedTextColor = colors.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        }
        CreateOperationButton(
            onClick = { onSelectDestination(AppDestination.AddOperation) },
            modifier = Modifier.testTag("tab_AddOperation"),
        )
    }
}

/** Rounded panel with tangent shoulders and an 8dp gap below the raised 64dp button. */
private object MainNavigationShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val radius = with(density) { 24.dp.toPx() }.coerceAtMost(size.height / 2f)
        val center = size.width / 2f
        val halfNotch = with(density) { 64.dp.toPx() }.coerceAtMost((center - radius).coerceAtLeast(0f))
        val depth = with(density) { 48.dp.toPx() }.coerceAtMost(size.height - radius)
        return Outline.Generic(
            Path().apply {
                moveTo(radius, 0f)
                lineTo(center - halfNotch, 0f)
                cubicTo(center - halfNotch * 0.55f, 0f, center - halfNotch * 0.65f, depth, center, depth)
                cubicTo(center + halfNotch * 0.65f, depth, center + halfNotch * 0.55f, 0f, center + halfNotch, 0f)
                lineTo(size.width - radius, 0f)
                quadraticTo(size.width, 0f, size.width, radius)
                lineTo(size.width, size.height - radius)
                quadraticTo(size.width, size.height, size.width - radius, size.height)
                lineTo(radius, size.height)
                quadraticTo(0f, size.height, 0f, size.height - radius)
                lineTo(0f, radius)
                quadraticTo(0f, 0f, radius, 0f)
                close()
            },
        )
    }
}

@Preview(name = "Home", widthDp = 411)
@Preview(name = "Home dark", widthDp = 411, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun MainBottomNavigationHomePreview() {
    MainBottomNavigationPreview(AppDestination.Dashboard)
}

@Preview(name = "Operations", widthDp = 411)
@Preview(name = "Operations dark", widthDp = 411, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun MainBottomNavigationHistoryPreview() {
    MainBottomNavigationPreview(AppDestination.History)
}

@Preview(name = "Plans", widthDp = 411)
@Preview(name = "Plans dark", widthDp = 411, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun MainBottomNavigationPlannedPreview() {
    MainBottomNavigationPreview(AppDestination.Planned)
}

@Preview(name = "Profile", widthDp = 411)
@Preview(name = "Profile dark", widthDp = 411, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun MainBottomNavigationProfilePreview() {
    MainBottomNavigationPreview(AppDestination.Profile)
}

@Preview(name = "Compact", widthDp = 320)
@Preview(name = "Compact dark", widthDp = 320, uiMode = UI_MODE_NIGHT_YES)
@Preview(name = "Large text", widthDp = 320, fontScale = 2f)
@Preview(name = "Large text dark", widthDp = 320, fontScale = 2f, uiMode = UI_MODE_NIGHT_YES)
@Preview(name = "Wide", widthDp = 840)
@Preview(name = "Wide dark", widthDp = 840, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun MainBottomNavigationAdaptivePreview() {
    MainBottomNavigationPreview(AppDestination.Dashboard)
}

@Composable
private fun MainBottomNavigationPreview(destination: AppDestination) {
    FillinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            MainBottomNavigation(
                selectedDestination = destination,
                onSelectDestination = {},
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
