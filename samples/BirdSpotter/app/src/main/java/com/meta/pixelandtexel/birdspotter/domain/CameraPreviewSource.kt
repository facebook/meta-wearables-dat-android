/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.domain

import android.graphics.Bitmap
import androidx.camera.core.SurfaceRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * One frame of a live camera, already the right way up.
 *
 * **Upright is the contract, not a suggestion.** Each source normalises at its own edge — CameraX
 * is asked to rotate its output — so nothing downstream carries a rotation it has to remember to
 * apply. The alternative (ship the angle, rotate in the renderer) leaks one platform's sensor
 * mounting into a screen that should only ever draw what it is handed.
 *
 * A wrapper around a single [Bitmap] rather than `Flow<Bitmap>`, because a bare bitmap says nothing
 * about what it is *for*: this is **the photograph the shutter will take** — see
 * [CameraPreviewSource.previewStream] for what frames are and are not used for.
 *
 * The two are the same choice: a graphics-framework image, not a UI-framework one, so the domain
 * does not depend on the UI framework to describe a picture.
 */
class PreviewFrame(val image: Bitmap)

/**
 * The platform's own hardware-path picture — what the viewfinder *shows*, where [PreviewFrame] is
 * what the shutter *takes*.
 *
 * The payload is deliberately platform plumbing: CameraX hands the screen a [SurfaceRequest] and
 * the camera draws into the surface itself, with no frame ever crossing app code — the whole point
 * of the handle. The type exists so the *seam* is mirrored even though the payload cannot be.
 */
class CameraViewfinder(val request: SurfaceRequest)

/**
 * Why a camera has no picture.
 *
 * Three cases, because there are exactly three answers worth giving a user: they said no, there is
 * nothing to look through, or something else took the camera.
 */
sealed class CameraPreviewError(message: String) : Exception(message) {

  /** Camera permission is not granted. The Identify gate should have caught this first. */
  data object AccessDenied : CameraPreviewError("Camera access is not granted")

  /** No camera to open — no hardware to look through. */
  data object Unavailable : CameraPreviewError("No camera is available")

  /** The camera was opened and then lost: another app took it, or the session dropped. */
  data object Interrupted : CameraPreviewError("The camera stream was interrupted")
}

/**
 * A live camera the watcher frames a shot with — **the phone's**, and only the phone's.
 *
 * This seam used to be described as the one the glasses would arrive through, and the frames-only
 * shape was chosen so a `GlassesPreviewSource` could drop in behind it. That class is never coming:
 * **a pair of glasses has no viewfinder in the app**, because the wearer's own eyes are the
 * viewfinder — they look at the bird, press the action button, and the finished photo lands on the
 * session timeline as an event, the way a detector's finding does. What the glasses need is a seam
 * for *photos that arrive*, not one for frames that stream.
 *
 * That is why this source has two outputs instead of one:
 *
 * - [viewfinderStream] is **the picture** — the platform's hardware path, camera to compositor,
 *   never touching app code. This is what makes a pinch feel like the camera app's: the preview
 *   does not wait on any copy this process makes.
 * - [previewStream] is **the photograph** — upright frames the shutter grabs the latest of, and the
 *   fallback picture for sources (Compose previews, simulated feeds) that have no surface to offer.
 *
 * Cold, per the architecture contract: collecting starts the camera and cancelling stops it, so
 * there is no `start()`/`stop()` pair to keep in sync across two platforms. The frame stream fails
 * with a [CameraPreviewError]; it does not complete on its own.
 */
interface CameraPreviewSource {

  /** Which camera this is — what the viewfinder's source pill reads. */
  val kind: CaptureSourceKind

  /**
   * What this camera can be asked to do — see [CameraControl]. Fixed for the life of the source: it
   * describes the hardware, not the state of a running stream.
   *
   * **Doing none of it is the default.** A source that only produces frames — the previews,
   * whatever a demo needs next — says nothing and gets an empty set.
   */
  val controls: Set<CameraControl>
    get() = emptySet()

  /**
   * How far this camera magnifies, `1` being none. `1..1` for a camera that cannot, which is also
   * what an absent [CameraControl.ZOOM] says — the range is here so a pinch has something to clamp
   * against without asking the hardware mid-gesture.
   */
  val zoomRange: ClosedFloatingPointRange<Float>
    get() = 1f..1f

  /** Frames, upright, until the collector goes away. Cold: collecting is what opens the camera. */
  fun previewStream(): Flow<PreviewFrame>

  /**
   * The hardware-path picture, for as long as [previewStream] is collected — a new
   * [CameraViewfinder] whenever the platform wants a new surface.
   *
   * Empty by default, and empty is meaningful: a source with no surface to offer (a preview, a
   * simulated feed) simply never emits, and the panel falls back to drawing [previewStream]'s
   * frames — a picture either way, just not a free one.
   */
  fun viewfinderStream(): Flow<CameraViewfinder> = emptyFlow()

  /**
   * Hold the light on, or let it go.
   *
   * **A torch is the mechanism; a flash is what it is for.** There is no still-capture pipeline
   * here to hand a flash mode to — a photo is the viewfinder frame that was on screen — so the
   * screen lights the torch, waits for the exposure to catch up, takes its frame and puts the light
   * out. That sequence is the session's to run, not this seam's: how long to wait is a judgement
   * about photographs, and a camera source only knows about lights.
   *
   * The no-op default is the honest answer to being asked anyway: a camera with no torch does not
   * fail when told to light up, it simply has no light. The screen never reaches here, because
   * [controls] said so first.
   */
  fun setTorch(isOn: Boolean) = Unit

  /** Magnify, clamped to [zoomRange]. */
  fun setZoom(factor: Float) = Unit
}

/**
 * Something a camera can be asked to do beyond producing frames.
 *
 * **A set, not a pile of booleans**, because the question the viewfinder asks is always "can this
 * one do X" and the answer for a source that can do neither of them should be an empty set rather
 * than two `false`s it had to remember to write.
 *
 * The phone answers with both; a simulated source answers with neither, and the panel simply has
 * fewer controls on it, without a line of `if (kind == ...)` anywhere in the UI. A control that
 * cannot do anything is not disabled here; it is **absent**, because a greyed-out flash is an
 * invitation to wonder what is broken.
 */
enum class CameraControl {
  /**
   * A light that can be lit for the moment a photo is taken — see [CameraPreviewSource.setTorch].
   */
  FLASH,

  /** Magnification, over some range wider than a single point. */
  ZOOM,
}
