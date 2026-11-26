package com.fortind.zenith.ui.nav

import android.os.Build
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isCaptionBarVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.roundToInt

private val MinClaimWidth = 48.dp

@Stable
class CaptionBarSlot {
    private val claims = mutableStateListOf<CaptionBarClaim>()

    internal val all: List<CaptionBarClaim> get() = claims

    internal fun add(claim: CaptionBarClaim) {
        claims.remove(claim)
        claims.add(claim)
    }

    internal fun remove(claim: CaptionBarClaim) {
        claims.remove(claim)
    }
}

@Stable
class CaptionPane {
    var left by mutableFloatStateOf(0f)
        private set
    var right by mutableFloatStateOf(0f)
        private set

    internal fun update(left: Float, right: Float) {
        this.left = left
        this.right = right
    }
}

val LocalCaptionPane = staticCompositionLocalOf<CaptionPane?> { null }

fun Modifier.captionPane(pane: CaptionPane): Modifier = onLayoutRectChanged(debounceMillis = 0) { bounds ->
    pane.update(bounds.boundsInWindow.left.toFloat(), bounds.boundsInWindow.right.toFloat())
}

@Stable
internal class CaptionBarClaim {
    var navigation: (@Composable () -> Unit)? by mutableStateOf(null)
    var title: @Composable () -> Unit by mutableStateOf({})
    var actions: @Composable RowScope.() -> Unit by mutableStateOf({})
    var pane: CaptionPane? by mutableStateOf(null)
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
    val pane = LocalCaptionPane.current
    val drawable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && WindowInsets.isCaptionBarVisible
    if (slot == null || !drawable) return false
    val claim = remember { CaptionBarClaim() }
    SideEffect {
        claim.navigation = navigation
        claim.title = title
        claim.actions = actions
        claim.pane = pane
    }
    DisposableEffect(slot, claim) {
        slot.add(claim)
        onDispose { slot.remove(claim) }
    }
    return true
}

@Composable
fun CaptionTopAppBar(
    title: @Composable () -> Unit,
    navigation: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
) {
    if (claimCaptionBar(navigation = navigation, title = title, actions = actions)) return
    TopAppBar(
        title = title,
        navigationIcon = { navigation?.invoke() },
        actions = actions,
        windowInsets = WindowInsets(0),
        colors = colors,
        scrollBehavior = scrollBehavior,
    )
}

@Composable
fun CaptionBarHost(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val slot = remember { CaptionBarSlot() }
    Box(modifier) {
        if (slot.all.isNotEmpty()) CaptionBarContent(slot)
        CompositionLocalProvider(LocalCaptionBarSlot provides slot, content = content)
    }
}

@Immutable
private data class CaptionSafeZone(val left: Int, val right: Int)

@Composable
private fun CaptionBarContent(slot: CaptionBarSlot) {
    val view = LocalView.current
    val density = LocalDensity.current
    val captionTop = WindowInsets.captionBar.getTop(density)
    var zone by remember { mutableStateOf<CaptionSafeZone?>(null) }
    var hostLeft by remember { mutableFloatStateOf(0f) }
    DisposableEffect(view, captionTop) {
        zone = view.captionSafeZone()
        val listener = View.OnLayoutChangeListener { changed, _, _, _, _, _, _, _, _ -> zone = changed.captionSafeZone() }
        view.addOnLayoutChangeListener(listener)
        onDispose { view.removeOnLayoutChangeListener(listener) }
    }
    val safe = zone ?: return
    val claims = slot.all
    val placements = placeCaptionClaims(
        panes = claims.map { claim -> claim.pane?.let { CaptionSpan(it.left.roundToInt(), it.right.roundToInt()) } },
        zoneLeft = safe.left,
        zoneRight = safe.right,
        minWidth = with(density) { MinClaimWidth.roundToPx() },
    )
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        ProvideTextStyle(MaterialTheme.typography.titleMedium) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.captionBar)
                    .onLayoutRectChanged(debounceMillis = 0) { hostLeft = it.boundsInWindow.left.toFloat() },
            ) {
                placements.forEach { placement ->
                    val claim = claims[placement.index]
                    key(claim) {
                        CaptionClaimRow(
                            claim = claim,
                            modifier = Modifier
                                .absoluteOffset { IntOffset((placement.span.start - hostLeft).roundToInt(), 0) }
                                .width(with(density) { placement.span.width.toDp() })
                                .fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptionClaimRow(claim: CaptionBarClaim, modifier: Modifier) {
    Row(
        modifier = modifier.semantics {
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

private fun View.captionSafeZone(): CaptionSafeZone? {
    val insets = ViewCompat.getRootWindowInsets(this) ?: return null
    val origin = IntArray(2).also(::getLocationInWindow)
    val middle = origin[0] + width / 2
    val rects = insets.getBoundingRects(WindowInsetsCompat.Type.captionBar())
    val leftEdge = rects.filter { it.centerX() < middle }.maxOfOrNull { it.right } ?: origin[0]
    val rightEdge = rects.filter { it.centerX() >= middle }.minOfOrNull { it.left } ?: (origin[0] + width)
    return CaptionSafeZone(
        left = leftEdge.coerceIn(origin[0], origin[0] + width),
        right = rightEdge.coerceIn(origin[0], origin[0] + width),
    )
}
