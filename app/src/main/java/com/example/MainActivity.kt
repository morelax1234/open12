package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.models.VideoItem
import com.example.data.preferences.AppLanguage
import com.example.data.preferences.AppTheme
import com.example.data.preferences.PreferencesRepository
import com.example.player.PlayerManager
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsDialog
import com.example.ui.theme.OpenPipeTheme
import com.example.ui.viewmodel.SearchViewModel
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val searchViewModel: SearchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = PreferencesRepository.getInstance(this)
        applyLocale(this, prefs.languageFlow.value)

        setContent {
            val themePref by prefs.themeFlow.collectAsState()
            val languagePref by prefs.languageFlow.collectAsState()
            val isDarkTheme = when (themePref) {
                AppTheme.SYSTEM -> isSystemInDarkTheme()
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
            }

            val isArabic = prefs.isEffectiveArabic(languagePref)
            val layoutDirection = if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr

            // Apply locale when changed
            LaunchedEffect(languagePref) {
                applyLocale(this@MainActivity, languagePref)
            }

            // Notification permission on Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { _ -> }
                LaunchedEffect(Unit) {
                    if (ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                OpenPipeTheme(darkTheme = isDarkTheme) {
                    OpenPipeMainContent(
                        searchViewModel = searchViewModel,
                        prefs = prefs,
                        currentTheme = themePref,
                        currentLanguage = languagePref,
                        isArabic = isArabic
                    )
                }
            }
        }
    }

    private fun applyLocale(context: Context, language: AppLanguage) {
        val locale = when (language) {
            AppLanguage.ARABIC -> Locale("ar")
            AppLanguage.ENGLISH -> Locale("en")
            AppLanguage.SYSTEM -> Locale.getDefault()
        }
        Locale.setDefault(locale)
        val resources = context.resources
        val config = resources.configuration
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }
}

@Composable
fun OpenPipeMainContent(
    searchViewModel: SearchViewModel,
    prefs: PreferencesRepository,
    currentTheme: AppTheme,
    currentLanguage: AppLanguage,
    isArabic: Boolean
) {
    val context = LocalContext.current
    val playerManager = remember { PlayerManager.getInstance(context) }
    val playerUiState by playerManager.uiState.collectAsState()

    var selectedVideo by remember { mutableStateOf<VideoItem?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val searchQuery by searchViewModel.searchQuery.collectAsState()
    val searchUiState by searchViewModel.uiState.collectAsState()

    if (showSettingsDialog) {
        SettingsDialog(
            currentTheme = currentTheme,
            currentLanguage = currentLanguage,
            onThemeChanged = { theme -> prefs.setTheme(theme) },
            onLanguageChanged = { lang -> prefs.setLanguage(lang) },
            onDismissRequest = { showSettingsDialog = false }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (selectedVideo != null) {
            PlayerScreen(
                videoItem = selectedVideo!!,
                isArabic = isArabic,
                onBack = { selectedVideo = null }
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    SearchScreen(
                        searchQuery = searchQuery,
                        onQueryChange = { searchViewModel.updateQuery(it) },
                        onSearch = { query -> searchViewModel.search(query, isArabic) },
                        uiState = searchUiState,
                        onVideoClick = { video -> selectedVideo = video },
                        onOpenSettings = { showSettingsDialog = true }
                    )
                }

                // Mini-player bottom bar if video is loaded/playing
                AnimatedVisibility(
                    visible = playerUiState.videoDetails != null,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it })
                ) {
                    val details = playerUiState.videoDetails
                    if (details != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .navigationBarsPadding()
                                .clickable {
                                    selectedVideo = VideoItem(
                                        id = details.id,
                                        url = details.url,
                                        title = details.title,
                                        uploaderName = details.uploaderName,
                                        uploaderAvatarUrl = details.uploaderAvatarUrl,
                                        durationSeconds = details.durationSeconds,
                                        formattedDuration = details.formattedDuration,
                                        viewCount = details.viewCount,
                                        formattedViews = details.formattedViews,
                                        thumbnailUrl = details.uploaderAvatarUrl
                                    )
                                }
                                .testTag("mini_player_bar"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (!details.uploaderAvatarUrl.isNullOrEmpty()) {
                                        AsyncImage(
                                            model = details.uploaderAvatarUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = details.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = details.uploaderName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }

                                    IconButton(
                                        onClick = { playerManager.togglePlayPause() },
                                        modifier = Modifier.testTag("mini_player_play_pause")
                                    ) {
                                        Icon(
                                            imageVector = if (playerUiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                }

                                if (playerUiState.durationMs > 0) {
                                    val progress = (playerUiState.currentPositionMs.toFloat() / playerUiState.durationMs.toFloat()).coerceIn(0f, 1f)
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.outlineVariant,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
