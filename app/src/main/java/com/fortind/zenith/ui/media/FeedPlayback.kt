package com.fortind.zenith.ui.media

import android.app.Application
import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntRect
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.fortind.zenith.media.PlaybackService
import com.fortind.zenith.timeline.MediaKind
import com.fortind.zenith.timeline.NoteFile
import com.google.common.util.concurrent.ListenableFuture

class FeedPlayback(context: Context) : ViewModel() {

    private val appContext = context.applicationContext
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var pendingPlay: NoteFile? = null
    private var requestedId: String? = null
    private var foreground = true

    var player: Player? by mutableStateOf(null)
        private set

    var current: NoteFile? by mutableStateOf(null)
        private set

    var fullscreen: Boolean by mutableStateOf(false)
        private set

    var pictureInPicture: Boolean by mutableStateOf(false)
        private set

    var videoPlaying: Boolean by mutableStateOf(false)
        private set

    var videoAspect: Float by mutableFloatStateOf(DEFAULT_ASPECT)
        private set

    var videoBounds: IntRect? by mutableStateOf(null)
        private set

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val id = mediaItem?.mediaId
            if (id == null || id != requestedId) forget()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) = syncVideoPlaying()

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                videoAspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
            }
        }
    }

    fun play(file: NoteFile) {
        current = file
        file.aspectRatio?.let { videoAspect = it }
        val connected = controller
        if (connected == null) {
            pendingPlay = file
            connect()
        } else {
            start(connected, file)
        }
    }

    fun pause() {
        pendingPlay = null
        controller?.pause()
    }

    fun toggle(file: NoteFile) {
        if (current?.id == file.id && controller?.isPlaying == true) pause() else play(file)
    }

    fun seekBy(deltaMs: Long) {
        val connected = controller ?: return
        connected.seekTo(seekTarget(connected.currentPosition, connected.duration, deltaMs))
    }

    fun enterFullscreen(file: NoteFile) {
        play(file)
        fullscreen = true
    }

    fun exitFullscreen() {
        fullscreen = false
    }

    fun stop() {
        forget()
        pendingPlay = null
        controller?.run {
            stop()
            clearMediaItems()
        }
    }

    fun stopIf(fileId: String) {
        if (current?.id == fileId && foreground && !pictureInPicture) stop()
    }

    fun setForeground(inForeground: Boolean) {
        foreground = inForeground
    }

    fun onPictureInPictureChanged(inPictureInPicture: Boolean) {
        pictureInPicture = inPictureInPicture
    }

    fun reportVideoBounds(bounds: IntRect) {
        if (!pictureInPicture) videoBounds = bounds
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        controller = null
        player = null
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
    }

    private fun connect() {
        if (controllerFuture != null) return
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        controllerFuture = future
        future.addListener({ onConnected(future) }, ContextCompat.getMainExecutor(appContext))
    }

    private fun onConnected(future: ListenableFuture<MediaController>) {
        if (controllerFuture !== future) return
        val connected = runCatching { future.get() }.getOrNull()
        if (connected == null) {
            controllerFuture = null
            pendingPlay = null
            forget()
            return
        }
        connected.addListener(listener)
        controller = connected
        player = connected
        pendingPlay?.let { start(connected, it) }
        pendingPlay = null
    }

    private fun start(connected: MediaController, file: NoteFile) {
        requestedId = file.id
        if (connected.currentMediaItem?.mediaId != file.id) {
            if (connected.isCommandAvailable(Player.COMMAND_SET_AUDIO_ATTRIBUTES)) {
                connected.setAudioAttributes(file.audioAttributes(), true)
            }
            connected.setMediaItem(file.mediaItem())
            connected.prepare()
        } else if (connected.playbackState == Player.STATE_ENDED) {
            connected.seekTo(0)
        } else if (connected.playbackState == Player.STATE_IDLE) {
            connected.prepare()
        }
        connected.play()
        syncVideoPlaying()
    }

    private fun forget() {
        requestedId = null
        current = null
        fullscreen = false
        videoPlaying = false
        videoBounds = null
    }

    private fun syncVideoPlaying() {
        videoPlaying = current?.kind == MediaKind.Video && controller?.playWhenReady == true
    }

    private fun NoteFile.mediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setUri(url)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(name).build())
        .build()

    private fun NoteFile.audioAttributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(if (kind == MediaKind.Audio) C.AUDIO_CONTENT_TYPE_MUSIC else C.AUDIO_CONTENT_TYPE_MOVIE)
        .build()

    companion object {
        private const val DEFAULT_ASPECT = 16f / 9f

        val Factory = viewModelFactory {
            initializer { FeedPlayback(this[APPLICATION_KEY] as Application) }
        }
    }
}
