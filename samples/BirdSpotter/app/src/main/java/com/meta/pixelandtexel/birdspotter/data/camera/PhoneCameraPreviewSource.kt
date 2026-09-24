/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Size
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.meta.pixelandtexel.birdspotter.domain.CameraControl
import com.meta.pixelandtexel.birdspotter.domain.CameraPreviewError
import com.meta.pixelandtexel.birdspotter.domain.CameraPreviewSource
import com.meta.pixelandtexel.birdspotter.domain.CameraViewfinder
import com.meta.pixelandtexel.birdspotter.domain.CaptureSourceKind
import com.meta.pixelandtexel.birdspotter.domain.PreviewFrame
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * The phone's rear camera as a [CameraPreviewSource] — the only live viewfinder the app has.
 * Glasses never stream one: their photos arrive finished, as timeline events.
 *
 * **`Preview` for the picture, `ImageAnalysis` for the photograph** — two use cases on one camera,
 * because the two jobs want opposite things:
 *
 * - The *picture* has to feel like a camera, which means the hardware path: `Preview` writes into a
 *   surface the compositor draws directly, no frame ever crossing app code. Zoom shows up there at
 *   sensor rate, which is why a pinch now tracks the fingers. This file used to route the picture
 *   through analysis frames instead, and that pipeline — convert, copy, flow, recompose, upload, 30
 *   times a second — was the viewfinder lag in its entirety.
 * - The *photograph* has to be bytes the app can keep: analysis frames, RGBA_8888 so the
 *   `toBitmap()` copy is a memcpy, of which the shutter grabs the latest. A capture pipeline would
 *   be the "proper" answer, but a photo here is defined as *the frame that was on screen* — see the
 *   flash note on [CameraPreviewSource.setTorch].
 *
 * Rotation is CameraX's job, not the renderer's: `setOutputImageRotationEnabled` is only honoured
 * for RGBA_8888 output, which is the other half of why the format is not a free choice. Frames
 * therefore arrive upright, as [PreviewFrame] promises; the `Preview` surface handles its own. The
 * app is portrait-only — see `android:screenOrientation` in AndroidManifest.xml — so the target
 * rotation is a constant rather than something to track across configuration changes.
 *
 * Cold, and it means it: nothing is opened until someone collects [previewStream], and the
 * `awaitClose` block is what releases the camera. [viewfinderStream] rides along rather than owning
 * anything — surfaces are requested only while the frames are collected. The producer runs on the
 * main thread — `bindToLifecycle` and `LifecycleRegistry` both insist on it — while frames are
 * converted on a single background thread, and `conflate` drops anything the UI could not keep up
 * with.
 *
 * Zoom is forwarded per gesture event, the way every camera app does it. The pacing this file
 * briefly grew was treating a symptom: requests only ever *felt* queued because the picture came
 * back through the frame pipeline. On the hardware path the crop lands within a frame or two of the
 * ask, and there is nothing left to pace.
 *
 * The seam is [CameraPreviewSource]; the capture graph below it is platform plumbing.
 */
class PhoneCameraPreviewSource(context: Context) : CameraPreviewSource {

  private val context = context.applicationContext

  override val kind = CaptureSourceKind.PHONE

  /** A phone does both. The only line the panel's controls are built from. */
  override val controls = setOf(CameraControl.FLASH, CameraControl.ZOOM)

  override val zoomRange = 1f..MaximumZoom

  /**
   * The bound camera.
   *
   * It belongs to the main thread — `bindToLifecycle` insists, and CameraX's control surface is
   * main-thread by convention — so the two setters below post there rather than touching it where
   * they were called. `@Volatile` covers the one hop the reference makes between the collector's
   * thread and that post.
   */
  @Volatile private var bound: BoundCamera? = null

  /**
   * Whether the light is meant to be on. Held rather than read back off the camera, so that tearing
   * the stream down can put it out without first asking hardware that may be gone.
   */
  @Volatile private var isTorchWanted = false

  /**
   * The surface asks, replayed to whoever is showing the picture.
   *
   * `replay = 1` is what lets the panel arrive *after* the camera: CameraX asks for its surface the
   * moment the `Preview` use case binds, and a collector that shows up mid-stream still needs that
   * ask. `DROP_OLDEST` keeps a fresh ask from ever waiting on a stale one.
   */
  private val viewfinders = MutableSharedFlow<CameraViewfinder>(
      replay = 1,
      extraBufferCapacity = 1,
      onBufferOverflow = BufferOverflow.DROP_OLDEST,
  )

  override fun viewfinderStream(): Flow<CameraViewfinder> = viewfinders.asSharedFlow()

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun previewStream(): Flow<PreviewFrame> = callbackFlow {
    // The Identify gate should have collected this already, so reaching here denied means
    // the permission was revoked from Settings while the cover was open — a real path, and
    // one that reads better as an honest error than as a viewfinder that never lights up.
    if (
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
    ) {
      close(CameraPreviewError.AccessDenied)
      return@callbackFlow
    }

    val provider =
        try {
          cameraProvider(context)
        } catch (_: Exception) {
          close(CameraPreviewError.Unavailable)
          return@callbackFlow
        }

    val frameExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    val analysis = buildAnalysis()
    // Throttled to [PhotographIntervalMillis]: these frames exist to keep the shutter's next
    // photograph warm, and warming it thirty times a second is ~110 MB/s of copying for a
    // picture taken maybe once a minute. The live view stopped needing them the day it moved
    // onto the Preview surface. `lastConverted` is the analyzer thread's alone.
    var lastConverted = 0L
    analysis.setAnalyzer(frameExecutor) { image ->
      // `use` closes the proxy no matter what: an analyzer that leaks one image stalls
      // the whole stream, because CameraX will not hand out the next until this returns
      // its buffer.
      image.use {
        val now = SystemClock.uptimeMillis()
        if (now - lastConverted >= PhotographIntervalMillis) {
          lastConverted = now
          trySend(PreviewFrame(it.toBitmap()))
        }
      }
    }

    // The picture. Its surface requests go out through [viewfinderStream]; the panel answers
    // them by handing CameraX a surface to draw into, and no pixel of the live picture ever
    // comes back through this process.
    val preview =
        Preview.Builder().setTargetRotation(Surface.ROTATION_0).build().apply {
          setSurfaceProvider { request -> viewfinders.tryEmit(CameraViewfinder(request)) }
        }

    val owner = StreamLifecycleOwner()
    try {
      owner.resume()
      isTorchWanted = false
      val camera =
          provider.bindToLifecycle(
              owner,
              CameraSelector.DEFAULT_BACK_CAMERA,
              preview,
              analysis,
          )
      bound = BoundCamera(camera)
    } catch (_: Exception) {
      // Nothing is bound, so there is nothing to unbind — but the executor and the
      // lifecycle are already ours to put back.
      owner.destroy()
      analysis.clearAnalyzer()
      frameExecutor.shutdown()
      close(CameraPreviewError.Unavailable)
      return@callbackFlow
    }

    awaitClose {
      // The light goes out with the stream, always. A torch is the one thing here that
      // outlives the screen that lit it, and a phone left glowing in a pocket is the worst
      // bug this file could ship.
      isTorchWanted = false
      bound?.camera?.cameraControl?.enableTorch(false)
      bound = null

      // Destroying the owner is what unbinds — CameraX watches the lifecycle rather than
      // taking a release call — so the order matters: let go of the camera, then stop
      // converting frames for a collector that has gone. Unbinding also cancels the
      // outstanding surface request, which is CameraX telling the panel's viewfinder it
      // is over; the replay cache is cleared so a *reopened* panel waits for the new
      // camera's ask instead of answering the dead one's.
      owner.destroy()
      analysis.clearAnalyzer()
      frameExecutor.shutdown()
      viewfinders.resetReplayCache()
    }
  }
      // Only the newest frame is worth keeping for the shutter; a queue of them is just memory.
      // `STRATEGY_KEEP_ONLY_LATEST` says the same thing to CameraX one stage earlier.
      .conflate()
      .flowOn(Dispatchers.Main)

  // ── The two controls ───────────────────────────────────────────────────
  //
  // Both hop to the main thread, because that is where the camera was bound and where
  // CameraX expects to be spoken to. With no stream open they are no-ops, which is the honest
  // answer to being asked to light a camera that is not on.

  override fun setTorch(isOn: Boolean) {
    isTorchWanted = isOn
    onMain { applyTorch(it) }
  }

  override fun setZoom(factor: Float) {
    val clamped = factor.coerceIn(zoomRange)
    onMain { it.camera.cameraControl.setZoomRatio(clamped) }
  }

  /**
   * Puts the light where it is wanted, as far as this camera can. A camera with no torch is not a
   * failure — it is a camera that takes its pictures by available light.
   */
  private fun applyTorch(current: BoundCamera) {
    if (!current.camera.cameraInfo.hasFlashUnit()) return
    current.camera.cameraControl.enableTorch(isTorchWanted)
  }

  /** Runs [block] against the bound camera on the main thread, or not at all. */
  private fun onMain(block: (BoundCamera) -> Unit) {
    ContextCompat.getMainExecutor(context).execute {
      bound?.let(block)
    }
  }

  private fun buildAnalysis(): ImageAnalysis =
      ImageAnalysis.Builder()
          .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
          .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
          .setOutputImageRotationEnabled(true)
          // Spelled out rather than left to default. CameraX would otherwise read the display's
          // rotation at bind time, which is the same value only because the activity is locked to
          // portrait — stating it keeps the frames upright even if that lock ever moves.
          .setTargetRotation(Surface.ROTATION_0)
          .setResolutionSelector(
              ResolutionSelector.Builder()
                  .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                  .setResolutionStrategy(
                      ResolutionStrategy(
                          FrameSize,
                          ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                      ),
                  )
                  .build(),
          )
          .build()
}

/**
 * 720p, deliberately modest — for the *photograph*, not the picture. The live view no longer pays
 * per-frame for its resolution, but every captured frame is still copied into a bitmap and kept on
 * the timeline, and a 12 MP one of those is a lot of photograph for a demo. It also matches what
 * the glasses' photos will arrive around, which is one less difference to explain on stage.
 */
private val FrameSize = Size(720, 1280)

/**
 * How often a frame is actually converted into a photograph-in-waiting — ten a second.
 *
 * Enough that the shutter's picture is never more than 100 ms behind the surface (the flash path
 * waits three times that for the exposure anyway), and a fraction of the copying that converting
 * every frame cost.
 */
private const val PhotographIntervalMillis = 100L

/**
 * How far the viewfinder will magnify. Well under what the sensor will digitally stretch to — past
 * this a bird is a smear of four pixels, and a control that keeps going long after the picture
 * stopped improving is a control that lies about what the camera can see.
 */
private const val MaximumZoom = 8f

/** The bound camera, and the handle its controls hang off. */
private data class BoundCamera(val camera: Camera)

/**
 * Bridges `ProcessCameraProvider.getInstance`'s `ListenableFuture` into a suspend call.
 *
 * CameraX ships no `suspend` overload at 1.6, and a future is not something a cold flow can hold
 * open across cancellation, so this is the one piece of glue the seam needs.
 */
private suspend fun cameraProvider(context: Context): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
      val future = ProcessCameraProvider.getInstance(context)
      future.addListener(
          {
            try {
              continuation.resume(future.get())
            } catch (error: Exception) {
              continuation.resumeWithException(error)
            }
          },
          ContextCompat.getMainExecutor(context),
      )
      continuation.invokeOnCancellation { future.cancel(false) }
    }

/**
 * A lifecycle of our own, so a cold stream can own a camera.
 *
 * `bindToLifecycle` is CameraX's only way in and it wants a [LifecycleOwner], which a `Flow` is
 * not. Rather than push the Activity's lifecycle down into the data layer — where it would tie the
 * camera to the screen being resumed — the stream brings its own: resumed while it is collected,
 * destroyed when it is not. That is exactly the cold-stream contract the architecture note asks
 * for, expressed in the one vocabulary CameraX understands.
 */
private class StreamLifecycleOwner : LifecycleOwner {

  private val registry = LifecycleRegistry(this)

  override val lifecycle: Lifecycle
    get() = registry

  fun resume() {
    registry.currentState = Lifecycle.State.RESUMED
  }

  fun destroy() {
    registry.currentState = Lifecycle.State.DESTROYED
  }
}
