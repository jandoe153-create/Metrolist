/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ruangdengar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.Player
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ruangdengar.RdMode
import com.metrolist.music.ruangdengar.RuangDengarViewModel
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

private fun fmtNum(n: Long): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0).replace(".0", "")
    n >= 1_000 -> "%.1fK".format(n / 1_000.0).replace(".0", "")
    else -> n.toString()
}

private fun fmtDur(s: Int): String {
    val m = s / 60
    val d = s % 60
    return if (m > 0) "$m:${d.toString().padStart(2, '0')}" else "${d}s"
}

@Composable
fun RuangDengarScreen(
    navController: NavController,
    sharedUrl: String? = null,
    viewModel: RuangDengarViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current
    val clipboard = LocalClipboardManager.current

    val url by viewModel.url.collectAsState()
    val autoBadge by viewModel.autoBadge.collectAsState()
    val mode by viewModel.mode.collectAsState()
    val linkError by viewModel.linkError.collectAsState()
    val video by viewModel.video.collectAsState()
    val downloadPct by viewModel.downloadPct.collectAsState()
    val downloadError by viewModel.downloadError.collectAsState()
    val downloadedSizeMb by viewModel.downloadedSizeMb.collectAsState()
    val identifyStep by viewModel.identifyStep.collectAsState()
    val result by viewModel.result.collectAsState()
    val isLiked by viewModel.isLiked.collectAsState()
    val fullDownloadStarted by viewModel.fullDownloadStarted.collectAsState()
    val toast by viewModel.toast.collectAsState()

    val isPlaying by (playerConnection?.isPlaying?.collectAsState()
        ?: remember { androidx.compose.runtime.mutableStateOf(false) })
    val nowPlaying by (playerConnection?.mediaMetadata?.collectAsState()
        ?: remember { androidx.compose.runtime.mutableStateOf(null) })
    val playbackState by (playerConnection?.playbackState?.collectAsState()
        ?: remember { androidx.compose.runtime.mutableStateOf(Player.STATE_IDLE) })

    val currentSong = result?.song
    val isCurrentSong = currentSong != null && nowPlaying?.id == currentSong.id
    val isPlayingThis = isCurrentSong && isPlaying
    val isBufferingThis = isCurrentSong && playbackState == Player.STATE_BUFFERING

    LaunchedEffect(sharedUrl) {
        if (!sharedUrl.isNullOrBlank()) viewModel.onSharedUrl(sharedUrl)
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2600)
            viewModel.consumeToast()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RdColors.Void),
    ) {
        RdAtmosphere()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 480.dp),
            ) {
                Spacer(Modifier.height(12.dp))
                RdMasthead(onBack = { navController.navigateUp() })

                // ── KARTU 1: LINK ─────────────────────────────
                RdCard {
                    RdCardLabel("Link Video TikTok")
                    Spacer(Modifier.height(16.dp))
                    RdLinkBox(
                        url = url,
                        onUrlChange = viewModel::setUrl,
                        autoBadge = autoBadge,
                        hasError = linkError != null,
                        enabled = mode == RdMode.INPUT || mode == RdMode.RESOLVED,
                        onPaste = {
                            clipboard.getText()?.text?.let { viewModel.setUrl(it.trim()) }
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    if (linkError != null) {
                        Text(
                            text = linkError.orEmpty(),
                            style = TextStyle(
                                fontFamily = RdFonts.Mono, fontSize = 11.sp,
                                color = RdColors.Rose, lineHeight = 16.sp,
                            ),
                            modifier = Modifier.padding(horizontal = 2.dp),
                        )
                    } else if (mode == RdMode.INPUT || mode == RdMode.RESOLVING) {
                        Text(
                            text = "Share dari app TikTok = otomatis. Atau tempel manual link vt.tiktok.com / www.tiktok.com.",
                            style = TextStyle(
                                fontFamily = RdFonts.Mono, fontSize = 10.5.sp,
                                color = RdColors.Dim, lineHeight = 17.sp,
                            ),
                            modifier = Modifier.padding(horizontal = 2.dp),
                        )
                    }
                    if (downloadError != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = downloadError.orEmpty(),
                            style = TextStyle(
                                fontFamily = RdFonts.Mono, fontSize = 11.sp,
                                color = RdColors.Rose, lineHeight = 16.sp,
                            ),
                        )
                    }

                    // preview video
                    AnimatedVisibility(visible = video != null) {
                        video?.let { v ->
                            Column {
                                Spacer(Modifier.height(14.dp))
                                RdVideoPreview(
                                    thumbnail = v.thumbnail,
                                    title = v.title,
                                    uploader = v.uploader,
                                    duration = v.duration,
                                    likes = v.likes,
                                    comments = v.comments,
                                )
                            }
                        }
                    }

                    // tombol utama
                    if (mode == RdMode.INPUT || mode == RdMode.RESOLVING ||
                        mode == RdMode.RESOLVED || mode == RdMode.DOWNLOADING
                    ) {
                        Spacer(Modifier.height(16.dp))
                        RdGoldButton(
                            text = when (mode) {
                                RdMode.RESOLVING -> "Membaca Link…"
                                RdMode.RESOLVED -> "Unduh Video"
                                RdMode.DOWNLOADING -> "Mengunduh…"
                                else -> "Cek Link"
                            },
                            enabled = mode == RdMode.INPUT || mode == RdMode.RESOLVED,
                            modifier = Modifier.fillMaxWidth(),
                            icon = {
                                Icon(
                                    painter = painterResource(
                                        if (mode == RdMode.RESOLVED) R.drawable.download else R.drawable.search,
                                    ),
                                    contentDescription = null,
                                    tint = RdColors.InkOnGold,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            onClick = {
                                if (mode == RdMode.RESOLVED) viewModel.download() else viewModel.resolve()
                            },
                        )
                    }

                    // progress unduh
                    AnimatedVisibility(visible = mode == RdMode.DOWNLOADING) {
                        Column {
                            Spacer(Modifier.height(20.dp))
                            RdProgressBar(pct = downloadPct)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── KARTU 2: VIDEO TERSIMPAN ──────────────────
                AnimatedVisibility(
                    visible = mode == RdMode.DOWNLOADED || mode == RdMode.IDENTIFYING ||
                        mode == RdMode.FOUND,
                    enter = fadeIn(tween(500)) + slideInVertically(tween(500, easing = RdEasing.Spring)) { it / 4 },
                    exit = fadeOut(),
                ) {
                    Column {
                        RdCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(R.drawable.check),
                                    contentDescription = null,
                                    tint = RdColors.Sage,
                                    modifier = Modifier.size(13.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                RdCardLabel(
                                    "Video Berhasil Diunduh" +
                                        if (downloadedSizeMb > 0) " · $downloadedSizeMb MB" else "",
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                RdOutlineButton(
                                    text = "Simpan ke Galeri",
                                    modifier = Modifier.weight(1f),
                                    icon = {
                                        Icon(
                                            painter = painterResource(R.drawable.insert_photo),
                                            contentDescription = null,
                                            tint = RdColors.Smoke,
                                            modifier = Modifier.size(15.dp),
                                        )
                                    },
                                    onClick = viewModel::saveToGallery,
                                )
                                RdGoldButton(
                                    text = "Cari Musiknya",
                                    modifier = Modifier.weight(1f),
                                    enabled = mode == RdMode.DOWNLOADED,
                                    icon = {
                                        Icon(
                                            painter = painterResource(R.drawable.search),
                                            contentDescription = null,
                                            tint = RdColors.InkOnGold,
                                            modifier = Modifier.size(15.dp),
                                        )
                                    },
                                    onClick = viewModel::identify,
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }

                // ── KARTU 3: CHECKLIST IDENTIFIKASI ───────────
                AnimatedVisibility(
                    visible = mode == RdMode.IDENTIFYING || mode == RdMode.FOUND,
                    enter = fadeIn(tween(500)) + slideInVertically(tween(500, easing = RdEasing.Spring)) { it / 4 },
                    exit = fadeOut(),
                ) {
                    Column {
                        RdCard {
                            RdCardLabel("Mengidentifikasi Audio")
                            Spacer(Modifier.height(14.dp))
                            RdChecklist(currentStep = identifyStep)
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }

                // ── KARTU 4: HASIL ────────────────────────────
                AnimatedVisibility(
                    visible = mode == RdMode.FOUND,
                    enter = fadeIn(tween(600)) + slideInVertically(tween(600, easing = RdEasing.Spring)) { it / 4 },
                    exit = fadeOut(),
                ) {
                    result?.let { r ->
                        Column {
                            RdCard {
                                RdCardLabel("Hasil Deteksi Versi Penuh")
                                Spacer(Modifier.height(14.dp))
                                RdSongCard(
                                    title = r.song.title,
                                    artist = buildString {
                                        append(r.song.artists.joinToString(", ") { it.name })
                                        r.song.duration?.let { append(" · ${fmtDur(it)}") }
                                        r.song.album?.name?.let { append(" · $it") }
                                    },
                                    coverUrl = r.shazam.coverArtUrl ?: r.song.thumbnail,
                                    isPlaying = isPlayingThis,
                                    isBuffering = isBufferingThis,
                                    onPlayPause = {
                                        if (isCurrentSong) {
                                            playerConnection?.player?.let { p ->
                                                if (p.isPlaying) p.pause() else p.play()
                                            }
                                        } else {
                                            playerConnection?.playQueue(
                                                YouTubeQueue.radio(r.song.toMediaMetadata()),
                                            )
                                        }
                                    },
                                )
                                Spacer(Modifier.height(16.dp))
                                RdVisualizer(live = isPlayingThis)
                            }

                            Spacer(Modifier.height(16.dp))

                            // aksi bawah
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                RdOutlineButton(
                                    text = "Favorit",
                                    modifier = Modifier.weight(1f),
                                    color = if (isLiked) RdColors.Rose else RdColors.Cream,
                                    borderColor = if (isLiked) RdColors.Rose.copy(alpha = 0.4f) else RdColors.Line,
                                    icon = {
                                        Icon(
                                            painter = painterResource(
                                                if (isLiked) R.drawable.favorite else R.drawable.favorite_border,
                                            ),
                                            contentDescription = null,
                                            tint = if (isLiked) RdColors.Rose else RdColors.Cream,
                                            modifier = Modifier.size(15.dp),
                                        )
                                    },
                                    onClick = {
                                        val wasLiked = isLiked
                                        viewModel.toggleFavorite()
                                        if (!wasLiked) {
                                            viewModel.showToast("Masuk playlist Disukai ♥")
                                        }
                                    },
                                )
                                RdGoldButton(
                                    text = if (fullDownloadStarted) "Tersimpan ✓" else "Download",
                                    modifier = Modifier.weight(1f),
                                    enabled = !fullDownloadStarted,
                                    icon = {
                                        Icon(
                                            painter = painterResource(R.drawable.download),
                                            contentDescription = null,
                                            tint = RdColors.InkOnGold,
                                            modifier = Modifier.size(15.dp),
                                        )
                                    },
                                    onClick = viewModel::downloadFullSong,
                                )
                            }
                            Spacer(Modifier.height(20.dp))
                            RdOutlineButton(
                                text = "MULAI ULANG",
                                modifier = Modifier.fillMaxWidth(),
                                color = RdColors.Dim,
                                dashed = true,
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.refresh),
                                        contentDescription = null,
                                        tint = RdColors.Dim,
                                        modifier = Modifier.size(13.dp),
                                    )
                                },
                                onClick = viewModel::reset,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))
                Text(
                    text = "Ruang Dengar — Metrolist Audio Lab.\nTikTok · Shazam · YT Music, langsung dari app.",
                    style = TextStyle(
                        fontFamily = RdFonts.Mono, fontSize = 10.sp,
                        color = Color(0xFF3E3729), textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(120.dp))
            }
        }

        // toast
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 26.dp),
        ) {
            AnimatedVisibility(
                visible = toast != null,
                enter = fadeIn(tween(300)) + slideInVertically(tween(550, easing = RdEasing.Pop)) { it },
                exit = fadeOut(tween(400)) + slideOutVertically(tween(400)) { it },
            ) {
                val (msg, bad) = toast ?: ("" to false)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF221A0C), Color(0xFF141008))))
                        .border(1.dp, RdColors.Line, RoundedCornerShape(99.dp))
                        .padding(horizontal = 18.dp, vertical = 11.dp),
                ) {
                    Icon(
                        painter = painterResource(if (bad) R.drawable.close else R.drawable.check),
                        contentDescription = null,
                        tint = if (bad) RdColors.Rose else RdColors.Sage,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = msg,
                        style = TextStyle(
                            fontFamily = RdFonts.Ui, fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium, color = RdColors.Cream,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/* ═══════════ ATMOSFER ═══════════ */
@Composable
private fun RdAtmosphere() {
    val t = rememberInfiniteTransition(label = "atmo")
    val drift by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(26000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift",
    )
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val r = size.maxDimension * 0.7f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(RdColors.Amber.copy(alpha = 0.10f), Color.Transparent),
                center = Offset(size.width * (1.05f - drift * 0.1f), -r * 0.25f),
                radius = r,
            ),
            radius = r,
            center = Offset(size.width * (1.05f - drift * 0.1f), -r * 0.25f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF7A4A1E).copy(alpha = 0.10f), Color.Transparent),
                center = Offset(-size.width * 0.15f + drift * 60f, size.height + r * 0.3f),
                radius = r,
            ),
            radius = r,
            center = Offset(-size.width * 0.15f + drift * 60f, size.height + r * 0.3f),
        )
    }
}

/* ═══════════ MASTHEAD ═══════════ */
@Composable
private fun RdMasthead(onBack: () -> Unit) {
    val blink = rememberInfiniteTransition(label = "blink")
    val dotAlpha by blink.animateFloat(
        initialValue = 1f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "dot",
    )
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back),
                    contentDescription = "Kembali",
                    tint = RdColors.Copper,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .size(5.dp)
                    .graphicsLayer { alpha = dotAlpha }
                    .background(RdColors.Amber, CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "METROLIST · AUDIO LAB",
                style = TextStyle(
                    fontFamily = RdFonts.Mono, fontSize = 10.sp,
                    fontWeight = FontWeight.Medium, letterSpacing = 3.sp,
                    color = RdColors.Copper,
                ),
            )
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(Brush.horizontalGradient(listOf(RdColors.Line, Color.Transparent))),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Ruang Dengar",
            style = TextStyle(
                fontFamily = RdFonts.Display,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Medium,
                fontSize = 30.sp,
                letterSpacing = 0.5.sp,
                brush = Brush.horizontalGradient(
                    0.2f to RdColors.Cream,
                    0.45f to RdColors.AmberHot,
                    0.7f to RdColors.Cream,
                ),
            ),
        )
        Spacer(Modifier.height(22.dp))
    }
}

/* ═══════════ LINK BOX ═══════════ */
@Composable
private fun RdLinkBox(
    url: String,
    onUrlChange: (String) -> Unit,
    autoBadge: Boolean,
    hasError: Boolean,
    enabled: Boolean,
    onPaste: () -> Unit,
) {
    val shakeX = remember { Animatable(0f) }
    LaunchedEffect(hasError) {
        if (hasError) {
            shakeX.animateTo(
                0f,
                keyframes {
                    durationMillis = 400
                    (-7f) at 80
                    6f at 160
                    (-4f) at 240
                    2f at 320
                    0f at 400
                },
            )
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .offset { androidx.compose.ui.unit.IntOffset(shakeX.value.dp.roundToPx(), 0) }
            .clip(RoundedCornerShape(14.dp))
            .background(RdColors.Pit)
            .border(
                1.dp,
                if (hasError) RdColors.Rose.copy(alpha = 0.6f) else RdColors.LineSoft,
                RoundedCornerShape(14.dp),
            )
            .padding(start = 14.dp, top = 5.dp, bottom = 5.dp, end = 5.dp),
    ) {
        BasicTextField(
            value = url,
            onValueChange = onUrlChange,
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = RdFonts.Mono, fontSize = 12.5.sp, color = RdColors.Cream,
            ),
            cursorBrush = SolidColor(RdColors.Amber),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (url.isEmpty()) {
                        Text(
                            text = "https://vt.tiktok.com/…",
                            style = TextStyle(
                                fontFamily = RdFonts.Mono, fontSize = 12.5.sp, color = RdColors.Dim,
                            ),
                        )
                    }
                    inner()
                }
            },
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 10.dp),
        )
        if (autoBadge) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "✓ AUTO",
                style = TextStyle(
                    fontFamily = RdFonts.Mono, fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
                    color = RdColors.Sage,
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(RdColors.Sage.copy(alpha = 0.08f))
                    .border(1.dp, RdColors.Sage.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1C160C))
                .border(1.dp, RdColors.Line, RoundedCornerShape(10.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = enabled,
                    onClick = onPaste,
                )
                .padding(horizontal = 13.dp, vertical = 9.dp),
        ) {
            Text(
                text = "TEMPEL",
                style = TextStyle(
                    fontFamily = RdFonts.Mono, fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
                    color = RdColors.Amber,
                ),
            )
        }
    }
}

/* ═══════════ PREVIEW VIDEO ═══════════ */
@Composable
private fun RdVideoPreview(
    thumbnail: String?,
    title: String,
    uploader: String,
    duration: Int,
    likes: Long,
    comments: Long,
) {
    Row {
        Box(
            modifier = Modifier
                .size(86.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF3A2508), Color(0xFF1B1206), Color(0xFF0F0B06)),
                    ),
                )
                .border(1.dp, RdColors.Line, RoundedCornerShape(16.dp)),
        ) {
            AsyncImage(
                model = thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(RdColors.Void.copy(alpha = 0.55f))
                    .border(1.dp, RdColors.Amber.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.play),
                    contentDescription = null,
                    tint = RdColors.AmberHot,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = fmtDur(duration),
                style = TextStyle(
                    fontFamily = RdFonts.Mono, fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold, color = RdColors.AmberHot,
                ),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(RdColors.Void.copy(alpha = 0.75f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                style = TextStyle(
                    fontFamily = RdFonts.Ui, fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold, color = RdColors.Cream,
                    lineHeight = 19.sp,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "@$uploader",
                style = TextStyle(
                    fontFamily = RdFonts.Mono, fontSize = 11.sp,
                    color = RdColors.Copper, letterSpacing = 0.5.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.favorite),
                    contentDescription = null,
                    tint = RdColors.Dim,
                    modifier = Modifier.size(11.dp),
                )
                Spacer(Modifier.width(5.dp))
                RdCountUp(target = likes)
                Spacer(Modifier.width(14.dp))
                Icon(
                    painter = painterResource(R.drawable.timer),
                    contentDescription = null,
                    tint = RdColors.Dim,
                    modifier = Modifier.size(11.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = fmtDur(duration),
                    style = TextStyle(fontFamily = RdFonts.Mono, fontSize = 11.sp, color = RdColors.Smoke),
                )
                Spacer(Modifier.width(14.dp))
                RdCountUp(target = comments, suffix = " komentar")
            }
        }
    }
}

@Composable
private fun RdCountUp(target: Long, suffix: String = "") {
    var shown by remember(target) { mutableFloatStateOf(0f) }
    LaunchedEffect(target) {
        val anim = Animatable(0f)
        anim.animateTo(1f, tween(1100)) {
            shown = value
        }
    }
    val eased = 1f - (1f - shown).pow(3)
    Text(
        text = fmtNum((target * eased).toLong()) + suffix,
        style = TextStyle(fontFamily = RdFonts.Mono, fontSize = 11.sp, color = RdColors.Smoke),
    )
}

/* ═══════════ PROGRESS ═══════════ */
@Composable
private fun RdProgressBar(pct: Int) {
    val fill by animateFloatAsState(pct / 100f, tween(300), label = "fill")
    val dotsT = rememberInfiniteTransition(label = "dots")
    val dotPhase by dotsT.animateFloat(
        initialValue = 0f, targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "dotPhase",
    )
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(RdColors.Pit)
                .border(1.dp, RdColors.LineSoft, RoundedCornerShape(4.dp))
                .drawBehind {
                    // garis tick tipis ala repeating-gradient
                    var x = 9.dp.toPx()
                    while (x < size.width) {
                        drawLine(
                            color = RdColors.Amber.copy(alpha = 0.07f),
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 1.dp.toPx(),
                        )
                        x += 10.dp.toPx()
                    }
                    val w = size.width * fill
                    if (w > 0f) {
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                0f to Color(0xFF8A5A20),
                                0.6f to RdColors.Amber,
                                1f to RdColors.AmberHot,
                                endX = w,
                            ),
                            size = Size(w, size.height),
                            cornerRadius = CornerRadius(4.dp.toPx()),
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                0f to Color(0xFFFFF3D0),
                                0.4f to RdColors.AmberHot,
                                0.7f to Color.Transparent,
                                radius = 6.dp.toPx(),
                                center = Offset(w, size.height / 2),
                            ),
                            radius = 6.dp.toPx(),
                            center = Offset(w, size.height / 2),
                        )
                    }
                },
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Mengunduh" + ".".repeat(dotPhase.toInt().coerceIn(0, 3)),
                style = TextStyle(
                    fontFamily = RdFonts.Mono, fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium, color = RdColors.Smoke,
                ),
            )
            Text(
                text = "$pct%",
                style = TextStyle(
                    fontFamily = RdFonts.Mono, fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium, color = RdColors.Smoke,
                ),
            )
        }
    }
}

/* ═══════════ CHECKLIST ═══════════ */
private val RdSteps = listOf(
    "Ekstrak audio dari video",
    "Bikin fingerprint audio",
    "Cocokkan ke database Shazam",
    "Cari versi full di YT Music",
)

@Composable
private fun RdChecklist(currentStep: Int) {
    Column {
        RdSteps.forEachIndexed { i, label ->
            val stepNo = i + 1
            val done = currentStep > stepNo
            val active = currentStep == stepNo
            Row(verticalAlignment = Alignment.Top) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RdStepIndicator(done = done, active = active)
                    if (i < RdSteps.lastIndex) {
                        val railFill by animateFloatAsState(
                            if (done) 1f else 0f,
                            tween(600, easing = RdEasing.Spring),
                            label = "rail",
                        )
                        Box(
                            Modifier
                                .width(2.dp)
                                .height(18.dp)
                                .background(RdColors.LineSoft),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleY = railFill
                                        transformOrigin = TransformOrigin(0.5f, 0f)
                                    }
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(RdColors.Amber, RdColors.Copper),
                                        ),
                                    ),
                            )
                        }
                    }
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    text = label,
                    style = TextStyle(
                        fontFamily = RdFonts.Ui,
                        fontSize = 13.sp,
                        color = when {
                            active -> RdColors.Cream
                            done -> RdColors.Smoke
                            else -> RdColors.Dim
                        },
                    ),
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun RdStepIndicator(done: Boolean, active: Boolean) {
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "angle",
    )
    val checkProgress by animateFloatAsState(
        if (done) 1f else 0f,
        tween(450, easing = RdEasing.Spring),
        label = "check",
    )
    androidx.compose.foundation.Canvas(modifier = Modifier.size(22.dp)) {
        val stroke = 2.dp.toPx()
        when {
            done -> {
                drawCircle(
                    brush = Brush.linearGradient(listOf(RdColors.Amber, RdColors.Copper)),
                    radius = size.minDimension / 2,
                )
                // centang stroke-draw
                val s = size.minDimension / 24f
                val path = Path().apply {
                    moveTo(20f * s, 7f * s)
                    lineTo(9.5f * s, 16.5f * s)
                    lineTo(4.5f * s, 12f * s)
                }
                val pm = PathMeasure()
                pm.setPath(path, false)
                val seg = Path()
                if (checkProgress > 0f) {
                    pm.getSegment(0f, pm.length * checkProgress, seg, true)
                    drawPath(
                        path = seg,
                        color = RdColors.InkOnGold,
                        style = Stroke(
                            width = 2.6f * s,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }
            }
            active -> {
                rotate(angle) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            0.1f to Color.Transparent,
                            0.85f to RdColors.Amber,
                            1f to RdColors.AmberHot,
                        ),
                        startAngle = 40f,
                        sweepAngle = 290f,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
            else -> {
                drawCircle(
                    color = RdColors.Line,
                    radius = (size.minDimension - stroke) / 2,
                    style = Stroke(width = stroke),
                )
            }
        }
    }
}

/* ═══════════ SONG CARD (THE DECK) ═══════════ */
@Composable
private fun RdSongCard(
    title: String,
    artist: String,
    coverUrl: String?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    onPlayPause: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RdColors.Pit)
            .border(1.dp, RdColors.LineSoft, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        RdVinylDeck(coverUrl = coverUrl, spinning = isPlaying, armDown = isPlaying || isBuffering)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = TextStyle(
                    fontFamily = RdFonts.Display, fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Medium, fontSize = 16.sp,
                    color = RdColors.AmberHot, letterSpacing = 0.3.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = artist,
                style = TextStyle(
                    fontFamily = RdFonts.Mono, fontSize = 11.5.sp,
                    color = RdColors.Smoke, letterSpacing = 0.3.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RdMatchRing()
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "Cocok via Shazam",
                    style = TextStyle(
                        fontFamily = RdFonts.Mono, fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold, color = RdColors.Sage,
                    ),
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(RdColors.GoldTop, RdColors.GoldDeep)))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onPlayPause,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isBuffering) {
                val spin = rememberInfiniteTransition(label = "buf")
                val a by spin.animateFloat(
                    initialValue = 0f, targetValue = 360f,
                    animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing)),
                    label = "bufA",
                )
                androidx.compose.foundation.Canvas(Modifier.size(24.dp)) {
                    rotate(a) {
                        drawArc(
                            color = RdColors.InkOnGold,
                            startAngle = 0f,
                            sweepAngle = 270f,
                            useCenter = false,
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                        )
                    }
                }
            } else {
                Icon(
                    painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                    contentDescription = null,
                    tint = RdColors.InkOnGold,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

@Composable
private fun RdMatchRing() {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(400)
        progress.animateTo(0.94f, tween(1400, easing = RdEasing.Spring))
    }
    androidx.compose.foundation.Canvas(Modifier.size(20.dp)) {
        val stroke = 2.dp.toPx()
        drawCircle(
            color = RdColors.Line,
            radius = (size.minDimension - stroke) / 2,
            style = Stroke(width = stroke),
        )
        drawArc(
            color = RdColors.Sage,
            startAngle = -90f,
            sweepAngle = 360f * progress.value,
            useCenter = false,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

/* ═══════════ VINYL + TONEARM ═══════════ */
@Composable
private fun RdVinylDeck(coverUrl: String?, spinning: Boolean, armDown: Boolean) {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(spinning) {
        if (spinning) {
            // spin-up inersia 0→720° lalu loop linear (ala vinylSpinUp + vinylLoop)
            rotation.animateTo(
                rotation.value + 720f,
                tween(2400, easing = RdEasing.VinylSpinUp),
            )
            while (true) {
                rotation.animateTo(
                    rotation.value + 360f,
                    tween(1700, easing = LinearEasing),
                )
            }
        }
    }
    val armAngle by animateFloatAsState(
        if (armDown) 0f else -38f,
        tween(1000, easing = RdEasing.Pop),
        label = "arm",
    )
    Box(modifier = Modifier.size(74.dp)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = rotation.value % 360f },
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val c = center
                val u = size.minDimension / 100f
                drawCircle(Color(0xFF0D0A06), radius = 48f * u, center = c)
                drawCircle(Color(0xFF14100A), radius = 46f * u, center = c)
                drawCircle(Color(0xFF2B2314), radius = 46f * u, center = c, style = Stroke(1f * u))
                // grooves
                for (r in intArrayOf(42, 39, 36, 33, 30, 27)) {
                    drawCircle(Color(0xFF1E180E), radius = r * u, center = c, style = Stroke(1f * u))
                }
                // shine arcs
                drawArc(
                    color = Color(0xFFFFF0C8).copy(alpha = 0.4f),
                    startAngle = -90f, sweepAngle = 90f, useCenter = false,
                    topLeft = Offset(c.x - 46f * u, c.y - 46f * u),
                    size = Size(92f * u, 92f * u),
                    style = Stroke(2.5f * u, cap = StrokeCap.Round),
                )
                drawArc(
                    color = Color(0xFFFFF0C8).copy(alpha = 0.22f),
                    startAngle = 90f, sweepAngle = 90f, useCenter = false,
                    topLeft = Offset(c.x - 46f * u, c.y - 46f * u),
                    size = Size(92f * u, 92f * u),
                    style = Stroke(2f * u, cap = StrokeCap.Round),
                )
                // label
                drawCircle(
                    brush = Brush.linearGradient(
                        colors = listOf(RdColors.GoldTop, Color(0xFFC08838), Color(0xFF8A5A20)),
                        start = Offset(c.x - 17f * u, c.y - 17f * u),
                        end = Offset(c.x + 17f * u, c.y + 17f * u),
                    ),
                    radius = 17f * u, center = c,
                )
                drawCircle(
                    color = Color(0xFFFFDF9E).copy(alpha = 0.5f),
                    radius = 17f * u, center = c, style = Stroke(0.8f * u),
                )
            }
            if (coverUrl != null) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(25.dp)
                        .clip(CircleShape)
                        .border(1.dp, Color(0xFFFFDF9E).copy(alpha = 0.5f), CircleShape),
                )
            }
            // spindle di atas cover
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(5.dp)
                    .background(Color(0xFF0D0A06), CircleShape),
            )
        }
        // tonearm nempel kanan-atas deck
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .size(width = 34.dp, height = 60.dp)
                .align(Alignment.TopEnd)
                .offset(x = 11.dp, y = (-8).dp)
                .graphicsLayer {
                    rotationZ = armAngle
                    transformOrigin = TransformOrigin(24f / 34f, 8f / 60f)
                },
        ) {
            val s = size.width / 34f
            drawCircle(Color(0xFF1E1810), radius = 7f * s, center = Offset(24f * s, 8f * s))
            drawCircle(
                Color(0xFF3A2F1A), radius = 7f * s, center = Offset(24f * s, 8f * s),
                style = Stroke(1.5f * s),
            )
            drawCircle(Color(0xFFC08838), radius = 2.5f * s, center = Offset(24f * s, 8f * s))
            drawLine(
                color = Color(0xFFC0B49A),
                start = Offset(24f * s, 8f * s),
                end = Offset(10f * s, 46f * s),
                strokeWidth = 2.5f * s,
                cap = StrokeCap.Round,
            )
            rotate(degrees = 20f, pivot = Offset(9f * s, 50f * s)) {
                drawRoundRect(
                    color = RdColors.Amber,
                    topLeft = Offset(4f * s, 44f * s),
                    size = Size(11f * s, 13f * s),
                    cornerRadius = CornerRadius(3f * s),
                )
                drawRoundRect(
                    color = Color(0xFF8A5A20),
                    topLeft = Offset(4f * s, 44f * s),
                    size = Size(11f * s, 13f * s),
                    cornerRadius = CornerRadius(3f * s),
                    style = Stroke(1f * s),
                )
            }
        }
    }
}

/* ═══════════ VISUALIZER ═══════════ */
@Composable
private fun RdVisualizer(live: Boolean) {
    val t = rememberInfiniteTransition(label = "viz")
    val phase by t.animateFloat(
        initialValue = 0f, targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "phase",
    )
    val amp by animateFloatAsState(if (live) 1f else 0.05f, tween(700), label = "amp")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RdColors.Pit)
            .border(1.dp, RdColors.LineSoft, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            Text(
                text = if (live) "LIVE · YT MUSIC" else "STANDBY",
                style = TextStyle(
                    fontFamily = RdFonts.Mono, fontSize = 8.5.sp,
                    letterSpacing = 2.sp, color = RdColors.Dim,
                ),
            )
        }
        Spacer(Modifier.height(4.dp))
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        ) {
            val n = 20
            val gap = 4.dp.toPx()
            val barW = (size.width - gap * (n - 1)) / n
            for (i in 0 until n) {
                // profil tengah tinggi + dua gelombang sinus biar organik
                val mid = 1f - abs(i - (n - 1) / 2f) / (n / 2f) * 0.55f
                val w1 = sin(phase * 1f + i * 0.9f)
                val w2 = sin(phase * 2.3f + i * 1.7f + 1.2f)
                val level = (0.25f + 0.75f * abs(0.6f * w1 + 0.4f * w2)) * mid * amp
                val h = (level * size.height).coerceAtLeast(2.dp.toPx())
                val x = i * (barW + gap)
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        0f to RdColors.AmberHot,
                        0.5f to RdColors.Amber,
                        1f to Color(0xFF8A5A20),
                        startY = size.height - h,
                        endY = size.height,
                    ),
                    topLeft = Offset(x, size.height - h),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
                // peak cap
                drawRoundRect(
                    color = RdColors.AmberHot.copy(alpha = 0.8f * amp),
                    topLeft = Offset(x, (size.height - h - 5.dp.toPx()).coerceAtLeast(0f)),
                    size = Size(barW, 2.dp.toPx()),
                    cornerRadius = CornerRadius(1.dp.toPx()),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (live) "Spectrum versi penuh sedang diputar." else "Tekan play — stel versi penuh dari YT Music.",
            style = TextStyle(
                fontFamily = RdFonts.Mono, fontSize = 10.5.sp,
                color = RdColors.Dim, textAlign = TextAlign.Center,
                letterSpacing = 0.5.sp,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
