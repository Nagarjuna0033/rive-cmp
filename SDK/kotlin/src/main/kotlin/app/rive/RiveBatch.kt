package app.rive

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.rive.core.ArtboardHandle
import app.rive.core.CommandQueue
import app.rive.core.RiveSurface
import app.rive.core.StateMachineHandle
import kotlinx.coroutines.isActive
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

private const val BATCH_DRAW_TAG = "Rive/Batch/Draw"

/**
 * Renders multiple Rive items using per-item offscreen surfaces and Compose DrawScope.
 *
 * Each item gets its own offscreen [RiveSurface] (PBuffer). The render loop draws each
 * artboard to its surface via [CommandQueue.drawToBuffer], converts pixels to a [Bitmap],
 * and distributes the bitmap to the item for display via [Modifier.drawBehind].
 *
 * This eliminates the TextureView position sync problem: [drawBehind] runs during
 * Compose's DRAW phase (after LAYOUT), so position is always correct.
 */
class RiveBatchRenderer(private val riveWorker: CommandQueue) {

    private class ItemState(
        var artboardHandle: ArtboardHandle,
        var stateMachineHandle: StateMachineHandle,
        var width: Int,
        var height: Int,
        var fit: Fit,
        var backgroundColor: Int,
        var onBitmap: (ImageBitmap) -> Unit,
    ) {
        var surface: RiveSurface? = null
        var pixels: ByteArray? = null
        var argbScratch: IntArray? = null
        var bitmap: Bitmap? = null
    }

    private val items = ConcurrentHashMap<Any, ItemState>()

    fun register(
        key: Any,
        artboardHandle: ArtboardHandle,
        stateMachineHandle: StateMachineHandle,
        width: Int,
        height: Int,
        fit: Fit,
        backgroundColor: Int,
        onBitmap: (ImageBitmap) -> Unit,
    ) {
        val existing = items[key]
        if (existing != null) {
            if (existing.width != width || existing.height != height) {
                existing.surface?.let { riveWorker.destroyRiveSurface(it) }
                existing.surface = null
                existing.pixels = null
                existing.argbScratch = null
                existing.bitmap = null // Old bitmap GC'd when RenderThread releases it
            }
            existing.artboardHandle = artboardHandle
            existing.stateMachineHandle = stateMachineHandle
            existing.width = width
            existing.height = height
            existing.fit = fit
            existing.backgroundColor = backgroundColor
            existing.onBitmap = onBitmap
            return
        }
        items[key] = ItemState(
            artboardHandle, stateMachineHandle,
            width, height, fit, backgroundColor, onBitmap,
        )
    }

    fun unregister(key: Any) {
        val item = items.remove(key) ?: return
        item.surface?.let { riveWorker.destroyRiveSurface(it) }
        // Do NOT recycle bitmap — Compose's RenderThread may still be drawing it.
        // Let GC handle it when no longer referenced.
    }

    fun renderFrame(deltaTime: Duration) {
        for ((_, item) in items) {
            if (item.width <= 0 || item.height <= 0) continue

            if (item.surface == null) {
                item.surface = riveWorker.createImageSurface(item.width, item.height)
                val pixelCount = item.width * item.height
                item.pixels = ByteArray(pixelCount * 4)
                item.argbScratch = IntArray(pixelCount)
                item.bitmap = Bitmap.createBitmap(
                    item.width, item.height, Bitmap.Config.ARGB_8888
                )
            }

            riveWorker.advanceStateMachine(item.stateMachineHandle, deltaTime)

            try {
                riveWorker.drawToBuffer(
                    item.artboardHandle,
                    item.stateMachineHandle,
                    item.surface!!,
                    item.pixels!!,
                    item.width,
                    item.height,
                    item.fit,
                    item.backgroundColor,
                )
            } catch (e: Exception) {
                RiveLog.e(BATCH_DRAW_TAG) { "drawToBuffer failed: ${e.message}" }
                continue
            }

            val px = item.pixels!!
            val argb = item.argbScratch!!
            var byteIdx = 0
            var pixelIdx = 0
            val totalPixels = item.width * item.height
            while (pixelIdx < totalPixels) {
                val r = px[byteIdx].toInt() and 0xFF
                val g = px[byteIdx + 1].toInt() and 0xFF
                val b = px[byteIdx + 2].toInt() and 0xFF
                val a = px[byteIdx + 3].toInt() and 0xFF
                argb[pixelIdx] = (a shl 24) or (r shl 16) or (g shl 8) or b
                byteIdx += 4
                pixelIdx++
            }
            item.bitmap!!.setPixels(argb, 0, item.width, 0, 0, item.width, item.height)

            item.onBitmap(item.bitmap!!.asImageBitmap())
        }
    }

    fun destroy() {
        for ((_, item) in items) {
            item.surface?.let { riveWorker.destroyRiveSurface(it) }
        }
        items.clear()
    }
}

val LocalRiveBatchRenderer = compositionLocalOf<RiveBatchRenderer?> { null }

@Deprecated("Use LocalRiveBatchRenderer", replaceWith = ReplaceWith("LocalRiveBatchRenderer"))
val LocalRiveBatchCoordinator = LocalRiveBatchRenderer

@Composable
fun RiveBatchSurface(
    riveWorker: CommandQueue,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER")
    surfaceClearColor: Int = Color.Transparent.toArgb(), // Deprecated: no shared surface to clear; each item uses backgroundColor
    content: @Composable () -> Unit,
) {
    val renderer = remember { RiveBatchRenderer(riveWorker) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(renderer) {
        onDispose { renderer.destroy() }
    }

    LaunchedEffect(lifecycleOwner, renderer) {
        RiveLog.d(BATCH_DRAW_TAG) { "Starting DrawScope render loop" }

        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var lastFrameTime = Duration.ZERO
            while (isActive) {
                val deltaTime = withFrameNanos { frameTimeNs ->
                    val frameTime = frameTimeNs.nanoseconds
                    val dt = if (lastFrameTime == Duration.ZERO) {
                        Duration.ZERO
                    } else {
                        frameTime - lastFrameTime
                    }
                    lastFrameTime = frameTime
                    dt
                }

                renderer.renderFrame(deltaTime)
            }
        }
    }

    CompositionLocalProvider(LocalRiveBatchRenderer provides renderer) {
        Layout(
            modifier = modifier,
            content = { content() },
        ) { measurables, constraints ->
            val placeables = measurables.map { it.measure(constraints) }
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeables.forEach { it.placeRelative(0, 0) }
            }
        }
    }
}

@Composable
fun RiveBatchItem(
    file: RiveFile,
    modifier: Modifier = Modifier,
    viewModelInstance: ViewModelInstance? = null,
    fit: Fit = Fit.Contain(),
    backgroundColor: Int = Color.Transparent.toArgb(),
) {
    val renderer = LocalRiveBatchRenderer.current
        ?: error("RiveBatchItem must be placed inside a RiveBatchSurface")

    val riveWorker = file.riveWorker
    val artboardToUse = rememberArtboard(file)
    val artboardHandle = artboardToUse.artboardHandle
    val stateMachineToUse = rememberStateMachine(artboardToUse)
    val stateMachineHandle = stateMachineToUse.stateMachineHandle

    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(stateMachineHandle, viewModelInstance) {
        viewModelInstance ?: return@LaunchedEffect
        riveWorker.bindViewModelInstance(stateMachineHandle, viewModelInstance.instanceHandle)
        viewModelInstance.dirtyFlow.collect { }
    }

    DisposableEffect(stateMachineHandle) {
        onDispose {
            renderer.unregister(stateMachineHandle)
        }
    }

    val combinedModifier = modifier
        .onGloballyPositioned { coords ->
            if (coords.isAttached && coords.size.width > 0 && coords.size.height > 0) {
                renderer.register(
                    key = stateMachineHandle,
                    artboardHandle = artboardHandle,
                    stateMachineHandle = stateMachineHandle,
                    width = coords.size.width,
                    height = coords.size.height,
                    fit = fit,
                    backgroundColor = backgroundColor,
                    onBitmap = { bitmap = it },
                )
            }
        }
        .drawBehind {
            bitmap?.let { drawImage(it) }
        }
        .pointerInput(stateMachineHandle, fit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val boundsWidth = size.width.toFloat()
                    val boundsHeight = size.height.toFloat()

                    for (change in event.changes) {
                        val px = change.position.x
                        val py = change.position.y
                        val id = change.id.value.toInt()

                        when (event.type) {
                            PointerEventType.Press -> {
                                riveWorker.pointerDown(stateMachineHandle, fit, boundsWidth, boundsHeight, id, px, py)
                            }
                            PointerEventType.Move -> {
                                riveWorker.pointerMove(stateMachineHandle, fit, boundsWidth, boundsHeight, id, px, py)
                            }
                            PointerEventType.Release -> {
                                riveWorker.pointerUp(stateMachineHandle, fit, boundsWidth, boundsHeight, id, px, py)
                                riveWorker.pointerExit(stateMachineHandle, fit, boundsWidth, boundsHeight, id, px, py)
                            }
                            PointerEventType.Exit -> {
                                riveWorker.pointerExit(stateMachineHandle, fit, boundsWidth, boundsHeight, id, px, py)
                            }
                        }
                        change.consume()
                    }
                }
            }
        }

    Layout(
        modifier = combinedModifier,
        content = {}
    ) { _, constraints ->
        // Use 0 if unbounded — item MUST have explicit size from modifier chain.
        val w = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
        val h = if (constraints.hasBoundedHeight) constraints.maxHeight else 0
        layout(w, h) {}
    }
}
