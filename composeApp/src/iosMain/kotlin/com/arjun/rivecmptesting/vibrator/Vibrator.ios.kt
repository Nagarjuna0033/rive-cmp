package com.arjun.rivecmptesting.vibrator

import com.arjun.rivecmptesting.haptics.IOSVibrator

actual object VibratorProvider {

    private val vibrator by lazy {
        IOSVibrator()
    }

    actual fun provideVibrator(): Vibrator {
        return vibrator
    }
}