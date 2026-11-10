@file:OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalTransitionApi::class)

package com.fortind.zenith.ui.motion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.ExperimentalTransitionApi
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.createChildTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldRole
import androidx.compose.material3.adaptive.navigation3.LocalListDetailSceneScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.navigation3.ui.LocalNavAnimatedContentScope

private const val REMEMBERED_THREAD_SOURCES = 16

val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalEntryVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

data class ThreadBoundsKey(val noteId: String)
data class MediaKey(val source: Any, val fileId: String)

@Stable
class SharedMotion {
    private var threadSources by mutableStateOf(emptyMap<String, String>())
    var imageSource: Any? by mutableStateOf(null)
        private set
    var viewerOpen by mutableStateOf(false)
        private set

    fun armThread(rowId: String, noteId: String) {
        if (threadSources[noteId] == rowId) return
        val next = LinkedHashMap(threadSources)
        next.remove(noteId)
        next[noteId] = rowId
        while (next.size > REMEMBERED_THREAD_SOURCES) next.remove(next.keys.first())
        threadSources = next
    }

    fun isThreadSource(rowId: String, noteId: String): Boolean = threadSources[noteId] == rowId

    var returnFocus: FocusRequester? = null
        private set

    fun armViewer(source: Any?, focus: FocusRequester?) {
        imageSource = source
        returnFocus = focus
    }

    fun showViewer() {
        viewerOpen = true
    }

    fun closeViewer() {
        viewerOpen = false
    }
}

@Composable
fun EntryVisibilityScope(paneRole: ThreePaneScaffoldRole?, content: @Composable () -> Unit) {
    val scene = LocalListDetailSceneScope.current
    val visibility: Transition<Boolean> = if (scene != null && paneRole != null) {
        scene.scaffoldTransitionScope.scaffoldStateTransition.createChildTransition { it[paneRole] != PaneAdaptedValue.Hidden }
    } else {
        LocalNavAnimatedContentScope.current.transition.createChildTransition { it == EnterExitState.Visible }
    }
    visibility.AnimatedVisibility(visible = { it }, enter = EnterTransition.None, exit = ExitTransition.None) {
        CompositionLocalProvider(LocalEntryVisibilityScope provides this, content = content)
    }
}

class SharedSpec internal constructor(
    val scope: SharedTransitionScope,
    val state: SharedTransitionScope.SharedContentState,
    val bounds: BoundsTransform,
    val fade: FiniteAnimationSpec<Float>,
    val slowFade: FiniteAnimationSpec<Float>,
    val visibility: AnimatedVisibilityScope?,
)

@Composable
fun rememberSharedSpec(key: Any?, visibility: AnimatedVisibilityScope? = null): SharedSpec? {
    if (key == null) return null
    val scope = LocalSharedTransitionScope.current ?: return null
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Rect>()
    val fade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val slowFade = MaterialTheme.motionScheme.slowEffectsSpec<Float>()
    val state = scope.rememberSharedContentState(key)
    return remember(scope, state, spatial, fade, slowFade, visibility) {
        SharedSpec(scope, state, BoundsTransform { _, _ -> spatial }, fade, slowFade, visibility)
    }
}

@Composable
fun rememberThreadBounds(noteId: String, enabled: Boolean): SharedSpec? {
    val visibility = LocalEntryVisibilityScope.current
    return rememberSharedSpec(ThreadBoundsKey(noteId).takeIf { enabled && visibility != null }, visibility)
}

fun Modifier.threadBounds(spec: SharedSpec?, page: Boolean): Modifier {
    val visibility = spec?.visibility ?: return this
    return with(spec.scope) {
        this@threadBounds.sharedBounds(
            sharedContentState = spec.state,
            animatedVisibilityScope = visibility,
            enter = fadeIn(if (page) spec.fade else spec.slowFade),
            exit = fadeOut(if (page) spec.slowFade else spec.fade),
            boundsTransform = spec.bounds,
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.FillWidth, Alignment.TopCenter),
            clipInOverlayDuringTransition = OverlayClip(RectangleShape),
        )
    }
}

fun Modifier.mediaSource(spec: SharedSpec?, visible: Boolean): Modifier {
    if (spec == null) return this
    return with(spec.scope) {
        this@mediaSource.sharedElementWithCallerManagedVisibility(
            sharedContentState = spec.state,
            visible = visible,
            boundsTransform = spec.bounds,
        )
    }
}

fun Modifier.mediaTarget(spec: SharedSpec?): Modifier {
    val visibility = spec?.visibility ?: return this
    return with(spec.scope) {
        this@mediaTarget.sharedElement(
            sharedContentState = spec.state,
            animatedVisibilityScope = visibility,
            boundsTransform = spec.bounds,
        )
    }
}
