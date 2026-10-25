package com.firkat.fillin.ui.navigation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import com.firkat.fillin.ui.component.SaldomiBottomSheet

/** The guard reads feature state directly, including between recompositions. */
internal class BottomSheetDismissController {
    var canDismiss: () -> Boolean = { true }
}

private class BottomSheetRemovalGuard { var removing = false }

internal val LocalBottomSheetDismissController = staticCompositionLocalOf { BottomSheetDismissController() }

/** Register before non-overlay strategies; any entry can opt in through [bottomSheet]. */
@OptIn(ExperimentalMaterial3Api::class)
internal class BottomSheetSceneStrategy<T : Any>(
    private val dismissController: BottomSheetDismissController = BottomSheetDismissController(),
) : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        // An overlay must always have a destination underneath it.
        if (entries.size < 2) return null
        val entry = entries.last()
        val properties = entry.metadata[BottomSheetKey] ?: return null
        return BottomSheetScene(
            entry = entry,
            previousEntries = entries.dropLast(1),
            properties = properties,
            onBack = onBack,
            dismissController = dismissController,
        )
    }

    companion object {
        private object BottomSheetKey : NavMetadataKey<ModalBottomSheetProperties>

        fun bottomSheet(properties: ModalBottomSheetProperties = ModalBottomSheetProperties()) =
            metadata { put(BottomSheetKey, properties) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
internal class BottomSheetScene<T : Any>(
    private val entry: NavEntry<T>,
    override val previousEntries: List<NavEntry<T>>,
    private val properties: ModalBottomSheetProperties,
    private val onBack: () -> Unit,
    private val dismissController: BottomSheetDismissController = BottomSheetDismissController(),
) : OverlayScene<T> {
    override val key: Any = entry.contentKey
    override val entries = listOf(entry)
    override val overlaidEntries = previousEntries
    private var sheetState: SheetState? = null
    private var removalGuard: BottomSheetRemovalGuard? = null

    override val content: @Composable () -> Unit = {
        // Share the removal flag across equivalent scene recalculations.
        val removal = remember { BottomSheetRemovalGuard() }
        removalGuard = removal
        val state = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { it != SheetValue.Hidden || removal.removing || dismissController.canDismiss() },
        )
        sheetState = state
        // A dialog window would otherwise resume the entry independently of its parent scene.
        val lifecycleOwner = rememberLifecycleOwner()
        SaldomiBottomSheet(onDismissRequest = { if (dismissController.canDismiss()) onBack() }, sheetState = state, properties = properties) {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner, LocalBottomSheetDismissController provides dismissController) {
                entry.Content()
            }
        }
    }

    override suspend fun onRemove() {
        removalGuard?.removing = true
        sheetState?.hide()
    }

    // Animation state and callbacks do not change the logical identity of a scene.
    override fun equals(other: Any?): Boolean = other is BottomSheetScene<*> &&
        entry == other.entry && previousEntries == other.previousEntries && properties == other.properties

    override fun hashCode(): Int = 31 * (31 * entry.hashCode() + previousEntries.hashCode()) + properties.hashCode()
}
