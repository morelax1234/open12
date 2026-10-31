package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.data.preferences.PreferencesRepository
import com.example.extractor.OkHttpDownloader
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor

class OpenPipeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        initNewPipe()
        createNotificationChannel()
    }

    private fun initNewPipe() {
        val downloader = OkHttpDownloader.getInstance()
        val prefs = PreferencesRepository.getInstance(this)
        val langPref = prefs.languageFlow.value
        val isAr = prefs.isEffectiveArabic(langPref)

        val langCode = if (isAr) "ar" else "en"
        val countryCode = if (isAr) "SA" else "US"

        NewPipe.init(downloader, Localization(langCode, countryCode), ContentCountry(countryCode))
        YoutubeStreamExtractor.setFetchIosClient(true)
        YoutubeParsingHelper.setConsentAccepted(true)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "openpipe_playback"
    }
}
