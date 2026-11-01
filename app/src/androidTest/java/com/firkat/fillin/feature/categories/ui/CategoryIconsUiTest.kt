package com.firkat.fillin.feature.categories.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.firkat.fillin.ui.component.CategoryIconImage
import com.firkat.fillin.ui.icon.CategoryIcons
import com.firkat.fillin.ui.theme.FillinTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CategoryIconsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun allPngsRenderAtFixedSizeInBothThemesAndAtLargeFontScale() {
        var dark by mutableStateOf(false)
        var fontScale by mutableFloatStateOf(1f)
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                FillinTheme(darkTheme = dark, dynamicColor = false) {
                    Surface {
                        Column {
                            CategoryIcons.options.chunked(4).forEach { row ->
                                Row {
                                    row.forEach { CategoryIconImage(it.code, Modifier.testTag(it.code)) }
                                }
                            }
                        }
                    }
                }
            }
        }
        listOf(false to 1f, true to 1f, false to 2f, true to 2f).forEach { (night, scale) ->
            compose.runOnIdle { dark = night; fontScale = scale }
            CategoryIcons.options.forEach {
                compose.onNodeWithTag(it.code).assertWidthIsEqualTo(24.dp).assertHeightIsEqualTo(24.dp)
            }
            val context = ApplicationProvider.getApplicationContext<Context>()
            val output = File(context.getExternalFilesDir(null), "category-icons-$night-$scale.png")
            output.outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    @Test fun unknownCodeRendersTheSamePngAsTheDefaultIcon() {
        compose.setContent {
            FillinTheme {
                Row {
                    CategoryIconImage("custom.icon-v2", Modifier.testTag("unknown"))
                    CategoryIconImage("HOUSEHOLD_GOODS", Modifier.testTag("default"))
                }
            }
        }
        val unknown = compose.onNodeWithTag("unknown").captureToImage().asAndroidBitmap()
        val default = compose.onNodeWithTag("default").captureToImage().asAndroidBitmap()
        assertTrue(unknown.sameAs(default))
    }

    @Test fun unknownCodeIsReplacedOnlyAfterAnExplicitIconChoice() {
        var editor by mutableStateOf(CategoryEditorUiState(name = "Кафе", iconCode = "custom.icon-v2"))
        compose.setContent {
            FillinTheme {
                CategoriesScreen(CategoriesUiState(editor = editor), { event ->
                    editor = when (event) {
                        CategoriesEvent.IconPickerRequested -> editor.copy(isIconPickerVisible = true)
                        is CategoriesEvent.IconSelected -> editor.copy(iconCode = event.iconCode, isIconPickerVisible = false)
                        else -> editor
                    }
                }, onCreateCategory = {}, onEditCategory = {})
            }
        }
        compose.onNodeWithText("Другая иконка").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals("custom.icon-v2", editor.iconCode) }
        compose.onNodeWithText("Продукты").assertIsNotSelected().performClick()
        compose.runOnIdle { assertEquals("GROCERIES", editor.iconCode) }
        compose.onNodeWithText("Продукты").assertExists()
    }

    @Test fun legacyIconIsHighlightedAndSavingDisablesTheSelector() {
        var editor by mutableStateOf(CategoryEditorUiState(name = "Кафе", iconCode = "FOOD", isIconPickerVisible = true))
        compose.setContent { FillinTheme { CategoriesScreen(CategoriesUiState(editor = editor), {}, onCreateCategory = {}, onEditCategory = {}) } }
        compose.onNode(hasText("Продукты") and isSelectable()).assertIsSelected()
        compose.runOnIdle { editor = editor.copy(isIconPickerVisible = false, isSaving = true) }
        compose.onNodeWithText("Продукты").assertIsNotEnabled()
    }
}
