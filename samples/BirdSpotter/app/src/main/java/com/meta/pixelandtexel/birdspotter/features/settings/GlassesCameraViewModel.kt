/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.settings

import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.meta.pixelandtexel.birdspotter.data.camera.CaptureScratchStore
import com.meta.pixelandtexel.birdspotter.domain.BirdLog
import com.meta.pixelandtexel.birdspotter.domain.CaptureQuality
import com.meta.pixelandtexel.birdspotter.domain.CaptureResolution
import com.meta.pixelandtexel.birdspotter.domain.GlassesAccess
import com.meta.pixelandtexel.birdspotter.domain.GlassesCameraRepository
import com.meta.pixelandtexel.birdspotter.domain.GlassesError
import com.meta.pixelandtexel.birdspotter.domain.GlassesPermission
import com.meta.pixelandtexel.birdspotter.domain.GlassesSessionRepository
import com.meta.pixelandtexel.birdspotter.domain.GlassesSessionState
import com.meta.pixelandtexel.birdspotter.domain.LogCategory
import com.meta.pixelandtexel.birdspotter.domain.PhotoFormat
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One photograph that crossed, and everything measured about the crossing.
 *
 * **The numbers are the point of the screen, not decoration on it.** A photograph on its own
 * answers "did it work"; the size it was asked for, the bytes that arrived and the seconds they
 * took are what answer "what does `LARGE` cost", which is the question the two pickers above exist
 * to ask.
 *
 * @property file Where the bytes landed — what the share sheet is handed. See
 *   [CaptureScratchStore].
 * @property resolution What was asked for, kept beside the result rather than read off the pickers:
 *   the pickers move on to the next experiment, and a photograph must go on saying which settings
 *   it is the answer for.
 * @property quality The same, for compression.
 * @property byteCount How many bytes arrived.
 * @property pixelWidth The image's own width, or `null` when the bytes could not be read as an
 *   image at all.
 * @property pixelHeight The image's own height, on the same terms.
 * @property crossingMillis How long the crossing took, shutter to bytes.
 */
data class GlassesCameraShot(
    val file: File,
    val resolution: CaptureResolution,
    val quality: CaptureQuality,
    val byteCount: Int,
    val pixelWidth: Int?,
    val pixelHeight: Int?,
    val crossingMillis: Long,
)

/**
 * What the camera screen shows.
 *
 * @property isRunning Whether a session has been asked for and not yet hung up on.
 * @property sessionState Where the session is — `null` before the first reading.
 * @property cameraAccess Meta AI's camera grant — `null` before the first reading. Without it the
 *   shutter is an offer that cannot be kept.
 * @property honoursCaptureSettings Whether the two pickers below actually reach the capture — see
 *   [GlassesCameraRepository.honoursCaptureSettings].
 * @property resolution What the next shutter will ask for.
 * @property quality The same, for compression.
 * @property isCapturing Whether a crossing is in flight. The shutter is one at a time — the
 *   capability refuses a second anyway, and a queue of them would be a queue of things nobody asked
 *   for by the time they landed.
 * @property shot The last photograph that arrived, or `null` before the first one.
 * @property captureFailure Why the last shutter came back with nothing. Cleared by the next press.
 * @property failure Why the run ended, when it ended badly.
 */
data class GlassesCameraUiState(
    val isRunning: Boolean = false,
    val sessionState: GlassesSessionState? = null,
    val cameraAccess: GlassesAccess? = null,
    val honoursCaptureSettings: Boolean = true,
    val resolution: CaptureResolution = CaptureResolution.SessionDefault,
    val quality: CaptureQuality = CaptureQuality.SessionDefault,
    val isCapturing: Boolean = false,
    val shot: GlassesCameraShot? = null,
    val captureFailure: String? = null,
    val failure: String? = null,
) {
  /**
   * Whether the shutter can be pressed at all: a session up, and nothing already crossing.
   *
   * **The grant is deliberately not in this test.** A denied grant is read off a live link and can
   * be answered in another app between one press and the next, so refusing the press on it would
   * leave a dead button on a screen whose own reading has gone stale. The row above says what is
   * wrong; the press is allowed to fail honestly.
   */
  val canCapture: Boolean
    get() = sessionState == GlassesSessionState.STARTED && !isCapturing
}

/**
 * A session opened for one purpose: to take one photograph at chosen settings and look at what
 * comes back.
 *
 * **A measuring instrument, not a feature.** The live flow already takes photographs, at the app's
 * own standing settings and straight onto the timeline where the point of them is the bird. What it
 * cannot do is take the *same* picture twice at two sizes and say what the second one cost — which
 * is the only way the standing settings (see [CaptureQuality]) ever get to be more than a guess. So
 * this screen opens a session, puts the SDK's two knobs on screen, and prints bytes and
 * milliseconds beside the result.
 *
 * The session comes up the moment the screen opens, the way the other glasses sub-screens do: the
 * whole point of being here is to fire the shutter, and a Connect button first would just be a step
 * on every attempt. The **full** lease, deliberately and unlike the display screen's — the camera
 * is exactly what this wants lit.
 */
class GlassesCameraViewModel(
    private val glassesSession: GlassesSessionRepository,
    private val glassesCamera: GlassesCameraRepository,
    private val scratch: CaptureScratchStore,
) : ViewModel() {

  private val _uiState = MutableStateFlow(
      GlassesCameraUiState(honoursCaptureSettings = glassesCamera.honoursCaptureSettings),
  )
  val uiState: StateFlow<GlassesCameraUiState> = _uiState.asStateFlow()

  /** The run: the session's lease, and the device watcher that lives inside it. */
  private var run: Job? = null

  /**
   * The crossing in flight. Leaving the screen drops it — a fifteen-second timeout has no business
   * running behind a screen nobody is on — which the scope this launches in already does; the
   * handle is kept so the shutter has one thing to name.
   */
  private var capture: Job? = null

  /**
   * How many photographs this visit has taken, which is all the file names need to be distinct —
   * see [fileName].
   */
  private var shotCount = 0

  init {
    start()
  }

  /**
   * Opens the session, and throws away whatever the last visit left on disk.
   *
   * Idempotent — called once from init, and again only by the retry offered after a failure. The
   * sweep is on the way in for the reason [CaptureScratchStore.empty] gives.
   */
  fun start() {
    if (run?.isActive == true) return
    _uiState.update { it.copy(isRunning = true, failure = null) }
    run = viewModelScope.launch {
      withContext(Dispatchers.IO) { scratch.empty() }
      hold()
    }
  }

  /**
   * What the next shutter asks for. Changing either leaves the photograph on screen alone — it
   * carries the settings it was taken at, so the two can be compared rather than one silently
   * relabelled.
   */
  fun choose(resolution: CaptureResolution) {
    _uiState.update { it.copy(resolution = resolution) }
  }

  fun choose(quality: CaptureQuality) {
    _uiState.update { it.copy(quality = quality) }
  }

  /**
   * Fires the shutter and waits out the crossing.
   *
   * The previous photograph stays on screen for the whole wait rather than being cleared at the
   * press: a blank frame for a second and a half reads as the screen having lost the picture, and
   * there is nothing to compare against while it is blank.
   */
  fun capturePhoto() {
    val current = _uiState.value
    if (!current.canCapture) return
    val resolution = current.resolution
    val quality = current.quality
    shotCount += 1
    val name = fileName(resolution, quality, shotCount)
    _uiState.update { it.copy(isCapturing = true, captureFailure = null) }

    capture = viewModelScope.launch {
      // `elapsedRealtime`, not the wall clock, which a correction mid-crossing would
      // move — a photograph that arrived before it was asked for is not a measurement.
      val startedAt = SystemClock.elapsedRealtime()
      try {
        val photo = glassesCamera.capturePhoto(PhotoFormat.JPEG, resolution, quality)
        val crossingMillis = SystemClock.elapsedRealtime() - startedAt
        // Off the main thread: a full-size still is megabytes, and both the write and
        // the header read are file work that has no business on the thread drawing
        // the screen.
        val landed =
            withContext(Dispatchers.IO) {
              val file = scratch.write(photo.imageData, name)
              file to file?.let { pixelSize(it) }
            }
        val file = landed.first
        if (file == null) {
          // Bytes that crossed and then could not be written are a different
          // failure from a crossing that never finished, and saying so is the
          // difference between blaming the glasses and blaming the phone.
          BirdLog.error(LogCategory.GLASSES) {
            "camera screen — the photograph could not be written"
          }
          _uiState.update {
            it.copy(
                isCapturing = false,
                captureFailure = "The photograph arrived but could not be saved on this phone.",
            )
          }
          return@launch
        }
        _uiState.update {
          it.copy(
              isCapturing = false,
              shot =
                  GlassesCameraShot(
                      file = file,
                      resolution = resolution,
                      quality = quality,
                      byteCount = photo.imageData.size,
                      pixelWidth = landed.second?.first,
                      pixelHeight = landed.second?.second,
                      crossingMillis = crossingMillis,
                  ),
          )
        }
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (error: Throwable) {
        BirdLog.error(LogCategory.GLASSES, error) {
          "camera screen — the shutter came back empty"
        }
        _uiState.update {
          it.copy(isCapturing = false, captureFailure = captureParting(error))
        }
      }
    }
  }

  /**
   * The run itself: the session held open, the device snapshot read for as long as it is, and the
   * camera grant re-asked whenever the link changes — the grant lives on the glasses, so a pair
   * coming into range is the moment it becomes answerable at all.
   */
  private suspend fun hold() {
    try {
      coroutineScope {
        // The watcher outlives nothing: the device stream never completes on its own,
        // so it is cancelled by hand once the session's stream has ended.
        // The device stream is followed for its *arrivals* rather than its
        // contents: the grant lives on the glasses, so a pair coming into range is the
        // moment it becomes answerable at all. Whether the pair is reachable is
        // deliberately not read here — the screen above owns that explanation, and
        // saying it twice is two places to keep true.
        val watcher = launch {
          glassesSession.deviceInfoStream().collect {
            val access = glassesSession.access(GlassesPermission.CAMERA)
            _uiState.update { state -> state.copy(cameraAccess = access) }
          }
        }
        glassesSession.sessionStream().collect { state ->
          _uiState.update { it.copy(sessionState = state) }
        }
        watcher.cancel()
      }
      // A session that ends of its own accord — a doff, a fold, a long press — takes
      // the shutter with it. The photograph stays: it is a measurement that already
      // happened, and it is still true.
      _uiState.update { it.copy(isRunning = false) }
    } catch (cancellation: CancellationException) {
      throw cancellation
    } catch (error: Throwable) {
      BirdLog.error(LogCategory.GLASSES, error) {
        "camera screen — the session ended in failure"
      }
      _uiState.update { it.copy(isRunning = false, failure = parting(error)) }
    }
  }

  companion object {

    /**
     * Why the run ended, in a line. The same two answers the realtime screen gives, because they
     * are the only two the app can tell apart — the log line beside this one carries the rest.
     */
    fun parting(error: Throwable): String =
        if (error is GlassesError.GlassesUpdateRequired) {
          "Your glasses need a firmware update — check them in the Meta AI app"
        } else {
          "The glasses session ended — check they are connected and try again"
        }

    /**
     * Why one shutter came back empty. **Separate from [parting] because a failed photograph is not
     * a failed session** — the link is usually still up, the next press usually works, and telling
     * somebody to check their connection over a dropped crossing sends them to fix something that
     * is not broken.
     */
    fun captureParting(error: Throwable): String =
        when (error) {
          is GlassesError.TransferFailed ->
              "The photograph never finished crossing. Try again — a big one takes longer."
          is GlassesError.NotConnected ->
              "The camera is not up. Give the session a moment, or reconnect."
          else -> "The shutter failed."
        }

    /**
     * What one photograph is called on disk: the settings it was taken at, and a number to keep two
     * of them apart.
     *
     * **The settings are in the name on purpose.** This name is what the share sheet shows and what
     * lands in the gallery, and three test shots called `photo-1`, `photo-2`, `photo-3` are three
     * photographs nobody can tell apart an hour later — which is the whole of what the exercise was
     * for.
     *
     * Lower-cased through [java.util.Locale.US] rather than the phone's: these are ASCII enum names
     * by contract, and a locale-sensitive fold would be wrong on a Turkish phone for no gain at
     * all.
     */
    fun fileName(
        resolution: CaptureResolution,
        quality: CaptureQuality,
        index: Int,
    ): String {
      val size = resolution.name.lowercase(java.util.Locale.US)
      val compression = quality.name.lowercase(java.util.Locale.US)
      return "glasses-$size-$compression-$index.jpg"
    }

    /**
     * The image's dimensions, read from the file's header rather than by decoding it —
     * `inJustDecodeBounds` never materialises the bitmap, which for a full-size still is the
     * difference between a few bytes read and several megabytes of pixels nobody wanted.
     */
    fun pixelSize(file: File): Pair<Int, Int>? {
      val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeFile(file.path, options)
      return if (options.outWidth > 0 && options.outHeight > 0) {
        options.outWidth to options.outHeight
      } else {
        null
      }
    }

    fun factory(
        glassesSession: GlassesSessionRepository,
        glassesCamera: GlassesCameraRepository,
        scratch: CaptureScratchStore,
    ): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
          override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            @Suppress("UNCHECKED_CAST")
            return GlassesCameraViewModel(glassesSession, glassesCamera, scratch) as T
          }
        }
  }
}
