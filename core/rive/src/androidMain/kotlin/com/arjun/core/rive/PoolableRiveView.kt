package com.arjun.core.rive

import android.graphics.SurfaceTexture
import android.util.Log
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.rive.Fit
import app.rive.RiveFile
import app.rive.ViewModelInstance
import app.rive.core.RiveSurface
import kotlinx.coroutines.isActive
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

private const val TAG = "Rive/PoolableView"

/**
 * A poolable Rive composable that uses [AndroidView] with `onReset` and `onRelease`
 * to recycle [TextureView] instances in LazyColumn, avoiding the ~100-250ms cost of
 * creating/destroying TextureViews on every enter/leave composition.
 *
 * Unlike the SDK's `Rive()` composable, this:
 * - Returns `false` from [TextureView.SurfaceTextureListener.onSurfaceTextureDestroyed]
 *   to keep the surface alive across recycles
 * - Creates its own artboard + state machine per binding via [RiveWorker] directly
 * - Runs its own advance + draw loop while lifecycle is RESUMED
 * - Cleans up artboard/SM on permanent dispose (via `onRelease`)
 */
@Composable
internal fun PoolableRiveView(
    file: RiveFile,
    modifier: Modifier = Modifier,
    viewModelInstance: ViewModelInstance? = null,
    fit: Fit = Fit.Contain(),
    backgroundColor: Int = Color.Transparent.toArgb(),
) {
    val riveWorker = file.riveWorker
    val lifecycleOwner = LocalLifecycleOwner.current

    // Artboard + state machine handles, created once per file binding.
    val artboardHandle = remember(file) {
        riveWorker.createDefaultArtboard(file.fileHandle)
    }
    val stateMachineHandle = remember(artboardHandle) {
        riveWorker.createDefaultStateMachine(artboardHandle)
    }

    // Track the RiveSurface created from the TextureView's SurfaceTexture.
    var riveSurface by remember { mutableStateOf<RiveSurface?>(null) }

    // Hide TextureView until the first Rive frame draws to avoid grey flash.
    var hasDrawnFirstFrame by remember { mutableStateOf(false) }

    // Bind view model instance to the state machine when provided.
    LaunchedEffect(stateMachineHandle, viewModelInstance) {
        viewModelInstance ?: return@LaunchedEffect
        riveWorker.bindViewModelInstance(stateMachineHandle, viewModelInstance.instanceHandle)
    }

    // Render loop: advance SM + draw each frame while lifecycle is RESUMED.
    LaunchedEffect(lifecycleOwner, riveSurface, artboardHandle, stateMachineHandle) {
        val surface = riveSurface ?: return@LaunchedEffect

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

                riveWorker.advanceStateMachine(stateMachineHandle, deltaTime)
                riveWorker.draw(artboardHandle, stateMachineHandle, surface, fit, backgroundColor)
                if (!hasDrawnFirstFrame) hasDrawnFirstFrame = true
            }
        }
    }

    // Clean up artboard + SM when handles change or on permanent dispose.
    DisposableEffect(artboardHandle, stateMachineHandle) {
        onDispose {
            try {
                riveWorker.deleteStateMachine(stateMachineHandle)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete state machine", e)
            }
            try {
                riveWorker.deleteArtboard(artboardHandle)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete artboard", e)
            }
        }
    }

    // Helper to destroy the current RiveSurface and null out the state.
    fun destroyCurrentSurface() {
        riveSurface?.let { surface ->
            try {
                riveWorker.destroyRiveSurface(surface)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to destroy RiveSurface", e)
            }
            riveSurface = null
        }
    }

    // Hide the TextureView until the first Rive frame draws to prevent grey flash.
    val viewModifier = if (hasDrawnFirstFrame) modifier else modifier.graphicsLayer { alpha = 0f }

    AndroidView(
        factory = { context ->
            TextureView(context).apply {
                isOpaque = false
                surfaceTextureListener = createSurfaceTextureListener(
                    riveWorker = riveWorker,
                    onSurfaceCreated = { newSurface ->
                        destroyCurrentSurface()
                        riveSurface = newSurface
                    },
                    onSurfaceDestroyed = {
                        destroyCurrentSurface()
                    },
                )
            }
        },
        modifier = viewModifier,
        onReset = { textureView ->
            // Called when the AndroidView is recycled in a LazyColumn.
            // Hide until new content draws to prevent showing stale content.
            hasDrawnFirstFrame = false
            val existingTexture = textureView.surfaceTexture
            if (existingTexture != null) {
                destroyCurrentSurface()
                riveSurface = riveWorker.createRiveSurface(existingTexture)
            }
        },
        onRelease = { textureView ->
            // Called on permanent dispose — destroy both the RiveSurface and the SurfaceTexture.
            destroyCurrentSurface()
            textureView.surfaceTexture?.release()
        },
    )
}

/**
 * Creates a [TextureView.SurfaceTextureListener] that:
 * - Creates a [RiveSurface] when the texture becomes available
 * - Returns `false` from [onSurfaceTextureDestroyed] to keep the surface alive on recycle
 */
private fun createSurfaceTextureListener(
    riveWorker: app.rive.core.RiveWorker,
    onSurfaceCreated: (RiveSurface) -> Unit,
    onSurfaceDestroyed: () -> Unit,
): TextureView.SurfaceTextureListener = object : TextureView.SurfaceTextureListener {

    override fun onSurfaceTextureAvailable(
        surfaceTexture: SurfaceTexture,
        width: Int,
        height: Int,
    ) {
        val surface = riveWorker.createRiveSurface(surfaceTexture)
        onSurfaceCreated(surface)
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        // Return false to keep the SurfaceTexture alive — the TextureView may be recycled.
        onSurfaceDestroyed()
        return false
    }

    override fun onSurfaceTextureSizeChanged(
        surfaceTexture: SurfaceTexture,
        width: Int,
        height: Int,
    ) {
        // Size changes are handled by the draw call's fit parameter.
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
        // No-op.
    }
}
