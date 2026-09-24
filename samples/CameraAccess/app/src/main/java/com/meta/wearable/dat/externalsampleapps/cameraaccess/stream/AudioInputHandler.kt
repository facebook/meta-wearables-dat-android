/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

// AudioInputHandler: captures PCM from the glasses mic over HFP (Bluetooth SCO) and forwards it via
// a callback.

package com.meta.wearable.dat.externalsampleapps.cameraaccess.stream

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRouting
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

class AudioInputHandler(context: Context) {
  // Stored as the application context so this non-lifecycle class never retains an Activity.
  private val context: Context = context.applicationContext
  private val audioManager = this.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

  companion object {
    private const val TAG = "AudioInputHandler"
    const val SAMPLE_RATE = 44100
    private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private const val BUFFER_SIZE_FACTOR = 2
    private const val SCO_CONNECT_TIMEOUT_MS = 3000L
    private const val SCO_POLL_INTERVAL_MS = 100L
    // routedDevice is null until capture is active, so poll briefly before deciding the source.
    private const val ROUTE_CHECK_ATTEMPTS = 10
    private const val ROUTE_CHECK_INTERVAL_MS = 50L
  }

  // PCM data is delivered to this callback (set by VideoRecorder to feed the AAC encoder).
  @Volatile var pcmDataCallback: ((ByteArray, Int, Int) -> Unit)? = null

  // Reference assigned on the caller thread (initializeAudioRecord/cleanup) and read on the
  // recording thread; @Volatile publishes those reads/writes safely.
  @Volatile private var audioRecord: AudioRecord? = null

  // Detects capture drifting off the glasses SCO device mid-recording (callback, not a per-buffer
  // poll).
  private var routingListener: AudioRouting.OnRoutingChangedListener? = null

  @Volatile private var glassesInputDevice: AudioDeviceInfo? = null

  @Volatile private var routedForGlasses = false
  @Volatile private var previousMode = AudioManager.MODE_NORMAL

  private val _isRecording = MutableStateFlow(false)
  val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

  // Set when capture stops unexpectedly (e.g. a phone call grabs the mic) so the caller can
  // tear the recording down gracefully.
  private val _wasInterrupted = MutableStateFlow(false)
  val wasInterrupted: StateFlow<Boolean> = _wasInterrupted.asStateFlow()

  @Volatile private var recordingThreadActive = false

  // Recording thread — guarded by `threadLock`
  private val threadLock = Any()
  private var recordingThread: Thread? = null

  /**
   * Routes audio to the glasses HFP mic. Returns false (never throws) when no glasses SCO device is
   * available or routing fails, so the caller records video-only — never the phone mic.
   */
  suspend fun prepare(): Boolean {
    if (routedForGlasses) return true

    val scoDevice =
        glassesCommunicationDevice()
            ?: run {
              Log.w(TAG, "No glasses (SCO) device; recording video only")
              return false
            }

    val routed =
        try {
          // Missing BLUETOOTH_CONNECT throws SecurityException; degrade to video-only, never crash.
          previousMode = audioManager.mode
          // Arm teardown before mutating system state so releaseGlassesAudio() runs on any failure.
          routedForGlasses = true
          audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
          audioManager.setCommunicationDevice(scoDevice) && awaitScoRouted()
        } catch (e: CancellationException) {
          // Restore the audio mode/route before propagating cancellation (structured concurrency).
          releaseGlassesAudio()
          throw e
        } catch (e: Exception) {
          Log.w(TAG, "Failed to route glasses audio", e)
          false
        }

    if (!routed) {
      Log.w(TAG, "Glasses SCO did not route; recording video only")
      releaseGlassesAudio()
      return false
    }

    glassesInputDevice = glassesInputDeviceOrNull()
    if (glassesInputDevice == null) {
      Log.w(TAG, "Glasses SCO routed but no input device found; recording video only")
      releaseGlassesAudio()
      return false
    }
    return true
  }

  private suspend fun awaitScoRouted(): Boolean =
      withTimeoutOrNull(SCO_CONNECT_TIMEOUT_MS) {
        while (audioManager.communicationDevice?.type != AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
          delay(SCO_POLL_INTERVAL_MS)
        }
        true
      } == true

  private fun glassesCommunicationDevice(): AudioDeviceInfo? = runCatching {
    audioManager.availableCommunicationDevices.firstOrNull {
      it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
    }
  }
      .getOrNull()

  private fun glassesInputDeviceOrNull(): AudioDeviceInfo? = runCatching {
    audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull {
      it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
    }
  }
      .getOrNull()

  private fun releaseGlassesAudio() {
    if (!routedForGlasses) return
    runCatching { audioManager.clearCommunicationDevice() }
    runCatching { audioManager.mode = previousMode }
    routedForGlasses = false
    glassesInputDevice = null
  }

  private fun initializeAudioRecord(): Boolean {
    if (audioRecord != null) {
      return true
    }

    if (
        ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
    ) {
      Log.w(TAG, "Audio recording permission not granted")
      return false
    }

    // Without a routed glasses device, refuse to open the mic so we never capture the phone.
    val glassesDevice = glassesInputDevice
    if (glassesDevice == null) {
      Log.w(TAG, "No glasses input device; refusing to open the phone mic")
      return false
    }

    val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
    if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
      Log.w(TAG, "Invalid buffer size for AudioRecord")
      return false
    }

    var record: AudioRecord? = null
    try {
      // VOICE_RECOGNITION over VOICE_COMMUNICATION: the latter's wideband SCO eats camera-stream
      // airtime and captured silence in practice.
      record =
          AudioRecord(
              MediaRecorder.AudioSource.VOICE_RECOGNITION,
              SAMPLE_RATE,
              CHANNEL_CONFIG,
              AUDIO_FORMAT,
              bufferSize * BUFFER_SIZE_FACTOR,
          )
      if (!record.setPreferredDevice(glassesDevice)) {
        Log.w(TAG, "setPreferredDevice(glasses) not accepted; relying on route verification")
      }

      if (record.state != AudioRecord.STATE_INITIALIZED) {
        Log.w(TAG, "AudioRecord initialization failed")
        record.release()
        return false
      }

      audioRecord = record
      Log.d(TAG, "AudioRecord initialized successfully")
      return true
    } catch (e: Exception) {
      Log.w(TAG, "Exception initializing AudioRecord", e)
      // Release the local instance — a throw before `audioRecord = record` leaves the field null.
      record?.release()
      audioRecord = null
      return false
    }
  }

  // Verify capture is on the glasses SCO mic before delivering PCM; routedDevice is null until
  // active, so poll.
  private fun isCaptureRoutedToGlasses(record: AudioRecord): Boolean {
    repeat(ROUTE_CHECK_ATTEMPTS) {
      val type = record.routedDevice?.type
      when {
        type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> return true
        type == null -> Thread.sleep(ROUTE_CHECK_INTERVAL_MS)
        else -> return false
      }
    }
    return record.routedDevice?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
  }

  private fun registerRoutingListener(record: AudioRecord) {
    val listener = AudioRouting.OnRoutingChangedListener { router ->
      val type = router.routedDevice?.type
      if (type != null && type != AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
        Log.w(TAG, "Capture drifted off the glasses mic; stopping audio (video keeps rolling)")
        recordingThreadActive = false
        _wasInterrupted.value = true
      }
    }
    record.addOnRoutingChangedListener(listener, Handler(Looper.getMainLooper()))
    routingListener = listener
  }

  private fun removeRoutingListener() {
    val listener = routingListener ?: return
    runCatching { audioRecord?.removeOnRoutingChangedListener(listener) }
    routingListener = null
  }

  /**
   * Starts glasses HFP mic capture (prepare() must have routed SCO first). Returns false (never
   * throws) when the glasses mic can't be opened, so the caller records video-only. Never opens the
   * phone mic.
   */
  fun startRecording(): Boolean {
    if (_isRecording.value) {
      return true
    }

    if (!initializeAudioRecord()) {
      Log.w(TAG, "Glasses mic unavailable; not starting audio capture")
      return false
    }

    _isRecording.value = true
    _wasInterrupted.value = false
    recordingThreadActive = true

    synchronized(threadLock) {
      recordingThread =
          Thread {
            val localAudioRecord = audioRecord
            if (localAudioRecord == null) {
              Log.e(TAG, "AudioRecord is null, cannot start recording")
              _isRecording.value = false
              recordingThreadActive = false
              return@Thread
            }

            var wasInterruptedBySystem = false
            try {
              localAudioRecord.startRecording()

              // Not on the glasses SCO device: stop without delivering PCM (finally handles
              // teardown).
              if (!isCaptureRoutedToGlasses(localAudioRecord)) {
                Log.w(TAG, "Capture not on the glasses mic; recording video only")
                wasInterruptedBySystem = true
                return@Thread
              }

              // Detect the route drifting off the glasses via a callback, not a per-buffer poll; a
              // null route is transient and ignored.
              registerRoutingListener(localAudioRecord)

              val buffer =
                  ByteArray(AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT))

              while (recordingThreadActive && !Thread.currentThread().isInterrupted) {
                val bytesRead = localAudioRecord.read(buffer, 0, buffer.size)
                when {
                  bytesRead > 0 -> {
                    if (recordingThreadActive) {
                      pcmDataCallback?.invoke(buffer, 0, bytesRead)
                    }
                  }
                  bytesRead == AudioRecord.ERROR_DEAD_OBJECT ||
                      bytesRead == AudioRecord.ERROR_INVALID_OPERATION ||
                      bytesRead == AudioRecord.ERROR_BAD_VALUE ||
                      bytesRead == AudioRecord.ERROR -> {
                    Log.e(TAG, "Audio recording interrupted (code=$bytesRead)")
                    wasInterruptedBySystem = true
                    break
                  }
                  else -> {
                    // bytesRead == 0: no data this round (common on an emulator's silent mic).
                    // Yield instead of spinning; the recorder's audio watchdog falls back to
                    // video-only when no PCM ever arrives.
                    Thread.sleep(10)
                  }
                }
              }
            } catch (e: InterruptedException) {
              Log.d(TAG, "Recording thread interrupted")
            } catch (e: IllegalStateException) {
              Log.e(TAG, "AudioRecord illegal state - likely interrupted by system", e)
              wasInterruptedBySystem = true
            } catch (e: Exception) {
              Log.e(TAG, "Error during audio recording", e)
              wasInterruptedBySystem = true
            } finally {
              try {
                if (localAudioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                  localAudioRecord.stop()
                }
              } catch (e: Exception) {
                Log.e(TAG, "Error stopping audio record", e)
              }
              // Written synchronously so stopRecording's thread.join() guarantees these land before
              // the next session.
              _isRecording.value = false
              if (wasInterruptedBySystem) {
                _wasInterrupted.value = true
              }
            }
          }
              .also { it.start() }
    }

    Log.d(TAG, "Audio recording started")
    return true
  }

  fun stopRecording() {
    if (_isRecording.value) {
      recordingThreadActive = false
      _isRecording.value = false

      val thread =
          synchronized(threadLock) {
            recordingThread?.interrupt()
            recordingThread
          }

      thread?.let { audioThread ->
        audioThread.join(1000)
        if (audioThread.isAlive) {
          Log.w(TAG, "Recording thread did not terminate within timeout")
        }
      }
    }

    removeRoutingListener()
    // Always restore the route — prepare() may have routed even if capture never started.
    releaseGlassesAudio()
  }

  fun cleanup() {
    pcmDataCallback = null
    stopRecording()
    _wasInterrupted.value = false

    removeRoutingListener()
    audioRecord?.release()
    audioRecord = null
  }
}
