package com.fortind.zenith.ui.nav

import android.os.Build
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isCaptionBarVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

@Stable
class CaptionBarSlot {
    private val claims = mutableStateListOf<CaptionBarClaim>()

    internal val active: CaptionBarClaim? get() = claims.lastOrNull()

    internal fun add(claim: CaptionBarClaim) {
        claims.remove(claim)
        claims.add(claim)
    }

    internal fun remove(claim: CaptionBarClaim) {
        claims.remove(claim)
    }
}

@Stable
internal class CaptionBarClaim {
    var navigation: (@Composable () -> Unit)? by mutableStateOf(null)
    var title: @Composable () -> Unit by mutableStateOf({})
    var actions: @Composable RowScope.() -> Unit by mutableStateOf({})
}

val LocalCaptionBarSlot = staticCompositionLocalOf<CaptionBarSlot?> { null }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun claimCaptionBar(
    navigation: (@Composable () -> Unit)?,
    title: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
): Boolean {
    val slot = LocalCaptionBarSlot.current
    val drawable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && WindowInsets.isCaptionBarVisible
    if (slot == null || !drawable) return false
    val claim = remember { CaptionBarClaim() }
    SideEffect {
        claim.navigation = navigation
        claim.title = title
        claim.actions = actions
    }
    DisposableEffect(slot, claim) {
        slot.add(claim)
        onDispose { slot.remove(claim) }
    }
    return true
}

@Composable
fun CaptionBarHost(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val slot = remember { CaptionBarSlot() }
    Box(modifier) {
        slot.active?.let { CaptionBarContent(it) }
        CompositionLocalProvider(LocalCaptionBarSlot provides slot, content = content)
    }
}

@Immutable
private data class CaptionSafeZone(val leftPx: Int, val rightPx: Int)

@Composable
private fun CaptionBarContent(claim: CaptionBarClaim) {
    val view = LocalView.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val captionTop = WindowInsets.captionBar.getTop(density)
    var zone by remember { mutableStateOf(CaptionSafeZone(0, 0)) }
    DisposableEffect(view, captionTop) {
        zone = view.captionSafeZone()
        val listener = View.OnLayoutChangeListener { changed, _, _, _, _, _, _, _, _ -> zone = changed.captionSafeZone() }
        view.addOnLayoutChangeListener(listener)
        onDispose { view.removeOnLayoutChangeListener(listener) }
    }
    val (startPx, endPx) = if (layoutDirection == LayoutDirection.Ltr) zone.leftPx to zone.rightPx else zone.rightPx to zone.leftPx
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        ProvideTextStyle(MaterialTheme.typography.titleMedium) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.captionBar)
                    .padding(start = with(density) { startPx.toDp() }, end = with(density) { endPx.toDp() })
                    .semantics {
                        isTraversalGroup = true
                        traversalIndex = -1f
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                claim.navigation?.let { navigation ->
                    Box(Modifier.systemGestureExclusion()) { navigation() }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                ) { claim.title() }
                Row(
                    modifier = Modifier.systemGestureExclusion(),
                    verticalAlignment = Alignment.CenterVertically,
                    content = claim.actions,
                )
            }
        }
    }
}

private fun View.captionSafeZone(): CaptionSafeZone {
    val insets = ViewCompat.getRootWindowInsets(this) ?: return CaptionSafeZone(0, 0)
    val origin = IntArray(2).also(::getLocationInWindow)
    val middle = origin[0] + width / 2
    val rects = insets.getBoundingRects(WindowInsetsCompat.Type.captionBar())
    val leftEdge = rects.filter { it.centerX() < middle }.maxOfOrNull { it.right } ?: origin[0]
    val rightEdge = rects.filter { it.centerX() >= middle }.minOfOrNull { it.left } ?: (origin[0] + width)
    return CaptionSafeZone(
        leftPx = (leftEdge - origin[0]).coerceIn(0, width),
        rightPx = (origin[0] + width - rightEdge).coerceIn(0, width),
    )
}
