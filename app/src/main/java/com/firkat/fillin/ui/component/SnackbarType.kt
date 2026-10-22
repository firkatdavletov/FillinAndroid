package com.firkat.fillin.ui.component
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class SnackbarType {
    Success,
    Error,
    Warning,
    Info,
}

data class SaldomiSnackbarVisuals(
    override val message: String,
    val type: SnackbarType = SnackbarType.Info,
    override val actionLabel: String? = null,
    override val withDismissAction: Boolean = true,
    override val duration: SnackbarDuration = SnackbarDuration.Short,
) : SnackbarVisuals

@Composable
fun SaldomiSnackbar(
    data: SnackbarData,
    modifier: Modifier = Modifier,
) {
    val visuals = data.visuals
    val type = (visuals as? SaldomiSnackbarVisuals)
        ?.type ?: SnackbarType.Info

    val colors = MaterialTheme.colorScheme

    val (backgroundColor, contentColor) = when (type) {
        SnackbarType.Success ->
            colors.primary to colors.onPrimary

        SnackbarType.Error ->
            colors.errorContainer to colors.onErrorContainer

        SnackbarType.Warning ->
            colors.tertiaryContainer to colors.onTertiaryContainer

        SnackbarType.Info ->
            colors.surfaceVariant to colors.onSurfaceVariant
    }

    val icon = when (type) {
        SnackbarType.Success -> Icons.Outlined.CheckCircle
        SnackbarType.Error -> Icons.Outlined.ErrorOutline
        SnackbarType.Warning -> Icons.Outlined.WarningAmber
        SnackbarType.Info -> Icons.Outlined.Info
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = backgroundColor,
        contentColor = contentColor,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(
                start = 16.dp,
                end = 8.dp,
                top = 12.dp,
                bottom = 12.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )

            Spacer(Modifier.width(12.dp))

            Text(
                text = visuals.message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )

            visuals.actionLabel?.let { label ->
                TextButton(
                    onClick = data::performAction,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = contentColor,
                    ),
                ) {
                    Text(
                        text = label,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (visuals.withDismissAction) {
                IconButton(
                    onClick = data::dismiss,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Закрыть",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
