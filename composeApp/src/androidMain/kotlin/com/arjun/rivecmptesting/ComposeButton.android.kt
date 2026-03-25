package com.arjun.rivecmptesting

import android.media.MediaPlayer
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private var soundPool: SoundPool? = null
private var clickSoundId: Int = 0

actual fun initSound() {
    soundPool = SoundPool.Builder()
        .setMaxStreams(5)
        .build()

    clickSoundId = soundPool?.load(appContext, R.raw.click, 1) ?: 0
}

actual fun playClickSound() {
    soundPool?.play(
        clickSoundId,
        1f, // left volume
        1f, // right volume
        1,  // priority
        0,  // loop
        1f  // rate
    )
}