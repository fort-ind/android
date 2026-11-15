package com.fortind.zenith.ui.nav

import android.content.Context
import android.content.Intent
import com.fortind.zenith.MainActivity

object MainWindowIntents {
    fun open(context: Context): Intent = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    fun shell(context: Context, request: ShellRequest): Intent =
        open(context).putExtra(MainActivity.EXTRA_SHELL_REQUEST, request.name)

    fun tab(context: Context, destination: AppDestination): Intent =
        open(context).putExtra(MainActivity.EXTRA_TAB, destination.name)

    fun route(context: Context, destination: AppDestination, route: AppRoute): Intent =
        tab(context, destination).putExtra(MainActivity.EXTRA_ROUTE, route.encode())

    fun game(context: Context, url: String): Intent =
        open(context).putExtra(MainActivity.EXTRA_GAME_URL, url)
}
