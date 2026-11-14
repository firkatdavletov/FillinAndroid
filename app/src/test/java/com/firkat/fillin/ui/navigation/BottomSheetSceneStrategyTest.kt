package com.firkat.fillin.ui.navigation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.SceneStrategyScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class BottomSheetSceneStrategyTest {
    private val strategy = BottomSheetSceneStrategy<String>()
    private val main = NavEntry("main") {}
    private val sheet = NavEntry("sheet", metadata = BottomSheetSceneStrategy.bottomSheet()) {}

    @Test fun `only marked entries with an underlying destination become overlays`() {
        assertNull(calculate(emptyList()))
        assertNull(calculate(listOf(main)))
        assertNull(calculate(listOf(sheet)))
        assertNull(calculate(listOf(main, NavEntry("screen") {})))
        val scene = calculate(listOf(main, sheet)) as OverlayScene<String>
        assertEquals(sheet.contentKey, scene.key)
        assertEquals(listOf(sheet), scene.entries)
        assertEquals(listOf(main), scene.previousEntries)
        assertEquals(listOf(main), scene.overlaidEntries)
    }

    @Test fun `multiple overlay entries preserve their underlying stack`() {
        val second = NavEntry("second-sheet", metadata = BottomSheetSceneStrategy.bottomSheet()) {}
        val scene = calculate(listOf(main, sheet, second)) as OverlayScene<String>
        assertEquals(listOf(second), scene.entries)
        assertEquals(listOf(main, sheet), scene.overlaidEntries)
    }

    @Test fun `scene equality excludes callbacks and includes destination and properties`() {
        val first = BottomSheetScene(sheet, listOf(main), ModalBottomSheetProperties(), {})
        val same = BottomSheetScene(sheet, listOf(main), ModalBottomSheetProperties(), { error("Different callback") })
        assertEquals(first, same)
        assertEquals(first.hashCode(), same.hashCode())
        assertNotEquals(
            first,
            BottomSheetScene(sheet, listOf(main), ModalBottomSheetProperties(shouldDismissOnBackPress = false), {
            })
        )
        assertNotEquals(first, BottomSheetScene(main, listOf(main), ModalBottomSheetProperties(), {}))
    }

    @Test fun `removing a sheet before its first composition is safe`() = runTest {
        (calculate(listOf(main, sheet)) as OverlayScene<String>).onRemove()
    }

    private fun calculate(
        entries: List<NavEntry<String>>
    ) = with(strategy) {
        SceneStrategyScope<String>().calculateScene(entries)
    }
}
