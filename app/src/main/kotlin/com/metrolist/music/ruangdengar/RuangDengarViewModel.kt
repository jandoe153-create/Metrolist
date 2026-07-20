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
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.SongEntity
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.ExoDownloadService
import com.metrolist.music.recognition.AudioResampler
import com.metrolist.music.recognition.VibraSignature
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

enum class RdMode { INPUT, RESOLVING, RESOLVED, DOWNLOADING, DOWNLOADED, IDENTIFYING, FOUND }

data class RdSongResult(
    val shazam: RecognitionResult,
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

    /** 0 = belum mulai; 1..4 = step yang lagi jalan; 5 = semua beres */
    val identifyStep = MutableStateFlow(0)
    val result = MutableStateFlow<RdSongResult?>(null)

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

    fun identify() {
        val file = downloadedFile.value ?: return
        mode.value = RdMode.IDENTIFYING
        identifyStep.value = 1
        job?.cancel()
        job = viewModelScope.launch {
            try {
                // Step 1: ekstrak audio dari video
                val decoded = VideoAudioDecoder.decode(file)

                // Step 2: bikin fingerprint
                identifyStep.value = 2
                val resampled = AudioResampler
                    .resample(decoded, VibraSignature.REQUIRED_SAMPLE_RATE)
                    .getOrElse { throw TikTokException("Gagal proses audio: ${it.message}") }
                val signature = withContext(Dispatchers.Default) {
                    VibraSignature.fromI16(resampled.data)
                }
                val sampleMs = (resampled.data.size / 2) * 1000L / VibraSignature.REQUIRED_SAMPLE_RATE

                // Step 3: cocokkan ke Shazam
                identifyStep.value = 3
                val shazamResult = Shazam.recognize(signature, sampleMs).getOrElse {
                    val msg = it.message ?: ""
                    if (msg.contains("No match", ignoreCase = true)) {
                        throw TikTokException("Lagu tidak dikenali Shazam. Mungkin original sound.")
                    }
                    throw TikTokException("Shazam error: $msg")
                }

                // Step 4: cari versi full di YT Music
                identifyStep.value = 4
                val query = "${shazamResult.title} ${shazamResult.artist}"
                val song = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG)
                    .getOrNull()
                    ?.items
                    ?.filterIsInstance<SongItem>()
                    ?.firstOrNull()
                    ?: throw TikTokException("\"${shazamResult.title}\" tidak ketemu di YT Music.")

                identifyStep.value = 5
                result.value = RdSongResult(shazamResult, song)
                mode.value = RdMode.FOUND
            } catch (e: Exception) {
                Timber.e(e, "identify failed")
                identifyStep.value = 0
                mode.value = RdMode.DOWNLOADED
                showToast(friendly(e, "Identifikasi gagal"), bad = true)
            }
        }
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
