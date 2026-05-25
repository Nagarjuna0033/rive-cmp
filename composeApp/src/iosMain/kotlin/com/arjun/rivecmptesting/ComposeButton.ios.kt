package com.arjun.rivecmptesting

// iosMain
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.Foundation.NSBundle
import platform.Foundation.NSURL

private var audioPlayer: AVAudioPlayer? = null

@OptIn(ExperimentalForeignApi::class)
actual fun initSound() {
    try {
        // Set up audio session
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayback, error = null)
        session.setActive(true, error = null)

        // Load click sound from bundle
        val url = NSBundle.mainBundle.URLForResource("click", withExtension = "mp3")
            ?: NSBundle.mainBundle.URLForResource("click", withExtension = "wav")
            ?: run {
                println("[Sound] click sound file not found in bundle")
                return
            }

        audioPlayer = AVAudioPlayer(contentsOfURL = url, error = null)
        audioPlayer?.prepareToPlay()
        println("[Sound] initSound success")

    } catch (e: Exception) {
        println("[Sound] initSound error: ${e.message}")
    }
}

actual fun playClickSound() {
    try {
        // Reset to start so rapid taps always play from beginning
        audioPlayer?.currentTime = 0.0
        audioPlayer?.play()
    } catch (e: Exception) {
        println("[Sound] playClickSound error: ${e.message}")
    }
}