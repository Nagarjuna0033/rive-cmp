package com.arjun.rivecmptesting

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.File
import app.rive.runtime.kotlin.core.Rive
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {
//    var riveFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

//        application.assets.open("home_nav.riv").use { inputStream ->
//            val fileBytes = inputStream.readBytes()
//            riveFile = File(fileBytes)
//        }

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