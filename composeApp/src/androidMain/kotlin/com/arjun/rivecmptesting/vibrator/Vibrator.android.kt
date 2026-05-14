package com.arjun.rivecmptesting.vibrator

object VibratorInitializer {

    lateinit var vibrator: Vibrator
}

actual object VibratorProvider {

    actual fun provideVibrator(): Vibrator {

        return VibratorInitializer.vibrator
    }
}