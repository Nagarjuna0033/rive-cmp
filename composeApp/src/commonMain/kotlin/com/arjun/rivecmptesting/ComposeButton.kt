package com.arjun.rivecmptesting

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val ColorGrayGray400 = Color(0xFFB2ADB4)
val ColorYellowYellow300 = Color(0xFFFFD970)
val ColorYellowYellow500 = Color(0xFFFFBF0F)
val ColorGreenGreen300 = Color(0xFF73E2A4)
val ColorGreenGreen500 = Color(0xFF27BE69)
val ColorYellowYellow50 = Color(0xFFFFF8E5)
val ColorYellowYellow100 = Color(0xFFFFF3D1)

val ColorGrayGray500 = Color(0xFF938C95)

enum class ButtonAnimationType {
    NONE,
    PRESS,
    BOUNCE,
    LIFT,
    SHAKE,
    PULSE,
    GLOW,
    ROTATE,
    COMBO
}

@Composable
fun ComposeAnimations() {

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(ButtonAnimationType.entries) { type ->
            ComposeButton(
                text = type.name,
                animationType = type
            )
        }
    }
}

@Composable
fun ComposeButton(
    modifier: Modifier = Modifier,
    text: String? = null,
    variant: PrimaryButtonVariant = PrimaryButtonVariant.Yellow,
    isLoading: Boolean = false,
    isEnabled: Boolean = true,
    cornerRadius: Dp = 8.dp,
    shadowOffsetFactor: Dp = 1.5.dp,
    onClick: () -> Unit = {},
    animationType: ButtonAnimationType = ButtonAnimationType.COMBO,
) {

    val haptic = LocalHapticFeedback.current

    val backgroundBrush = when {
        !isEnabled -> DisabledButtonBrush
        variant is PrimaryButtonVariant.Green -> GreenButtonBrush
        variant is PrimaryButtonVariant.LightYello -> LightYelloButtonBrush
        else -> YellowButtonBrush
    }

    val borderBrush = if (isEnabled) EnabledBorderBrush else DisabledBorderBrush

    val displayText = if (isLoading) "LOADING…" else text


    val scaleX = remember { Animatable(1f) }
    val scaleY = remember { Animatable(1f) }
    val translationY = remember { Animatable(0f) }
    val translationX = remember { Animatable(0f) }
    val rotation = remember { Animatable(0f) }

    val animationCollection = remember {
        Json.decodeFromString<AnimationCollection>(animationConfig)
    }

    val animationKey = animationType.name.lowercase()
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopEnd
    ) {
        Button(
            modifier = Modifier
                .graphicsLayer {
                this.scaleX = scaleX.value
                this.scaleY = scaleY.value
                this.translationY = translationY.value
                this.translationX = translationX.value
                this.rotationZ = rotation.value
                }
                .drawBehind {
                    val cr = CornerRadius(cornerRadius.toPx())
                    val offset = shadowOffsetFactor.toPx()

                    drawRoundRect(
                        brush = borderBrush,
                        size = size,
                        cornerRadius = cr,
                        style = Stroke(width = 1.5.dp.toPx())
                    )

                    drawRoundRect(
                        brush = borderBrush,
                        topLeft = Offset(0f, offset + 1.6.dp.toPx()),
                        size = size,
                        cornerRadius = cr
                    )
                }
                .background(
                    brush = backgroundBrush,
                    shape = RoundedCornerShape(cornerRadius)
                )
                .padding(horizontal = 1.dp, vertical = 2.dp)
                .alpha(if (isLoading) 0.6f else 1f),

            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                playClickSound()

                val config = animationCollection.animations[animationKey]
                if (config != null) {
                    scope.launch {
                        runAnimation(
                            config = config,
                            defaults = animationCollection.defaults,
                            scaleX = scaleX,
                            scaleY = scaleY,
                            translationX = translationX,
                            translationY = translationY,
                            rotation = rotation
                        )
                    }
                }

                onClick()
            },
            shape = RoundedCornerShape(cornerRadius),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues(0.dp),
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {

                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = ColorTextSecondary
                    )
                    Spacer(Modifier.width(8.dp))
                }

                displayText?.let {
                    Text(text = it, color = Color.Black)
                }
            }
        }
    }
}


suspend fun runAnimation(
    config: AnimationConfig,
    defaults: DefaultConfig,
    scaleX: Animatable<Float, AnimationVector1D>,
    scaleY: Animatable<Float, AnimationVector1D>,
    translationX: Animatable<Float, AnimationVector1D>,
    translationY: Animatable<Float, AnimationVector1D>,
    rotation: Animatable<Float, AnimationVector1D>
) {

    suspend fun runStep(step: AnimationStep) {
        val duration = step.duration ?: defaults.duration
        val spec = (step.easing ?: defaults.easing).toSpec(duration)

        if (step.delay > 0) delay(step.delay.toLong())

        when (step.property) {
            "scaleX" -> scaleX.animateTo(step.to, spec)
            "scaleY" -> scaleY.animateTo(step.to, spec)
            "translationX" -> translationX.animateTo(step.to, spec)
            "translationY" -> translationY.animateTo(step.to, spec)
            "rotation" -> rotation.animateTo(step.to, spec)
        }
    }

    for (group in config.steps) {
        repeat(group.repeat) {
            when (group.mode) {

                "parallel" -> coroutineScope {
                    group.items.forEach { step ->
                        launch { runStep(step) }
                    }
                }

                "sequence" -> {
                    group.items.forEach { step ->
                        runStep(step)
                    }
                }
            }
        }
    }
}


@Serializable
data class AnimationCollection(
    val version: Int,
    val defaults: DefaultConfig,
    val animations: Map<String, AnimationConfig>
)

@Serializable
data class DefaultConfig(
    val duration: Int = 120,
    val easing: EasingConfig = EasingConfig()
)

@Serializable
data class AnimationConfig(
    val steps: List<AnimationStepGroup>
)

@Serializable
data class AnimationStepGroup(
    val mode: String,
    val repeat: Int = 1,
    val items: List<AnimationStep>
)

@Serializable
data class AnimationStep(
    val property: String,
    val to: Float,
    val duration: Int? = null,
    val easing: EasingConfig? = null,
    val delay: Int = 0
)

@Serializable
data class EasingConfig(
    val type: String = "tween",
    val dampingRatio: Float = 0.5f,
    val stiffness: Float = 300f
)


fun EasingConfig.toSpec(defaultDuration: Int): AnimationSpec<Float> {
    return when (type) {
        "spring" -> spring(
            dampingRatio = dampingRatio,
            stiffness = stiffness
        )
        else -> tween(durationMillis = defaultDuration)
    }
}


val animationConfig = """
    {
      "version": 1,
      "defaults": {
        "duration": 120,
        "easing": {
          "type": "tween"
        }
      },
      "animations": {
        "combo": {
          "steps": [
            {
              "mode": "parallel",
              "items": [
                {
                  "property": "scaleY",
                  "to": 0.9,
                  "duration": 250
                }
              ]
            },
            {
              "mode": "parallel",
              "items": [
                {
                  "property": "scaleY",
                  "to": 1.0,
                  "easing": {
                    "type": "spring",
                    "dampingRatio": 0.9,
                    "stiffness": 500
                  }
                }
              ]
            }
          ]
        },

        "bounce": {
          "steps": [
            {
              "mode": "parallel",
              "items": [
                { "property": "scaleX", "to": 0.9 },
                { "property": "scaleY", "to": 0.9 }
              ]
            },
            {
              "mode": "parallel",
              "items": [
                {
                  "property": "scaleX",
                  "to": 1.1,
                  "easing": { "type": "spring", "dampingRatio": 0.4 }
                },
                {
                  "property": "scaleY",
                  "to": 1.1,
                  "easing": { "type": "spring", "dampingRatio": 0.4 }
                }
              ]
            },
            {
              "mode": "parallel",
              "items": [
                { "property": "scaleX", "to": 1.0 },
                { "property": "scaleY", "to": 1.0 }
              ]
            }
          ]
        },

        "shake": {
          "steps": [
            {
              "mode": "sequence",
              "repeat": 3,
              "items": [
                {
                  "property": "translationX",
                  "to": -12,
                  "duration": 40
                },
                {
                  "property": "translationX",
                  "to": 12,
                  "duration": 40
                }
              ]
            },
            {
              "mode": "sequence",
              "items": [
                {
                  "property": "translationX",
                  "to": 0,
                  "duration": 40
                }
              ]
            }
          ]
        },

        "pulse": {
          "steps": [
            {
              "mode": "parallel",
              "items": [
                { "property": "scaleX", "to": 1.08, "duration": 150 },
                { "property": "scaleY", "to": 1.08, "duration": 150 }
              ]
            },
            {
              "mode": "parallel",
              "items": [
                { "property": "scaleX", "to": 1.0, "duration": 150 },
                { "property": "scaleY", "to": 1.0, "duration": 150 }
              ]
            }
          ]
        },

        "rotate": {
          "steps": [
            {
              "mode": "sequence",
              "items": [
                { "property": "rotation", "to": 10 },
                { "property": "rotation", "to": -10 },
                { "property": "rotation", "to": 0 }
              ]
            }
          ]
        },

        "lift": {
          "steps": [
            {
              "mode": "sequence",
              "items": [
                { "property": "translationY", "to": -12 },
                { "property": "translationY", "to": 0 }
              ]
            }
          ]
        },

        "glow": {
          "steps": [
            {
              "mode": "parallel",
              "items": [
                { "property": "scaleX", "to": 1.05, "duration": 100 },
                { "property": "scaleY", "to": 1.05, "duration": 100 }
              ]
            },
            {
              "mode": "parallel",
              "items": [
                { "property": "scaleX", "to": 1.0, "duration": 100 },
                { "property": "scaleY", "to": 1.0, "duration": 100 }
              ]
            }
          ]
        }
      }
    }
""".trimIndent()




private val EnabledBorderBrush = Brush.verticalGradient(
    listOf(ColorGrayGray900.copy(alpha = 0.3f), ColorGrayGray900)
)

private val DisabledBorderBrush = Brush.verticalGradient(
    listOf(ColorGrayGray400, ColorGrayGray400)
)

private val YellowButtonBrush = Brush.verticalGradient(
    colorStops = arrayOf(
        0.0f to ColorYellowYellow300,
        0.5f to ColorYellowYellow300,
        0.5f to ColorYellowYellow500,
        1.0f to ColorYellowYellow500
    )
)

private val GreenButtonBrush = Brush.verticalGradient(
    colorStops = arrayOf(
        0.0f to ColorGreenGreen300,
        0.5f to ColorGreenGreen300,
        0.5f to ColorGreenGreen500,
        1.0f to ColorGreenGreen500
    )
)
private val LightYelloButtonBrush = Brush.verticalGradient(
    colorStops = arrayOf(
        0.0f to ColorYellowYellow50,
        0.5f to ColorYellowYellow50,
        0.5f to ColorYellowYellow100,
        1.0f to ColorYellowYellow100
    )
)

private val DisabledButtonBrush = Brush.verticalGradient(
    listOf(ColorGrayGray500, ColorGrayGray500)
)

@Stable
sealed class PrimaryButtonVariant {
    data object Yellow : PrimaryButtonVariant()
    data object LightYello : PrimaryButtonVariant()
    data object Green : PrimaryButtonVariant()
}

@Stable
sealed class BadgeAlignment {

    data class TopEnd(
        val offsetX: Dp = (-30).dp,
        val offsetY: Dp = (-10).dp
    ) : BadgeAlignment()

    data class TopEndInset(
        val offsetX: Dp = 6.dp,
        val offsetY: Dp = (-12).dp
    ) : BadgeAlignment()

    data class Custom(
        val alignment: Alignment = Alignment.TopEnd,
        val offsetX: Dp = 0.dp,
        val offsetY: Dp = 0.dp
    ) : BadgeAlignment()
}

expect fun playClickSound()

expect fun initSound()
