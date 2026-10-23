package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun VoiceWaveVisualizer(
    isListening: Boolean,
    rmsLevel: Float,
    modifier: Modifier = Modifier,
    barCount: Int = 9,
    maxHeight: Dp = 44.dp,
    minHeight: Dp = 6.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave_transition")

    val animPhase1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "phase1"
    )

    val animPhase2 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "phase2"
    )

    Row(
        modifier = modifier.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val middle = barCount / 2
        for (i in 0 until barCount) {
            val distFromCenter = kotlin.math.abs(i - middle).toFloat() / middle.toFloat()
            val centerWeight = 1f - (distFromCenter * 0.4f)

            val heightFraction = if (isListening) {
                val animatedFactor = if (i % 2 == 0) animPhase1 else animPhase2
                val voiceFactor = (rmsLevel * 1.5f).coerceIn(0.1f, 1f)
                ((animatedFactor * 0.4f + voiceFactor * 0.6f) * centerWeight).coerceIn(0.12f, 1f)
            } else {
                0.15f
            }

            val barHeight = minHeight + (maxHeight - minHeight) * heightFraction

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(barHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = if (isListening) {
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            } else {
                                listOf(
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                )
                            }
                        )
                    )
            )
        }
    }
}
