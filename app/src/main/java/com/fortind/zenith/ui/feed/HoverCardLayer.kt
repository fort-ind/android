package com.fortind.zenith.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.fortind.zenith.R
import com.fortind.zenith.people.FollowState
import com.fortind.zenith.people.followState
import com.fortind.zenith.timeline.NoteUser
import com.fortind.zenith.timeline.Reactions
import com.fortind.zenith.ui.Avatar
import com.fortind.zenith.ui.handCursor
import com.fortind.zenith.ui.mfm.MfmContent
import com.fortind.zenith.ui.people.PeopleActions
import com.fortind.zenith.ui.people.PeopleViewModel
import com.fortind.zenith.ui.people.PersonState
import com.fortind.zenith.ui.people.PersonTitle
import com.fortind.zenith.ui.people.labelRes
import com.fortind.zenith.ui.pointerHover
import com.fortind.zenith.ui.rememberReduceMotion

private val ProfileCardWidth = 320.dp
private val ReactorsCardWidth = 240.dp
private val CardGap = 8.dp
private val BannerHeight = 88.dp
private val CardAvatarSize = 56.dp
private val BioMaxHeight = 72.dp

@Composable
fun HoverCardLayer(
    cards: HoverCards,
    people: PeopleViewModel,
    feed: FeedViewModel,
    environment: NoteEnvironment,
    peopleActions: PeopleActions,
    onOpenProfile: (NoteUser) -> Unit,
) {
    val shown = cards.shown ?: return
    val gap = with(LocalDensity.current) { CardGap.roundToPx() }
    val position = remember(shown.anchor, gap) { HoverCardPosition(shown.anchor, gap) }
    Popup(
        popupPositionProvider = position,
        onDismissRequest = cards::dismiss,
        properties = PopupProperties(focusable = false, dismissOnClickOutside = true),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 6.dp,
            modifier = Modifier.pointerHover(icon = PointerIcon.Default) { cards.cardHovered(it) },
        ) {
            when (val target = shown.target) {
                is HoverTarget.Profile -> ProfileCard(target.user, people, environment, peopleActions) {
                    cards.dismiss()
                    onOpenProfile(it)
                }
                is HoverTarget.Reactors -> ReactorsCard(target.key, feed, environment) {
                    cards.dismiss()
                    onOpenProfile(it)
                }
            }
        }
    }
}

private class HoverCardPosition(private val anchor: IntRect, private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val start = if (layoutDirection == LayoutDirection.Ltr) anchor.left else anchor.right - popupContentSize.width
        val x = start.coerceIn(0, maxOf(0, windowSize.width - popupContentSize.width))
        val below = anchor.bottom + gap
        val y = if (below + popupContentSize.height <= windowSize.height) below else maxOf(0, anchor.top - gap - popupContentSize.height)
        return IntOffset(x, y)
    }
}

@Composable
private fun ProfileCard(
    user: NoteUser,
    people: PeopleViewModel,
    environment: NoteEnvironment,
    actions: PeopleActions,
    onOpen: (NoteUser) -> Unit,
) {
    LaunchedEffect(people, user.id) {
        people.seed(user)
        people.open(user.id)
    }
    val state by people.uiState.collectAsStateWithLifecycle()
    val person = state.person(user.id)
    val shownUser = person.user ?: user
    Column(
        modifier = Modifier
            .width(ProfileCardWidth),
    ) {
        Box {
            CardBanner(person.detail?.bannerUrl)
            Avatar(
                name = shownUser.displayName,
                url = shownUser.avatarUrl,
                size = CardAvatarSize,
                modifier = Modifier
                    .padding(start = 16.dp)
                    .offset(y = BannerHeight - CardAvatarSize / 2),
            )
        }
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = CardAvatarSize / 2 + 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(onClick = { onOpen(shownUser) }, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column {
                    ProvideTextStyle(MaterialTheme.typography.titleMedium) { PersonTitle(shownUser, environment) }
                    Text(
                        text = shownUser.handle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            ProfileCardDetail(person, state.myUserId, state.signedIn, environment, actions)
        }
    }
}

@Composable
private fun CardBanner(url: String?) {
    val modifier = Modifier
        .fillMaxWidth()
        .height(BannerHeight)
        .background(MaterialTheme.colorScheme.secondaryContainer)
    if (url == null) {
        Box(modifier)
    } else {
        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProfileCardDetail(person: PersonState, myUserId: String?, signedIn: Boolean, environment: NoteEnvironment, actions: PeopleActions) {
    val detail = person.detail
    if (detail == null) {
        if (rememberReduceMotion()) {
            Text(stringResource(R.string.side_loading), style = MaterialTheme.typography.bodyMedium)
        } else {
            LoadingIndicator(Modifier.size(32.dp))
        }
        return
    }
    if (detail.bioMfm.isNotEmpty()) {
        val bioEnvironment = remember(detail.user, environment.mfm) { environment.mfm.withEmoji(environment.userEmoji(detail.user)) }
        Box(Modifier.heightIn(max = BioMaxHeight)) {
            MfmContent(detail.bioMfm, bioEnvironment, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        CardCount(pluralStringResource(R.plurals.person_notes_count, detail.notesCount, detail.notesCount))
        if (detail.showsFollowingCount(myUserId)) {
            CardCount(pluralStringResource(R.plurals.person_following_count, detail.followingCount, detail.followingCount))
        }
        if (detail.showsFollowersCount(myUserId)) {
            CardCount(pluralStringResource(R.plurals.person_followers_count, detail.followersCount, detail.followersCount))
        }
    }
    val follow = detail.followState(myUserId)
    if (signedIn && follow != FollowState.Hidden) {
        val label = stringResource(follow.labelRes())
        val spoken = stringResource(R.string.person_follow_row_description, label, detail.user.displayName)
        val buttonModifier = Modifier.handCursor().semantics { contentDescription = spoken }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            if (follow.invitesFollow) {
                Button(onClick = { actions.toggleFollow(detail) }, enabled = !person.busy, modifier = buttonModifier) { Text(label, maxLines = 1) }
            } else {
                FilledTonalButton(onClick = { actions.toggleFollow(detail) }, enabled = !person.busy, modifier = buttonModifier) { Text(label, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun CardCount(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReactorsCard(key: ReactorsKey, feed: FeedViewModel, environment: NoteEnvironment, onOpen: (NoteUser) -> Unit) {
    LaunchedEffect(feed, key) { feed.loadReactors(key) }
    val all by feed.reactors.collectAsStateWithLifecycle()
    val preview = all[key] ?: ReactorsPreview(loading = true)
    Column(
        modifier = Modifier.width(ReactorsCardWidth).padding(vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.hover_reactors_title, Reactions.spokenName(key.reaction)),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        when {
            preview.failed -> Text(
                text = stringResource(R.string.hover_card_failed),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            preview.loading && preview.users.isEmpty() -> Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                if (rememberReduceMotion()) Text(stringResource(R.string.side_loading)) else LoadingIndicator(Modifier.size(32.dp))
            }
            else -> {
                preview.users.forEach { user -> ReactorRow(user, environment) { onOpen(user) } }
                val more = key.count - preview.users.size
                if (more > 0) {
                    Text(
                        text = pluralStringResource(R.plurals.hover_reactors_more, more, more),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReactorRow(user: NoteUser, environment: NoteEnvironment, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().handCursor()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Avatar(name = user.displayName, url = user.avatarUrl, size = 28.dp, modifier = Modifier.clip(CircleShape))
            ProvideTextStyle(MaterialTheme.typography.bodyMedium) { PersonTitle(user, environment) }
        }
    }
}
