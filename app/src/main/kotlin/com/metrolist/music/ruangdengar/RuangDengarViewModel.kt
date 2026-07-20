/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ruangdengar

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.constants.OpenRouterApiKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.SongEntity
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.ExoDownloadService
import com.metrolist.music.recognition.AudioResampler
import com.metrolist.music.recognition.VibraSignature
import com.metrolist.music.utils.AiKeyRing
import com.metrolist.music.utils.dataStore
import com.metrolist.shazamkit.Shazam
import com.metrolist.shazamkit.models.RecognitionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

enum class RdMode { INPUT, RESOLVING, RESOLVED, DOWNLOADING, DOWNLOADED, IDENTIFYING, FOUND, GEMINI_PICK }

/** Sumber identifikasi — urutan tier: metadata TikTok → Shazam → Gemini. */
enum class RdSource { TIKTOK, SHAZAM, GEMINI }

data class RdSongResult(
    val source: RdSource,
    val coverUrl: String?,
    val song: SongItem,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RuangDengarViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) : ViewModel() {
    private val workDir = File(context.cacheDir, "ruang_dengar")

    val url = MutableStateFlow("")
    val autoBadge = MutableStateFlow(false)
    val mode = MutableStateFlow(RdMode.INPUT)
    val linkError = MutableStateFlow<String?>(null)
    val toast = MutableStateFlow<Pair<String, Boolean>?>(null) // msg to isError

    val video = MutableStateFlow<TikTokVideo?>(null)
    val downloadPct = MutableStateFlow(0)
    val downloadError = MutableStateFlow<String?>(null)
    val downloadedFile = MutableStateFlow<File?>(null)
    val downloadedSizeMb = MutableStateFlow(0.0)
    val savedToGallery = MutableStateFlow(false)

    /** 0 = belum mulai; 1..5 = step yang lagi jalan; 6 = semua beres */
    val identifyStep = MutableStateFlow(0)
    val result = MutableStateFlow<RdSongResult?>(null)

    /** Opsi keyword hasil Gemini (mode GEMINI_PICK) — tap untuk lempar ke pencarian. */
    val geminiGuesses = MutableStateFlow<List<GeminiGuess>>(emptyList())

    val fullDownloadStarted = MutableStateFlow(false)

    val isLiked: StateFlow<Boolean> = result
        .flatMapLatest { r ->
            if (r == null) flowOf(false)
            else database.song(r.song.id).map { it?.song?.liked == true }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    private var job: Job? = null

    fun showToast(msg: String, bad: Boolean = false) {
        toast.value = msg to bad
    }

    fun consumeToast() {
        toast.value = null
    }

    fun setUrl(value: String) {
        url.value = value
        linkError.value = null
        if (autoBadge.value) autoBadge.value = false
    }

    /** Dipanggil dari share intent: auto-paste + auto-resolve + badge AUTO. */
    fun onSharedUrl(sharedUrl: String) {
        if (mode.value != RdMode.INPUT && url.value == sharedUrl) return
        reset()
        url.value = sharedUrl
        autoBadge.value = true
        resolve()
    }

    fun resolve() {
        val target = url.value.trim()
        if (!TikTokExtractor.isTikTokUrl(target)) {
            linkError.value = "Itu bukan link TikTok. Formatnya harus tiktok.com / vt.tiktok.com / vm.tiktok.com."
            return
        }
        linkError.value = null
        mode.value = RdMode.RESOLVING
        job?.cancel()
        job = viewModelScope.launch {
            try {
                video.value = TikTokExtractor.resolve(target)
                mode.value = RdMode.RESOLVED
            } catch (e: Exception) {
                Timber.e(e, "resolve failed")
                mode.value = RdMode.INPUT
                linkError.value = friendly(e, "Gagal baca link")
            }
        }
    }

    fun download() {
        val v = video.value ?: return
        mode.value = RdMode.DOWNLOADING
        downloadPct.value = 0
        downloadError.value = null
        job?.cancel()
        job = viewModelScope.launch {
            try {
                val dest = File(workDir, "${v.id}.mp4")
                val file = TikTokExtractor.download(v, dest) { pct ->
                    downloadPct.value = pct
                }
                downloadedFile.value = file
                downloadedSizeMb.value = (file.length() / 104857.6).toInt() / 10.0
                mode.value = RdMode.DOWNLOADED
            } catch (e: Exception) {
                Timber.e(e, "download failed")
                downloadError.value = friendly(e, "Gagal unduh video")
                mode.value = RdMode.RESOLVED
            }
        }
    }

    fun saveToGallery() {
        val file = downloadedFile.value ?: return
        val v = video.value
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val name = "RuangDengar_${v?.id ?: System.currentTimeMillis()}.mp4"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, name)
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/RuangDengar")
                    }
                    val uri = context.contentResolver.insert(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values,
                    ) ?: throw IllegalStateException("MediaStore menolak")
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        file.inputStream().use { it.copyTo(out) }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "RuangDengar")
                    dir.mkdirs()
                    file.copyTo(File(dir, name), overwrite = true)
                }
                savedToGallery.value = true
                showToast("Tersimpan ke Galeri · Movies/RuangDengar")
            } catch (e: Exception) {
                Timber.e(e, "saveToGallery failed")
                showToast("Gagal simpan ke galeri: ${e.message}", bad = true)
            }
        }
    }

    /** Alur auto 3 tier: metadata TikTok → Shazam → Gemini. */
    fun identify() = runIdentify(force = null)

    /** Override manual "Lempar ke Shazam" — bisa dari hasil apa pun. */
    fun throwToShazam() = runIdentify(force = RdSource.SHAZAM)

    /** Override manual "Lempar ke Gemini" — bisa dari hasil apa pun. */
    fun throwToGemini() = runIdentify(force = RdSource.GEMINI)

    private fun runIdentify(force: RdSource?) {
        val file = downloadedFile.value ?: return
        val v = video.value
        mode.value = RdMode.IDENTIFYING
        result.value = null
        geminiGuesses.value = emptyList()
        identifyStep.value = 1
        job?.cancel()
        job = viewModelScope.launch {
            try {
                // ── TIER 1: metadata musik TikTok (gratis, udah ada di JSON) ──
                if (force == null) {
                    val found = tryTikTokMetadata(v)
                    if (found != null) {
                        finishFound(found)
                        return@launch
                    }
                }

                // ── TIER 2: Shazam (multi-window: awal/tengah/akhir video) ──
                if (force == null || force == RdSource.SHAZAM) {
                    identifyStep.value = 2
                    val shazamResult = shazamMultiWindow(file, v?.duration ?: 0)
                    if (shazamResult != null) {
                        identifyStep.value = 5
                        val song = searchYtMusic("${shazamResult.title} ${shazamResult.artist}")
                        if (song != null) {
                            finishFound(
                                RdSongResult(
                                    source = RdSource.SHAZAM,
                                    coverUrl = shazamResult.coverArtUrl ?: song.thumbnail,
                                    song = song,
                                ),
                            )
                            return@launch
                        }
                        if (force == RdSource.SHAZAM) {
                            throw TikTokException("\"${shazamResult.title}\" tidak ketemu di YT Music.")
                        }
                    } else if (force == RdSource.SHAZAM) {
                        throw TikTokException("Shazam tetap tidak mengenali lagunya. Coba lempar ke Gemini.")
                    }
                }

                // ── TIER 3: Gemini 3.5 Flash (jaring pengaman, hasil = opsi keyword) ──
                runGeminiTier(file, v)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.e(e, "identify failed")
                identifyStep.value = 0
                mode.value = RdMode.DOWNLOADED
                showToast(friendly(e, "Identifikasi gagal"), bad = true)
            }
        }
    }

    /** Tier 1: pakai judul sound dari TikTok kalau bukan original sound. */
    private suspend fun tryTikTokMetadata(v: TikTokVideo?): RdSongResult? {
        val title = v?.musicTitle?.trim().orEmpty()
        val usable = title.isNotBlank() &&
            !v!!.musicIsOriginal &&
            !title.startsWith("original sound", ignoreCase = true) &&
            !title.startsWith("suara asli", ignoreCase = true) &&
            !title.startsWith("son original", ignoreCase = true)
        if (!usable) return null
        identifyStep.value = 5
        val author = v.musicAuthor?.trim().orEmpty()
        val song = searchYtMusic(listOf(title, author).filter { it.isNotBlank() }.joinToString(" "))
            ?: return null // metadata ada tapi ga ketemu di YT Music → lanjut tier 2
        return RdSongResult(source = RdSource.TIKTOK, coverUrl = song.thumbnail, song = song)
    }

    /**
     * Tier 2: Shazam dicoba dari beberapa titik video (intro TikTok sering
     * voiceover / musik masuk belakangan) — match pertama menang.
     */
    private suspend fun shazamMultiWindow(file: File, durationSec: Int): RecognitionResult? {
        val starts = buildList {
            add(1)
            if (durationSec > 26) add(durationSec / 2 - 6)
            if (durationSec > 40) add(durationSec - 14)
        }.map { it.coerceAtLeast(0) }.distinct()

        for ((i, startSec) in starts.withIndex()) {
            try {
                val decoded = VideoAudioDecoder.decode(file, startSec = startSec, maxSec = 12)
                identifyStep.value = 3
                val resampled = AudioResampler
                    .resample(decoded, VibraSignature.REQUIRED_SAMPLE_RATE)
                    .getOrElse { throw TikTokException("Gagal proses audio: ${it.message}") }
                val signature = withContext(Dispatchers.Default) {
                    VibraSignature.fromI16(resampled.data)
                }
                val sampleMs = (resampled.data.size / 2) * 1000L / VibraSignature.REQUIRED_SAMPLE_RATE
                val match = Shazam.recognize(signature, sampleMs).getOrNull()
                if (match != null) return match
                Timber.d("Shazam window %d (mulai %ds) tanpa match", i + 1, startSec)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.w(e, "Shazam window %d error", i + 1)
            }
        }
        return null
    }

    /** Tier 3: Gemini dengerin audionya, hasil = daftar opsi keyword klik-able. */
    private suspend fun runGeminiTier(file: File, v: TikTokVideo?) {
        identifyStep.value = 4
        val rawKeys = context.dataStore.data.first()[OpenRouterApiKey] ?: ""
        val keys = AiKeyRing.parse(rawKeys)
        if (keys.isEmpty()) {
            throw TikTokException(
                "Shazam nyerah & API key Gemini belum diisi. Isi di Profil → Setting → Terjemahan lirik AI.",
            )
        }
        // Audio lebih panjang dari window Shazam biar Gemini kebagian lirik
        val decoded = VideoAudioDecoder.decode(
            file,
            startSec = 0,
            maxSec = (v?.duration ?: 60).coerceIn(10, 60),
        )
        val slim = AudioResampler.resample(decoded, 16_000)
            .getOrElse { throw TikTokException("Gagal proses audio buat Gemini: ${it.message}") }
        val hint = buildString {
            v?.title?.takeIf { it.isNotBlank() && it != "(tanpa judul)" }?.let { append("caption: $it") }
            v?.musicTitle?.takeIf { it.isNotBlank() }?.let {
                if (isNotEmpty()) append("; ")
                append("sound label: $it")
                v.musicAuthor?.takeIf { a -> a.isNotBlank() }?.let { a -> append(" — $a") }
            }
        }.ifBlank { null }
        val guesses = GeminiMusicIdentifier.identify(slim, hint, keys)
        identifyStep.value = 5
        geminiGuesses.value = guesses
        mode.value = RdMode.GEMINI_PICK
    }

    private suspend fun searchYtMusic(query: String): SongItem? =
        YouTube.search(query, YouTube.SearchFilter.FILTER_SONG)
            .getOrNull()
            ?.items
            ?.filterIsInstance<SongItem>()
            ?.firstOrNull()

    private fun finishFound(r: RdSongResult) {
        identifyStep.value = 6
        result.value = r
        mode.value = RdMode.FOUND
    }

    fun toggleFavorite() {
        val song = result.value?.song ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val existing = database.song(song.id).firstOrNull()
            database.transaction {
                if (existing == null) {
                    insert(song.toMediaMetadata(), SongEntity::toggleLike)
                } else {
                    update(existing.song.toggleLike())
                }
            }
        }
    }

    fun downloadFullSong() {
        val song = result.value?.song ?: return
        database.transaction {
            insert(song.toMediaMetadata())
        }
        val downloadRequest = DownloadRequest
            .Builder(song.id, song.id.toUri())
            .setCustomCacheKey(song.id)
            .setData(song.title.toByteArray())
            .build()
        DownloadService.sendAddDownload(
            context,
            ExoDownloadService::class.java,
            downloadRequest,
            false,
        )
        fullDownloadStarted.value = true
        showToast("Lagu masuk antrian unduhan Pustaka")
    }

    fun reset() {
        job?.cancel()
        url.value = ""
        autoBadge.value = false
        mode.value = RdMode.INPUT
        linkError.value = null
        video.value = null
        downloadPct.value = 0
        downloadError.value = null
        downloadedFile.value = null
        downloadedSizeMb.value = 0.0
        savedToGallery.value = false
        identifyStep.value = 0
        result.value = null
        geminiGuesses.value = emptyList()
        fullDownloadStarted.value = false
    }

    private fun friendly(e: Exception, prefix: String): String =
        if (e is TikTokException) e.message ?: prefix
        else "$prefix: ${e.message?.take(120) ?: "error tidak dikenal"}"

    override fun onCleared() {
        super.onCleared()
        workDir.deleteRecursively()
    }
}
