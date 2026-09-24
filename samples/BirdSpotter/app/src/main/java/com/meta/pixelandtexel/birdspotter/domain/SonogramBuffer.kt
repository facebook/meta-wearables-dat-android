/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.domain

/**
 * Columns per second of session — [CaptureSampleRate] over [SonogramHop], which is 62.5.
 *
 * The rate that turns a moment into a column and back. It is **not** the session's clock: that is
 * [SessionClock], which goes on counting when the audio does not.
 */
const val SonogramColumnsPerSecond = CaptureSampleRate.toDouble() / SonogramHop

/**
 * Ten minutes of columns. Long past any demo, and only 9.6 MB — a byte per bin is plenty for
 * something that ends up as one pixel.
 */
const val SonogramCapacity = 37_500

/**
 * Somewhere to keep a session's sonogram: a ring of columns, written at one end and read from
 * anywhere still inside it.
 *
 * **Bytes, not floats.** A magnitude becomes one pixel's brightness, and a pixel has 256 of those —
 * carrying 32-bit precision to a destination with 8 would cost four times the memory to draw the
 * identical strip.
 *
 * Indices are **absolute**: column 0 is the first of the session and stays column 0 forever, even
 * after it has been overwritten. That is what lets an event's timestamp turn into a column number
 * with one multiplication, and it is why [oldest] exists — to say which of those numbers are still
 * answerable.
 *
 * Not thread-safe, and not required to be: one producer appends on the session's coroutine, and the
 * strip reads on the main thread after being told there is something new.
 */
class SonogramBuffer(private val capacity: Int = SonogramCapacity) {

  private val data = ByteArray(capacity * SonogramBins)

  /**
   * One byte per column: how loud it was. The ring the **waveform** reading is drawn from — see
   * [waveform]. Kept beside the bins rather than derived from them, because a column's loudness is
   * a fact the analyzer measured and summing its magnitudes back up would be an estimate of a
   * number we already had.
   */
  private val levels = ByteArray(capacity)

  /** How many columns the session has produced, ever. */
  var count = 0
    private set

  /** The oldest column still in the ring. Below this, [magnitude] answers zero. */
  val oldest: Int
    get() = maxOf(0, count - capacity)

  fun append(column: SonogramColumn) {
    val slot = (count % capacity) * SonogramBins
    for (bin in 0 until SonogramBins) {
      data[slot + bin] = (column.magnitudes[bin] * 255f).toInt().coerceIn(0, 255).toByte()
    }
    levels[count % capacity] = (column.level * 255f).toInt().coerceIn(0, 255).toByte()
    count++
  }

  /**
   * Moves the write head forward to an absolute column, leaving silence behind it — what a gap in
   * the audio looks like on the strip.
   *
   * [append] can only ever write at [count], so without this the columns either side of a dropout
   * end up adjacent and the session reads as though the silence never happened.
   *
   * **The skipped columns are cleared, not merely stepped over.** This is a ring: the slots a gap
   * passes over still hold whatever was written there a full buffer ago, and leaving them would
   * draw ten-minute-old song inside the silence.
   *
   * Never moves backwards. A column that has been written is a column that happened, and audio
   * running fractionally ahead of the clock is the harmless direction — see [AudioGapColumns].
   */
  fun advance(to: Int) {
    if (to <= count) return
    // Anything more than a whole ring back is already unreachable, so clearing it would only be
    // clearing what this skip is about to overwrite.
    val first = maxOf(count, to - capacity)
    for (skipped in first until to) {
      val slot = (skipped % capacity) * SonogramBins
      for (bin in 0 until SonogramBins) data[slot + bin] = 0
      levels[skipped % capacity] = 0
    }
    count = to
  }

  /** Forgets the session. A new one starts on an empty strip. */
  fun reset() {
    // A buffer nobody has written to is already blank, so the first session of an app's life
    // pays nothing at all — 9.6 MB is not much to clear, but it is being cleared on the frame
    // that opens the screen.
    if (count == 0) return
    count = 0
    data.fill(0)
    levels.fill(0)
  }

  /**
   * The magnitude at an absolute column, 0..1 — or zero for a column that has not happened yet or
   * has already been overwritten. Out of range is silence rather than an error: the strip scrolls
   * past both ends of the session and drawing needs an answer, not an exception.
   */
  fun magnitude(column: Int, bin: Int): Float {
    if (column < oldest || column >= count) return 0f
    val slot = (column % capacity) * SonogramBins
    return (data[slot + bin].toInt() and 0xFF) / 255f
  }

  /**
   * How loud an absolute column was, 0..1 — or zero for one that has not happened yet or has
   * already been overwritten, on the same terms [magnitude] answers.
   */
  fun level(column: Int): Float {
    if (column < oldest || column >= count) return 0f
    return (levels[column % capacity].toInt() and 0xFF) / 255f
  }

  /**
   * The visible window as 8-bit greyscale, row-major, [columns] wide and [SonogramBins] tall,
   * lowest frequency at the **bottom** — which is how a sonogram is read, and the opposite of how a
   * bitmap is stored.
   *
   * Building the whole window in one pass rather than asking per pixel is what keeps the strip
   * cheap: one allocation and one loop per redraw, no matter how long the session has run.
   */
  fun window(from: Int, columns: Int): ByteArray {
    val pixels = ByteArray(columns * SonogramBins)
    for (x in 0 until columns) {
      val column = from + x
      if (column < oldest || column >= count) continue
      val slot = (column % capacity) * SonogramBins
      for (bin in 0 until SonogramBins) {
        pixels[(SonogramBins - 1 - bin) * columns + x] = data[slot + bin]
      }
    }
    return pixels
  }

  /**
   * The newest [columns] columns, averaged bin by bin — the spectrum the **live trace** is drawn
   * from. See [LiveWaveform].
   *
   * **Averaged rather than simply the newest one, and this is the whole of the trace's smoothing.**
   * A single column is 32 ms of a bird, and drawing it raw gives a shape that changes completely
   * sixty times a second — legible as motion, illegible as a picture. Four columns is about a tenth
   * of a second, which is roughly how long the eye wants a shape to hold still. Doing it here
   * rather than in the drawing keeps the smoothing a fact about the *data* — one arithmetic mean,
   * identical on both platforms — instead of an animation with a clock in it that the two apps
   * would have to be trusted to run at the same rate.
   *
   * A session with nothing in it yet answers zeros, which is a flat trace.
   */
  fun recentBins(columns: Int): FloatArray {
    val bins = FloatArray(SonogramBins)
    val first = maxOf(oldest, count - columns)
    if (first >= count) return bins

    for (column in first until count) {
      val slot = (column % capacity) * SonogramBins
      for (bin in 0 until SonogramBins) {
        bins[bin] += (data[slot + bin].toInt() and 0xFF) / 255f
      }
    }
    val taken = (count - first).toFloat()
    for (bin in 0 until SonogramBins) bins[bin] /= taken
    return bins
  }

  /**
   * How loud the newest [columns] columns were, averaged — the trace's amplitude, smoothed over
   * exactly the span [recentBins] smooths the shape over.
   */
  fun recentLevel(columns: Int): Float {
    val first = maxOf(oldest, count - columns)
    if (first >= count) return 0f

    var total = 0f
    for (column in first until count) total += (levels[column % capacity].toInt() and 0xFF) / 255f
    return total / (count - first)
  }

  // ── Keeping it ─────────────────────────────────────────────────────────

  /**
   * The strip as bytes: a header naming its shape, then every column's bins, then every column's
   * level.
   *
   * **The greyscale, not a picture of it.** A rendered strip would bake in the palette and one
   * chosen size; these are the numbers the analyzer measured, and every destination still
   * rasterises them for itself at whatever width it has — see the design notes, "Derived visuals
   * are not stored".
   *
   * Only the columns still in the ring are written, and [oldest] rides along, so absolute column
   * numbers survive the round trip and an event's timestamp still lands where it did during the
   * session.
   */
  fun encoded(): ByteArray {
    val first = oldest
    val kept = count - first
    val out = ByteArray(SonogramHeaderBytes + kept * SonogramBins + kept)

    SonogramMagic.copyInto(out, 0)
    out.putLittleEndian16(4, SonogramFormatVersion)
    out.putLittleEndian16(6, SonogramBins)
    out.putLittleEndian32(8, first)
    out.putLittleEndian32(12, count)

    var target = SonogramHeaderBytes
    for (column in first until count) {
      val slot = (column % capacity) * SonogramBins
      data.copyInto(out, target, slot, slot + SonogramBins)
      target += SonogramBins
    }
    for (column in first until count) {
      out[target++] = levels[column % capacity]
    }
    return out
  }

  companion object {

    /**
     * Rebuilds a strip written by [encoded].
     *
     * **Null is the ordinary answer, not an error.** Bytes that are not a strip, a version this
     * build does not know, a bin count from before someone changed [SonogramWindow], or a file
     * truncated by a crash all land here — and every one of them means the caller recomputes from
     * the audio, which is what it did before this file existed. Nothing downstream has to tell
     * those cases apart.
     */
    fun decoded(bytes: ByteArray): SonogramBuffer? {
      if (bytes.size < SonogramHeaderBytes) return null
      for (i in SonogramMagic.indices) if (bytes[i] != SonogramMagic[i]) return null
      if (bytes.readLittleEndian16(4) != SonogramFormatVersion) return null
      if (bytes.readLittleEndian16(6) != SonogramBins) return null

      val first = bytes.readLittleEndian32(8)
      val total = bytes.readLittleEndian32(12)
      if (total < first) return null
      val kept = total - first
      // Nothing Save writes is longer than the live ring, and the bound is also what keeps
      // the size arithmetic below from overflowing on a corrupt header.
      if (kept > SonogramCapacity) return null
      if (bytes.size != SonogramHeaderBytes + kept * SonogramBins + kept) return null
      if (kept == 0) return SonogramBuffer(capacity = 1)

      // Sized to exactly what was kept, which is what puts `oldest` back where it was: the
      // ring reports `count - capacity`, and capacity is the number of columns in the file.
      val buffer = SonogramBuffer(capacity = kept)
      val binsAt = SonogramHeaderBytes
      val levelsAt = binsAt + kept * SonogramBins
      for (i in 0 until kept) {
        val column = first + i
        val slot = (column % kept) * SonogramBins
        bytes.copyInto(
            buffer.data,
            slot,
            binsAt + i * SonogramBins,
            binsAt + (i + 1) * SonogramBins,
        )
        buffer.levels[column % kept] = bytes[levelsAt + i]
      }
      buffer.count = total
      return buffer
    }
  }
}

/**
 * Four bytes that say this is one of ours before anything reads a length off it — "BirdSpotter
 * SonoGram".
 */
private val SonogramMagic = "BSSG".toByteArray(Charsets.US_ASCII)

/**
 * The layout's version. Bump it when the bytes after the header change meaning, and every strip
 * written by an older build is quietly recomputed instead of drawn wrong.
 */
private const val SonogramFormatVersion = 1

/** Magic, version, bins, oldest, count. */
private const val SonogramHeaderBytes = 16

/**
 * Little-endian on both platforms, written out a byte at a time rather than reinterpreting memory —
 * the host's own order is a fact about the phone, and this file is read by two.
 */
private fun ByteArray.putLittleEndian16(offset: Int, value: Int) {
  this[offset] = (value and 0xFF).toByte()
  this[offset + 1] = ((value shr 8) and 0xFF).toByte()
}

private fun ByteArray.putLittleEndian32(offset: Int, value: Int) {
  this[offset] = (value and 0xFF).toByte()
  this[offset + 1] = ((value shr 8) and 0xFF).toByte()
  this[offset + 2] = ((value shr 16) and 0xFF).toByte()
  this[offset + 3] = ((value shr 24) and 0xFF).toByte()
}

private fun ByteArray.readLittleEndian16(offset: Int): Int =
    (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)

private fun ByteArray.readLittleEndian32(offset: Int): Int =
    (this[offset].toInt() and 0xFF) or
        ((this[offset + 1].toInt() and 0xFF) shl 8) or
        ((this[offset + 2].toInt() and 0xFF) shl 16) or
        ((this[offset + 3].toInt() and 0xFF) shl 24)
