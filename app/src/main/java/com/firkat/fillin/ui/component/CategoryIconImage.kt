package com.firkat.fillin.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.firkat.fillin.R
import com.firkat.fillin.ui.icon.CategoryIcons
import com.firkat.fillin.ui.icon.categoryInitialColorIndex
import com.firkat.fillin.ui.icon.categoryInitialLetter
import com.firkat.fillin.ui.icon.categoryInitialName
import com.firkat.fillin.ui.theme.saldomiColors

@Composable
internal fun CategoryIconImage(
    iconCode: String,
    categoryName: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val icon = CategoryIcons.find(iconCode)
    val iconModifier = modifier.size(24.dp)
    if (icon != null) {
        Image(painterResource(icon.drawableResource), contentDescription, iconModifier)
    } else {
        val name = categoryInitialName(categoryName, stringResource(R.string.categories_editor_create_title))
        val colors = MaterialTheme.saldomiColors
        val palette = colors.categoryInitialColors
        Surface(
            modifier = iconModifier.clearAndSetSemantics {
                if (contentDescription != null) this.contentDescription = contentDescription
            },
            shape = CircleShape,
            color = palette[categoryInitialColorIndex(name, palette.size)],
            contentColor = colors.onCategoryInitial
        ) {
            Box(contentAlignment = Alignment.Center) {
                BasicText(
                    text = categoryInitialLetter(name),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = colors.onCategoryInitial,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 5.sp, maxFontSize = 13.sp)
                )
            }
        }
    }
}
