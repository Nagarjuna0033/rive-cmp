package com.arjun.rivecmptesting.haptics


/**
 * Cross-platform haptic capability contract.
 *
 * Each platform exposes:
 * - primitive composition support
 *
 * This architecture intentionally focuses on:
 * - premium primitive compositions
 * - semantic Compose haptics
 *
 * and avoids:
 * - waveform
 * - oneshot
 */
interface HapticCapabilities {

    /**
     * Whether advanced primitive compositions
     * are supported.
     *
     * Android:
     * API 30+ (Android 11+)
     */
    val supportsComposition: Boolean
}

/**
 * Runtime haptic configuration.
 *
 * Allows:
 * - accessibility scaling
 * - reduced intensity mode
 * - user preference control
 */
data class HapticConfig(

    /**
     * Global intensity multiplier.
     *
     * Range:
     * 0f -> disabled
     * 1f -> normal
     * >1f -> boosted
     */
    val intensityMultiplier: Float = 1f
)

/**
 * Cross-platform haptic effect model.
 *
 * Shared code defines abstract effects.
 *
 * Platform renderers translate effects
 * into native platform APIs.
 */
sealed interface HapticEffect {

    /**
     * Advanced primitive composition.
     *
     * Android:
     * VibrationEffect.startComposition()
     *
     * iOS:
     * CoreHaptics
     */
    data class Composition(
        val primitives: List<Primitive>
    ) : HapticEffect

    /**
     * Single primitive step.
     */
    data class Primitive(

        val type: PrimitiveType,

        /**
         * Primitive intensity.
         */
        val scale: Float = 1f,

        /**
         * Delay before execution.
         */
        val delay: Int = 0
    )

    /**
     * DSL Builder.
     */
    class Builder {

        private val primitives =
            mutableListOf<Primitive>()

        fun primitive(
            type: PrimitiveType,
            scale: Float = 1f,
            delay: Int = 0
        ) {

            primitives += Primitive(
                type = type,
                scale = scale,
                delay = delay
            )
        }

        internal fun build(): HapticEffect {

            return Composition(
                primitives = primitives
            )
        }
    }
}

/**
 * Cross-platform primitive abstraction.
 */
enum class PrimitiveType {

    CLICK,

    TICK,

    THUD,

    SPIN,

    SLOW_RISE,

    QUICK_FALL
}
