package com.firkat.fillin.feature.main.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.navigation3.runtime.NavKey
import com.firkat.fillin.R
import kotlinx.serialization.Serializable

@Serializable
internal enum class AppDestination(
    @StringRes val labelResource: Int,
    @DrawableRes val iconResource: Int,
) : NavKey {
    Dashboard(
        labelResource = R.string.navigation_dashboard,
        iconResource = R.drawable.ic_home,
    ),
    History(
        labelResource = R.string.navigation_history,
        iconResource = R.drawable.ic_receipt,
    ),
    AddOperation(
        labelResource = R.string.notice,
        iconResource = R.drawable.ic_add_24,
    ),
    Planned(
        labelResource = R.string.navigation_planned,
        iconResource = R.drawable.ic_calendar,
    ),
}
