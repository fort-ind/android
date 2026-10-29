package com.fortind.zenith

import android.app.Application
import com.fortind.zenith.auth.FortHttp
import com.fortind.zenith.auth.HttpMiAuthApi
import com.fortind.zenith.auth.HttpUserApi
import com.fortind.zenith.auth.KeystoreTokenStore
import com.fortind.zenith.auth.MiAuthApi
import com.fortind.zenith.auth.UserSessionService
import com.fortind.zenith.games.AssetGameCatalog
import com.fortind.zenith.games.DataStoreFavoritesStore
import com.fortind.zenith.games.FavoritesStore
import com.fortind.zenith.games.GameCatalog
import com.fortind.zenith.notifications.AndroidSystemNotifier
import com.fortind.zenith.notifications.AppForegroundTracker
import com.fortind.zenith.notifications.DataStoreNotificationSyncStore
import com.fortind.zenith.notifications.HttpNotificationsApi
import com.fortind.zenith.notifications.NotificationStream
import com.fortind.zenith.notifications.NotificationSyncCoordinator
import com.fortind.zenith.notifications.NotificationSyncStore
import com.fortind.zenith.notifications.NotificationSyncer
import com.fortind.zenith.notifications.NotificationWork
import com.fortind.zenith.notifications.NotificationsApi
import com.fortind.zenith.notifications.OkHttpNotificationStream
import com.fortind.zenith.notifications.SystemNotifier
import com.fortind.zenith.notifications.WorkManagerNotificationWork
import com.fortind.zenith.settings.AppIconSwitcher
import com.fortind.zenith.settings.DataStoreSettingsStore
import com.fortind.zenith.settings.PackageManagerAppIconSwitcher
import com.fortind.zenith.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(application: Application) {
    private val http = FortHttp()
    val miAuthApi: MiAuthApi = HttpMiAuthApi(http)
    val sessionService = UserSessionService(KeystoreTokenStore(application), HttpUserApi(http))
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val notificationsApi: NotificationsApi = HttpNotificationsApi(http)
    val notificationStream: NotificationStream = OkHttpNotificationStream()
    val gameCatalog: GameCatalog = AssetGameCatalog(application.assets)
    val favoritesStore: FavoritesStore = DataStoreFavoritesStore(application)
    val settingsStore: SettingsStore = DataStoreSettingsStore(application)
    val appIconSwitcher: AppIconSwitcher = PackageManagerAppIconSwitcher(application)
    val foregroundTracker = AppForegroundTracker()
    private val notificationSyncStore: NotificationSyncStore = DataStoreNotificationSyncStore(application)
    val systemNotifier: SystemNotifier = AndroidSystemNotifier(application)
    val notificationWork: NotificationWork = WorkManagerNotificationWork(application)
    val notificationSyncer = NotificationSyncer(
        session = sessionService,
        api = notificationsApi,
        settings = settingsStore,
        syncStore = notificationSyncStore,
        foreground = foregroundTracker,
        notifier = systemNotifier,
    )
    val notificationSyncCoordinator = NotificationSyncCoordinator(
        settings = settingsStore,
        session = sessionService,
        work = notificationWork,
        syncStore = notificationSyncStore,
        notifier = systemNotifier,
    )
}

class ZenithApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch { container.sessionService.restore() }
        container.notificationSyncCoordinator.start(container.scope)
    }
}
