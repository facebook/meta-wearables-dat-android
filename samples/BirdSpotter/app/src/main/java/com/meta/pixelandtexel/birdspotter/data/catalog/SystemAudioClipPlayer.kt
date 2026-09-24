/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.catalog

import android.content.res.AssetFileDescriptor
import android.media.MediaPlayer
import com.meta.pixelandtexel.birdspotter.domain.AudioClipPlayer

/**
 * The [AudioClipPlayer] the app ships: `MediaPlayer` over a descriptor the [CatalogAssetStore]
 * opens from `assets/`.
 *
 * The mirror is [AudioClipPlayer], not this class — the engine and the audio focus it takes are
 * exactly the platform plumbing the architecture note keeps idiomatic. The one behaviour the
 * interface promises: `play` after a clip has finished replays it from the top —
 * `MediaPlayer.start()` rewinds a completed player itself — so the play button always means the
 * same thing.
 */
class SystemAudioClipPlayer(private val assets: CatalogAssetStore) : AudioClipPlayer {

  override var onFinish: (() -> Unit)? = null

  private var player: MediaPlayer? = null
  private var descriptor: AssetFileDescriptor? = null

  /** Which clip [player] holds, so [play] can tell "resume this" from "load that". */
  private var loadedMediaId: String? = null

  override val isPlaying: Boolean
    get() = player?.isPlaying ?: false

  override val progress: Double
    get() {
      val engine = player ?: return 0.0
      val duration = engine.duration
      return if (duration > 0) {
        (engine.currentPosition.toDouble() / duration).coerceIn(0.0, 1.0)
      } else {
        0.0
      }
    }

  override fun play(media: SpeciesMedia) {
    // ensureLoaded hands back the clip already loaded — at wherever pause or a scrub left
    // it, or 0 after it finished — or a fresh one cued to the top.
    ensureLoaded(media)?.start()
  }

  override fun seek(media: SpeciesMedia, progress: Double) {
    val engine = ensureLoaded(media) ?: return
    engine.seekTo((progress.coerceIn(0.0, 1.0) * engine.duration).toInt())
  }

  /**
   * The player for [media]: the one already loaded, keeping its position, or a freshly loaded and
   * prepared one cued to the top. Never starts playback — the caller decides, so a scrub can
   * position a clip that then waits, paused, for the play button.
   */
  private fun ensureLoaded(media: SpeciesMedia): MediaPlayer? {
    player?.let { if (loadedMediaId == media.id) return it }

    stop() // release any previous clip and its descriptor

    val engine = MediaPlayer()
    return try {
      val fd = assets.openFd(media)
      engine.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
      engine.setOnCompletionListener { onFinish?.invoke() }
      engine.prepare() // a local asset prepares in a few milliseconds
      player = engine
      descriptor = fd
      loadedMediaId = media.id
      engine
    } catch (_: Exception) {
      // A missing or unreadable recording leaves the card silent, not crashed.
      engine.release()
      null
    }
  }

  override fun pause() {
    player?.takeIf { it.isPlaying }?.pause()
  }

  override fun stop() {
    player?.release()
    player = null
    descriptor?.close()
    descriptor = null
    loadedMediaId = null
  }
}
