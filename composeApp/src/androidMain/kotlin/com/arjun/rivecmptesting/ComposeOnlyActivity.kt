package com.arjun.rivecmptesting
//
//import android.os.Bundle
//import androidx.activity.ComponentActivity
//import androidx.activity.compose.setContent
//import androidx.compose.animation.core.RepeatMode
//import androidx.compose.animation.core.animateFloat
//import androidx.compose.animation.core.infiniteRepeatable
//import androidx.compose.animation.core.rememberInfiniteTransition
//import androidx.compose.animation.core.tween
//import androidx.compose.foundation.background
//import androidx.compose.foundation.layout.Arrangement
//import androidx.compose.foundation.layout.Box
//import androidx.compose.foundation.layout.PaddingValues
//import androidx.compose.foundation.layout.Row
//import androidx.compose.foundation.layout.Spacer
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.foundation.layout.height
//import androidx.compose.foundation.layout.padding
//import androidx.compose.foundation.layout.size
//import androidx.compose.foundation.layout.width
//import androidx.compose.foundation.lazy.LazyColumn
//import androidx.compose.foundation.shape.RoundedCornerShape
//import androidx.compose.material3.Button
//import androidx.compose.material3.ButtonDefaults
//import androidx.compose.material3.CircularProgressIndicator
//import androidx.compose.material3.MaterialTheme
//import androidx.compose.material3.Text
//import androidx.compose.runtime.Composable
//import androidx.compose.runtime.LaunchedEffect
//import androidx.compose.runtime.getValue
//import androidx.compose.runtime.mutableStateOf
//import androidx.compose.runtime.remember
//import androidx.compose.runtime.saveable.rememberSaveable
//import androidx.compose.runtime.setValue
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.draw.drawBehind
//import androidx.compose.ui.geometry.CornerRadius
//import androidx.compose.ui.geometry.Offset
//import androidx.compose.ui.graphics.Brush
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.graphics.graphicsLayer
//import androidx.compose.ui.semantics.contentDescription
//import androidx.compose.ui.semantics.semantics
//import androidx.compose.ui.unit.dp
//import app.rive.Rive
//import app.rive.runtime.kotlin.core.Rive
//
//class ComposeOnlyActivity : ComponentActivity() {
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//
//
//        Rive.init(this)
//        setContent {
//            var enabled by remember { mutableStateOf(true) }
//
//            Box(
//                modifier = Modifier
//                    .fillMaxSize()
//                    .background(Color.White),
//                contentAlignment = Alignment.Center
//            ) {
//                LazyColumn(
//                    verticalArrangement = Arrangement.spacedBy(10.dp)
//                ) {
//                    items(25) {
//                        DemoPrimaryButton(
//                            modifier = Modifier
//                                .height(50.dp)
//                                .width(220.dp)
//                                .semantics {
//                                    contentDescription = "benchmark_button_${it}"
//                                },
//                            primaryText = "Demo",
//                            isEnabled = enabled,
//                            onClick = {
//                                enabled = !enabled
//                            }
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
//fun DemoPrimaryButton(
//    modifier: Modifier = Modifier,
//    floatingImage: Any? = null,
//    primaryImage: String? = null,
////    secondaryImage: AssetKey? = null,
//    primaryText: String? = null,
//    secondaryText: String? = null,
//    isLoading: Boolean = false,
//    isEnabled: Boolean = true,
//    onClick: () -> Unit = {}
//) {
//
//    val radius by remember { mutableStateOf(8.dp) }
//
//    val enableBorderColor by remember {
//        mutableStateOf(
//            Brush.verticalGradient(
//                listOf(
//                    ColorGrayGray900.copy(alpha = 0.3f),
//                    ColorGrayGray900
//                )
//            )
//        )
//    }
//
//    val disableBorderColor by remember {
//        mutableStateOf(
//            Brush.verticalGradient(
//                listOf(
//                    ColorGrayGray400,
//                    ColorGrayGray400
//                )
//            )
//        )
//    }
//
//    val enableButtonBrush by remember {
//        mutableStateOf(
//            Brush.verticalGradient(
//                colorStops = arrayOf(
//                    0.0f to ColorYellowYellow300,
//                    0.5f to ColorYellowYellow300,
//                    0.5f to ColorYellowYellow500,
//                    1.0f to ColorYellowYellow500
//                )
//            )
//        )
//    }
//
//    val disabledButtonBrush by remember {
//        mutableStateOf(
//            Brush.verticalGradient(
//                listOf(
//                    ColorGrayGray500,
//                    ColorGrayGray500
//                )
//            )
//        )
//    }
//
//    var displayText by rememberSaveable { mutableStateOf(primaryText) }
//
//    LaunchedEffect(isLoading) {
//        displayText = if (isLoading) {
//            "LOADING..."
//        } else {
//            primaryText
//        }
//    }
//
//    val handleClick = remember(onClick, isEnabled) {
//        {
//            if (isEnabled && !isLoading) {
//                onClick()
//            }
//        }
//    }
//
//    Box(
//        modifier = Modifier,
//        //.padding(horizontal = 16.dp),
//        contentAlignment = Alignment.TopEnd
//    ) {
//
//        Button(
//            modifier = modifier
//                .align(Alignment.Center)
//                .drawBehind {
//                    val cornerRadius = radius.toPx()
//
//                    // Top shadow (1dp)
//                    drawRoundRect(
//                        brush = if (isEnabled) enableBorderColor else disableBorderColor,
//                        topLeft = Offset(0f, 1.4.dp.toPx()),
//                        size = size,
//                        cornerRadius = CornerRadius(cornerRadius)
//                    )
//
//                    drawRoundRect(
//                        brush = if (isEnabled) enableBorderColor else disableBorderColor,
//                        topLeft = Offset(0f, 1.5.dp.toPx()),
//                        size = size,
//                        cornerRadius = CornerRadius(cornerRadius)
//                    )
//                }
//                .padding(horizontal = 1.dp, vertical = 2.dp)
//                .background(
//                    brush = if (isEnabled) enableButtonBrush else disabledButtonBrush,
//                    shape = RoundedCornerShape(radius)
//                ),
//
//            onClick = handleClick,
//            shape = RoundedCornerShape(radius),
//            colors = ButtonDefaults.buttonColors(
//                containerColor = Color.Transparent
//            ),
//            contentPadding = PaddingValues(vertical = 0.dp, horizontal = 0.dp),
//
//            ) {
//            Row(
//                modifier = Modifier.padding(
//                    vertical = 12.dp,
//                    horizontal = 16.dp
//                ),
//                verticalAlignment = Alignment.CenterVertically,
//                horizontalArrangement = Arrangement.Center
//            ) {
//
//                // Primary image
//                if (isLoading) {
//                    CircularProgressIndicator(
//                        modifier = Modifier.size(12.dp),
//                        strokeWidth = 2.dp,
//                        color = ColorTextSecondary
//                    )
//                } else {
////                    primaryImage?.let { image ->
////                        DemoAsyncImage(
////                            modifier = Modifier.size(20.dp),
////                            model = image
////                        )
////                    }
//                }
//                Spacer(modifier = Modifier.width(8.dp))
//
//                displayText?.let { text ->
//                    Text(
//                        text = text,
//                        color = if (isEnabled) ColorGrayGray900 else ColorGrayGray200,
//                        style = MaterialTheme.typography.labelLarge
//                    )
//                }
//
//                Spacer(modifier = Modifier.width(8.dp))
//
//                // Secondary image
////                secondaryImage?.let { image ->
////                    DemoAsyncImage(
////                        modifier = Modifier.size(20.dp),
////                        model = image.toImageRequest()
////                    )
////                }
//
//                Spacer(modifier = Modifier.width(8.dp))
//
//                // Secondary Text
////                secondaryText?.let { text ->
////                    DemoText(
////                        text = text,
////                        color = if (isEnabled) ColorGrayGray900 else ColorGrayGray200,
////                        style = DemoTypography.current.labelLargeBlack
////                    )
////                }
//            }
//        }
//        // floating image
////        floatingImage?.let { image ->
////            DemoAsyncImage(
////                modifier = Modifier
////                    .size(30.dp)
////                    .offset(x = (-30).dp, y = (-10).dp)  // TODO: CHECK
////                ,
////                model = image
////            )
////        }
//    }
//}
//
//
//
//
//val ColorGrayGray200 = Color(0xFFE6E5E6)
//val ColorGrayGray400 = Color(0xFFB2ADB4)
//val ColorGrayGray500 = Color(0xFF938C95)
//val ColorGrayGray600 = Color(0xFF6E6672)
//val ColorGrayGray900 = Color(0xFF261A2C)
//val ColorYellowYellow500 = Color(0xFFFFBF0F)
//
//val ColorTextSecondary = ColorGrayGray600
//
//val ColorYellowYellow300 = Color(0xFFFFD970)
//
