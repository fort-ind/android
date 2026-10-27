package com.fortind.zenith.games

import android.content.res.AssetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface GameCatalog {
    suspend fun games(): List<Game>
}

class AssetGameCatalog(private val assets: AssetManager) : GameCatalog {
    override suspend fun games(): List<Game> = withContext(Dispatchers.IO) {
        assets.open(SITEMAP_ASSET).use(::parseSitemapGames)
    }

    private companion object {
        const val SITEMAP_ASSET = "sitemap.xml"
    }
}
