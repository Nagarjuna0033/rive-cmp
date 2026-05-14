package com.arjun.rivecmptesting.vibrator

import com.arjun.rivecmptesting.haptics.HapticCapabilities
import com.arjun.rivecmptesting.haptics.HapticConfig
import com.arjun.rivecmptesting.haptics.HapticEffect

/**
 * Cross-platform premium haptic renderer.
 */
interface Vibrator {

    val capabilities: HapticCapabilities

    val config: HapticConfig

    val isVibrationSupported: Boolean

    /**
     * Render haptic effect.
     */
    fun vibrate(
        effect: HapticEffect
    )

    /**
     * DSL composition builder.
     */
    fun vibrate(
        builder: HapticEffect.Builder.() -> Unit
    ) {

        val effect =
            HapticEffect.Builder()
                .apply(builder)
                .build()

        vibrate(effect)
    }

    /**
     * Cancel ongoing haptics.
     */
    fun cancel()
}

/**
 * Platform provider.
 */
expect object VibratorProvider {

    fun provideVibrator(): Vibrator
}