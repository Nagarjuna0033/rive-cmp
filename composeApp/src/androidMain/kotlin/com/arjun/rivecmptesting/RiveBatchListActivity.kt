package com.arjun.rivecmptesting

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.rive.Result
import app.rive.Result.Loading.andThen
import app.rive.RiveBatchItem
import app.rive.RiveBatchSurface
import app.rive.RiveFile
import app.rive.RiveFileSource
import app.rive.rememberRegisteredFont
import app.rive.rememberRegisteredImage
import app.rive.rememberRiveFile
import app.rive.rememberRiveWorker
import app.rive.rememberViewModelInstance
import app.rive.runtime.kotlin.core.Rive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalResources


class RiveBatchListActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Rive.init(this)


        setContent {
            MaterialTheme {

                Scaffold(
                    containerColor = Color.White
                ) { padding ->

                    val riveWorker = rememberRiveWorker()

                    val resources = LocalResources.current


                    val fontBytes by produceState<Result<ByteArray>>(Result.Loading) {
                        value = withContext(Dispatchers.IO) {
                            resources.openRawResource(R.raw.rajdhani_bold)
                                .use { Result.Success(it.readBytes()) }
                        }
                    }

                    val font = fontBytes.andThen { bytes ->
                        rememberRegisteredFont(riveWorker, "Outfit-4229794", bytes)
                    }


                    val imageBytes by produceState<Result<ByteArray>>(Result.Loading) {
                        value = withContext(Dispatchers.IO) {
                            resources.openRawResource(R.raw.ic_coin1)
                                .use { Result.Success(it.readBytes()) }
                        }
                    }

                    val image = imageBytes.andThen { bytes ->
                        println("bytes = [${bytes.size}]")
                        rememberRegisteredImage(riveWorker, "1-5293216", bytes)
                    }

                    val riveFileResult = font.andThen {
                        image.andThen {
                            rememberRiveFile(
                                source = RiveFileSource.RawRes.from(R.raw.testing_extracting_all),
                                riveWorker = riveWorker
                            )
                        }
                    }

//                    val riveFileResult = font.andThen {
//                        rememberRiveFile(
//                            RiveFileSource.RawRes.from(R.raw.testing_button_svg_without_font),
//                            riveWorker
//                        )
//                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {

                        Text(
                            text = "Batched Testing",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        )

                        if (riveFileResult is Result.Success) {
                            val riveFile = riveFileResult.value
                            RiveBatchSurface(
                                riveWorker = riveWorker,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(15.dp)
                                ) {
                                    items(
                                        count = 25,
                                        key = { index -> "rive_item_$index" }
                                    ) { item ->
                                        BatchedRivePanel(
                                            riveFile = riveFile,
                                            index = item
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

            }
        }
    }
}


@Composable
fun BatchedRivePanel(
    modifier: Modifier = Modifier,
    riveFile: RiveFile,
    index: Int,
) {
    val vmi = rememberViewModelInstance(riveFile)

    LaunchedEffect(Unit) {
        vmi.setString("Button Text", "Testing")
        vmi.setEnum("Show Lock Icon", "Show")
    }

    RiveBatchItem(
        file = riveFile,
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .semantics {
                contentDescription = "rive_box_$index"
            }
            .clickable {
                vmi.fireTrigger("Press")
            },
        viewModelInstance = vmi,
    )
}
