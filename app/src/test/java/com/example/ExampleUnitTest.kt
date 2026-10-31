package com.example

import com.example.data.preferences.AppLanguage
import com.example.data.preferences.AppTheme
import com.example.data.repository.YouTubeRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun formatDuration_isCorrect() {
        assertEquals("0:00", YouTubeRepository.formatDuration(0))
        assertEquals("0:45", YouTubeRepository.formatDuration(45))
        assertEquals("3:25", YouTubeRepository.formatDuration(205))
        assertEquals("1:02:15", YouTubeRepository.formatDuration(3735))
    }

    @Test
    fun formatViews_isCorrect() {
        assertEquals("950", YouTubeRepository.formatViews(950, isArabic = false))
        assertEquals("12K", YouTubeRepository.formatViews(12000, isArabic = false))
        assertEquals("1.5M", YouTubeRepository.formatViews(1500000, isArabic = false))
        assertEquals("1.5 مليون", YouTubeRepository.formatViews(1500000, isArabic = true))
        assertEquals("1.5B", YouTubeRepository.formatViews(1500000000, isArabic = false))
        assertEquals("1.5 مليار", YouTubeRepository.formatViews(1500000000, isArabic = true))
    }

    @Test
    fun appLanguage_values() {
        assertEquals("ar", AppLanguage.ARABIC.code)
        assertEquals("en", AppLanguage.ENGLISH.code)
        assertEquals("", AppLanguage.SYSTEM.code)
    }
}
