package com.example.data.models

data class VideoItem(
    val id: String,
    val url: String,
    val title: String,
    val uploaderName: String,
    val uploaderAvatarUrl: String?,
    val durationSeconds: Long,
    val formattedDuration: String,
    val viewCount: Long,
    val formattedViews: String,
    val thumbnailUrl: String?
)

data class StreamOption(
    val label: String,
    val url: String,
    val isAudioOnly: Boolean,
    val isVideoOnly: Boolean,
    val resolution: String?,
    val format: String?,
    val audioUrl: String? = null
)

data class VideoDetails(
    val id: String,
    val url: String,
    val title: String,
    val uploaderName: String,
    val uploaderAvatarUrl: String?,
    val description: String?,
    val durationSeconds: Long,
    val formattedDuration: String,
    val viewCount: Long,
    val formattedViews: String,
    val streamOptions: List<StreamOption>,
    val defaultStreamOption: StreamOption?
)
