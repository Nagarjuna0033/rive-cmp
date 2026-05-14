package com.arjun.rivecmptesting.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.annotation.RequiresApi
import com.arjun.rivecmptesting.vibrator.Vibrator

class AndroidVibrator(
    context: Context,
    override val config: HapticConfig = HapticConfig()
) : Vibrator {

    private val vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        }
    }

    override val isVibrationSupported: Boolean
        get() = vibrator.hasVibrator()

    override val capabilities = object : HapticCapabilities {
        override val supportsComposition: Boolean
            get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    vibrator.hasVibrator()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun vibrate(effect: HapticEffect) {
        if (!capabilities.supportsComposition) return
        when (effect) {
            is HapticEffect.Composition -> handleComposition(effect)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun handleComposition(effect: HapticEffect.Composition) {
        val composition = VibrationEffect.startComposition()
        var addedAny = false

        effect.primitives.forEach { primitive ->
            val primitiveId = mapPrimitive(primitive.type)
            if (vibrator.areAllPrimitivesSupported(primitiveId)) {
                composition.addPrimitive(
                    primitiveId,
                    (primitive.scale * config.intensityMultiplier).coerceIn(0f, 1f),
                    primitive.delay
                )
                addedAny = true
            }
        }

        if (addedAny) {
            vibrator.vibrate(composition.compose())
        }
    }

    override fun cancel() {
        vibrator.cancel()
    }

    private fun mapPrimitive(primitive: PrimitiveType): Int {
        return when (primitive) {
            PrimitiveType.CLICK -> VibrationEffect.Composition.PRIMITIVE_CLICK
            PrimitiveType.TICK -> VibrationEffect.Composition.PRIMITIVE_TICK
            PrimitiveType.THUD -> VibrationEffect.Composition.PRIMITIVE_THUD
            PrimitiveType.SPIN -> VibrationEffect.Composition.PRIMITIVE_SPIN
            PrimitiveType.SLOW_RISE -> VibrationEffect.Composition.PRIMITIVE_SLOW_RISE
            PrimitiveType.QUICK_FALL -> VibrationEffect.Composition.PRIMITIVE_QUICK_FALL
        }
    }
}
