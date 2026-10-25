package com.firkat.fillin.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.firkat.fillin.ui.icon.FillinIcons

@Composable
internal fun CategoryIconImage(
    iconCode: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Image(
        painter = FillinIcons.category(iconCode),
        contentDescription = contentDescription,
        modifier = modifier.size(24.dp),
    )
}
