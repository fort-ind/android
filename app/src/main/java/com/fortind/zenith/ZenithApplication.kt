package com.fortind.zenith

import com.fortind.zenith.discovery.CollectionsApi
import com.fortind.zenith.discovery.DataStoreDismissedAnnouncementsStore
import com.fortind.zenith.discovery.DiscoveryApi
import com.fortind.zenith.discovery.DismissedAnnouncementsStore
import com.fortind.zenith.discovery.HttpCollectionsApi
import com.fortind.zenith.discovery.HttpDiscoveryApi
import android.app.Application
import android.content.Context
import android.os.Build
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import androidx.work.Configuration
import com.fortind.zenith.account.AccountApi
import com.fortind.zenith.account.HttpAccountApi
import com.fortind.zenith.account.HttpModerationApi
import com.fortind.zenith.account.ModerationApi
import com.fortind.zenith.emoji.DEFAULT_LIKE
import com.fortind.zenith.net.FortHttp
import com.fortind.zenith.auth.DataStoreScopeStampStore
import com.fortind.zenith.auth.HttpMiAuthApi
import com.fortind.zenith.auth.HttpUserApi
import com.fortind.zenith.auth.KeystoreTokenStore
import com.fortind.zenith.auth.MiAuthApi
import com.fortind.zenith.auth.UserSessionService
import com.fortind.zenith.autocomplete.HttpSuggestionsApi
import com.fortind.zenith.autocomplete.SuggestionsApi
import com.fortind.zenith.composer.DataStoreDraftStore
import com.fortind.zenith.composer.DraftStore
import com.fortind.zenith.drive.DriveApi
import com.fortind.zenith.drive.HttpDriveApi
import com.fortind.zenith.emoji.AssetUnicodeEmojiSource
import com.fortind.zenith.emoji.DataStoreRecentReactionsStore
import com.fortind.zenith.emoji.EmojiRepository
import com.fortind.zenith.emoji.HttpInstanceApi
import com.fortind.zenith.emoji.RecentReactionsStore
import com.fortind.zenith.emoji.UnicodeEmojiSource
import com.fortind.zenith.games.AssetGameCatalog
import com.fortind.zenith.games.DataStoreFavoritesStore
import com.fortind.zenith.games.FavoritesStore
import com.fortind.zenith.games.GameCatalog
import com.fortind.zenith.media.AndroidMediaCompressor
import com.fortind.zenith.media.AndroidMediaSanitizer
import com.fortind.zenith.media.ContentResolverMediaReader
import com.fortind.zenith.media.LegacyAnimatedDecoder
import com.fortind.zenith.media.MediaCompressor
import com.fortind.zenith.media.MediaReader
import com.fortind.zenith.media.MediaSanitizer
import com.fortind.zenith.net.MisskeyStreaming
import com.fortind.zenith.net.OkHttpStreamTransport
import com.fortind.zenith.net.defaultNetworkOf
import com.fortind.zenith.notifications.AndroidSystemNotifier
import com.fortind.zenith.notifications.AppForegroundTracker
import com.fortind.zenith.notifications.DataStoreNotificationSyncStore
import com.fortind.zenith.notifications.HttpNotificationsApi
import com.fortind.zenith.notifications.NotificationQuickActions
import com.fortind.zenith.notifications.NotificationStream
import com.fortind.zenith.notifications.NotificationSyncCoordinator
import com.fortind.zenith.window.WindowEvents
import com.fortind.zenith.notifications.NotificationSyncStore
import com.fortind.zenith.notifications.NotificationSyncer
import com.fortind.zenith.notifications.NotificationWork
import com.fortind.zenith.notifications.NotificationsApi
import com.fortind.zenith.notifications.StreamingNotificationStream
import com.fortind.zenith.notifications.SystemNotifier
import com.fortind.zenith.notifications.WorkManagerNotificationWork
import com.fortind.zenith.account.HttpReportApi
import com.fortind.zenith.account.ReportApi
import com.fortind.zenith.people.FollowApi
import com.fortind.zenith.people.HttpFollowApi
import com.fortind.zenith.people.HttpUsersApi
import com.fortind.zenith.people.UsersApi
import com.fortind.zenith.settings.AppIconSwitcher
import com.fortind.zenith.settings.DataStoreSettingsStore
import com.fortind.zenith.settings.PackageManagerAppIconSwitcher
import com.fortind.zenith.settings.SettingsStore
import com.fortind.zenith.timeline.CachingLinkPreviews
import com.fortind.zenith.timeline.HttpLinkPreviewApi
import com.fortind.zenith.timeline.HttpNotesApi
import com.fortind.zenith.timeline.LinkPreviewSource
import com.fortind.zenith.timeline.NotesApi
import com.fortind.zenith.timeline.StreamingTimelineStream
import com.fortind.zenith.timeline.TimelineStream
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class AppContainer(application: Application) {
    private val http = FortHttp()
    val miAuthApi: MiAuthApi = HttpMiAuthApi(http)
    val sessionService = UserSessionService(KeystoreTokenStore(application), HttpUserApi(http), DataStoreScopeStampStore(application))
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val okHttpClient: OkHttpClient by lazy { OkHttpClient() }
    private val streaming = MisskeyStreaming(OkHttpStreamTransport { okHttpClient }, scope, networks = defaultNetworkOf(application, scope))
    val notificationsApi: NotificationsApi = HttpNotificationsApi(http)
    val notificationStream: NotificationStream = StreamingNotificationStream(streaming)
    val notesApi: NotesApi = HttpNotesApi(http)
    val draftStore: DraftStore = DataStoreDraftStore(application)
    val accountApi: AccountApi = HttpAccountApi(http)
    val moderationApi: ModerationApi = HttpModerationApi(http)
    val reportApi: ReportApi = HttpReportApi(http)
    val usersApi: UsersApi = HttpUsersApi(http)
    val followApi: FollowApi = HttpFollowApi(http)
    val suggestionsApi: SuggestionsApi = HttpSuggestionsApi(http)
    val collectionsApi: CollectionsApi = HttpCollectionsApi(http)
    val discoveryApi: DiscoveryApi = HttpDiscoveryApi(http)
    val dismissedAnnouncements: DismissedAnnouncementsStore = DataStoreDismissedAnnouncementsStore(application)
    val mediaReader: MediaReader = ContentResolverMediaReader(application.contentResolver) { File(application.cacheDir, "staged") }
    val driveApi: DriveApi = HttpDriveApi(http, { okHttpClient }, mediaReader)
    val mediaCompressor: MediaCompressor = AndroidMediaCompressor(application)
    val mediaSanitizer: MediaSanitizer = AndroidMediaSanitizer(application, mediaReader)
    val timelineStream: TimelineStream = StreamingTimelineStream(streaming)
    val linkPreviews: LinkPreviewSource = CachingLinkPreviews(HttpLinkPreviewApi(http) { Locale.getDefault().toLanguageTag() }, scope)
    val emojiRepository = EmojiRepository(HttpInstanceApi(http), { File(application.cacheDir, "instance") }, scope)
    val unicodeEmoji: UnicodeEmojiSource = AssetUnicodeEmojiSource(application.assets)
    val recentReactions: RecentReactionsStore = DataStoreRecentReactionsStore(application)
    val gameCatalog: GameCatalog = AssetGameCatalog(application.assets)
    val favoritesStore: FavoritesStore = DataStoreFavoritesStore(application)
    val settingsStore: SettingsStore = DataStoreSettingsStore(application)
    val appIconSwitcher: AppIconSwitcher = PackageManagerAppIconSwitcher(application)
    val foregroundTracker = AppForegroundTracker()
    val windowEvents = WindowEvents()
    private val notificationSyncStore: NotificationSyncStore = DataStoreNotificationSyncStore(application)
    private val androidNotifier = AndroidSystemNotifier(application)
    val systemNotifier: SystemNotifier = androidNotifier
    val notificationWork: NotificationWork = WorkManagerNotificationWork(application)
    val quickActions = NotificationQuickActions(
        session = sessionService,
        notes = notesApi,
        drafts = draftStore,
        notifier = systemNotifier,
        defaultLike = { emojiRepository.meta.value?.defaultLike ?: DEFAULT_LIKE },
    )
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

    init {
        http.onPermissionDenied = { scope.launch { sessionService.markScopesStale() } }
        scope.launch { androidNotifier.ensureChannels() }
        scope.launch { okHttpClient }
    }
}

class ZenithApplication : Application(), SingletonImageLoader.Factory, Configuration.Provider {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        DebugStrictMode.install()
        container = AppContainer(this)
        appScope.launch { container.sessionService.restore() }
        container.notificationSyncCoordinator.start(container.scope)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun newImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = { container.okHttpClient }))
            add(LegacyAnimatedDecoder.Factory())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                add(AnimatedImageDecoder.Factory())
            } else {
                add(GifDecoder.Factory())
            }
        }
        .build()
}
