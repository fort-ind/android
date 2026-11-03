package com.fortind.zenith.ui.people

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Badge
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import coil3.compose.AsyncImage
import com.fortind.zenith.R
import com.fortind.zenith.mfm.MfmText
import com.fortind.zenith.people.FollowListKind
import com.fortind.zenith.people.FollowState
import com.fortind.zenith.people.UserDetail
import com.fortind.zenith.people.followState
import com.fortind.zenith.timeline.NoteUser
import com.fortind.zenith.ui.contextMenuTrigger
import com.fortind.zenith.ui.feed.NoteEnvironment
import com.fortind.zenith.ui.feed.TooltipIconButton
import com.fortind.zenith.ui.mfm.MfmContent
import com.fortind.zenith.ui.rememberMorphingShape
import com.fortind.zenith.ui.rememberReduceMotion

private val AvatarSize = 96.dp
private val AvatarRing = 6.dp

class OwnProfileActions(
    val onEditProfile: () -> Unit,
    val onLogOut: () -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonHeader(
    user: NoteUser,
    detail: UserDetail?,
    environment: NoteEnvironment,
    wide: Boolean,
    busy: Boolean,
    own: OwnProfileActions?,
    pending: PendingRequests?,
    actions: PeopleActions,
    onOpenList: (FollowListKind) -> Unit,
    aboutInHeader: Boolean,
    modifier: Modifier = Modifier,
) {
    var contextOffset by remember { mutableStateOf<DpOffset?>(null) }
    val gutter = if (wide) 24.dp else 16.dp
    Column(
        modifier = modifier
            .fillMaxWidth()
            .contextMenuTrigger(onOpen = { contextOffset = it }),
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(bottom = (AvatarSize + AvatarRing * 2) / 2)) {
            PersonBanner(url = detail?.bannerUrl, aspectRatio = if (wide) 3f else 16f / 9f, modifier = Modifier.fillMaxWidth())
            PersonAvatar(
                user = user,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = gutter, y = (AvatarSize + AvatarRing * 2) / 2),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(y = 56.dp)
                    .padding(end = gutter),
            ) {
                if (own == null) {
                    PersonActionButton(detail, environment.myUserId, busy, user, actions)
                } else {
                    PersonOverflowButton(user, detail, environment.myUserId, environment.signedIn, actions)
                }
            }
        }
        Column(
            modifier = Modifier.padding(horizontal = gutter),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                PersonName(user, environment)
                Text(
                    text = user.fullHandle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (detail != null) {
                PersonTags(detail)
                if (own != null) OwnActions(own)
                PersonCounts(detail, environment.myUserId, pending.takeIf { own != null }, onOpenList)
                if (aboutInHeader) PersonAboutContent(detail, environment)
            }
        }
        contextOffset?.let { offset ->
            DropdownMenu(expanded = true, onDismissRequest = { contextOffset = null }, offset = offset) {
                PersonMenuItems(user, detail, environment.myUserId, environment.signedIn, actions) { contextOffset = null }
            }
        }
    }
}

@Composable
fun PersonTitle(user: NoteUser, environment: NoteEnvironment) {
    val nameEnvironment = remember(user, environment.mfm) { environment.mfm.withEmoji(environment.userEmoji(user)) }
    MfmContent(
        nodes = user.nameMfm.ifEmpty { listOf(MfmText(user.username)) },
        environment = nameEnvironment,
        style = LocalTextStyle.current,
        maxLines = 1,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PersonName(user: NoteUser, environment: NoteEnvironment) {
    val nameEnvironment = remember(user, environment.mfm) { environment.mfm.withEmoji(environment.userEmoji(user)) }
    MfmContent(
        nodes = user.nameMfm.ifEmpty { listOf(MfmText(user.username)) },
        environment = nameEnvironment,
        style = MaterialTheme.typography.headlineSmallEmphasized,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics { heading() },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PersonActionButton(
    detail: UserDetail?,
    myUserId: String?,
    busy: Boolean,
    user: NoteUser,
    actions: PeopleActions,
) {
    val state = detail?.followState(myUserId) ?: FollowState.Hidden
    if (detail == null || state == FollowState.Hidden) {
        PersonOverflowButton(user, detail, myUserId, myUserId != null, actions)
        return
    }
    var menuOpen by remember { mutableStateOf(false) }
    val reduceMotion = rememberReduceMotion()
    val rotation by animateFloatAsState(
        targetValue = if (menuOpen) 180f else 0f,
        animationSpec = if (reduceMotion) snap() else MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "follow menu arrow",
    )
    val moreLabel = stringResource(R.string.person_more)
    val label = stringResource(state.labelRes())
    Box {
        SplitButtonLayout(
            modifier = Modifier.heightIn(min = 48.dp).testTag("person:follow"),
            leadingButton = {
                if (state.invitesFollow) {
                    SplitButtonDefaults.LeadingButton(onClick = { actions.toggleFollow(detail) }, enabled = !busy) {
                        Text(label, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                } else {
                    SplitButtonDefaults.TonalLeadingButton(onClick = { actions.toggleFollow(detail) }, enabled = !busy) {
                        Text(label, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                }
            },
            trailingButton = {
                val icon = @Composable {
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = moreLabel,
                        modifier = Modifier.size(SplitButtonDefaults.TrailingIconSize).rotate(rotation),
                    )
                }
                if (state.invitesFollow) {
                    SplitButtonDefaults.TrailingButton(checked = menuOpen, onCheckedChange = { menuOpen = it }) { icon() }
                } else {
                    SplitButtonDefaults.TonalTrailingButton(checked = menuOpen, onCheckedChange = { menuOpen = it }) { icon() }
                }
            },
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            PersonMenuItems(user, detail, myUserId, myUserId != null, actions) { menuOpen = false }
        }
    }
}

@Composable
private fun PersonOverflowButton(
    user: NoteUser,
    detail: UserDetail?,
    myUserId: String?,
    signedIn: Boolean,
    actions: PeopleActions,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        TooltipIconButton(label = stringResource(R.string.person_more), icon = Icons.Default.MoreVert, onClick = { menuOpen = true })
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            PersonMenuItems(user, detail, myUserId, signedIn, actions) { menuOpen = false }
        }
    }
}

@Composable
fun PersonMenuItems(
    user: NoteUser,
    detail: UserDetail?,
    myUserId: String?,
    signedIn: Boolean,
    actions: PeopleActions,
    onDismiss: () -> Unit,
) {
    val item = @Composable { label: String, destructive: Boolean, action: () -> Unit ->
        DropdownMenuItem(
            text = {
                Text(label, color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified)
            },
            onClick = {
                onDismiss()
                action()
            },
        )
    }
    item(stringResource(R.string.person_menu_open_browser), false) { actions.openInBrowser(user.profileUrl) }
    item(stringResource(R.string.person_menu_copy_link), false) { actions.copyLink(user) }
    item(stringResource(R.string.person_menu_share), false) { actions.share(user) }
    if (signedIn && detail != null && user.id != myUserId) {
        val name = user.displayName
        if (detail.isMuted) {
            item(stringResource(R.string.person_menu_unmute, name), false) { actions.moderate(detail, ModerationAction.Unmute) }
        } else {
            item(stringResource(R.string.note_menu_mute, name), false) { actions.moderate(detail, ModerationAction.Mute) }
        }
        if (detail.isRenoteMuted) {
            item(stringResource(R.string.person_menu_show_renotes, name), false) { actions.moderate(detail, ModerationAction.ShowRenotes) }
        } else {
            item(stringResource(R.string.note_menu_hide_renotes, name), false) { actions.moderate(detail, ModerationAction.HideRenotes) }
        }
        if (detail.isBlocking) {
            item(stringResource(R.string.person_menu_unblock, name), false) { actions.moderate(detail, ModerationAction.Unblock) }
        } else {
            item(stringResource(R.string.note_menu_block, name), true) { actions.moderate(detail, ModerationAction.Block) }
        }
        item(stringResource(R.string.person_menu_report, name), true) { actions.report(detail) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonTags(detail: UserDetail) {
    val tags = buildList {
        if (detail.isFollowed) add(R.string.person_tag_follows_you)
        if (detail.isLocked) add(R.string.person_tag_locked)
        if (detail.user.isBot) add(R.string.person_tag_bot)
        if (detail.isMuted) add(R.string.person_tag_muted)
        if (detail.isBlocking) add(R.string.person_tag_blocked)
    }
    if (tags.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        tags.forEach { tag ->
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    text = stringResource(tag),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OwnActions(own: OwnProfileActions) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OwnActionButton(Icons.Default.Edit, stringResource(R.string.profile_edit_profile), own.onEditProfile, destructive = false)
        OwnActionButton(Icons.AutoMirrored.Filled.ExitToApp, stringResource(R.string.profile_log_out), own.onLogOut, destructive = true)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OwnActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, destructive: Boolean) {
    FilledTonalButton(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        colors = if (destructive) {
            ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        } else {
            ButtonDefaults.filledTonalButtonColors()
        },
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(label, maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonCounts(
    detail: UserDetail,
    myUserId: String?,
    pending: PendingRequests?,
    onOpenList: (FollowListKind) -> Unit,
) {
    val waiting = pending?.count?.takeIf { it > 0 }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = pluralStringResource(R.plurals.person_notes_count, detail.notesCount, detail.notesCount),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp),
        )
        if (detail.showsFollowingCount(myUserId)) {
            CountLink(
                text = pluralStringResource(R.plurals.person_following_count, detail.followingCount, detail.followingCount),
                onClick = { onOpenList(FollowListKind.Following) },
            )
        }
        if (detail.showsFollowersCount(myUserId)) {
            val text = pluralStringResource(R.plurals.person_followers_count, detail.followersCount, detail.followersCount)
            if (waiting != null) {
                val description = pluralStringResource(R.plurals.person_requests_waiting, waiting, waiting)
                CountLink(
                    text = text,
                    onClick = { onOpenList(FollowListKind.Requests) },
                    description = "$text, $description",
                    badge = { Badge { Text(if (pending.partial) stringResource(R.string.person_requests_many, waiting) else waiting.toString()) } },
                )
            } else {
                CountLink(text = text, onClick = { onOpenList(FollowListKind.Followers) })
            }
        }
    }
}

@Composable
private fun CountLink(
    text: String,
    onClick: () -> Unit,
    description: String? = null,
    badge: (@Composable () -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text, style = MaterialTheme.typography.labelLarge)
            badge?.invoke()
        }
    }
}

@Composable
private fun PersonBanner(url: String?, aspectRatio: Float, modifier: Modifier = Modifier) {
    Box(modifier = modifier.aspectRatio(aspectRatio)) {
        BannerArt(Modifier.matchParentSize())
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BannerArt(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(modifier = modifier.clip(androidx.compose.ui.graphics.RectangleShape).background(Brush.linearGradient(listOf(colors.primaryContainer, colors.tertiaryContainer)))) {
        ArtShape(MaterialShapes.Sunny, colors.secondaryContainer, 180.dp, Modifier.align(Alignment.TopStart).offset(x = (-40).dp, y = (-50).dp))
        ArtShape(MaterialShapes.Cookie9Sided, colors.primary, 120.dp, Modifier.align(Alignment.TopEnd).offset(x = 30.dp, y = 20.dp), alpha = 0.25f)
        ArtShape(MaterialShapes.SoftBurst, colors.tertiary, 96.dp, Modifier.align(Alignment.BottomCenter).offset(x = 60.dp, y = 36.dp), alpha = 0.3f)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ArtShape(polygon: RoundedPolygon, color: Color, size: Dp, modifier: Modifier = Modifier, alpha: Float = 1f) {
    Box(modifier = modifier.size(size).alpha(alpha).clip(polygon.toShape()).background(color))
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PersonAvatar(user: NoteUser, modifier: Modifier = Modifier) {
    val shape = rememberMorphingShape(
        target = MaterialShapes.Cookie12Sided,
        reduceMotion = rememberReduceMotion(),
        start = MaterialShapes.Circle,
    )
    val description = stringResource(R.string.person_avatar_description, user.displayName)
    Box(
        modifier = modifier
            .size(AvatarSize + AvatarRing * 2)
            .clip(shape)
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(AvatarSize)
                .clip(shape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = user.displayName.take(1).uppercase(),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (user.avatarUrl != null) {
                AsyncImage(
                    model = user.avatarUrl,
                    contentDescription = description,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

fun FollowState.labelRes(): Int = when (this) {
    FollowState.Follow, FollowState.Hidden -> R.string.person_follow
    FollowState.FollowBack -> R.string.person_follow_back
    FollowState.RequestToFollow -> R.string.person_request_follow
    FollowState.Following -> R.string.person_following
    FollowState.Requested -> R.string.person_requested
}
