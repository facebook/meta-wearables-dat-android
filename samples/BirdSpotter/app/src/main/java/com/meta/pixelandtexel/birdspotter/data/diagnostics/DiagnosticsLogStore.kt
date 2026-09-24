/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.diagnostics

import android.content.Context
import com.meta.pixelandtexel.birdspotter.domain.LogCategory
import com.meta.pixelandtexel.birdspotter.domain.LogEntry
import com.meta.pixelandtexel.birdspotter.domain.LogLevel
import java.io.File
import java.io.FileWriter
import java.io.Writer
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * One log file on disk, as the Diagnostics screen lists it.
 *
 * [startedAtMillis] is read back out of the file's own name rather than from its filesystem dates —
 * a restored backup or a file copied off the device keeps its name and loses its dates, and the
 * name is the only thing that still says when the run happened.
 */
data class DiagnosticsLogFile(
    /** The file name, and the id the viewer is addressed by. */
    val name: String,
    val startedAtMillis: Long,
    val sizeBytes: Long,
    /** Whether lines are still landing in this one. */
    val isCurrent: Boolean,
)

/**
 * The rolling diagnostic log: a directory of plain-text files, newest still being written.
 *
 * **A file per run, and a size cap within one.** A run is the unit anybody actually asks about —
 * "the demo at eleven" is one launch — so [beginNewRun] starts a fresh file each time the app comes
 * up, and a run long enough to fill [MaxFileBytes] rolls onto another. Once there are more than
 * [MaxFiles], the oldest go. That bounds the whole feature at a little under 3 MB without anything
 * having to run a sweep on a timer.
 *
 * **Plain text, not JSON.** These get read three ways — in the Diagnostics screen, in a text editor
 * after being shared out, and by `grep` — and only the first would be helped by a structured
 * format. [LogEntry] owns the line shape in both directions. (A JSONL log would answer a different
 * job: one file per identify flow, structured because a *machine* diffs two platforms' runs. This
 * one is for a person.)
 *
 * **Writes are immediate.** Nothing waits for a flush, because the run this exists to explain is
 * very often the one that ended in a crash, and a buffer is exactly the last few lines that matter.
 * [FileLogSink] keeps the caller off the disk instead, by owning the single consumer this is called
 * from.
 */
class DiagnosticsLogStore(private val rootDir: File) {

  private val lock = Any()
  private var currentName: String? = null
  private var currentWriter: Writer? = null

  /** The stamp the last file was named with. See [nextFileMillisLocked]. */
  private var lastFileMillis: Long = 0

  /**
   * Tracked rather than re-read: the size is checked on every line, and a `length()` per line is a
   * filesystem call this does not need to make.
   */
  private var currentBytes: Long = 0

  // region Writing

  /**
   * Starts a fresh file for a new run of the app, and prunes back to [MaxFiles].
   *
   * Safe to call more than once; each call simply starts another file.
   */
  fun beginNewRun() {
    synchronized(lock) {
      closeCurrentLocked()
      openFileLocked(fileName(nextFileMillisLocked()))
      pruneLocked()
    }
  }

  /**
   * Appends one already-formatted line, rolling onto a new file if this one has had enough.
   *
   * **One writer.** [FileLogSink] calls this from its single consumer and nothing else calls it at
   * all, which is what keeps lines in the order they happened. The monitor is here for the readers
   * below, not for a second writer.
   */
  fun append(line: String) {
    val bytes = (line.toByteArray(Charsets.UTF_8).size + 1).toLong() // + the newline
    synchronized(lock) {
      if (currentWriter == null) openFileLocked(fileName(nextFileMillisLocked()))
      // Rolled *before* the write, not after, so [MaxFileBytes] is a ceiling the file
      // stays under rather than one it steps over by a line each time.
      if (currentBytes + bytes > MaxFileBytes) {
        closeCurrentLocked()
        openFileLocked(fileName(nextFileMillisLocked()))
        pruneLocked()
      }
      // A failed write is dropped in silence, which is the one place in the app that is
      // right: the alternative is a logger that logs about logging, and a disk that
      // will not take a diagnostic line will not take the complaint either.
      runCatching {
        currentWriter?.apply {
          write(line)
          write("\n")
          flush()
        }
      }
      currentBytes += bytes
    }
  }

  // endregion

  // region Reading

  /**
   * Every file, newest first — which is the order the screen lists them in, because the run
   * somebody is asking about is nearly always the one that just happened.
   */
  fun files(): List<DiagnosticsLogFile> {
    val current = synchronized(lock) { currentName }
    return (rootDir.listFiles() ?: emptyArray())
        .filter { it.name.startsWith(FilePrefix) && it.name.endsWith(".$FileExtension") }
        // Lexicographic *is* chronological: the stamp in the name is fixed-width and
        // most-significant-first, which is the whole reason it is shaped that way.
        .sortedByDescending { it.name }
        .mapNotNull { file ->
          val startedAt = millisFromFileName(file.name) ?: return@mapNotNull null
          DiagnosticsLogFile(
              name = file.name,
              startedAtMillis = startedAt,
              sizeBytes = file.length(),
              isCurrent = file.name == current,
          )
        }
  }

  /** One file's raw text — what the share sheet hands out. */
  fun text(name: String): String =
      resolve(name).takeIf { it.exists() }?.runCatching { readText() }?.getOrNull() ?: ""

  /**
   * One file, parsed back into entries, oldest first.
   *
   * A line that will not parse is kept rather than dropped, filed under [LogCategory.APP] at
   * [LogLevel.INFO] with its text intact — it is far more likely to be a line from a build with a
   * category this one has not heard of than noise, and a viewer that silently eats what it does not
   * understand is a viewer you cannot trust to be showing you everything.
   */
  fun entries(name: String): List<LogEntry> =
      text(name)
          .lineSequence()
          .filter { it.isNotBlank() }
          .map { line ->
            LogEntry.parse(line) ?: LogEntry(0L, LogLevel.INFO, LogCategory.APP, line)
          }
          .toList()

  fun resolve(name: String): File = File(rootDir, name)

  // endregion

  // region Deleting

  /**
   * Empties the directory and starts a fresh file, so logging carries on straight after.
   *
   * The fresh file is the point: without it the next line would reopen the file that was just
   * deleted, and the screen would show a log the user had asked to be rid of.
   */
  fun deleteAll() {
    synchronized(lock) {
      closeCurrentLocked()
      rootDir.listFiles()?.forEach { it.delete() }
      openFileLocked(fileName(nextFileMillisLocked()))
    }
  }

  // endregion

  // region Behind the monitor

  /**
   * The stamp to name the next file with — now, or one millisecond past the last one, whichever is
   * later.
   *
   * **Names have to be strictly increasing, not merely current.** Two files opened inside one
   * millisecond would otherwise be handed the same name, and the second would reopen the first: a
   * rotation that rotates onto itself, and — worse — a launch that appends to the previous run's
   * file. [files] also sorts on the name, so equal names would make the order of two runs
   * undefined. The clock is only ever read forwards.
   */
  private fun nextFileMillisLocked(): Long {
    val millis = maxOf(System.currentTimeMillis(), lastFileMillis + 1)
    lastFileMillis = millis
    return millis
  }

  private fun closeCurrentLocked() {
    runCatching { currentWriter?.close() }
    currentWriter = null
    currentName = null
    currentBytes = 0
  }

  /**
   * **Appending, not `File.bufferedWriter()`.** That overload truncates, which would empty a file
   * the moment a run reopened one it had already written to — the reach-for-it-first call is the
   * wrong one here.
   */
  private fun openFileLocked(name: String) {
    val file = resolve(name)
    runCatching {
      file.parentFile?.mkdirs()
      currentBytes = if (file.exists()) file.length() else 0L
      currentWriter = FileWriter(file, /* append= */ true).buffered()
      currentName = name
    }
        .onFailure {
          currentWriter = null
          currentName = null
          currentBytes = 0
        }
  }

  /**
   * Drops the oldest files past [MaxFiles]. Never the current one — it is the newest, so the sort
   * keeps it at the front regardless.
   */
  private fun pruneLocked() {
    val files =
        (rootDir.listFiles() ?: emptyArray())
            .filter { it.name.startsWith(FilePrefix) && it.name.endsWith(".$FileExtension") }
            .sortedByDescending { it.name }
    if (files.size <= MaxFiles) return
    files.drop(MaxFiles).forEach { it.delete() }
  }

  // endregion

  companion object {

    /** How large one file may grow before the run rolls onto another. */
    const val MaxFileBytes: Long = 256L * 1024

    /**
     * How many files are kept. Twelve runs is more demo history than anyone has needed, and with
     * the size cap it holds the directory under 3 MB.
     */
    const val MaxFiles = 12

    private const val FilePrefix = "birdspotter-"
    private const val FileExtension = "log"

    /**
     * The store rooted at `filesDir/diagnostics`.
     *
     * Beside `media`, and for the same reason
     * [com.meta.pixelandtexel.birdspotter.data.media.MediaFileStore] is there: it is the app's own
     * storage rather than the user's, and unlike a cache directory the system will not evict it out
     * from under a run somebody still means to read.
     */
    fun open(context: Context): DiagnosticsLogStore =
        DiagnosticsLogStore(File(context.filesDir, "diagnostics").apply { mkdirs() })

    /**
     * `birdspotter-20260729-091422-913.log`
     *
     * Fixed width and most-significant-first, so sorting the names sorts the runs — see [files]. To
     * the millisecond because a long run can roll twice inside one second, and two files with one
     * name is a run that overwrites itself.
     */
    fun fileName(millis: Long): String {
      val stamp =
          NameFormatter.format(
              ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()),
          )
      return "$FilePrefix$stamp.$FileExtension"
    }

    /** The run's start time, read back out of the name. `null` for anything not ours. */
    fun millisFromFileName(name: String): Long? {
      if (!name.startsWith(FilePrefix) || !name.endsWith(".$FileExtension")) return null
      val stamp = name.removePrefix(FilePrefix).removeSuffix(".$FileExtension")
      return runCatching {
        LocalDateTime.parse(stamp, NameFormatter)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
      }
          .getOrNull()
    }

    /**
     * **`uuuu`, not `yyyy`.** They format identically for every date this app will ever see, but
     * `yyyy` is year-*of-era* and needs an era to parse back — which this stamp does not carry, so
     * a `yyyy` pattern reads a name it wrote itself and fails. The bytes on disk are
     * `yyyyMMdd-HHmmss-SSS` either way.
     */
    private val NameFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss-SSS")
  }
}
