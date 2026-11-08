package com.firkat.fillin.ui.icon

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.firkat.fillin.R

/** Shared vector and bitmap painters. */
internal object FillinIcons {
    val Add: Painter @Composable get() = painterResource(R.drawable.ic_add_24)

    val Wallet: Painter @Composable get() = painterResource(R.drawable.ic_wallet)
    val Cloud: Painter @Composable get() = painterResource(R.drawable.ic_cloud)
    val Folder: Painter @Composable get() = painterResource(R.drawable.ic_setup_folder)
    val Currency: Painter @Composable get() = painterResource(R.drawable.ic_setup_currency)
    val Device: Painter @Composable get() = painterResource(R.drawable.ic_setup_device)
    val Back: Painter @Composable get() = painterResource(R.drawable.ic_setup_back)
    val Categories: Painter @Composable get() = painterResource(R.drawable.ic_categories)
    val FamilyAccess: Painter @Composable get() = painterResource(R.drawable.ic_family_access)
    val CloudUpload: Painter @Composable get() = painterResource(R.drawable.ic_cloud_upload)
}
