/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ruangdengar

import android.util.Base64
import com.metrolist.music.recognition.DecodedAudio
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import timber.log.Timber
import java.io.ByteArrayOutputStream

data class GeminiGuess(
    val keyword: String,
    val confidence: Float,
)

/**
 * Tier 3 identifikasi: kirim potongan audio ke Gemini multimodal,
 * hasilnya BUKAN 1 match pasti melainkan daftar opsi keyword pencarian
 * (paling yakin duluan) yang tinggal dilempar ke search Metrolist.
 *
 * Model WAJIB gemini-3.5-flash (keputusan final Indra, terverifikasi ada
 * di API). Endpoint sering 503 "high demand" — makanya retry + rotasi
 * key di sini bukan opsional.
 */
object GeminiMusicIdentifier {
    private const val TAG = "GeminiMusicId"
    private const val MODEL = "gemini-3.5-flash"
    private const val ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"
    private const val MAX_ATTEMPTS = 12

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client by lazy {
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 120_000
                connectTimeoutMillis = 20_000
                socketTimeoutMillis = 120_000
            }
        }
    }

    private class TransientGeminiError(message: String) : Exception(message)

    suspend fun identify(
        audio: DecodedAudio,
        hint: String?,
        keys: List<String>,
    ): List<GeminiGuess> = withContext(Dispatchers.IO) {
        require(keys.isNotEmpty()) { "keys kosong" }
        val body = requestBody(audio, hint)

        var lastError: Exception? = null
        val attempts = minOf(MAX_ATTEMPTS, maxOf(4, keys.size * 2))
        for (attempt in 0 until attempts) {
            val key = keys[attempt % keys.size]
            try {
                return@withContext call(key, body)
            } catch (e: TransientGeminiError) {
                lastError = e
                Timber.tag(TAG).w("attempt ${attempt + 1}/$attempts gagal: ${e.message}")
                // backoff pendek, membesar tiap putaran penuh rotasi key
                delay(700L + 500L * (attempt / keys.size.coerceAtLeast(1)))
            }
        }
        Timber.tag(TAG).e(lastError, "semua percobaan Gemini gagal")
        throw TikTokException(
            "Gemini lagi penuh (503) di semua key setelah $attempts percobaan. Coba lagi bentar.",
        )
    }

    private suspend fun call(key: String, body: String): List<GeminiGuess> {
        val response = client.post(ENDPOINT) {
            contentType(ContentType.Application.Json)
            header("x-goog-api-key", key)
            setBody(body)
        }
        val text = response.bodyAsText()
        when {
            response.status.value == 429 || response.status.value >= 500 ->
                throw TransientGeminiError("HTTP ${response.status.value}")
            response.status.value == 400 || response.status.value == 403 ->
                // key invalid/expired — lanjut rotasi ke key berikutnya
                throw TransientGeminiError("key ditolak (HTTP ${response.status.value})")
            response.status.value !in 200..299 ->
                throw TikTokException("Gemini error HTTP ${response.status.value}")
        }
        return parseGuesses(text)
            ?: throw TransientGeminiError("respon Gemini tidak berformat JSON opsi")
    }

    private fun parseGuesses(responseBody: String): List<GeminiGuess>? {
        return try {
            val root = json.parseToJsonElement(responseBody).jsonObject
            var content = root["candidates"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("content")?.jsonObject
                ?.get("parts")?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                ?: return null
            content = content.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()
            val parsed = json.parseToJsonElement(content)
            val arr = when {
                parsed is kotlinx.serialization.json.JsonObject -> parsed["options"]?.jsonArray
                else -> parsed.jsonArray
            } ?: return null
            arr.mapNotNull { el ->
                val obj = el.jsonObject
                val kw = obj["keyword"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                GeminiGuess(
                    keyword = kw,
                    confidence = obj["confidence"]?.jsonPrimitive?.floatOrNull?.coerceIn(0f, 1f)
                        ?: 0.5f,
                )
            }.sortedByDescending { it.confidence }.take(4).ifEmpty { null }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "gagal parse respon Gemini")
            null
        }
    }

    private fun requestBody(audio: DecodedAudio, hint: String?): String {
        val wav = toWav(audio)
        val b64 = Base64.encodeToString(wav, Base64.NO_WRAP)
        val prompt = buildString {
            append("Listen carefully to this audio clip extracted from a TikTok video. ")
            append("It is very likely a sped-up, slowed, remixed, phonk, or funk/montagem edit ")
            append("of an original song, possibly with non-English lyrics (Russian, Portuguese, etc). ")
            if (!hint.isNullOrBlank()) {
                append("Context from the TikTok post: ").append(hint.take(300)).append(". ")
            }
            append("Identify the most likely ORIGINAL song(s). If you can hear lyrics, transcribe a ")
            append("distinctive line (keep the original language/script) and use it as one keyword. ")
            append("Respond ONLY with JSON in this exact shape: ")
            append("""{"options":[{"keyword":"<YouTube Music search query, e.g. song title + artist>","confidence":0.9}]} """)
            append("with 1 to 4 options sorted by confidence descending. No other text.")
        }
        return buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", prompt) })
                        add(buildJsonObject {
                            putJsonObject("inlineData") {
                                put("mimeType", "audio/wav")
                                put("data", b64)
                            }
                        })
                    })
                })
            })
            putJsonObject("generationConfig") {
                put("temperature", 0.3)
                put("responseMimeType", "application/json")
            }
        }.toString()
    }

    /** PCM 16-bit mono → WAV (header 44 byte + data). */
    private fun toWav(audio: DecodedAudio): ByteArray {
        val dataSize = audio.data.size
        val sampleRate = audio.sampleRate
        val channels = audio.channelCount
        val byteRate = sampleRate * channels * 2
        val out = ByteArrayOutputStream(44 + dataSize)
        fun str(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        fun le32(v: Int) {
            out.write(v and 0xFF); out.write((v shr 8) and 0xFF)
            out.write((v shr 16) and 0xFF); out.write((v shr 24) and 0xFF)
        }
        fun le16(v: Int) {
            out.write(v and 0xFF); out.write((v shr 8) and 0xFF)
        }
        str("RIFF"); le32(36 + dataSize); str("WAVE")
        str("fmt "); le32(16); le16(1); le16(channels)
        le32(sampleRate); le32(byteRate); le16(channels * 2); le16(16)
        str("data"); le32(dataSize)
        out.write(audio.data)
        return out.toByteArray()
    }
}
