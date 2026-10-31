package com.example.data.repository

import com.example.data.models.StreamOption
import com.example.data.models.VideoDetails
import com.example.data.models.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Locale

class YouTubeRepository {

    suspend fun search(query: String, isArabic: Boolean = false): Result<List<VideoItem>> = withContext(Dispatchers.IO) {
        try {
            val searchExtractor = ServiceList.YouTube.getSearchExtractor(query, emptyList(), "")
            searchExtractor.fetchPage()
            val page = searchExtractor.initialPage
            val items = page.items
                .filterIsInstance<StreamInfoItem>()
                .map { item ->
                    val duration = item.duration
                    val views = item.viewCount
                    val thumb = item.thumbnails?.firstOrNull()?.url
                    VideoItem(
                        id = item.url.substringAfterLast("v=", item.url).substringAfterLast("/", item.url),
                        url = item.url,
                        title = item.name ?: "",
                        uploaderName = item.uploaderName ?: "",
                        uploaderAvatarUrl = item.uploaderAvatars?.firstOrNull()?.url,
                        durationSeconds = duration,
                        formattedDuration = formatDuration(duration),
                        viewCount = views,
                        formattedViews = formatViews(views, isArabic),
                        thumbnailUrl = thumb
                    )
                }
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getVideoDetails(url: String, isArabic: Boolean = false): Result<VideoDetails> = withContext(Dispatchers.IO) {
        try {
            YoutubeStreamExtractor.setFetchIosClient(true)
            val streamInfo = StreamInfo.getInfo(url)

            val videoStreams = streamInfo.videoStreams ?: emptyList()
            val videoOnlyStreams = streamInfo.videoOnlyStreams ?: emptyList()
            val audioStreams = streamInfo.audioStreams ?: emptyList()
            val bestAudio = audioStreams.maxByOrNull { it.averageBitrate }
            val bestAudioUrl = bestAudio?.content

            val options = mutableListOf<StreamOption>()

            // 1. Progressive streams (Video + Audio combined in single container)
            videoStreams
                .sortedByDescending { it.height }
                .forEach { vs ->
                    val res = vs.resolution ?: "${vs.height}p"
                    val formatName = vs.format?.name ?: "MP4"
                    options.add(
                        StreamOption(
                            label = "$res ($formatName)",
                            url = vs.content,
                            isAudioOnly = false,
                            isVideoOnly = false,
                            resolution = res,
                            format = formatName
                        )
                    )
                }

            // 2. Video-only streams (e.g. 1080p, 1440p) if available
            videoOnlyStreams
                .filter { vo ->
                    val res = vo.resolution ?: "${vo.height}p"
                    options.none { it.resolution == res }
                }
                .sortedByDescending { it.height }
                .take(3)
                .forEach { vo ->
                    val res = vo.resolution ?: "${vo.height}p"
                    options.add(
                        StreamOption(
                            label = "$res (HD)",
                            url = vo.content,
                            isAudioOnly = false,
                            isVideoOnly = true,
                            resolution = res,
                            format = vo.format?.name ?: "WebM",
                            audioUrl = bestAudioUrl
                        )
                    )
                }

            // 3. Audio-only stream
            bestAudio?.let { audio ->
                val bitrateKbps = if (audio.averageBitrate > 0) "${audio.averageBitrate / 1000} kbps" else ""
                val label = if (isArabic) "صوت فقط ($bitrateKbps)" else "Audio only ($bitrateKbps)"
                options.add(
                    StreamOption(
                        label = label.trim(),
                        url = audio.content,
                        isAudioOnly = true,
                        isVideoOnly = false,
                        resolution = null,
                        format = audio.format?.name
                    )
                )
            }

            // Select default: 720p or highest progressive stream, or first option
            val defaultStream = options.firstOrNull {
                !it.isAudioOnly && !it.isVideoOnly && (it.resolution?.contains("720") == true || it.resolution?.contains("480") == true || it.resolution?.contains("360") == true)
            } ?: options.firstOrNull { !it.isAudioOnly && !it.isVideoOnly }
              ?: options.firstOrNull { !it.isAudioOnly }
              ?: options.firstOrNull()

            val thumb = streamInfo.thumbnails?.firstOrNull()?.url
            val details = VideoDetails(
                id = streamInfo.url.substringAfterLast("v=", streamInfo.url).substringAfterLast("/", streamInfo.url),
                url = streamInfo.url,
                title = streamInfo.name ?: "",
                uploaderName = streamInfo.uploaderName ?: "",
                uploaderAvatarUrl = streamInfo.uploaderAvatars?.firstOrNull()?.url ?: thumb,
                description = streamInfo.description?.content,
                durationSeconds = streamInfo.duration,
                formattedDuration = formatDuration(streamInfo.duration),
                viewCount = streamInfo.viewCount,
                formattedViews = formatViews(streamInfo.viewCount, isArabic),
                streamOptions = options,
                defaultStreamOption = defaultStream
            )
            Result.success(details)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        fun formatDuration(durationSeconds: Long): String {
            if (durationSeconds <= 0) return "0:00"
            val hours = durationSeconds / 3600
            val minutes = (durationSeconds % 3600) / 60
            val seconds = durationSeconds % 60
            return if (hours > 0) {
                String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.US, "%d:%02d", minutes, seconds)
            }
        }

        fun formatViews(views: Long, isArabic: Boolean): String {
            if (views < 0) return ""
            return when {
                views >= 1_000_000_000 -> {
                    val count = String.format(Locale.US, "%.1f", views / 1_000_000_000.0).trimEnd('0').trimEnd('.')
                    if (isArabic) "$count مليار" else "${count}B"
                }
                views >= 1_000_000 -> {
                    val count = String.format(Locale.US, "%.1f", views / 1_000_000.0).trimEnd('0').trimEnd('.')
                    if (isArabic) "$count مليون" else "${count}M"
                }
                views >= 1_000 -> {
                    val count = String.format(Locale.US, "%.1f", views / 1_000.0).trimEnd('0').trimEnd('.')
                    if (isArabic) "$count ألف" else "${count}K"
                }
                else -> views.toString()
            }
        }
    }
}
