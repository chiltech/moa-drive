package com.moagauge.pleos.ui.care

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.moagauge.pleos.R
import com.moagauge.pleos.ui.IsDarkTheme

enum class CareArt { CHILD, PET }

/** Dedicated transparent hero objects; labels and actions remain accessible native UI. */
@Composable
fun CareArtwork(kind: CareArt, modifier: Modifier = Modifier) {
    Image(painterResource(when (kind) {
        CareArt.CHILD -> if (IsDarkTheme) R.drawable.care_child_dark_20261113 else R.drawable.care_child_20261113
        CareArt.PET -> if (IsDarkTheme) R.drawable.care_pet_dark_20261113 else R.drawable.care_pet_20261113
    }), contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit)
}
