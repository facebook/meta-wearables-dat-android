/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.media

import android.content.Context
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The app's captured-media directory: bytes in, relative paths out.
 *
 * Deliberately knows nothing about sightings, the database, or bird anything — it moves files.
 * Pairing a file with a row is
 * [com.meta.pixelandtexel.birdspotter.data.journal.LocalJournalRepository]'s job, and keeping the
 * two concerns apart is what lets the write ordering (file first, then row) live in exactly one
 * place.
 *
 * **Paths are always relative to [rootDir].** An absolute path stored in the database would be
 * wrong the moment the app's data directory moves — which happens on restore — so callers store
 * what [write] returns and resolve it again through [resolve].
 *
 * Bundled catalog media is *not* handled here; that is an `assetKey` resolved out of `assets/`, and
 * it is read-only. This store owns only what the user captured.
 */
class MediaFileStore(private val rootDir: File) {

  /**
   * Writes [bytes] to `<group>/<name>.<fileExtension>` and returns the path relative to the media
   * root.
   *
   * [group] is an opaque bucket the caller chooses (the repository uses the sighting id) so related
   * files can be dropped together with [deleteGroup].
   */
  suspend fun write(
      bytes: ByteArray,
      group: String,
      name: String,
      fileExtension: String,
  ): String =
      withContext(Dispatchers.IO) {
        val relativePath = "$group/$name.$fileExtension"
        val target = resolve(relativePath)
        target.parentFile?.mkdirs()
        target.writeBytes(bytes)
        relativePath
      }

  /** Absolute file for a stored relative path. Does not check existence. */
  fun resolve(relativePath: String): File = File(rootDir, relativePath)

  suspend fun exists(relativePath: String): Boolean =
      withContext(Dispatchers.IO) {
        resolve(relativePath).exists()
      }

  suspend fun sizeBytes(relativePath: String): Long =
      withContext(Dispatchers.IO) {
        resolve(relativePath).takeIf { it.exists() }?.length() ?: 0L
      }

  /** Removes one file. Already-absent is success — deletion is idempotent. */
  suspend fun delete(relativePath: String) {
    withContext(Dispatchers.IO) {
      val target = resolve(relativePath)
      if (target.exists() && !target.deleteRecursively()) {
        throw IOException("Could not delete media file $relativePath")
      }
    }
  }

  /**
   * Removes a whole group directory — every file ever written under [group].
   *
   * This is the deliberate reason [write] groups at all: it also sweeps files from a capture that
   * crashed before its row was inserted, which a row-by-row delete cannot see.
   */
  suspend fun deleteGroup(group: String) = delete(group)

  /**
   * Empties the store — every group, every file the app ever captured.
   *
   * The root directory itself stays, so the store is writable again straight after and nothing has
   * to re-`open()` it. Already-empty is success, like every other delete here.
   */
  suspend fun deleteAll() {
    withContext(Dispatchers.IO) {
      rootDir.listFiles()?.forEach { child ->
        if (!child.deleteRecursively()) {
          throw IOException("Could not delete media file ${child.name}")
        }
      }
    }
  }

  companion object {

    /**
     * What an outing's sonogram sidecar is called inside the outing's own group.
     *
     * One name, reached from both ends: Save writes it and the journal page reads it, and a sidecar
     * the reader cannot find is a walk that recomputes its strip for no reason.
     */
    const val SonogramName = "sonogram"
    const val SonogramExtension = "sono"

    /**
     * Where [SonogramName] lands for an outing, relative to the media root.
     *
     * Under the outing's group rather than beside it, which is the whole reason it needs no cleanup
     * of its own: [deleteGroup] already takes the directory.
     */
    fun sonogramPath(outingId: String): String = "$outingId/$SonogramName.$SonogramExtension"

    /**
     * The store rooted at `filesDir/media` — internal storage, backed up with the app, not visible
     * to the gallery. Captures are Journal content, not camera roll.
     */
    fun open(context: Context): MediaFileStore =
        MediaFileStore(File(context.filesDir, "media").apply { mkdirs() })
  }
}
