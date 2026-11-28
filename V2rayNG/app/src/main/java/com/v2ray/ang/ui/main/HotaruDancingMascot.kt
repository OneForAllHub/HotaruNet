package com.v2ray.ang.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.v2ray.ang.R
import com.v2ray.ang.handler.HotaruSmartEngine

private val STAGE1_FRAMES = intArrayOf(
    R.drawable.hotaru_stage1_f0,
    R.drawable.hotaru_stage1_f1,
    R.drawable.hotaru_stage1_f2,
    R.drawable.hotaru_stage1_f3,
    R.drawable.hotaru_stage1_f4,
    R.drawable.hotaru_stage1_f5,
)

private val STAGE2_FRAMES = intArrayOf(
    R.drawable.hotaru_stage2_f0,
    R.drawable.hotaru_stage2_f1,
    R.drawable.hotaru_stage2_f2,
    R.drawable.hotaru_stage2_f3,
    R.drawable.hotaru_stage2_f4,
    R.drawable.hotaru_stage2_f5,
)

private val STAGE3_FRAMES = intArrayOf(
    R.drawable.hotaru_stage3_f0,
    R.drawable.hotaru_stage3_f1,
    R.drawable.hotaru_stage3_f2,
    R.drawable.hotaru_stage3_f3,
    R.drawable.hotaru_stage3_f4,
    R.drawable.hotaru_stage3_f5,
)

private val STAGE4_FRAMES = intArrayOf(
    R.drawable.hotaru_stage4_f0,
    R.drawable.hotaru_stage4_f1,
    R.drawable.hotaru_stage4_f2,
    R.drawable.hotaru_stage4_f3,
    R.drawable.hotaru_stage4_f4,
    R.drawable.hotaru_stage4_f5,
)

/**
 * Displays the stage-specific 6-frame animated robot mascot inside the connecting orb:
 * - Stage 1 (SYNC): Thinking Robot searching for nodes
 * - Stage 2 (PING): Server Checkpoint Robot inspecting server rack
 * - Stage 3 (SPEED / CLOUD): Chibi Robot plugging in the speed tunnel
 * - Stage 4 (LOCKED): Neon Robot victory jump
 */
@Composable
fun HotaruDancingMascot(
    contentDescription: String,
    modifier: Modifier = Modifier,
    connectStage: HotaruSmartEngine.ConnectStage = HotaruSmartEngine.ConnectStage.IDLE,
) {
    val activeStep = connectStage.stepIndex.coerceIn(1, 4)

    val transition = rememberInfiniteTransition(label = "hotaruStageGif")
    val frameProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 960, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "gifFrameProgress",
    )
    val floatY by transition.animateFloat(
        initialValue = -2.5f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "gifFloatY",
    )
    val breatheScale by transition.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "gifBreatheScale",
    )

    val frameIdx = frameProgress.toInt().coerceIn(0, 5)

    AnimatedContent(
        targetState = activeStep,
        modifier = modifier,
        transitionSpec = {
            (fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.90f, animationSpec = tween(220)))
                .togetherWith(fadeOut(animationSpec = tween(150)))
        },
        label = "stageMascotSwitch",
    ) { step ->
        val frames = when (step) {
            1 -> STAGE1_FRAMES
            2 -> STAGE2_FRAMES
            3 -> STAGE3_FRAMES
            else -> STAGE4_FRAMES
        }
        val drawableId = frames[frameIdx % frames.size]

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(drawableId),
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = floatY * density
                        scaleX = breatheScale
                        scaleY = breatheScale
                    },
            )
        }
    }
}
