package com.fortind.zenith

import android.app.Application
import com.fortind.zenith.auth.FortHttp
import com.fortind.zenith.auth.HttpMiAuthApi
import com.fortind.zenith.auth.HttpUserApi
import com.fortind.zenith.auth.KeystoreTokenStore
import com.fortind.zenith.auth.MiAuthApi
import com.fortind.zenith.auth.UserSessionService
import com.fortind.zenith.games.AssetGameCatalog
import com.fortind.zenith.games.GameCatalog
import com.fortind.zenith.settings.AppIconSwitcher
import com.fortind.zenith.settings.DataStoreSettingsStore
import com.fortind.zenith.settings.PackageManagerAppIconSwitcher
import com.fortind.zenith.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** hi **/
class AppContainer(application: Application) {
    private val http = FortHttp()
    val miAuthApi: MiAuthApi = HttpMiAuthApi(http)
    val sessionService = UserSessionService(KeystoreTokenStore(application), HttpUserApi(http))
    val gameCatalog: GameCatalog = AssetGameCatalog(application.assets)
    val settingsStore: SettingsStore = DataStoreSettingsStore(application)
    val appIconSwitcher: AppIconSwitcher = PackageManagerAppIconSwitcher(application)
}

class ZenithApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch { container.sessionService.restore() }
    }
}
