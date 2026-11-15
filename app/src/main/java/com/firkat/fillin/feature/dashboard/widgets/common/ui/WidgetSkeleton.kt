package com.firkat.fillin.feature.dashboard.widgets.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.firkat.fillin.R
import com.valentinilk.shimmer.shimmer

@Composable
@Suppress("FunctionNaming")
internal fun WidgetSkeleton(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val loading = stringResource(R.string.dashboard_widget_loading)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = loading }
            .shimmer(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
@Suppress("FunctionNaming")
internal fun WidgetSkeletonBlock(
    modifier: Modifier = Modifier
) {
    Box(modifier.background(LocalContentColor.current.copy(alpha = 0.22f), RoundedCornerShape(6.dp)))
}
