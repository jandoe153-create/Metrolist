/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ruangdengar

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.contentLength
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.contentOrNull
import timber.log.Timber
import java.io.File

data class TikTokVideo(
    val id: String,
    val title: String,
    val uploader: String,
    val duration: Int,
    val likes: Long,
    val comments: Long,
    val thumbnail: String?,
    val videoUrl: String,
    val pageUrl: String,
    /** Metadata sound TikTok (tier 1 identifikasi) — null kalau ga ada di JSON. */
    val musicTitle: String? = null,
    val musicAuthor: String? = null,
    /** true = "original sound" kreator, bukan lagu beneran. */
    val musicIsOriginal: Boolean = false,
)

class TikTokException(message: String) : Exception(message)

/**
 * Extractor TikTok native (port konsep dari yt-dlp):
 * ambil halaman video, parse JSON __UNIVERSAL_DATA_FOR_REHYDRATION__,
 * lalu unduh video pakai cookies sesi yang sama.
 */
object TikTokExtractor {
    private const val TAG = "TikTokExtractor"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    val URL_REGEX = Regex("""https?://(www\.|vt\.|vm\.|m\.)?tiktok\.com/\S+""", RegexOption.IGNORE_CASE)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client by lazy {
        HttpClient(CIO) {
            followRedirects = true
            install(HttpCookies) { storage = AcceptAllCookiesStorage() }
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 20_000
                socketTimeoutMillis = 30_000
            }
        }
    }

    fun isTikTokUrl(url: String): Boolean = URL_REGEX.matches(url.trim())

    fun findUrl(text: String): String? = URL_REGEX.find(text)?.value

    suspend fun resolve(rawUrl: String): TikTokVideo = withContext(Dispatchers.IO) {
        val response = client.get(rawUrl.trim()) {
            header(HttpHeaders.UserAgent, USER_AGENT)
            header(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9,id;q=0.8")
        }
        val finalUrl = response.call.request.url.toString()
        val html = response.bodyAsText()

        val jsonBlob = Regex(
            """<script id="__UNIVERSAL_DATA_FOR_REHYDRATION__"[^>]*>(.+?)</script>""",
            RegexOption.DOT_MATCHES_ALL,
        ).find(html)?.groupValues?.get(1)
            ?: throw TikTokException("TikTok tidak mengirim data video. Coba lagi, atau linknya privat.")

        val root = try {
            json.parseToJsonElement(jsonBlob).jsonObject
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to parse universal data")
            throw TikTokException("Data TikTok tidak bisa dibaca (format berubah).")
        }

        val scope = root["__DEFAULT_SCOPE__"]?.jsonObject
            ?: throw TikTokException("Struktur data TikTok berubah (__DEFAULT_SCOPE__).")
        val detail = scope["webapp.video-detail"]?.jsonObject
            ?: throw TikTokException("Video tidak ketemu. Mungkin dihapus atau khusus follower.")

        val statusCode = detail["statusCode"]?.jsonPrimitive?.intOrNull ?: 0
        if (statusCode != 0) {
            val msg = detail["statusMsg"]?.jsonPrimitive?.contentOrNull ?: "status $statusCode"
            throw TikTokException("TikTok menolak: $msg")
        }

        val item = detail["itemInfo"]?.jsonObject?.get("itemStruct")?.jsonObject
            ?: throw TikTokException("Detail video kosong. Mungkin ini foto/slide, bukan video.")

        val video = item["video"]?.jsonObject
            ?: throw TikTokException("Ini bukan post video (mungkin carousel foto).")
        val videoUrl = video["playAddr"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?: video["downloadAddr"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: throw TikTokException("URL video tidak ketemu di data TikTok.")

        val author = item["author"]?.jsonObject
        val stats = item["stats"]?.jsonObject
        val music = item["music"]?.jsonObject

        TikTokVideo(
            id = item["id"]?.jsonPrimitive?.contentOrNull ?: "tiktok",
            title = item["desc"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: "(tanpa judul)",
            uploader = author?.get("uniqueId")?.jsonPrimitive?.contentOrNull
                ?: author?.get("nickname")?.jsonPrimitive?.contentOrNull ?: "?",
            duration = video["duration"]?.jsonPrimitive?.intOrNull ?: 0,
            likes = stats?.get("diggCount")?.jsonPrimitive?.longOrNull ?: 0,
            comments = stats?.get("commentCount")?.jsonPrimitive?.longOrNull ?: 0,
            thumbnail = video["cover"]?.jsonPrimitive?.contentOrNull
                ?: video["originCover"]?.jsonPrimitive?.contentOrNull,
            videoUrl = videoUrl,
            pageUrl = finalUrl,
            musicTitle = music?.get("title")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
            musicAuthor = music?.get("authorName")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
            musicIsOriginal = music?.get("original")?.jsonPrimitive?.booleanOrNull ?: false,
        )
    }

    suspend fun download(
        video: TikTokVideo,
        destFile: File,
        onProgress: (Int) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        destFile.parentFile?.mkdirs()
        client.prepareGet(video.videoUrl) {
            header(HttpHeaders.UserAgent, USER_AGENT)
            header(HttpHeaders.Referrer, "https://www.tiktok.com/")
            header(HttpHeaders.Accept, "*/*")
        }.execute { response ->
            if (response.status.value !in 200..299) {
                throw TikTokException("Server video menolak (HTTP ${response.status.value}).")
            }
            val total = response.contentLength() ?: 0L
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(64 * 1024)
            var received = 0L
            destFile.outputStream().use { out ->
                while (true) {
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read == -1) break
                    if (read > 0) {
                        out.write(buffer, 0, read)
                        received += read
                        if (total > 0) onProgress(((received * 100) / total).toInt().coerceIn(0, 100))
                    }
                }
            }
        }
        if (!destFile.exists() || destFile.length() == 0L) {
            throw TikTokException("File video kosong setelah diunduh.")
        }
        onProgress(100)
        destFile
    }
}
