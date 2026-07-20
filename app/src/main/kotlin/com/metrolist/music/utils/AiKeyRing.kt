/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import java.util.concurrent.atomic.AtomicInteger

/**
 * Satu sumber API key bersama untuk semua fitur AI (terjemahan lirik +
 * identifikasi Gemini di Ruang Dengar).
 *
 * Key disimpan di preference API key yang sudah ada, satu key per baris
 * (koma juga diterima sebagai pemisah). Setting lama yang cuma berisi
 * satu key tetap valid tanpa migrasi apa pun.
 */
object AiKeyRing {
    private val cursor = AtomicInteger(0)

    fun parse(raw: String): List<String> =
        raw
            .split('\n', '\r', ',', ' ', '\t')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    /** Round-robin: setiap panggilan mengembalikan key berikutnya. */
    fun next(raw: String): String {
        val keys = parse(raw)
        if (keys.isEmpty()) return ""
        val i = cursor.getAndIncrement()
        return keys[((i % keys.size) + keys.size) % keys.size]
    }
}
