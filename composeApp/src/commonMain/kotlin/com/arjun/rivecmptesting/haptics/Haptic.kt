package com.arjun.rivecmptesting.haptics

import com.arjun.rivecmptesting.vibrator.VibratorProvider

/**
 * Centralized reusable premium haptic recipes.
 */
object HapticEffects {

    /**
     * Splash / Launch
     */
    val Splash =
        HapticEffect.Composition(

            primitives = listOf(

                HapticEffect.Primitive(
                    type = PrimitiveType.SLOW_RISE
                ),

                HapticEffect.Primitive(
                    type = PrimitiveType.QUICK_FALL
                )
            )
        )

    /**
     * Primary CTA Button
     */
    val PrimaryButton =
        HapticEffect.Composition(

            primitives = listOf(

                HapticEffect.Primitive(
                    type = PrimitiveType.CLICK,
                )
            )
        )

    /**
     * Gem / Reward Credit
     */
    val GemCredit =
        HapticEffect.Composition(

            primitives = listOf(

                HapticEffect.Primitive(
                    type = PrimitiveType.SLOW_RISE
                ),

                HapticEffect.Primitive(
                    type = PrimitiveType.CLICK
                )
            )
        )

    /**
     * Payment Success
     */
    val PaymentSuccess =
        HapticEffect.Composition(

            primitives = listOf(

                HapticEffect.Primitive(
                    type = PrimitiveType.SLOW_RISE
                ),

                HapticEffect.Primitive(
                    type = PrimitiveType.QUICK_FALL
                ),

                HapticEffect.Primitive(
                    type = PrimitiveType.CLICK
                )
            )
        )

    /**
     * Match Making
     */
    val MatchMaking =
        HapticEffect.Composition(

            primitives = listOf(

                HapticEffect.Primitive(
                    type = PrimitiveType.SLOW_RISE
                ),

                HapticEffect.Primitive(
                    type = PrimitiveType.SPIN,
                )
            )
        )

    /**
     * Chest Open
     */
    val ChestOpen =
        HapticEffect.Composition(

            primitives = listOf(

                HapticEffect.Primitive(
                    type = PrimitiveType.TICK,
                ),

                HapticEffect.Primitive(
                    type = PrimitiveType.CLICK,
                ),

                HapticEffect.Primitive(
                    type = PrimitiveType.THUD
                )
            )
        )
}




/**
 * Centralized application haptics facade.
 *
 * UI layer should ONLY communicate with this object.
 *
 * Benefits:
 * - centralized PRD mapping
 * - reusable effects
 * - platform abstraction
 * - easier iOS support
 * - cleaner Compose code
 */
object Haptics {

    private val vibrator by lazy {
        VibratorProvider.provideVibrator()
    }

    /**
     * Splash / launch haptic.
     */
    fun splash() {

        vibrator.vibrate(
            HapticEffects.Splash
        )
    }

    /**
     * Primary CTA button.
     */
    fun primaryButton() {

        vibrator.vibrate(
            HapticEffects.PrimaryButton
        )
    }

    /**
     * Reward / gem credit.
     */
    fun gemCredit() {

        vibrator.vibrate(
            HapticEffects.GemCredit
        )
    }

    /**
     * Payment success.
     */
    fun paymentSuccess() {

        vibrator.vibrate(
            HapticEffects.PaymentSuccess
        )
    }

    /**
     * Match making.
     */
    fun matchMaking() {

        vibrator.vibrate(
            HapticEffects.MatchMaking
        )
    }

    /**
     * Chest open reward.
     */
    fun chestOpen() {

        vibrator.vibrate(
            HapticEffects.ChestOpen
        )
    }

    /**
     * Custom DSL composition.
     */
    fun custom(
        builder: HapticEffect.Builder.() -> Unit
    ) {

        vibrator.vibrate(builder)
    }

    /**
     * Stop active vibration.
     */
    fun cancel() {

        vibrator.cancel()
    }
}