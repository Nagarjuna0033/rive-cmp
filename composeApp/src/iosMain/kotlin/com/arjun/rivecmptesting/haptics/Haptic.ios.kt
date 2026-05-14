package com.arjun.rivecmptesting.haptics

import com.arjun.rivecmptesting.vibrator.Vibrator
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreHaptics.CHHapticDynamicParameterIDHapticIntensityControl
import platform.CoreHaptics.CHHapticEngine
import platform.CoreHaptics.CHHapticEvent
import platform.CoreHaptics.CHHapticEventParameter
import platform.CoreHaptics.CHHapticEventParameterIDHapticIntensity
import platform.CoreHaptics.CHHapticEventParameterIDHapticSharpness
import platform.CoreHaptics.CHHapticEventTypeHapticContinuous
import platform.CoreHaptics.CHHapticParameterCurve
import platform.CoreHaptics.CHHapticParameterCurveControlPoint
import platform.CoreHaptics.CHHapticPattern
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType

class IOSVibrator(
    override val config: HapticConfig = HapticConfig()
) : Vibrator {

    @OptIn(ExperimentalForeignApi::class)
    private val engine: CHHapticEngine? by lazy {

        runCatching {

            val capabilities =
                CHHapticEngine.capabilitiesForHardware()

            if (!capabilities.supportsHaptics) {
                return@runCatching null
            }

            val engine =
                CHHapticEngine()

            engine.startAndReturnError(null)

            engine

        }.getOrNull()
    }

    override val capabilities =
        object : HapticCapabilities {

            override val supportsComposition: Boolean
                get() = engine != null
        }

    override val isVibrationSupported: Boolean
        get() = true

    override fun vibrate(
        effect: HapticEffect
    ) {

        when (effect) {

            is HapticEffect.Composition ->
                handleComposition(effect)
        }
    }

    private fun handleComposition(
        effect: HapticEffect.Composition
    ) {

        val primitives =
            effect.primitives.map {
                it.type
            }

        when {

            // ---------------------------------------------------
            // Primary Button
            // ---------------------------------------------------

            primitives == listOf(
                PrimitiveType.CLICK
            ) -> {

                UIImpactFeedbackGenerator(
                    style = UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium
                ).impactOccurred()
            }

            // ---------------------------------------------------
            // Chest Open
            // ---------------------------------------------------

            primitives.contains(
                PrimitiveType.THUD
            ) -> {

                playChestOpen()
            }

            // ---------------------------------------------------
            // Payment Success
            // ---------------------------------------------------

            primitives.contains(
                PrimitiveType.QUICK_FALL
            ) -> {

                UINotificationFeedbackGenerator()
                    .notificationOccurred(
                        UINotificationFeedbackType.UINotificationFeedbackTypeSuccess
                    )
            }

            // ---------------------------------------------------
            // Splash / MatchMaking
            // ---------------------------------------------------

            primitives.contains(
                PrimitiveType.SLOW_RISE
            ) -> {

                playContinuousHaptic()
            }
        }
    }

    /**
     * Premium cinematic CoreHaptics curve.
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun playContinuousHaptic(
        rise: Boolean = true
    ) {

        val engine = engine ?: return

        runCatching {

            val intensity =
                CHHapticEventParameter(
                    parameterID =
                        CHHapticEventParameterIDHapticIntensity,
                    value = 1f
                )

            val sharpness =
                CHHapticEventParameter(
                    parameterID =
                        CHHapticEventParameterIDHapticSharpness,
                    value = 0.5f
                )

            val event =
                CHHapticEvent(
                    eventType =
                        CHHapticEventTypeHapticContinuous,
                    parameters = listOf(
                        intensity,
                        sharpness
                    ),
                    relativeTime = 0.0,
                    duration = 0.6
                )

            val curve =
                CHHapticParameterCurve(

                    parameterID =
                        CHHapticDynamicParameterIDHapticIntensityControl,

                    controlPoints = listOf(

                        CHHapticParameterCurveControlPoint(
                            relativeTime = 0.0,
                            value = if (rise) 0.1f else 1f
                        ),

                        CHHapticParameterCurveControlPoint(
                            relativeTime = 0.3,
                            value = 1f
                        ),

                        CHHapticParameterCurveControlPoint(
                            relativeTime = 0.6,
                            value = if (rise) 0.2f else 0f
                        )
                    ),

                    relativeTime = 0.0
                )

            val pattern =
                CHHapticPattern(
                    events = listOf(event),
                    parameterCurves = listOf(curve),
                    error = null
                )

            val player =
                engine.createPlayerWithPattern(
                    pattern,
                    error = null
                )

            player?.startAtTime(
                time = 0.0,
                error = null
            )

        }.getOrNull()
    }

    /**
     * Sequential reward impacts.
     */
    private fun playChestOpen() {

        UIImpactFeedbackGenerator(
            style = UIImpactFeedbackStyle.UIImpactFeedbackStyleLight
        ).impactOccurred()

        UIImpactFeedbackGenerator(
            style = UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium
        ).impactOccurred()

        UIImpactFeedbackGenerator(
            style = UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy
        ).impactOccurred()
    }

    override fun cancel() {

        runCatching {

            engine?.stopWithCompletionHandler(null)
        }
    }
}