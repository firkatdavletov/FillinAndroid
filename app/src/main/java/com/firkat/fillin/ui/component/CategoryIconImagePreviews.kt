package com.firkat.fillin.ui.component

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.firkat.fillin.feature.categories.domain.model.CATEGORY_INITIAL_ICON_CODE
import com.firkat.fillin.ui.theme.FillinTheme

@Preview(name = "PNG")
@Preview(name = "PNG dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CategoryPngPreview() {
    FillinTheme { Surface { CategoryIconImage("GROCERIES", "Продукты") } }
}

@Preview(name = "Unknown code")
@Preview(name = "Unknown code dark", uiMode = UI_MODE_NIGHT_YES)
@Preview(name = "Unknown code large text", fontScale = 2f)
@Composable
private fun CategoryInitialPreview() {
    FillinTheme { Surface { CategoryIconImage("custom.icon-v2", "Электроника") } }
}

@Preview(name = "Empty name")
@Preview(name = "Empty name dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CategoryInitialPlaceholderPreview() {
    FillinTheme { Surface { CategoryIconImage(CATEGORY_INITIAL_ICON_CODE, "") } }
}
