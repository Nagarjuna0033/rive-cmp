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
import app.rive.runtime.kotlin.core.Alignment
import com.arjun.core.rive.utils.RiveAlignment
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
    artboardName: String? = null,
    stateMachineName: String? = null,
) {
    val riveWorker = file.riveWorker
    val lifecycleOwner = LocalLifecycleOwner.current

    Log.d(TAG, "[1] PoolableRiveView composing — file=${file.fileHandle}, artboard=$artboardName, sm=$stateMachineName")

    // Artboard + state machine handles, created once per file binding.
    val artboardHandle = remember(file, artboardName) {
        val h = if (artboardName != null) {
            riveWorker.createArtboardByName(file.fileHandle, artboardName)
        } else {
            riveWorker.createDefaultArtboard(file.fileHandle)
        }
        Log.d(TAG, "[2] Artboard created — handle=$h")
        h
    }
    val stateMachineHandle = remember(artboardHandle, stateMachineName) {
        val h = if (stateMachineName != null) {
            riveWorker.createStateMachineByName(artboardHandle, stateMachineName)
        } else {
            riveWorker.createDefaultStateMachine(artboardHandle)
        }
        Log.d(TAG, "[3] StateMachine created — handle=$h")
        h
    }

    // Track the RiveSurface created from the TextureView's SurfaceTexture.
    var riveSurface by remember { mutableStateOf<RiveSurface?>(null) }

    // Hide TextureView until the first Rive frame draws to avoid grey flash.
    var hasDrawnFirstFrame by remember { mutableStateOf(false) }

    // Bind view model instance to the state machine when provided.
    LaunchedEffect(stateMachineHandle, viewModelInstance) {
        viewModelInstance ?: return@LaunchedEffect
        Log.d(TAG, "[4] Binding VMI — sm=$stateMachineHandle, vmi=${viewModelInstance.instanceHandle}")
        riveWorker.bindViewModelInstance(stateMachineHandle, viewModelInstance.instanceHandle)
        // Keep collecting dirty flow to stay responsive to VMI property changes.
        viewModelInstance.dirtyFlow.collect { /* no-op, just keeps collection alive */ }
    }

    // Render loop: advance SM + draw each frame while lifecycle is RESUMED.
    LaunchedEffect(lifecycleOwner, riveSurface, artboardHandle, stateMachineHandle) {
        val surface = riveSurface
        Log.d(TAG, "[5] Render LaunchedEffect — riveSurface=$surface, artboard=$artboardHandle, sm=$stateMachineHandle")
        if (surface == null) {
            Log.w(TAG, "[5] riveSurface is NULL — render loop skipped")
            return@LaunchedEffect
        }

        Log.d(TAG, "[6] Starting render loop — waiting for RESUMED lifecycle")
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            Log.d(TAG, "[7] Lifecycle RESUMED — entering frame loop")
            var lastFrameTime = Duration.ZERO
            var frameCount = 0
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
                if (!hasDrawnFirstFrame) {
                    hasDrawnFirstFrame = true
                    Log.d(TAG, "[8] First frame drawn!")
                }
                frameCount++
                if (frameCount <= 3 || frameCount % 60 == 0) {
                    Log.d(TAG, "[9] Frame #$frameCount drawn (dt=${deltaTime})")
                }
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
        Log.d(TAG, "[S1] onSurfaceTextureAvailable — ${width}x${height}")
        try {
            val surface = riveWorker.createRiveSurface(surfaceTexture)
            Log.d(TAG, "[S2] RiveSurface created — $surface")
            onSurfaceCreated(surface)
        } catch (e: Exception) {
            Log.e(TAG, "[S2] FAILED to create RiveSurface", e)
        }
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        Log.d(TAG, "[S3] onSurfaceTextureDestroyed")
        // Return false to keep the SurfaceTexture alive — the TextureView may be recycled.
        onSurfaceDestroyed()
        return false
    }

    override fun onSurfaceTextureSizeChanged(
        surfaceTexture: SurfaceTexture,
        width: Int,
        height: Int,
    ) {
        // No-op — matches the upstream SDK pattern (Rive.kt / RiveBatch.kt).
        // The SurfaceTexture handles size changes internally; recreating the
        // EGL surface here causes EGL_BAD_ALLOC on some GPUs (Adreno).
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
        // No-op.
    }
}
