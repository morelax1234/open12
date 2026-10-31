package com.example.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.example.data.models.StreamOption
import com.example.data.models.VideoDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerUiState(
    val videoDetails: VideoDetails? = null,
    val selectedStream: StreamOption? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isEnded: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isFullscreen: Boolean = false,
    val errorMessage: String? = null
)

class PlayerManager private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.Main)
    private var progressJob: Job? = null

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(appContext)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            true
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val dataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(20000)
        .setReadTimeoutMs(20000)
        .setDefaultRequestProperties(
            mapOf(
                "Referer" to "https://www.youtube.com/",
                "Origin" to "https://www.youtube.com"
            )
        )

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
                if (isPlaying) {
                    startProgressTracker()
                } else {
                    stopProgressTracker()
                    updateProgress()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val isBuffering = playbackState == Player.STATE_BUFFERING
                val isEnded = playbackState == Player.STATE_ENDED
                _uiState.value = _uiState.value.copy(
                    isBuffering = isBuffering,
                    isEnded = isEnded,
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                )
                if (playbackState == Player.STATE_READY) {
                    updateProgress()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                _uiState.value = _uiState.value.copy(
                    isBuffering = false,
                    isPlaying = false,
                    errorMessage = error.localizedMessage ?: "Playback error"
                )
            }
        })
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                updateProgress()
                delay(500)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun updateProgress() {
        val current = exoPlayer.currentPosition.coerceAtLeast(0L)
        val duration = exoPlayer.duration.coerceAtLeast(0L)
        _uiState.value = _uiState.value.copy(
            currentPositionMs = current,
            durationMs = duration
        )
    }

    fun playVideo(videoDetails: VideoDetails, streamOption: StreamOption? = null) {
        val stream = streamOption ?: videoDetails.defaultStreamOption
        if (stream == null) {
            _uiState.value = _uiState.value.copy(
                videoDetails = videoDetails,
                errorMessage = "No playable stream found"
            )
            return
        }

        // Start playback foreground service for background audio
        try {
            val serviceIntent = Intent(appContext, PlaybackService::class.java)
            ContextCompat.startForegroundService(appContext, serviceIntent)
        } catch (_: Exception) {
            // Service start fallback
        }

        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(videoDetails.title)
            .setArtist(videoDetails.uploaderName)
            .setArtworkUri(videoDetails.uploaderAvatarUrl?.let { Uri.parse(it) })
            .build()

        if (stream.isVideoOnly && !stream.audioUrl.isNullOrEmpty()) {
            val videoMediaItem = MediaItem.Builder()
                .setUri(stream.url)
                .setMediaMetadata(mediaMetadata)
                .build()
            val audioMediaItem = MediaItem.Builder()
                .setUri(stream.audioUrl)
                .build()
            val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(videoMediaItem)
            val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(audioMediaItem)
            val mergedSource = MergingMediaSource(videoSource, audioSource)
            exoPlayer.setMediaSource(mergedSource)
        } else {
            val mediaItem = MediaItem.Builder()
                .setUri(stream.url)
                .setMediaMetadata(mediaMetadata)
                .build()
            val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            exoPlayer.setMediaSource(mediaSource)
        }

        exoPlayer.prepare()
        exoPlayer.playWhenReady = true

        _uiState.value = _uiState.value.copy(
            videoDetails = videoDetails,
            selectedStream = stream,
            errorMessage = null,
            isBuffering = true
        )
    }

    fun changeQuality(streamOption: StreamOption) {
        val details = _uiState.value.videoDetails ?: return
        val currentPosition = exoPlayer.currentPosition
        val wasPlaying = exoPlayer.isPlaying

        playVideo(details, streamOption)
        if (currentPosition > 0) {
            exoPlayer.seekTo(currentPosition)
        }
        exoPlayer.playWhenReady = wasPlaying
    }

    fun play() {
        if (_uiState.value.isEnded) {
            exoPlayer.seekTo(0)
        }
        exoPlayer.play()
    }

    fun pause() {
        exoPlayer.pause()
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs.coerceIn(0L, exoPlayer.duration.coerceAtLeast(0L)))
        updateProgress()
    }

    fun seekForward(millis: Long = 10000) {
        val target = (exoPlayer.currentPosition + millis).coerceAtMost(exoPlayer.duration.coerceAtLeast(0L))
        exoPlayer.seekTo(target)
        updateProgress()
    }

    fun seekBack(millis: Long = 10000) {
        val target = (exoPlayer.currentPosition - millis).coerceAtLeast(0L)
        exoPlayer.seekTo(target)
        updateProgress()
    }

    fun toggleFullscreen() {
        _uiState.value = _uiState.value.copy(isFullscreen = !_uiState.value.isFullscreen)
    }

    fun setFullscreen(fullscreen: Boolean) {
        _uiState.value = _uiState.value.copy(isFullscreen = fullscreen)
    }

    fun release() {
        stopProgressTracker()
        exoPlayer.release()
    }

    companion object {
        @Volatile
        private var instance: PlayerManager? = null

        fun getInstance(context: Context): PlayerManager {
            return instance ?: synchronized(this) {
                instance ?: PlayerManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
