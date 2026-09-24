/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.camera

import android.content.Context
import java.io.File

/**
 * Somewhere to put a photograph that is only being looked at.
 *
 * **Deliberately not [com.meta.pixelandtexel.birdspotter.data.media.MediaFileStore].** That one is
 * the Journal's: what it holds is content the watcher captured on purpose, it is backed up with the
 * app, and every file in it is owned by a row in the database. A photograph taken to find out what
 * `LARGE` costs is none of those things — it belongs to nobody, it should not survive the next
 * demo, and filing it beside a real sighting would put a test shot in somebody's journal.
 *
 * So it lands in the cache directory, where the system is free to take it back, and the screen that
 * writes it empties the whole directory first. A file exists here for exactly one reason: **the
 * share sheet hands out a URI, not bytes.** Looking at the photograph needs no file at all; saving
 * it to the gallery needs one.
 *
 * The directory is named in `res/xml/capture_paths.xml`, which is the only scope the captures
 * [androidx.core.content.FileProvider] will hand out.
 */
class CaptureScratchStore(val root: File) {

  /**
   * Writes [data] as [name] and hands back where it landed, or `null` when the write failed — a
   * cache directory the system reclaimed mid-demo is a real thing rather than a programming error,
   * and the screen has a line for it.
   *
   * [name] carries the extension, because the extension is the whole reason the file has a name at
   * all: it is what the share sheet shows and what decides whether the gallery takes it.
   */
  fun write(data: ByteArray, name: String): File? = runCatching {
    root.mkdirs()
    File(root, name).apply { writeBytes(data) }
  }
      .getOrNull()

  /**
   * Throws the lot away. Already-empty is success, like every other delete in the app.
   *
   * Called on the way in rather than on the way out: a screen that swept up after itself would also
   * sweep the file out from under a chooser the watcher left open, and a crash would leave the
   * directory behind anyway.
   */
  fun empty() {
    root.listFiles()?.forEach { it.deleteRecursively() }
  }

  companion object {
    /** What the directory is called under the cache, and in `capture_paths.xml`. */
    const val DirectoryName = "glasses-captures"

    /** The authority the captures provider is declared under in `AndroidManifest.xml`. */
    fun authority(context: Context): String = "${context.packageName}.captures"

    /** The store rooted at the cache directory's [DirectoryName]. */
    fun open(context: Context): CaptureScratchStore =
        CaptureScratchStore(File(context.cacheDir, DirectoryName).apply { mkdirs() })
  }
}
