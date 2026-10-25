package com.firkat.fillin.ui.icon

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.firkat.fillin.R

/** Shared vector and bitmap painters. */
internal object FillinIcons {
    @Composable
    fun category(code: String): Painter = painterResource(CategoryIcons.drawableResource(code))

    val Categories: Painter @Composable get() = painterResource(R.drawable.ic_categories)
    val FamilyAccess: Painter @Composable get() = painterResource(R.drawable.ic_family_access)
    val CloudUpload: Painter @Composable get() = painterResource(R.drawable.ic_cloud_upload)
}
