package com.arjun.rivecmptesting

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.rive.runtime.kotlin.core.Rive
import com.arjun.rivecmptesting.haptics.AndroidVibrator
import com.arjun.rivecmptesting.haptics.Haptics
import com.arjun.rivecmptesting.vibrator.VibratorInitializer

class MainActivity : ComponentActivity() {
//    var riveFile: File? = null

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

//        application.assets.open("home_nav.riv").use { inputStream ->
//            val fileBytes = inputStream.readBytes()
//            riveFile = File(fileBytes)
//        }

        VibratorInitializer.vibrator =
            AndroidVibrator(
                applicationContext
            )

        Haptics.splash()

        Rive.init(this)
        appContext = applicationContext
        initSound()

        setContent {

//            val riveView: MainViewModel = koinInject()

//            riveView.setRiveView(RiveAnimationView(this).apply {
//                setRiveFile(riveFile!!)
//                setAutoPlay(true)
//            })

            App()
        }
    }


    override fun onDestroy(){
//        riveFile?.release()
        super.onDestroy()
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}