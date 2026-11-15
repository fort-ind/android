package com.fortind.zenith.shortcuts

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.fortind.zenith.ComposerShortcutActivity
import com.fortind.zenith.R
import com.fortind.zenith.ui.nav.AppDestination
import com.fortind.zenith.ui.nav.AppRoute
import com.fortind.zenith.ui.nav.MainWindowIntents
import com.fortind.zenith.ui.nav.ShellRequest

private const val STAMP_EXTRA = "stamp"

class AndroidShortcutStore(context: Context) : ShortcutStore {
    private val context = context.applicationContext

    override fun published(): List<PublishedShortcut> =
        ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_DYNAMIC or ShortcutManagerCompat.FLAG_MATCH_PINNED)
            .distinctBy { it.id }
            .map { info ->
                PublishedShortcut(
                    id = info.id,
                    rank = info.rank,
                    stamp = info.extras?.getString(STAMP_EXTRA),
                    dynamic = info.isDynamic,
                    pinned = info.isPinned,
                    enabled = info.isEnabled,
                )
            }

    override fun enable(targets: List<ShortcutTarget>, stamp: ShortcutStamp) {
        ShortcutManagerCompat.enableShortcuts(context, targets.map { info(it, stamp) })
    }

    override fun publish(shortcuts: List<LauncherShortcut>, stamp: ShortcutStamp): Boolean =
        ShortcutManagerCompat.setDynamicShortcuts(
            context,
            shortcuts.mapIndexed { rank, shortcut -> info(ShortcutTarget.Menu(shortcut), stamp, rank) },
        )

    override fun update(targets: List<ShortcutTarget>, stamp: ShortcutStamp): Boolean =
        ShortcutManagerCompat.updateShortcuts(context, targets.map { info(it, stamp) })

    override fun disableGames(ids: List<String>) {
        ShortcutManagerCompat.disableShortcuts(context, ids, context.getString(R.string.shortcut_game_unavailable))
    }

    override fun reportUsed(id: String) {
        ShortcutManagerCompat.reportShortcutUsed(context, id)
    }

    override fun canPin(): Boolean = ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    override fun requestPin(target: ShortcutTarget, stamp: ShortcutStamp): Boolean =
        ShortcutManagerCompat.requestPinShortcut(context, info(target, stamp), null)

    private fun info(target: ShortcutTarget, stamp: ShortcutStamp, rank: Int? = null): ShortcutInfoCompat {
        val builder = ShortcutInfoCompat.Builder(context, target.id)
            .setActivity(ComponentName(context.packageName, stamp.appIcon.launcherClassName))
            .setExtras(PersistableBundle().apply { putString(STAMP_EXTRA, stamp.value) })
        when (target) {
            is ShortcutTarget.Menu -> builder
                .setShortLabel(context.getString(target.shortcut.shortLabel))
                .setLongLabel(context.getString(target.shortcut.longLabel))
                .setIcon(IconCompat.createWithResource(context, target.shortcut.icon))
                .setIntent(menuIntent(target.shortcut))
            is ShortcutTarget.PinnedGame -> builder
                .setShortLabel(target.game.title)
                .setLongLabel(target.game.title)
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_games))
                .setIntent(MainWindowIntents.game(context, target.game.url).setAction(Intent.ACTION_VIEW))
            is ShortcutTarget.PinnedTag -> {
                val label = context.getString(R.string.tag_title, target.tag)
                builder
                    .setShortLabel(label)
                    .setLongLabel(label)
                    .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_tag))
                    .setIntent(MainWindowIntents.route(context, AppDestination.HOME, AppRoute.Tag(target.tag)).setAction(Intent.ACTION_VIEW))
            }
        }
        rank?.let(builder::setRank)
        return builder.build()
    }

    private fun menuIntent(shortcut: LauncherShortcut): Intent = when (shortcut) {
        LauncherShortcut.NEW_POST -> Intent(context, ComposerShortcutActivity::class.java)
        LauncherShortcut.SEARCH -> MainWindowIntents.shell(context, ShellRequest.SEARCH)
        LauncherShortcut.NOTIFICATIONS -> MainWindowIntents.tab(context, AppDestination.NOTIFICATIONS)
        LauncherShortcut.PROFILE -> MainWindowIntents.tab(context, AppDestination.PROFILE)
        LauncherShortcut.GAMES -> MainWindowIntents.tab(context, AppDestination.GAMES)
        LauncherShortcut.SIGN_IN -> MainWindowIntents.shell(context, ShellRequest.SIGN_IN)
    }.setAction(Intent.ACTION_VIEW)
}
