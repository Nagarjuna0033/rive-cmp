package com.arjun.rivecmptesting
//
//import android.os.Bundle
//import androidx.activity.ComponentActivity
//import androidx.activity.compose.setContent
//import androidx.compose.foundation.clickable
//import androidx.compose.foundation.layout.Arrangement
//import androidx.compose.foundation.layout.Box
//import androidx.compose.foundation.layout.Column
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.foundation.layout.fillMaxWidth
//import androidx.compose.foundation.layout.height
//import androidx.compose.foundation.layout.padding
//import androidx.compose.foundation.lazy.LazyColumn
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
//import app.rive.ImageAsset
//import app.rive.Result
//import app.rive.Result.Loading.andThen
//import app.rive.Result.Loading.zip
//import app.rive.Rive
//import app.rive.RiveFile
//import app.rive.RiveFileSource
//import app.rive.rememberImage
//import app.rive.rememberRegisteredFont
//import app.rive.rememberRegisteredImage
//import app.rive.rememberRiveFile
//import app.rive.rememberRiveWorker
//import app.rive.rememberViewModelInstance
//import app.rive.runtime.kotlin.core.RendererType
//import app.rive.runtime.kotlin.core.Rive
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.withContext
//
//
//class RiveListActivity : ComponentActivity() {
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
//                    val context = LocalContext.current
//                    val riveWorker = rememberRiveWorker()
//
//                    val fontBytes by produceState<Result<ByteArray>>(Result.Loading) {
//                        value = withContext(Dispatchers.IO) {
//                            context.resources.openRawResource(R.raw.rajdhani_bold)
//                                .use { Result.Success(it.readBytes()) }
//                        }
//                    }
//
//                    val font = fontBytes.andThen { bytes ->
//                        rememberRegisteredFont(riveWorker, "Outfit-4229794", bytes)
//                    }
//
//                    val imageBytes by produceState<Result<ByteArray>>(Result.Loading) {
//                        value = withContext(Dispatchers.IO) {
//                            context.resources.openRawResource(R.raw.coin)
//                                .use { Result.Success(it.readBytes()) }
//                        }
//                    }
//
//                    val image = imageBytes.andThen { bytes ->
//                        println("bytes = [${bytes.size}]")
//                        rememberRegisteredImage(riveWorker, "1-5293216", bytes)
//                    }
//
//                    val riveFileResult = font.andThen {
//                        image.andThen {
//                            rememberRiveFile(
//                                RiveFileSource.RawRes.from(R.raw.testing),
//                                riveWorker
//                            )
//                        }
//                    }
//
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
//                        when (riveFileResult) {
//                            is Result.Loading -> {}
//                            is Result.Error -> {}
//                            is Result.Success -> {
//                                LazyColumn(
//                                    modifier = Modifier.fillMaxSize(),
//                                    verticalArrangement = Arrangement.spacedBy(10.dp)
//                                ) {
//                                    items(25) { item ->
//                                        BottomRivePanelTesting(
//                                            riveFile = riveFileResult.value,
//                                            index = item
//                                        )
//                                    }
//                                }
//                            }
//                        }
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
//fun BottomRivePanelTesting(
//    modifier: Modifier = Modifier,
//    riveFile: RiveFile,
//    index: Int,
//) {
//    val vmi = rememberViewModelInstance(riveFile)
//
//
//    LaunchedEffect(Unit) {
//        vmi.setString("Button Text", "Testing")
////        vmi.setEnum("Right Cash", "Show")
////        vmi.setEnum("Right Coin", "Show")
//        vmi.setEnum("Show Lock Icon", "Show")
//    }
//
//    Box(
//        modifier = modifier
//            .fillMaxWidth()
//            .height(50.dp)
//            .semantics {
//                contentDescription = "rive_box_$index"
//            }
//            .clickable {
//                vmi.fireTrigger("Press")
//            },
//        contentAlignment = Alignment.Center
//    ) {
//        Rive(
//            file = riveFile,
//            viewModelInstance = vmi,
//        )
//    }
//}
