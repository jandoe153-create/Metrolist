/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ruangdengar

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.metrolist.music.recognition.DecodedAudio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Decode track audio dari file video (mp4) jadi PCM 16-bit mono,
 * pengganti `ffmpeg -vn` di prototype. Hasilnya siap masuk
 * AudioResampler → VibraSignature → Shazam.
 */
object VideoAudioDecoder {
    private const val TAG = "VideoAudioDecoder"
    private const val TIMEOUT_US = 10_000L

    /**
     * @param startSec  detik mulai (prototype pakai -ss 1)
     * @param maxSec    durasi maksimum yang di-decode (prototype pakai -t 12)
     */
    suspend fun decode(
        videoFile: File,
        startSec: Int = 1,
        maxSec: Int = 12,
    ): DecodedAudio = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(videoFile.absolutePath)
            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format = f
                    break
                }
            }
            if (trackIndex < 0 || format == null) {
                throw TikTokException("Video ini tidak punya track audio.")
            }
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            extractor.selectTrack(trackIndex)
            extractor.seekTo(startSec * 1_000_000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val pcmOut = ByteArrayOutputStream()
            var outSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var outChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val maxBytesPerSecond = { outSampleRate * outChannels * 2 }
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val inBuf = codec.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(inBuf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outIndex >= 0 -> {
                        if (info.size > 0) {
                            val outBuf = codec.getOutputBuffer(outIndex)!!
                            val chunk = ByteArray(info.size)
                            outBuf.get(chunk)
                            pcmOut.write(chunk)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        } else if (pcmOut.size() >= maxSec * maxBytesPerSecond()) {
                            outputDone = true
                        }
                    }
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = codec.outputFormat
                        outSampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        outChannels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }
            }

            var data = pcmOut.toByteArray()
            if (data.isEmpty()) throw TikTokException("Audio dari video kosong.")
            if (data.size % 2 != 0) data = data.copyOf(data.size - 1)
            if (outChannels > 1) {
                data = downmixToMono(data, outChannels)
                outChannels = 1
            }
            Timber.tag(TAG).d("Decoded %d bytes PCM, %dHz mono", data.size, outSampleRate)
            DecodedAudio(
                data = data,
                channelCount = outChannels,
                sampleRate = outSampleRate,
                pcmEncoding = AudioFormat.ENCODING_PCM_16BIT,
            )
        } finally {
            try {
                codec?.stop()
                codec?.release()
            } catch (_: Exception) {
            }
            extractor.release()
        }
    }

    private fun downmixToMono(interleaved: ByteArray, channels: Int): ByteArray {
        val frameCount = interleaved.size / (2 * channels)
        val out = ByteArray(frameCount * 2)
        var i = 0
        for (frame in 0 until frameCount) {
            var acc = 0
            for (ch in 0 until channels) {
                val idx = (frame * channels + ch) * 2
                val sample = ((interleaved[idx + 1].toInt() shl 8) or (interleaved[idx].toInt() and 0xFF)).toShort()
                acc += sample
            }
            val mono = (acc / channels).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            out[i++] = (mono and 0xFF).toByte()
            out[i++] = ((mono shr 8) and 0xFF).toByte()
        }
        return out
    }
}
