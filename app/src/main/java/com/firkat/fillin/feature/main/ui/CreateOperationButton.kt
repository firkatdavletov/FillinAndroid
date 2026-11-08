package com.firkat.fillin.feature.main.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.firkat.fillin.R
import com.firkat.fillin.ui.icon.FillinIcons
import com.firkat.fillin.ui.theme.FillinTheme

@Composable
internal fun CreateOperationButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    FilledIconButton(
        onClick = onClick,
        modifier = modifier
            .size(64.dp)
//            .shadow(
//                elevation = 12.dp,
//                shape = CircleShape,
//                ambientColor = colors.primary,
//                spotColor = colors.primary,
//            )
            .background(
                brush = Brush.linearGradient(
                    listOf(colors.primary, colors.primary.copy(alpha = 0.8f).compositeOver(colors.background)),
                ),
                shape = CircleShape,
            ),
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = Color.Transparent,
            contentColor = colors.onPrimary,
        ),
    ) {
        Icon(
            painter = FillinIcons.Add,
            contentDescription = stringResource(R.string.operation_create_title),
            modifier = Modifier.size(32.dp),
        )
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CreateOperationButtonPreview() {
    FillinTheme {
        CreateOperationButton(onClick = {})
    }
}
