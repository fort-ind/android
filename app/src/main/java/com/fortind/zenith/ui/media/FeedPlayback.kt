package com.fortind.zenith.ui.media

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.fortind.zenith.timeline.MediaKind
import com.fortind.zenith.timeline.NoteFile

class FeedPlayback(context: Context) : ViewModel() {

    private val appContext = context.applicationContext

    var player: ExoPlayer? by mutableStateOf(null)
        private set

    var current: NoteFile? by mutableStateOf(null)
        private set

    var fullscreen: Boolean by mutableStateOf(false)
        private set

    fun play(file: NoteFile) {
        val exo = player ?: createPlayer().also { player = it }
        if (current?.id != file.id) {
            exo.setAudioAttributes(file.audioAttributes(), true)
            exo.setMediaItem(file.mediaItem())
            exo.prepare()
            current = file
        } else if (exo.playbackState == Player.STATE_ENDED) {
            exo.seekTo(0)
        }
        exo.play()
    }

    fun pause() {
        player?.pause()
    }

    fun enterFullscreen(file: NoteFile) {
        play(file)
        fullscreen = true
    }

    fun exitFullscreen() {
        fullscreen = false
    }

    fun stop() {
        fullscreen = false
        current = null
        player?.run {
            stop()
            clearMediaItems()
        }
    }

    fun stopIf(fileId: String) {
        if (current?.id == fileId) stop()
    }

    override fun onCleared() {
        player?.release()
        player = null
    }

    private fun createPlayer(): ExoPlayer = ExoPlayer.Builder(appContext)
        .setHandleAudioBecomingNoisy(true)
        .build()

    private fun NoteFile.mediaItem(): MediaItem = MediaItem.Builder()
        .setUri(url)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(name).build())
        .build()

    private fun NoteFile.audioAttributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(if (kind == MediaKind.Audio) C.AUDIO_CONTENT_TYPE_MUSIC else C.AUDIO_CONTENT_TYPE_MOVIE)
        .build()

    companion object {
        val Factory = viewModelFactory {
            initializer { FeedPlayback(this[APPLICATION_KEY] as Application) }
        }
    }
}
