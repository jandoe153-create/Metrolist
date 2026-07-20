/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ruangdengar.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.metrolist.music.R

/**
 * FAB "Ruang Dengar" — kotak rounded hitam lacquer, ikon download amber,
 * ring conic muter + pulse. Sengaja beda dari FAB Material bawaan.
 */
@Composable
fun RuangDengarFab(onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "rdFab")
    val ringAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring",
    )
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = RdEasing.Spring),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse",
    )

    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .size(60.dp)
            .drawBehind {
                // pulse ring melebar lalu pudar (ala @keyframes pulse)
                val spread = pulse * 14.dp.toPx()
                drawRoundRect(
                    color = RdColors.Amber.copy(alpha = (1f - pulse) * 0.30f),
                    topLeft = Offset(-spread, -spread),
                    size = Size(size.width + spread * 2, size.height + spread * 2),
                    cornerRadius = CornerRadius(20.dp.toPx() + spread),
                    style = Stroke(width = 2.dp.toPx()),
                )
                // ring conic muter (ala .box::before)
                rotate(ringAngle) {
                    drawRoundRect(
                        brush = Brush.sweepGradient(
                            0.0f to Color.Transparent,
                            0.40f to Color.Transparent,
                            0.50f to RdColors.Amber.copy(alpha = 0.55f),
                            0.60f to Color.Transparent,
                            1.0f to Color.Transparent,
                        ),
                        cornerRadius = CornerRadius(21.dp.toPx()),
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                }
            }
            .shadow(elevation = 10.dp, shape = shape, clip = false)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF1E1810), Color(0xFF0F0C07)),
                ),
            )
            .border(1.dp, RdColors.Line, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.download),
            contentDescription = "Ruang Dengar",
            tint = RdColors.Amber,
            modifier = Modifier.size(24.dp),
        )
    }
}
