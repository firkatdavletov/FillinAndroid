package com.firkat.fillin.ui.icon

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.firkat.fillin.R

/** Original profile vectors exported from the Figma design. */
internal object FillinIcons {
    val FamilyAccess: Painter @Composable get() = painterResource(R.drawable.ic_family_access)
    val CloudUpload: Painter @Composable get() = painterResource(R.drawable.ic_cloud_upload)
}
