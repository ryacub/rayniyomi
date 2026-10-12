package aniyomi.util

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.source.service.SourcePreferences.DataSaver.BANDWIDTH_HERO
import eu.kanade.domain.source.service.SourcePreferences.DataSaver.NONE
import eu.kanade.domain.source.service.SourcePreferences.DataSaver.RESMUSH_IT
import eu.kanade.domain.source.service.SourcePreferences.DataSaver.WSRV_NL
import eu.kanade.tachiyomi.di.appGraph
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.MangaSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers.Companion.headersOf
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import tachiyomi.core.common.preference.Preference
import java.io.IOException
import java.net.URLEncoder

interface DataSaver {

    fun compress(imageUrl: String): String

    companion object {
        val NoOp = object : DataSaver {
            override fun compress(imageUrl: String): String {
                return imageUrl
            }
        }
    }
}

fun DataSaver(source: MangaSource, preferences: SourcePreferences): DataSaver {
    val dataSaver = preferences.dataSaver().get()
    if (dataSaver != NONE && source.id.toString() in preferences.dataSaverExcludedSources().get()) {
        return DataSaver.NoOp
    }
    return when (dataSaver) {
        NONE -> DataSaver.NoOp
        BANDWIDTH_HERO -> BandwidthHeroDataSaver(preferences)
        WSRV_NL -> WsrvNlDataSaver(preferences)
        RESMUSH_IT -> ReSmushItDataSaver(preferences)
    }
}

private class BandwidthHeroDataSaver(preferences: SourcePreferences) : DataSaver {
    private val dataSavedServer = preferences.dataSaverServer().get().trimEnd('/')

    private val ignoreJpg = preferences.dataSaverIgnoreJpeg().get()
    private val ignoreGif = preferences.dataSaverIgnoreGif().get()

    private val format = preferences.dataSaverImageFormatJpeg().toIntRepresentation()
    private val quality = preferences.dataSaverImageQuality().get()
    private val colorBW = preferences.dataSaverColorBW().toIntRepresentation()

    override fun compress(imageUrl: String): String {
        return if (dataSavedServer.isNotBlank() && !imageUrl.contains(dataSavedServer)) {
            imageUrl.compressUnlessIgnored(ignoreJpg, ignoreGif, ::getUrl)
        } else {
            imageUrl
        }
    }

    private fun getUrl(imageUrl: String): String {
        val escapedUrl = URLEncoder.encode(imageUrl, "utf-8")
        // Network Request sent for the Bandwidth Hero Proxy server
        return "$dataSavedServer/?jpg=$format&l=$quality&bw=$colorBW&url=$escapedUrl"
    }

    private fun Preference<Boolean>.toIntRepresentation() = if (get()) "1" else "0"
}

private class WsrvNlDataSaver(preferences: SourcePreferences) : DataSaver {
    private val ignoreJpg = preferences.dataSaverIgnoreJpeg().get()
    private val ignoreGif = preferences.dataSaverIgnoreGif().get()

    private val format = preferences.dataSaverImageFormatJpeg().get()
    private val quality = preferences.dataSaverImageQuality().get()

    override fun compress(imageUrl: String): String {
        return imageUrl.compressUnlessIgnored(ignoreJpg, ignoreGif, ::getUrl)
    }

    private fun getUrl(imageUrl: String): String {
        // Network Request sent to wsrv
        return "https://wsrv.nl/?url=$imageUrl" + if (imageUrl.contains(".webp", true) ||
            imageUrl.contains(
                ".gif",
                true,
            )
        ) {
            if (!format) {
                // Preserve output image extension for animated images(.webp and .gif)
                "&q=$quality&n=-1"
            } else {
                // Do not preserve output Extension if User asked to convert into Jpeg
                "&output=jpg&q=$quality&n=-1"
            }
        } else {
            if (format) {
                "&output=jpg&q=$quality"
            } else {
                "&output=webp&q=$quality"
            }
        }
    }
}

private class ReSmushItDataSaver(preferences: SourcePreferences) : DataSaver {

    private val network: NetworkHelper by lazy { appGraph.networkHelper }

    private val client: OkHttpClient
        get() = network.client

    private val ignoreJpg = preferences.dataSaverIgnoreJpeg().get()
    private val ignoreGif = preferences.dataSaverIgnoreGif().get()

    private val quality = preferences.dataSaverImageQuality().get()

    override fun compress(imageUrl: String): String {
        return imageUrl.compressUnlessIgnored(ignoreJpg, ignoreGif, ::getUrl)
    }

    private fun getUrl(imageUrl: String): String {
        val url = "https://api.resmush.it/ws.php".toHttpUrl().newBuilder()
            .addQueryParameter("img", imageUrl)
            .addQueryParameter("qlty", quality.toString())
            .build()
        return try {
            client.newCall(
                GET(url, headersOf("Referer", "https://github.com/ryacub/rayniyomi")),
            ).execute().use { response ->
                if (!response.isSuccessful) return imageUrl
                val body = Json.parseToJsonElement(response.body.string()).jsonObject
                if (body["error"]?.jsonPrimitive?.intOrNull?.let { it != 0 } == true) return imageUrl
                body["dest"]?.jsonPrimitive?.takeIf { it.isString }?.contentOrNull
                    ?.takeIf { it.toHttpUrlOrNull() != null }
                    ?: imageUrl
            }
        } catch (e: IOException) {
            imageUrl
        } catch (e: IllegalArgumentException) {
            imageUrl
        }
    }
}

private inline fun String.compressUnlessIgnored(
    ignoreJpg: Boolean,
    ignoreGif: Boolean,
    compress: (String) -> String,
): String {
    return when {
        isJpg() && ignoreJpg -> this
        contains(".gif", true) && ignoreGif -> this
        else -> compress(this)
    }
}

private fun String.isJpg() = contains(".jpeg", true) || contains(".jpg", true)
