/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ruangdengar.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.metrolist.music.R

/** Token warna "amber audiophile" — porting 1:1 dari :root index.html */
object RdColors {
    val Void = Color(0xFF0C0A07)
    val Pit = Color(0xFF100D09)
    val Line = Color(0xFF2B2314)
    val LineSoft = Color(0xFF221B10)
    val Amber = Color(0xFFE8B44A)
    val AmberHot = Color(0xFFFFDF9E)
    val Copper = Color(0xFFA96A32)
    val Cream = Color(0xFFF4ECDD)
    val Smoke = Color(0xFF9A9180)
    val Dim = Color(0xFF5F5744)
    val Sage = Color(0xFFA8C98A)
    val Rose = Color(0xFFE8896B)
    val GoldTop = Color(0xFFF2C263)
    val GoldMid = Color(0xFFD69A34)
    val GoldDeep = Color(0xFFB4762A)
    val InkOnGold = Color(0xFF1C1204)
}

object RdEasing {
    val Spring = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    val Pop = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
    val VinylSpinUp = CubicBezierEasing(0.45f, 0.05f, 0.9f, 0.6f)
}

object RdFonts {
    val Display = FontFamily(
        Font(
            R.font.fraunces_italic,
            weight = FontWeight.Medium,
            style = FontStyle.Italic,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(560),
                FontVariation.Setting("opsz", 40f),
            ),
        ),
    )
    val Ui = FontFamily(
        Font(R.font.instrument_sans, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
        Font(R.font.instrument_sans, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
        Font(R.font.instrument_sans, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
        Font(R.font.instrument_sans, weight = FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    )
    val Mono = FontFamily(
        Font(R.font.spline_sans_mono, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
        Font(R.font.spline_sans_mono, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
        Font(R.font.spline_sans_mono, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    )
}

/** Label kecil mono uppercase ala .card-label */
@Composable
fun RdCardLabel(text: String, modifier: Modifier = Modifier, color: Color = RdColors.Dim) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = TextStyle(
            fontFamily = RdFonts.Mono,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.5.sp,
            color = color,
        ),
    )
}

/** Kartu gelap lacquer ala .card (gradient + garis amber tipis di atas) */
@Composable
fun RdCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF181308), Color(0xFF120E08), Color(0xFF100C07)),
                ),
            )
            .border(1.dp, RdColors.LineSoft, RoundedCornerShape(22.dp))
            .drawBehind {
                drawLine(
                    brush = Brush.horizontalGradient(
                        0f to Color.Transparent,
                        0.5f to RdColors.Amber.copy(alpha = 0.45f),
                        1f to Color.Transparent,
                    ),
                    start = androidx.compose.ui.geometry.Offset(size.width * 0.08f, 0.5f),
                    end = androidx.compose.ui.geometry.Offset(size.width * 0.92f, 0.5f),
                )
            }
            .padding(20.dp),
        content = content,
    )
}

/** Tombol emas ala .btn-gold, lengkap dengan sheen sweep */
@Composable
fun RdGoldButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val sheen = rememberInfiniteTransition(label = "sheen")
    val sheenX by sheen.animateFloat(
        initialValue = -1.2f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sheenX",
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(RdColors.GoldTop, RdColors.GoldMid, RdColors.GoldDeep),
                ),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .drawBehind {
                if (enabled) {
                    val w = size.width
                    drawRect(
                        brush = Brush.linearGradient(
                            0f to Color.Transparent,
                            0.5f to Color.White.copy(alpha = 0.35f),
                            1f to Color.Transparent,
                            start = androidx.compose.ui.geometry.Offset(w * sheenX, 0f),
                            end = androidx.compose.ui.geometry.Offset(w * (sheenX + 0.35f), size.height),
                        ),
                    )
                }
            }
            .alpha(if (enabled) 1f else 0.35f)
            .padding(vertical = 14.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                icon()
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = TextStyle(
                    fontFamily = RdFonts.Ui,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = RdColors.InkOnGold,
                ),
                maxLines = 1,
            )
        }
    }
}

/** Tombol outline ala .btn-outline */
@Composable
fun RdOutlineButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = RdColors.Smoke,
    borderColor: Color = RdColors.Line,
    dashed: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(15.dp)
    val base = if (dashed) {
        Modifier.drawBehind {
            val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 1.dp.toPx(),
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                    floatArrayOf(6.dp.toPx(), 5.dp.toPx()),
                ),
            )
            drawRoundRect(
                color = borderColor,
                style = stroke,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(15.dp.toPx()),
            )
        }
    } else {
        Modifier.border(1.dp, borderColor, shape)
    }
    Box(
        modifier = modifier
            .clip(shape)
            .then(base)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 14.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                icon()
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = TextStyle(
                    fontFamily = RdFonts.Ui,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                ),
                maxLines = 1,
            )
        }
    }
}
