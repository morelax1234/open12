package com.example.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.R
import com.example.data.models.VideoDetails
import com.example.data.models.VideoItem
import com.example.data.repository.YouTubeRepository
import com.example.player.PlayerManager
import com.example.ui.components.PlayerControlsOverlay
import com.example.ui.components.QualityDialog
import kotlinx.coroutines.launch

sealed interface VideoLoadState {
    data object Loading : VideoLoadState
    data class Ready(val details: VideoDetails) : VideoLoadState
    data class Error(val message: String) : VideoLoadState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    videoItem: VideoItem,
    isArabic: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val playerManager = remember { PlayerManager.getInstance(context) }
    val uiState by playerManager.uiState.collectAsState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var showQualityDialog by remember { mutableStateOf(false) }
    var isDescriptionExpanded by remember { mutableStateOf(false) }
    var loadState by remember { mutableStateOf<VideoLoadState>(VideoLoadState.Loading) }
    val repository = remember { YouTubeRepository() }
    val scope = rememberCoroutineScope()

    // Sync fullscreen state with orientation
    LaunchedEffect(isLandscape) {
        playerManager.setFullscreen(isLandscape)
    }

    // Load video details and initiate stream
    fun loadStream() {
        loadState = VideoLoadState.Loading
        scope.launch {
            val result = repository.getVideoDetails(videoItem.url, isArabic)
            result.fold(
                onSuccess = { details ->
                    loadState = VideoLoadState.Ready(details)
                    playerManager.playVideo(details)
                },
                onFailure = { err ->
                    loadState = VideoLoadState.Error(err.localizedMessage ?: "Failed to extract streams")
                }
            )
        }
    }

    LaunchedEffect(videoItem.url) {
        loadStream()
    }

    // Handle back button
    BackHandler {
        if (uiState.isFullscreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            playerManager.setFullscreen(false)
        } else {
            onBack()
        }
    }

    // Toggle orientation and system bars for fullscreen
    val isFullscreen = uiState.isFullscreen || isLandscape
    DisposableEffect(isFullscreen) {
        activity?.let { act ->
            val window = act.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (isFullscreen) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            activity?.let { act ->
                val window = act.window
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    if (showQualityDialog && uiState.videoDetails != null) {
        QualityDialog(
            options = uiState.videoDetails!!.streamOptions,
            selectedOption = uiState.selectedStream,
            onOptionSelected = { option ->
                playerManager.changeQuality(option)
            },
            onDismissRequest = { showQualityDialog = false }
        )
    }

    if (isFullscreen) {
        // Fullscreen Mode
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            when (val state = loadState) {
                is VideoLoadState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                is VideoLoadState.Error -> {
                    ErrorRetryBox(
                        errorMessage = stringResource(R.string.video_error),
                        onRetry = { loadStream() },
                        onBack = onBack
                    )
                }
                is VideoLoadState.Ready -> {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = playerManager.exoPlayer
                                useController = false
                            }
                        },
                        update = { view ->
                            if (view.player != playerManager.exoPlayer) {
                                view.player = playerManager.exoPlayer
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (uiState.errorMessage != null) {
                        ErrorRetryBox(
                            errorMessage = stringResource(R.string.video_error),
                            onRetry = {
                                uiState.selectedStream?.let { playerManager.playVideo(state.details, it) }
                                    ?: playerManager.playVideo(state.details)
                            },
                            onBack = onBack
                        )
                    } else {
                        PlayerControlsOverlay(
                            uiState = uiState,
                            onPlayPause = { playerManager.togglePlayPause() },
                            onSeek = { pos -> playerManager.seekTo(pos) },
                            onSeekBack = { playerManager.seekBack() },
                            onSeekForward = { playerManager.seekForward() },
                            onToggleFullscreen = {
                                val newFullscreen = !uiState.isFullscreen
                                playerManager.setFullscreen(newFullscreen)
                                activity?.requestedOrientation = if (newFullscreen) {
                                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                } else {
                                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                }
                            },
                            onOpenQualityDialog = { showQualityDialog = true },
                            onBack = {
                                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                playerManager.setFullscreen(false)
                            }
                        )
                    }
                }
            }
        }
    } else {
        // Portrait / Standard Mode
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = videoItem.title,
                            maxLines = 1,
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("player_nav_back")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            modifier = modifier.fillMaxSize()
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
            ) {
                // Video Player Container (16:9)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                ) {
                    when (val state = loadState) {
                        is VideoLoadState.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!videoItem.thumbnailUrl.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = videoItem.thumbnailUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.45f))
                                    )
                                }
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        is VideoLoadState.Error -> {
                            ErrorRetryBox(
                                errorMessage = stringResource(R.string.video_error),
                                onRetry = { loadStream() },
                                onBack = onBack
                            )
                        }

                        is VideoLoadState.Ready -> {
                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        player = playerManager.exoPlayer
                                        useController = false
                                    }
                                },
                                update = { view ->
                                    if (view.player != playerManager.exoPlayer) {
                                        view.player = playerManager.exoPlayer
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            if (uiState.errorMessage != null) {
                                ErrorRetryBox(
                                    errorMessage = stringResource(R.string.video_error),
                                    onRetry = {
                                        uiState.selectedStream?.let { playerManager.playVideo(state.details, it) }
                                            ?: playerManager.playVideo(state.details)
                                    },
                                    onBack = onBack
                                )
                            } else {
                                PlayerControlsOverlay(
                                    uiState = uiState,
                                    onPlayPause = { playerManager.togglePlayPause() },
                                    onSeek = { pos -> playerManager.seekTo(pos) },
                                    onSeekBack = { playerManager.seekBack() },
                                    onSeekForward = { playerManager.seekForward() },
                                    onToggleFullscreen = {
                                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                        playerManager.setFullscreen(true)
                                    },
                                    onOpenQualityDialog = { showQualityDialog = true },
                                    onBack = onBack
                                )
                            }
                        }
                    }
                }

                // Video Meta Details
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Video Title
                    Text(
                        text = (loadState as? VideoLoadState.Ready)?.details?.title ?: videoItem.title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            lineHeight = 24.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Views & Duration
                    val viewsText = (loadState as? VideoLoadState.Ready)?.details?.formattedViews ?: videoItem.formattedViews
                    val durationText = (loadState as? VideoLoadState.Ready)?.details?.formattedDuration ?: videoItem.formattedDuration
                    Text(
                        text = if (viewsText.isNotEmpty()) {
                            stringResource(R.string.views_format, viewsText) + " • " + durationText
                        } else {
                            durationText
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Channel Row & Quality Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Channel avatar
                        val avatarUrl = (loadState as? VideoLoadState.Ready)?.details?.uploaderAvatarUrl ?: videoItem.uploaderAvatarUrl
                        val channelName = (loadState as? VideoLoadState.Ready)?.details?.uploaderName ?: videoItem.uploaderName

                        if (!avatarUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = channelName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = channelName.take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = channelName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )

                        // Quality selection chip
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .clickable { showQualityDialog = true }
                                .testTag("quality_badge_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HighQuality,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = uiState.selectedStream?.label ?: stringResource(R.string.quality),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Background Audio Notification Pill
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Headphones,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.background_playing_title),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Description (Expandable)
                    val description = (loadState as? VideoLoadState.Ready)?.details?.description
                    if (!description.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isDescriptionExpanded = !isDescriptionExpanded }
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Description",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Icon(
                                        imageVector = if (isDescriptionExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 3
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorRetryBox(
    errorMessage: String,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = errorMessage,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row {
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("retry_video_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.retry))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.testTag("back_video_button")
                ) {
                    Text(stringResource(R.string.back))
                }
            }
        }
    }
}
