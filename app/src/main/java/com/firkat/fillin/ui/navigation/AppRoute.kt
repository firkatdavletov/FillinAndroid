package com.firkat.fillin.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface AppRoute : NavKey {
    @Serializable data object Launch : AppRoute
    @Serializable data object Main : AppRoute
    @Serializable data object Auth : AppRoute
}
