package com.arjun.rivecmptesting
//
//import android.os.Bundle
//import androidx.activity.ComponentActivity
//import androidx.activity.compose.setContent
//import androidx.compose.foundation.clickable
//import androidx.compose.foundation.layout.Box
//import androidx.compose.foundation.layout.Column
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.foundation.layout.fillMaxWidth
//import androidx.compose.foundation.layout.height
//import androidx.compose.foundation.layout.padding
//import androidx.compose.material3.MaterialTheme
//import androidx.compose.material3.Scaffold
//import androidx.compose.material3.Text
//import androidx.compose.runtime.Composable
//import androidx.compose.runtime.LaunchedEffect
//import androidx.compose.runtime.getValue
//import androidx.compose.runtime.produceState
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.semantics.contentDescription
//import androidx.compose.ui.semantics.semantics
//import androidx.compose.ui.unit.dp
//import app.rive.Result
//import app.rive.Result.Loading.andThen
//import app.rive.Rive
//import app.rive.RiveFileSource
//import app.rive.RiveLog
//import app.rive.core.RiveWorker
//import app.rive.rememberRegisteredFont
//import app.rive.rememberRiveFile
//import app.rive.rememberRiveWorker
//import app.rive.rememberViewModelInstance
//import app.rive.runtime.kotlin.core.Rive
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.withContext
//
//class RiveClickActivity : ComponentActivity() {
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//
//        Rive.init(this)
//
//        setContent {
//            MaterialTheme {
//
//                Scaffold(
//                    containerColor = Color.White
//                ) { padding ->
//
//                    val riveWorker = rememberRiveWorker()
//
//                    Column(
//                        modifier = Modifier
//                            .fillMaxSize()
//                            .padding(padding)
//                    ) {
//
//                        Text(
//                            text = "Testing",
//                            modifier = Modifier
//                                .fillMaxWidth()
//                                .padding(16.dp)
//                        )
//
//                        RiveClickTesting(
//                            riveWorker = riveWorker,
//                            index = 1
//                        )
//                    }
//                }
//
//            }
//        }
//    }
//}
//
//
//@Composable
//fun RiveClickTesting(
//    modifier: Modifier = Modifier,
//    riveWorker: RiveWorker,
//    index: Int,
//) {
//
//    val context = LocalContext.current
//    RiveLog.logger = RiveLog.LogcatLogger()
//
//
//    val fontBytes by produceState<app.rive.Result<ByteArray>>(app.rive.Result.Loading) {
//        value = withContext(Dispatchers.IO) {
//            context.resources.openRawResource(R.raw.rajdhani_bold)
//                .use { app.rive.Result.Success(it.readBytes()) }
//        }
//    }
//
//    val font = fontBytes.andThen { bytes ->
//        rememberRegisteredFont(riveWorker, "Outfit-4229794", bytes)
//    }
//
//    val riveFileResult = font.andThen {
//        rememberRiveFile(
//            RiveFileSource.RawRes.from(R.raw.testing),
//            riveWorker
//        )
//    }
//
//    val riveFile = (riveFileResult as? Result.Success)?.value
//
//    Box(
//        modifier = modifier
//            .fillMaxWidth()
//            .height(50.dp)
//            .semantics { contentDescription = "rive_box_$index" },
//        contentAlignment = Alignment.Center
//    ) {
//        riveFile?.let { file ->
//
//            val vmi = rememberViewModelInstance(file)
//
//            LaunchedEffect(vmi) {
//                vmi.setString("Button Text", "Testing")
//            }
//
//            Box(
//                modifier = Modifier
//                    .matchParentSize()
//                    .clickable { vmi.fireTrigger("Press") }
//            )
//
//            Rive(
//                file = file,
//                viewModelInstance = vmi
//            )
//        }
//    }
//
//}
