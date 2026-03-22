package app.rive

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.rive.core.ArtboardHandle
import app.rive.core.CommandQueue
import app.rive.core.RiveSurface
import app.rive.core.RiveWorker
import app.rive.core.StateMachineHandle
import kotlinx.coroutines.isActive
import androidx.compose.runtime.withFrameNanos
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

private const val BATCH_TAG = "Rive/Batch"
private const val BATCH_DRAW_TAG = "Rive/Batch/Draw"

/**
 * Describes a single item within a batched Rive surface.
 *
 * Holds the rendering state needed to draw one artboard/state-machine pair
 * at a specific viewport position on a shared surface.
 */
data class BatchItemDescriptor(
    val artboardHandle: ArtboardHandle,
    val stateMachineHandle: StateMachineHandle,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val fit: Fit,
    val backgroundColor: Int,
)

/**
 * Coordinates batched rendering of multiple Rive items on a single shared surface.
 *
 * Each [RiveBatchItem] registers/unregisters itself with the coordinator, providing its
 * [BatchItemDescriptor]. The [RiveBatchSurface] reads all registered items each frame
 * and renders them in a single `drawBatch` call.
 */
class RiveBatchCoordinator {
    private val items = ConcurrentHashMap<Any, BatchItemDescriptor>()

    /** The root X position of the batch surface in the composition's root coordinates. */
    var surfaceRootX: Float = 0f
        internal set

    /** The root Y position of the batch surface in the composition's root coordinates. */
    var surfaceRootY: Float = 0f
        internal set

    // Pre-allocated batch arrays — power-of-2 sized to avoid reallocation thrashing.
    private var capacity = 0
    internal var artboardHandles = LongArray(0); private set
    internal var smHandles = LongArray(0); private set
    internal var viewportXs = IntArray(0); private set
    internal var viewportYs = IntArray(0); private set
    internal var viewportWidths = IntArray(0); private set
    internal var viewportHeights = IntArray(0); private set
    internal var fits = ByteArray(0); private set
    internal var alignments = ByteArray(0); private set
    internal var scaleFactors = FloatArray(0); private set
    internal var clearColors = IntArray(0); private set
    // StateMachineHandle references for advancing (not passed to JNI).
    internal var smHandleRefs = arrayOfNulls<StateMachineHandle>(0); private set

    // Snapshot array for iteration — avoids ConcurrentHashMap iterator allocation per frame.
    private var snapshot = arrayOfNulls<Map.Entry<Any, BatchItemDescriptor>>(0)

    /** Register an item for batched rendering. */
    internal fun register(key: Any, descriptor: BatchItemDescriptor) {
        items[key] = descriptor
    }

    /** Unregister an item (e.g. when it leaves composition). */
    internal fun unregister(key: Any) {
        items.remove(key)
    }

    /**
     * Apply a scroll delta to all registered items immediately.
     * Called from [NestedScrollConnection.onPreScroll] which fires BEFORE
     * [withFrameNanos], so positions are pre-corrected before the render loop reads them.
     * [onPlaced] overwrites with official positions after layout, keeping things in sync.
     */
    internal fun applyScrollDelta(dx: Float, dy: Float) {
        val dxInt = dx.toInt()
        val dyInt = dy.toInt()
        if (dxInt == 0 && dyInt == 0) return

        for (entry in items.entries) {
            val item = entry.value
            entry.setValue(
                item.copy(
                    x = item.x + dxInt,
                    y = item.y + dyInt,
                )
            )
        }
    }

    /** Round up to next power of 2 (minimum 4). */
    private fun nextPowerOf2(n: Int): Int {
        if (n <= 4) return 4
        var v = n - 1
        v = v or (v shr 1)
        v = v or (v shr 2)
        v = v or (v shr 4)
        v = v or (v shr 8)
        v = v or (v shr 16)
        return v + 1
    }

    /**
     * Fills pre-allocated arrays with current item data.
     * Uses power-of-2 sizing to avoid reallocation during scroll.
     * Returns the number of items filled (0 means nothing to draw).
     */
    internal fun fillBatchArrays(): Int {
        val count = items.size
        if (count == 0) return 0

        // Only reallocate when count exceeds capacity or drops below half.
        if (count > capacity || count < capacity / 4) {
            capacity = nextPowerOf2(count)
            artboardHandles = LongArray(capacity)
            smHandles = LongArray(capacity)
            viewportXs = IntArray(capacity)
            viewportYs = IntArray(capacity)
            viewportWidths = IntArray(capacity)
            viewportHeights = IntArray(capacity)
            fits = ByteArray(capacity)
            alignments = ByteArray(capacity)
            scaleFactors = FloatArray(capacity)
            clearColors = IntArray(capacity)
            smHandleRefs = arrayOfNulls(capacity)
            snapshot = arrayOfNulls(capacity)
        }

        var i = 0
        for (entry in items.entries) {
            if (i >= capacity) break
            val item = entry.value
            artboardHandles[i] = item.artboardHandle.handle
            smHandles[i] = item.stateMachineHandle.handle
            viewportXs[i] = item.x
            viewportYs[i] = item.y
            viewportWidths[i] = item.width
            viewportHeights[i] = item.height
            fits[i] = item.fit.nativeMapping
            alignments[i] = item.fit.alignment.nativeMapping
            scaleFactors[i] = item.fit.scaleFactor
            clearColors[i] = item.backgroundColor
            smHandleRefs[i] = item.stateMachineHandle
            i++
        }

        return i
    }
}

/**
 * CompositionLocal that provides the [RiveBatchCoordinator] to child [RiveBatchItem]s.
 */
val LocalRiveBatchCoordinator = compositionLocalOf<RiveBatchCoordinator?> { null }

/**
 * A shared rendering surface that batches all child [RiveBatchItem] draws into a single
 * GPU pass.
 *
 * Wraps a single [TextureView] and renders all registered items each frame using
 * [CommandQueue.drawBatch], reducing per-frame GPU context switches from N to 1.
 *
 * Uses a [NestedScrollConnection] to intercept scroll deltas before the render loop,
 * pre-correcting item positions so they are accurate when [fillBatchArrays] reads them.
 *
 * @param riveWorker The Rive command queue / worker to use for rendering.
 * @param modifier Modifier applied to the surface layout.
 * @param surfaceClearColor Color to clear the entire surface with before drawing items.
 * @param content Composable content that should contain [RiveBatchItem]s.
 */
@Composable
fun RiveBatchSurface(
    riveWorker: CommandQueue,
    modifier: Modifier = Modifier,
    surfaceClearColor: Int = Color.Transparent.toArgb(),
    content: @Composable () -> Unit,
) {
    val coordinator = remember { RiveBatchCoordinator() }
    val lifecycleOwner = LocalLifecycleOwner.current

    var surface by remember { mutableStateOf<RiveSurface?>(null) }

    // Cleanup: destroy the RiveSurface when it changes or on dispose.
    DisposableEffect(surface) {
        val nonNullSurface = surface ?: return@DisposableEffect onDispose { }
        onDispose {
            riveWorker.destroyRiveSurface(nonNullSurface)
        }
    }

    // Intercept scroll deltas BEFORE they are consumed by the LazyColumn.
    // onPreScroll fires during input processing, which happens BEFORE withFrameNanos.
    // This pre-corrects item positions so the render loop reads accurate coordinates.
    // After layout, onPlaced overwrites with official positions (self-correcting).
    val nestedScrollConnection = remember(coordinator) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                coordinator.applyScrollDelta(available.x, available.y)
                return Offset.Zero // don't consume — let the scrollable handle it
            }
        }
    }

    // Render loop: advances state machines and draws all items each frame.
    LaunchedEffect(lifecycleOwner, surface) {
        val currentSurface = surface ?: return@LaunchedEffect
        RiveLog.d(BATCH_DRAW_TAG) { "Starting batched render loop" }

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

                val count = coordinator.fillBatchArrays()
                if (count == 0) continue

                // Advance all state machines.
                for (j in 0 until count) {
                    val smRef = coordinator.smHandleRefs[j] ?: continue
                    riveWorker.advanceStateMachine(smRef, deltaTime)
                }

                try {
                    riveWorker.drawBatch(
                        currentSurface,
                        coordinator.artboardHandles,
                        coordinator.smHandles,
                        coordinator.viewportXs,
                        coordinator.viewportYs,
                        coordinator.viewportWidths,
                        coordinator.viewportHeights,
                        coordinator.fits,
                        coordinator.alignments,
                        coordinator.scaleFactors,
                        coordinator.clearColors,
                        surfaceClearColor,
                    )
                } catch (e: Exception) {
                    RiveLog.e(BATCH_DRAW_TAG) { "drawBatch failed: ${e.message}" }
                }
            }
        }
    }

    // Track the surface's root position so items can compute relative offsets.
    val positionTrackingModifier = modifier
        .nestedScroll(nestedScrollConnection)
        .onGloballyPositioned { coords ->
            val pos = coords.positionInRoot()
            coordinator.surfaceRootX = pos.x
            coordinator.surfaceRootY = pos.y
        }

    Layout(
        modifier = positionTrackingModifier,
        content = {
            // Provide the coordinator to children and render content FIRST (behind).
            CompositionLocalProvider(LocalRiveBatchCoordinator provides coordinator) {
                content()
            }

            // Single TextureView SECOND (on top as transparent overlay).
            // isOpaque = false means only Rive items are visible; rest is transparent.
            AndroidView(factory = { context: Context ->
                TextureView(context).apply {
                    isOpaque = false
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(
                            newSurfaceTexture: SurfaceTexture,
                            width: Int,
                            height: Int
                        ) {
                            RiveLog.d(BATCH_TAG) {
                                "Batch surface texture available ($width x $height)"
                            }
                            surface = riveWorker.createRiveSurface(newSurfaceTexture)
                        }

                        override fun onSurfaceTextureDestroyed(
                            destroyedSurfaceTexture: SurfaceTexture
                        ): Boolean {
                            RiveLog.d(BATCH_TAG) { "Batch surface texture destroyed" }
                            surface = null
                            return false
                        }

                        override fun onSurfaceTextureSizeChanged(
                            surfaceTexture: SurfaceTexture,
                            width: Int,
                            height: Int
                        ) {
                            RiveLog.d(BATCH_TAG) {
                                "Batch surface texture size changed ($width x $height)"
                            }
                        }

                        override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}
                    }
                }
            })
        }
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEach { it.placeRelative(0, 0) }
        }
    }
}

/**
 * A single Rive item rendered within a [RiveBatchSurface].
 *
 * Instead of creating its own rendering surface, this composable registers itself
 * with the parent [RiveBatchCoordinator] and is drawn as part of the batch.
 *
 * @param file The [RiveFile] to instantiate the artboard from.
 * @param modifier Modifier for layout sizing and interaction.
 * @param viewModelInstance Optional [ViewModelInstance] to bind to the state machine.
 * @param fit How the artboard should be fitted within this item's bounds.
 * @param backgroundColor Per-item clear color before drawing.
 */
@Composable
fun RiveBatchItem(
    file: RiveFile,
    modifier: Modifier = Modifier,
    viewModelInstance: ViewModelInstance? = null,
    fit: Fit = Fit.Contain(),
    backgroundColor: Int = Color.Transparent.toArgb(),
) {
    val coordinator = LocalRiveBatchCoordinator.current
        ?: error("RiveBatchItem must be placed inside a RiveBatchSurface")

    val riveWorker = file.riveWorker
    val artboardToUse = rememberArtboard(file)
    val artboardHandle = artboardToUse.artboardHandle
    val stateMachineToUse = rememberStateMachine(artboardToUse)
    val stateMachineHandle = stateMachineToUse.stateMachineHandle

    // Bind the view model instance to the state machine if provided.
    LaunchedEffect(stateMachineHandle, viewModelInstance) {
        viewModelInstance ?: return@LaunchedEffect
        riveWorker.bindViewModelInstance(stateMachineHandle, viewModelInstance.instanceHandle)
        // Keep collecting dirty flow to stay responsive to VMI changes.
        viewModelInstance.dirtyFlow.collect { /* no-op, just keeps collection alive */ }
    }

    // Unregister when leaving composition.
    DisposableEffect(stateMachineHandle) {
        onDispose {
            coordinator.unregister(stateMachineHandle)
        }
    }

    // Helper to update position in the coordinator from layout coordinates.
    val updatePosition = { coords: androidx.compose.ui.layout.LayoutCoordinates ->
        if (coords.isAttached) {
            val rootPos = coords.positionInRoot()
            val size = coords.size
            val relativeX = (rootPos.x - coordinator.surfaceRootX).toInt()
            val relativeY = (rootPos.y - coordinator.surfaceRootY).toInt()
            coordinator.register(
                stateMachineHandle,
                BatchItemDescriptor(
                    artboardHandle = artboardHandle,
                    stateMachineHandle = stateMachineHandle,
                    x = relativeX,
                    y = relativeY,
                    width = size.width,
                    height = size.height,
                    fit = fit,
                    backgroundColor = backgroundColor,
                )
            )
        }
    }

    // Register/update position on layout AND placement (scroll).
    // onGloballyPositioned fires on layout changes.
    // onPlaced fires on every placement including scroll offsets.
    // pointerInput forwards touch events to the state machine.
    val combinedModifier = modifier
        .onGloballyPositioned(updatePosition)
        .onPlaced(updatePosition)
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
        layout(constraints.maxWidth, constraints.maxHeight) {}
    }
}
