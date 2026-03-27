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
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
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

val ColorNeutralBlack = Color(0xFF030712)

enum class ButtonAnimationType {
    NONE,
    PRESS,
    BOUNCE,
    LIFT,
    SHAKE,
    PULSE,
    SLAM,
    ROTATE,
    COMBO
}

enum class ButtonHapticType {
    CONFIRM,
    CONTEXT_CLICK,
    GESTURE_END,
    GESTURE_THRESHOLD_ACTIVATE,
    KEYBOARD_TAP,
    LONG_PRESS,
    REJECT,
    SEGMENT_FREQUENT_TICK,
    SEGMENT_TICK,
    TEXT_HANDLE_MOVE,
    TOGGLE_OFF,
    TOGGLE_ON,
    VIRTUAL_KEY
}

fun ButtonHapticType.toHaptic(): HapticFeedbackType {
    return when (this) {
        ButtonHapticType.CONFIRM -> HapticFeedbackType.Confirm
        ButtonHapticType.CONTEXT_CLICK -> HapticFeedbackType.ContextClick
        ButtonHapticType.GESTURE_END -> HapticFeedbackType.GestureEnd
        ButtonHapticType.GESTURE_THRESHOLD_ACTIVATE -> HapticFeedbackType.GestureThresholdActivate
        ButtonHapticType.KEYBOARD_TAP -> HapticFeedbackType.KeyboardTap
        ButtonHapticType.LONG_PRESS -> HapticFeedbackType.LongPress
        ButtonHapticType.REJECT -> HapticFeedbackType.Reject
        ButtonHapticType.SEGMENT_FREQUENT_TICK -> HapticFeedbackType.SegmentFrequentTick
        ButtonHapticType.SEGMENT_TICK -> HapticFeedbackType.SegmentTick
        ButtonHapticType.TEXT_HANDLE_MOVE -> HapticFeedbackType.TextHandleMove
        ButtonHapticType.TOGGLE_OFF -> HapticFeedbackType.ToggleOff
        ButtonHapticType.TOGGLE_ON -> HapticFeedbackType.ToggleOn
        ButtonHapticType.VIRTUAL_KEY -> HapticFeedbackType.VirtualKey
    }
}

@Composable
fun ComposeAnimations() {

    val animations = ButtonAnimationType.entries
    val haptics = ButtonHapticType.entries

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(animations.size * haptics.size) { index ->

            val animation = animations[index % animations.size]
            val haptic = haptics[index % haptics.size]

            ComposeButton(
                modifier = Modifier.fillMaxWidth(),
                text = "${animation.name} + ${haptic.name}",
                animationType = animation,
                hapticType = haptic
            )
        }
    }
}

//@Composable
//fun ComposeButton(
//    modifier: Modifier = Modifier,
//    text: String? = null,
//    variant: PrimaryButtonVariant = PrimaryButtonVariant.Yellow,
//    isLoading: Boolean = false,
//    isEnabled: Boolean = true,
//    cornerRadius: Dp = 8.dp,
//    shadowOffsetFactor: Dp = 3.dp,
//    onClick: () -> Unit = {},
//    animationType: ButtonAnimationType = ButtonAnimationType.SLAM,
//) {
//
//    val backgroundBrush = when {
//        !isEnabled -> DisabledButtonBrush
//        variant is PrimaryButtonVariant.Green -> GreenButtonBrush
//        variant is PrimaryButtonVariant.LightYello -> LightYelloButtonBrush
//        else -> YellowButtonBrush
//    }
//
//    val borderBrush = if (isEnabled) EnabledBorderBrush else DisabledBorderBrush
//
//    val displayText = if (isLoading) "LOADING…" else text
//
//
//    val scaleX = remember { Animatable(1f) }
//    val scaleY = remember { Animatable(1f) }
//    val translationY = remember { Animatable(0f) }
//    val translationX = remember { Animatable(0f) }
//    val rotation = remember { Animatable(0f) }
//    val shadowOffset = remember { Animatable(shadowOffsetFactor.value) }
//
//    val animationCollection = remember {
//        Json.decodeFromString<AnimationCollection>(animationConfig)
//    }
//
//    val animationKey = animationType.name.lowercase()
//    val scope = rememberCoroutineScope()
//    var animationJob by remember { mutableStateOf<Job?>(null) }
//
//    Box(
//        modifier = modifier,
//        contentAlignment = Alignment.TopEnd,
//
//    ) {
//        CompositionLocalProvider(
//            LocalRippleConfiguration provides null
//        ) {
//
//
//            Button(
//                modifier = Modifier
//                    .graphicsLayer {
//                        this.scaleX = scaleX.value
//                        this.scaleY = scaleY.value
//                        this.translationY = translationY.value
//                        this.translationX = translationX.value
//                        this.rotationZ = rotation.value
//                    }
//                    .drawBehind {
//                        val cr = CornerRadius(cornerRadius.toPx())
//                        val offset = shadowOffset.value * density
//
//                        // Border stroke
//                        drawRoundRect(
//                            brush = borderBrush,
//                            topLeft = Offset(0f, 0f),
//                            size = size,
//                            cornerRadius = cr,
//                            style = Stroke(width = 1.5.dp.toPx())
//                        )
//
//                        // Bottom shadow — shifted down by animated offset
//                        drawRoundRect(
//                            brush = borderBrush,
//                            topLeft = Offset(0f, offset + 1.6.dp.toPx()),
//                            size = size,
//                            cornerRadius = cr
//                        )
//                    }
//                    .background(
//                        brush = backgroundBrush,
//                        shape = RoundedCornerShape(cornerRadius)
//                    )
////                .padding(horizontal = 1.dp, vertical = 2.dp)
//                    .alpha(if (isLoading) 0.6f else 1f),
////            contentPadding = PaddingValues(horizontal = 1.dp, vertical = 2.dp),
//                onClick = {
//                    if (!isEnabled || isLoading) return@Button
//
//                    playClickSound()
//
//                    animationJob?.cancel()
//
//                    val config = animationCollection.animations[animationKey]
//                    if (config != null) {
//                        animationJob = scope.launch {
//                            scaleX.snapTo(1f)
//                            scaleY.snapTo(1f)
//                            translationX.snapTo(0f)
//                            translationY.snapTo(0f)
//                            rotation.snapTo(0f)
//                            shadowOffset.snapTo(shadowOffsetFactor.value)
//
//                            runAnimation(
//                                config = config,
//                                defaults = animationCollection.defaults,
//                                scaleX = scaleX,
//                                scaleY = scaleY,
//                                translationX = translationX,
//                                translationY = translationY,
//                                rotation = rotation,
//                                shadowOffset = shadowOffset
//                            )
//
//                            onClick()
//                        }
//                    } else {
//                        onClick()
//                    }
//                },
//                shape = RoundedCornerShape(cornerRadius),
//                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
//                interactionSource = remember { MutableInteractionSource() },
////            contentPadding = PaddingValues(0.dp),
//            ) {
//
//                Row(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .padding(horizontal = 16.dp),
//                    verticalAlignment = Alignment.CenterVertically,
//                    horizontalArrangement = Arrangement.Center
//                ) {
//
//                    if (isLoading) {
//                        CircularProgressIndicator(
//                            modifier = Modifier.size(12.dp),
//                            strokeWidth = 2.dp,
//                            color = ColorTextSecondary
//                        )
//                        Spacer(Modifier.width(8.dp))
//                    }
//
//                    displayText?.let {
//                        Text(text = it, color = Color.Black)
//                    }
//                }
//            }
//        }
//    }
//}


@Composable
fun ComposeButton(
    modifier: Modifier = Modifier,
    text: String? = null,
    variant: PrimaryButtonVariant = PrimaryButtonVariant.Yellow,
    leadingIcon: Any? = null,
    trailingText: String? = null,
    trailingTextColor: Color? = null,
    isLoading: Boolean = false,
    isEnabled: Boolean = true,
    cornerRadius: Dp = 8.dp,
    shadowOffsetFactor: Dp = 2.5.dp,                                         // CHANGED: was 1.5.dp, now 4.dp for slam travel
    floatingBadge: Any? = null,
    floatingBadgeSize: Dp = 30.dp,
    topLeftBadgeOffsetX: Dp = 6.dp,
    topLeftBadgeOffsetY: Dp = (-24).dp,
    floatingBadgeAlignment: BadgeAlignment = BadgeAlignment.TopEnd(),
    onClick: () -> Unit = {},
    iconName: String? = null,
    selection: String? = null,
    animationType: ButtonAnimationType = ButtonAnimationType.SLAM,          // CHANGED: default to SLAM
    hapticType: ButtonHapticType = ButtonHapticType.SEGMENT_TICK
) {

    val shadowOffset = remember { Animatable(shadowOffsetFactor.value) }    // ADDED: animated shadow

    val animationState = remember { AnimationState() }
    val engine = remember {
        AnimationEngine(
            Json.decodeFromString(animationConfig)
        )
    }
    val scope = rememberCoroutineScope()

    val haptic = LocalHapticFeedback.current


    var animationJob by remember { mutableStateOf<Job?>(null) }

    val backgroundBrush by remember(isEnabled, variant) {
        mutableStateOf(
            when {
                !isEnabled -> DisabledButtonBrush
                variant is PrimaryButtonVariant.Green -> GreenButtonBrush
                variant is PrimaryButtonVariant.LightYello -> LightYelloButtonBrush
                else -> YellowButtonBrush
            }
        )
    }

    val borderBrush by remember(isEnabled) {
        mutableStateOf(
            if (isEnabled) EnabledBorderBrush else DisabledBorderBrush
        )
    }

    val displayText = if (isLoading) "LOADING…" else text


    val guardedClick = remember(onClick, isEnabled, isLoading, text) {
        {
            haptic.performHapticFeedback(hapticType.toHaptic())
            // Cancel any running animation first
            animationJob?.cancel()

            if (!isEnabled && !isLoading) {
                // Disabled state: play shake animation
                scope.launch {
                    engine.play(
                        name = ButtonAnimationType.SHAKE.name.lowercase(),
                        state = animationState,
                        shadowDefault = shadowOffsetFactor.value
                    )
                    onClick()
                }
            }

            if (isEnabled && !isLoading) {
                playClickSound()

                animationJob = scope.launch {
                    // Reset all values before starting
                    scope.launch {
                        engine.play(
                            name = animationType.name.lowercase(),
                            state = animationState,
                            shadowDefault = shadowOffsetFactor.value
                        )
                    }

                    // onClick fires AFTER animation completes
                    onClick()
                }
            }
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopEnd
    ) {
        // ADDED: Disable ripple since we have custom animations
        CompositionLocalProvider(
            LocalRippleConfiguration provides null
        ) {
            Button(
                modifier = Modifier
                    .graphicsLayer {
                        this.scaleX = animationState.scaleX.value
                        this.scaleY = animationState.scaleY.value
                        this.translationY = animationState.translationY.value
                        this.translationX = animationState.translationX.value
                        rotationZ = animationState.rotation.value
                    }
                    .drawBehind {
                        val cr = CornerRadius(cornerRadius.toPx())
                        val offset = shadowOffset.value * density              // CHANGED: animated shadow offset

                        // Border stroke
                        drawRoundRect(
                            brush = borderBrush,
                            topLeft = Offset(0f, 0f),
                            size = size,
                            cornerRadius = cr,
                            style = Stroke(width = 1.5.dp.toPx())
                        )

                        // Bottom shadow — shifted down by animated offset
                        drawRoundRect(
                            brush = borderBrush,
                            topLeft = Offset(0f, offset + 1.6.dp.toPx()),
                            size = size,
                            cornerRadius = CornerRadius(10.dp.toPx())
                        )
                    }
                    .background(
                        brush = backgroundBrush,
                        shape = RoundedCornerShape(cornerRadius)
                    )
                    .alpha(if (isLoading) 0.6f else 1f),
                onClick = guardedClick,
                shape = RoundedCornerShape(cornerRadius),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                interactionSource = remember { MutableInteractionSource() },   // ADDED: for ripple suppression
                contentPadding = PaddingValues(horizontal = 1.dp, vertical = 2.dp),  // CHANGED: moved from modifier padding
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Leading icon / spinner
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp,
                            color = ColorTextSecondary
                        )
                        Spacer(Modifier.width(8.dp))
                    } else if (leadingIcon != null) {
//                        BeBettaAsyncImage(
//                            modifier = Modifier.size(20.dp),
//                            model = leadingIcon
//                        )
                        Spacer(Modifier.width(3.dp))
                    }

                    when {
                        displayText != null -> Text(
                            text = displayText,
                            color = Color.Black
                        )
                    }
                    // Trailing text
                    if (trailingText != null) {
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = trailingText,
                            color = Color.Black
                        )
                    }
                }
            }
        }
    }
}

class AnimationEngine(
    private val collection: AnimationCollection
) {

    suspend fun play(
        name: String,
        state: AnimationState,
        shadowDefault: Float = 0f
    ) {
        val config = collection.animations[name] ?: return

        val registry = AnimationRegistry(state)

        state.reset(shadowDefault)

        runAnimation(config, collection.defaults, registry)
    }
}

suspend fun runAnimation(
    config: AnimationConfig,
    defaults: DefaultConfig,
    registry: AnimationRegistry
) {

    suspend fun runStep(step: AnimationStep) {
        val animatable = registry.get(step.property) ?: return

        val duration = step.duration ?: defaults.duration
        val spec = (step.easing ?: defaults.easing).toSpec(duration)

        if (step.delay > 0) delay(step.delay.toLong())

        animatable.animateTo(step.value, spec)
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


class AnimationState {

    val scaleX = Animatable(1f)
    val scaleY = Animatable(1f)
    val translationX = Animatable(0f)
    val translationY = Animatable(0f)
    val rotation = Animatable(0f)
    val shadowOffset = Animatable(0f)

    suspend fun reset(shadowDefault: Float) {
        scaleX.snapTo(1f)
        scaleY.snapTo(1f)
        translationX.snapTo(0f)
        translationY.snapTo(0f)
        rotation.snapTo(0f)
        shadowOffset.snapTo(shadowDefault)
    }
}


class AnimationRegistry(
    private val state: AnimationState
) {

    private val map = mapOf(
        "scaleX" to state.scaleX,
        "scaleY" to state.scaleY,
        "translationX" to state.translationX,
        "translationY" to state.translationY,
        "rotation" to state.rotation,
        "shadowOffset" to state.shadowOffset
    )

    fun get(property: String) = map[property]
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
    val value: Float,
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
                  "value": 0.88,
                  "duration": 180
                },
                {
                  "property": "scaleX",
                  "value": 1.05,
                  "duration": 180
                }
              ]
            },
            {
              "mode": "parallel",
              "items": [
                {
                  "property": "scaleY",
                  "value": 1.0,
                  "duration": 400,
                  "easing": {
                    "type": "spring",
                    "dampingRatio": 0.45,
                    "stiffness": 300
                  }
                },
                {
                  "property": "scaleX",
                  "value": 1.0,
                  "duration": 400,
                  "easing": {
                    "type": "spring",
                    "dampingRatio": 0.45,
                    "stiffness": 300
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
                { "property": "scaleX", "value": 0.9 },
                { "property": "scaleY", "value": 0.9 }
              ]
            },
            {
              "mode": "parallel",
              "items": [
                {
                  "property": "scaleX",
                  "value": 1.1,
                  "easing": { "type": "spring", "dampingRatio": 0.4 }
                },
                {
                  "property": "scaleY",
                  "value": 1.1,
                  "easing": { "type": "spring", "dampingRatio": 0.4 }
                }
              ]
            },
            {
              "mode": "parallel",
              "items": [
                { "property": "scaleX", "value": 1.0 },
                { "property": "scaleY", "value": 1.0 }
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
                  "value": -8,
                  "duration": 45
                },
                {
                  "property": "translationX",
                  "value": 8,
                  "duration": 45
                }
              ]
            },
            {
              "mode": "sequence",
              "items": [
                {
                  "property": "translationX",
                  "value": 0,
                  "duration": 80
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
                { "property": "scaleX", "value": 1.08, "duration": 150 },
                { "property": "scaleY", "value": 1.08, "duration": 150 }
              ]
            },
            {
              "mode": "parallel",
              "items": [
                { "property": "scaleX", "value": 1.0, "duration": 150 },
                { "property": "scaleY", "value": 1.0, "duration": 150 }
              ]
            }
          ]
        },
        "rotate": {
          "steps": [
            {
              "mode": "sequence",
              "items": [
                { "property": "rotation", "value": 10 },
                { "property": "rotation", "value": -10 },
                { "property": "rotation", "value": 0 }
              ]
            }
          ]
        },
        "lift": {
          "steps": [
            {
              "mode": "sequence",
              "items": [
                {
                  "property": "translationY",
                  "value": -14,
                  "duration": 140
                },
                {
                  "property": "translationY",
                  "value": 0,
                  "easing": {
                    "type": "spring",
                    "dampingRatio": 0.45,
                    "stiffness": 300
                  }
                }
              ]
            }
          ]
        },
        "glow": {
          "steps": [
            {
              "mode": "parallel",
              "items": [
                { "property": "scaleX", "value": 1.05, "duration": 100 },
                { "property": "scaleY", "value": 1.05, "duration": 100 }
              ]
            },
            {
              "mode": "parallel",
              "items": [
                { "property": "scaleX", "value": 1.0, "duration": 100 },
                { "property": "scaleY", "value": 1.0, "duration": 100 }
              ]
            }
          ]
        },
        "slam": {
          "steps": [
            {
              "mode": "parallel",
              "items": [
                { "property": "scaleY", "value": 0.92, "duration": 100 },
                { "property": "translationY", "value": 4, "duration": 100 },
                { "property": "shadowOffset", "value": 0, "duration": 100 }
              ]
            },
            {
              "mode": "parallel",
              "items": [
                {
                  "property": "scaleY", "value": 1.0,
                  "easing": { "type": "spring", "dampingRatio": 0.7, "stiffness": 600 }
                },
                {
                  "property": "translationY", "value": 0,
                  "easing": { "type": "spring", "dampingRatio": 0.7, "stiffness": 600 }
                },
                {
                  "property": "shadowOffset", "value": 3,
                  "easing": { "type": "spring", "dampingRatio": 0.7, "stiffness": 600 }
                }
              ]
            }
          ]
        },
        "slamsmall": {
          "steps": [
            {
              "mode": "parallel",
              "items": [
                { "property": "scaleY", "value": 0.92, "duration": 100 },
                { "property": "translationY", "value": 2, "duration": 100 },
                { "property": "shadowOffset", "value": 0, "duration": 100 }
              ]
            },
            {
              "mode": "parallel",
              "items": [
                {
                  "property": "scaleY", "value": 1.0,
                  "easing": { "type": "spring", "dampingRatio": 0.7, "stiffness": 600 }
                },
                {
                  "property": "translationY", "value": 0,
                  "easing": { "type": "spring", "dampingRatio": 0.7, "stiffness": 600 }
                },
                {
                  "property": "shadowOffset", "value": 2,
                  "easing": { "type": "spring", "dampingRatio": 0.7, "stiffness": 600 }
                }
              ]
            }
          ]
        }
      }
    }
""".trimIndent()




private val EnabledBorderBrush = Brush.verticalGradient(
    listOf(ColorNeutralBlack, ColorNeutralBlack)
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
