@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.fortind.zenith.ui.feed

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fortind.zenith.R
import com.fortind.zenith.auth.FortInstance
import com.fortind.zenith.timeline.Note
import com.fortind.zenith.timeline.TimelineKind
import com.fortind.zenith.ui.Avatar
import com.fortind.zenith.ui.PrimaryButton
import com.fortind.zenith.ui.StatusMessage

val FeedMaxWidth = 640.dp

private class TimelineOption(val kind: TimelineKind, val labelRes: Int, val selectedIcon: ImageVector, val icon: ImageVector)

private val TimelineOptions = listOf(
    TimelineOption(TimelineKind.Home, R.string.feed_timeline_home, Icons.Filled.Home, Icons.Outlined.Home),
    TimelineOption(TimelineKind.Following, R.string.feed_timeline_following, Icons.Filled.HowToReg, Icons.Outlined.HowToReg),
    TimelineOption(TimelineKind.Local, R.string.feed_timeline_local, Icons.Filled.Groups, Icons.Outlined.Groups),
    TimelineOption(TimelineKind.Global, R.string.feed_timeline_global, Icons.Filled.Public, Icons.Outlined.Public),
    TimelineOption(TimelineKind.Lists, R.string.feed_timeline_lists, Icons.AutoMirrored.Filled.FormatListBulleted, Icons.AutoMirrored.Outlined.FormatListBulleted),
)

private val AllLabelsWidth = 520.dp
private val IconOnlyButtonWidth = 48.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TimelineSwitcher(
    selected: TimelineKind,
    available: Set<TimelineKind>,
    onSelect: (TimelineKind) -> Unit,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val options = TimelineOptions.filter { it.kind in available }
    BoxWithConstraints(modifier = modifier.widthIn(max = FeedMaxWidth).fillMaxWidth()) {
        val gaps = ButtonGroupDefaults.ConnectedSpaceBetween * (options.size - 1)
        val allLabels = maxWidth >= AllLabelsWidth
        val equalWidth = (maxWidth - gaps) / options.size
        val selectedWidth = maxWidth - gaps - IconOnlyButtonWidth * (options.size - 1)
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            options.forEachIndexed { index, option ->
                val checked = option.kind == selected
                val label = stringResource(option.labelRes)
                val showLabel = allLabels || checked
                val width by animateDpAsState(
                    targetValue = when {
                        allLabels -> equalWidth
                        checked -> selectedWidth
                        else -> IconOnlyButtonWidth
                    },
                    animationSpec = if (reduceMotion) snap() else MaterialTheme.motionScheme.defaultSpatialSpec(),
                    label = "timelineButtonWidth",
                )
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
                    tooltip = { if (!showLabel) PlainTooltip { Text(label) } },
                    state = rememberTooltipState(),
                ) {
                    ToggleButton(
                        checked = checked,
                        onCheckedChange = { onSelect(option.kind) },
                        colors = ToggleButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        contentPadding = PaddingValues(horizontal = if (showLabel) 8.dp else 0.dp),
                        modifier = Modifier
                            .width(width)
                            .testTag("timeline:${option.kind.name}")
                            .semantics { role = Role.RadioButton },
                    ) {
                        Icon(
                            if (checked) option.selectedIcon else option.icon,
                            contentDescription = if (showLabel) null else label,
                        )
                        if (showLabel) {
                            Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NewPostsPill(
    pending: List<Note>,
    gap: Boolean,
    visible: Boolean,
    reduceMotion: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = visible,
        enter = if (reduceMotion) fadeIn(snap()) else slideInVertically(motion.fastSpatialSpec()) { -it } + fadeIn(motion.fastEffectsSpec()),
        exit = if (reduceMotion) fadeOut(snap()) else slideOutVertically(motion.fastSpatialSpec()) { -it } + fadeOut(motion.fastEffectsSpec()),
        modifier = modifier,
    ) {
        val label = if (gap || pending.isEmpty()) {
            stringResource(R.string.feed_new_posts_gap)
        } else {
            pluralStringResource(R.plurals.feed_new_posts, pending.size, pending.size)
        }
        SmallExtendedFloatingActionButton(
            onClick = onClick,
            icon = { Icon(Icons.Default.ArrowUpward, contentDescription = null) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val authors = pending.map { it.user }.distinctBy { it.id }.take(3)
                    if (authors.isNotEmpty()) {
                        Box {
                            authors.forEachIndexed { index, user ->
                                Avatar(
                                    name = user.displayName,
                                    url = user.avatarUrl,
                                    size = 24.dp,
                                    modifier = Modifier.offset(x = (index * 14).dp),
                                )
                            }
                            Spacer(Modifier.size(width = (24 + (authors.size - 1) * 14).dp, height = 24.dp))
                        }
                    }
                    Text(label)
                }
            },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
fun FeedLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        LoadingIndicator(modifier = Modifier.size(56.dp))
    }
}

@Composable
fun FeedMessage(status: TimelineStatus, kind: TimelineKind?, onRetry: () -> Unit, onLogIn: () -> Unit, modifier: Modifier = Modifier, emptyBody: Int? = null) {
    val (shape, title, body) = when (status) {
        TimelineStatus.Failed -> Triple(MaterialShapes.Cookie9Sided, R.string.feed_failed_title, stringResource(R.string.feed_failed_body))
        TimelineStatus.Unavailable -> Triple(
            MaterialShapes.Clover4Leaf,
            R.string.feed_unavailable_title,
            stringResource(R.string.feed_unavailable_body, FortInstance.HOST),
        )
        TimelineStatus.SignInRequired -> Triple(MaterialShapes.Sunny, R.string.feed_sign_in_title, stringResource(R.string.feed_sign_in_body))
        else -> Triple(
            MaterialShapes.SoftBurst,
            R.string.feed_empty_title,
            stringResource(
                when (kind) {
                    TimelineKind.Home -> R.string.feed_empty_home_body
                    TimelineKind.Following -> R.string.feed_empty_following_body
                    TimelineKind.Lists -> emptyBody ?: R.string.feed_empty_list_body
                    null -> emptyBody ?: R.string.bookmarks_empty_body
                    else -> R.string.feed_empty_body
                },
            ),
        )
    }
    Box(modifier = modifier.fillMaxWidth().padding(bottom = 32.dp), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.widthIn(max = 480.dp)) {
            StatusMessage(shape = shape, title = stringResource(title), body = body) {
                when (status) {
                    TimelineStatus.Failed -> PrimaryButton(stringResource(R.string.feed_retry), onRetry)
                    TimelineStatus.SignInRequired -> PrimaryButton(stringResource(R.string.feed_log_in), onLogIn)
                    else -> Unit
                }
            }
        }
    }
}

@Composable
fun FeedFooter(timeline: TimelineState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
        when {
            timeline.loadingMore -> FeedLoading()
            timeline.loadMoreFailed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.feed_load_more_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onRetry) { Text(stringResource(R.string.feed_retry)) }
            }
            timeline.endReached -> Text(
                stringResource(R.string.feed_caught_up),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun PermissionBanner(
    onGrant: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    newPermissions: Boolean = false,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(if (newPermissions) R.string.feed_new_permissions_title else R.string.feed_permission_title),
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
            Text(
                stringResource(if (newPermissions) R.string.feed_new_permissions_body else R.string.feed_permission_body, FortInstance.HOST),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.End)) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.feed_permission_dismiss)) }
                FilledTonalButton(onClick = onGrant) { Text(stringResource(R.string.feed_permission_grant)) }
            }
        }
    }
}
