package com.fortind.zenith.ui.media

import android.content.Context
import android.media.MediaRouter2
import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.fortind.zenith.R
import com.fortind.zenith.ui.feed.TooltipIconButton

fun canShowOutputSwitcher(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

fun showOutputSwitcher(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && MediaRouter2.getInstance(context).showSystemOutputSwitcher()

@Composable
fun OutputSwitcherButton(modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    if (!canShowOutputSwitcher()) return
    val context = LocalContext.current
    TooltipIconButton(
        label = stringResource(R.string.media_output),
        icon = Icons.Default.Speaker,
        onClick = { showOutputSwitcher(context) },
        tint = tint,
        modifier = modifier,
    )
}
