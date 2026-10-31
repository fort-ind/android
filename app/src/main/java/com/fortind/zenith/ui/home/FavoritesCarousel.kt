package com.fortind.zenith.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.fortind.zenith.R
import com.fortind.zenith.games.Game
import com.fortind.zenith.ui.contextMenuTrigger
import com.fortind.zenith.ui.games.GameActions
import com.fortind.zenith.ui.icons.GamesIcon
import com.fortind.zenith.ui.pointerHover

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesCarousel(
    favorites: List<Game>,
    hasMore: Boolean,
    actions: GameActions,
    onSeeAll: () -> Unit,
    onBrowseGames: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.home_favorites_carousel_label),
            style = MaterialTheme.typography.titleMediumEmphasized,
            modifier = Modifier.padding(horizontal = 4.dp).semantics { heading() },
        )
        if (favorites.isEmpty()) {
            CarouselTile(
                title = stringResource(R.string.home_favorites_browse),
                trailing = true,
                onClick = onBrowseGames,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            )
            return@Column
        }
        val count = favorites.size + if (hasMore) 1 else 0
        HorizontalUncontainedCarousel(
            state = rememberCarouselState { count },
            itemWidth = 168.dp,
            itemSpacing = 8.dp,
            contentPadding = PaddingValues(horizontal = 0.dp),
            modifier = Modifier.fillMaxWidth().height(64.dp),
        ) { index ->
            if (index < favorites.size) {
                val game = favorites[index]
                FavoriteTile(game, actions, Modifier.maskClip(MaterialTheme.shapes.extraLarge))
            } else {
                CarouselTile(
                    title = stringResource(R.string.home_favorites_see_all),
                    trailing = true,
                    onClick = onSeeAll,
                    modifier = Modifier.fillMaxSize().maskClip(MaterialTheme.shapes.extraLarge),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FavoriteTile(game: Game, actions: GameActions, modifier: Modifier = Modifier) {
    var hovered by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    Box(modifier = modifier.fillMaxSize()) {
        Surface(
            onClick = { actions.open(game) },
            color = if (hovered) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxSize()
                .pointerHover { hovered = it }
                .contextMenuTrigger { offset ->
                    menuOffset = offset
                    menuOpen = true
                },
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(MaterialShapes.Cookie7Sided.toShape())
                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(GamesIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(18.dp))
                }
                Text(
                    text = game.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, offset = menuOffset) {
            DropdownMenuItem(text = { Text(stringResource(R.string.home_favorites_open)) }, onClick = {
                menuOpen = false
                actions.open(game)
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.home_favorites_remove)) }, onClick = {
                menuOpen = false
                actions.toggleFavorite(game)
            })
        }
    }
}

@Composable
private fun CarouselTile(title: String, trailing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var hovered by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = if (hovered) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.pointerHover { hovered = it },
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (trailing) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }
    }
}
