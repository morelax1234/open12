package com.example.extractor

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request as OkRequest
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response as OkResponse
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class OkHttpDownloader private constructor(private val client: OkHttpClient) : Downloader() {

    private val cookieMap = ConcurrentHashMap<String, String>()

    init {
        // Seed default consent and language cookies for YouTube
        cookieMap["youtube.com"] = "PREF=f6=40000000&hl=en; SOCS=CAI"
        cookieMap["www.youtube.com"] = "PREF=f6=40000000&hl=en; SOCS=CAI"
    }

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBuilder = OkRequest.Builder().url(url)

        var hasUserAgent = false
        var hasCookie = false

        headers?.forEach { (name, values) ->
            values.forEach { value ->
                requestBuilder.addHeader(name, value)
                if (name.equals("User-Agent", ignoreCase = true)) {
                    hasUserAgent = true
                }
                if (name.equals("Cookie", ignoreCase = true)) {
                    hasCookie = true
                }
            }
        }

        if (!hasUserAgent) {
            requestBuilder.header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
        }

        if (!hasCookie) {
            val domain = url.toHttpUrlOrNull()?.host ?: ""
            val cookies = cookieMap[domain] ?: cookieMap["youtube.com"]
            if (!cookies.isNullOrEmpty()) {
                requestBuilder.header("Cookie", cookies)
            }
        }

        val body = when {
            httpMethod.equals("POST", ignoreCase = true) -> {
                val contentType = headers?.entries
                    ?.firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
                    ?.value?.firstOrNull() ?: "application/json"
                (dataToSend ?: ByteArray(0)).toRequestBody(contentType.toMediaTypeOrNull())
            }
            else -> null
        }

        requestBuilder.method(httpMethod, body)

        val okCall = client.newCall(requestBuilder.build())
        val okResponse: OkResponse = okCall.execute()

        // Capture and update session cookies
        val setCookies = okResponse.headers("Set-Cookie")
        if (setCookies.isNotEmpty()) {
            val domain = url.toHttpUrlOrNull()?.host ?: "youtube.com"
            val existing = cookieMap[domain] ?: cookieMap["youtube.com"] ?: ""
            val cookiePairs = existing.split(";")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toMutableList()

            setCookies.forEach { sc ->
                val cookiePart = sc.substringBefore(";")
                val name = cookiePart.substringBefore("=")
                cookiePairs.removeAll { it.startsWith("$name=") }
                cookiePairs.add(cookiePart)
            }
            val newCookieStr = cookiePairs.joinToString("; ")
            cookieMap[domain] = newCookieStr
            cookieMap["youtube.com"] = newCookieStr
        }

        val responseCode = okResponse.code
        val responseMessage = okResponse.message
        val responseHeaders = okResponse.headers.toMultimap()
        val responseBody = okResponse.body?.string() ?: ""
        val latestUrl = okResponse.request.url.toString()

        if (responseCode == 429) {
            okResponse.close()
            throw ReCaptchaException("reCAPTCHA requested", url)
        }

        return Response(responseCode, responseMessage, responseHeaders, responseBody, latestUrl)
    }

    companion object {
        @Volatile
        private var instance: OkHttpDownloader? = null

        fun getInstance(): OkHttpDownloader {
            return instance ?: synchronized(this) {
                instance ?: OkHttpDownloader(
                    OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .followRedirects(true)
                        .build()
                ).also { instance = it }
            }
        }
    }
}
