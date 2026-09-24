/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.domain

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * How many samples go into one column. 512 at 16 kHz is **32 ms** — long enough to resolve a
 * warbler's trill, short enough that the strip keeps moving.
 */
const val SonogramWindow = 512

/**
 * How far the window advances between columns. Half a window, so consecutive columns overlap and a
 * call that starts mid-window still lands somewhere legible: **62.5 columns per second**.
 */
const val SonogramHop = 256

/** Bins in a column — half the window, covering DC to 8 kHz in 31.25 Hz steps. */
const val SonogramBins = SonogramWindow / 2

/**
 * The quietest thing the strip draws, in decibels below full scale.
 *
 * Bird song covers a range that linear amplitude renders as a black rectangle with three white dots
 * in it, so the strip is drawn in dB and everything under this is simply floor.
 */
const val SonogramFloorDb = -70f

/**
 * One column of the strip: [SonogramBins] magnitudes in 0..1, lowest frequency first, and the one
 * number that says how loud the whole column was.
 *
 * **The level is not the magnitudes summed.** It is the window's own RMS, measured on the samples
 * before the Hann taper touches them — a fact about the sound rather than about the transform of
 * it. It rides here because the strip has two readings of one instrument and both come off one pass
 * of the analyzer: see [StripReading].
 *
 * It is normalised on the *same* dB scale the magnitudes are, floor and all, so a column that
 * paints bright on the sonogram stands tall on the waveform. Two scales would be two instruments.
 *
 * [level] is defaulted, so a test or a preview that only cares about the picture can still say
 * `SonogramColumn(magnitudes)` and mean silence.
 *
 * A plain class rather than a `data class` for the reason [AudioChunk] is: generated `equals` over
 * an array compares references.
 */
class SonogramColumn(val magnitudes: FloatArray, val level: Float = 0f)

/**
 * Turns a stream of [AudioChunk]s into the columns a live sonogram is drawn from.
 *
 * **Stateful on purpose.** Audio arrives in whatever slices the microphone feels like giving, and a
 * column needs exactly [SonogramWindow] samples starting every [SonogramHop] — so leftovers carry
 * from one chunk to the next. Feed it everything, in order, and it answers with however many
 * columns that made possible, which is often none.
 *
 * **The transform is written out longhand.** This could call a vendor FFT and be faster, and that
 * is exactly why it doesn't: this is the kind of code Meta puts on a slide, and a radix-2
 * Cooley–Tukey loop is readable as arithmetic where a framework call is readable only as a
 * framework call. It costs about 65k floating-point operations per column at 62 columns a second,
 * which is nothing on either phone.
 *
 * Twiddle factors are `Double` even though samples and output are `Float`: the recurrence that
 * walks them around the unit circle accumulates error, and single precision shows it as a smear
 * across the top of the strip.
 */
class SonogramAnalyzer {

  /** Samples seen but not yet consumed by a column. */
  private var pending = FloatArray(0)

  /**
   * Scratch for one column's transform, reused by every column the analyzer ever draws.
   *
   * The transform is in-place and its result is read out into a [SonogramColumn] before the next
   * column begins, so these are never needed twice over — and allocating them per column meant two
   * heap allocations sixty-two times a second live, and twice [SonogramCapacity] of them again
   * every time the journal reopens the walk.
   *
   * Not part of the analyzer's state in any meaningful sense: [reset] leaves them alone, because a
   * column overwrites every element it reads.
   */
  private val real = DoubleArray(SonogramWindow)
  private val imaginary = DoubleArray(SonogramWindow)

  /**
   * Adds a chunk and returns every column it completed, oldest first.
   *
   * Empty is the ordinary answer for a short chunk — the samples are kept, not dropped.
   */
  fun analyze(chunk: AudioChunk): List<SonogramColumn> {
    pending += chunk.samples

    val columns = mutableListOf<SonogramColumn>()
    var offset = 0
    while (pending.size - offset >= SonogramWindow) {
      columns += columnAt(offset)
      offset += SonogramHop
    }

    // Keep the tail: the next window starts inside it.
    if (offset > 0) pending = pending.copyOfRange(offset, pending.size)
    return columns
  }

  /** Forgets everything buffered — a new session starts on silence, not on the last one's tail. */
  fun reset() {
    pending = FloatArray(0)
  }

  private fun columnAt(offset: Int): SonogramColumn {
    // `real` is written in full by the loop below, so it carries nothing across; `imaginary`
    // has to start each column at zero, which is what makes this a real-input transform.
    imaginary.fill(0.0)
    // The level is taken in the same pass, off the untapered sample: the Hann window is there
    // to stop a tone leaking across the transform, and applying it to a loudness reading would
    // only mean the same second measured quieter at the edges of a window nobody chose.
    var sumOfSquares = 0.0
    for (i in 0 until SonogramWindow) {
      val sample = pending[offset + i].toDouble()
      sumOfSquares += sample * sample
      real[i] = sample * HannWindow[i]
    }

    transform(real, imaginary)

    val magnitudes = FloatArray(SonogramBins)
    for (bin in 0 until SonogramBins) {
      // `MagnitudeScale` puts a full-scale sine at 1.0, so 0 dB means "as loud as it gets"
      // rather than an arbitrary reference nobody can read off the strip.
      val magnitude = hypot(real[bin], imaginary[bin]) * MagnitudeScale
      val decibels = 20.0 * log10(magnitude + Silence)
      magnitudes[bin] = normalise(decibels)
    }

    // RMS rather than peak: a peak is one sample of 512 and jitters a whole column's worth on a
    // single click, where the RMS is what the second actually sounded like. A full-scale sine
    // reads 0.707, which is -3 dB — near the top of the scale without pinning to it.
    val rms = sqrt(sumOfSquares / SonogramWindow)
    val level = normalise(20.0 * log10(rms + Silence))

    return SonogramColumn(magnitudes, level)
  }

  private companion object {

    /**
     * Hann, precomputed once. A rectangular window would leak every pure tone across the whole
     * column and turn each bird into a vertical smudge.
     */
    val HannWindow =
        DoubleArray(SonogramWindow) { i ->
          0.5 * (1.0 - cos(2.0 * PI * i / (SonogramWindow - 1)))
        }

    /**
     * Two over the window's sum. The Hann window sums to half its length and a real signal splits
     * its energy between the positive and negative frequency, so this is what puts a full-scale
     * sine at magnitude 1.
     */
    const val MagnitudeScale = 2.0 / (SonogramWindow / 2.0)

    /** Keeps `log10` off zero. Well below the floor, so it never shows. */
    const val Silence = 1e-12

    fun normalise(decibels: Double): Float {
      val clamped = decibels.coerceIn(SonogramFloorDb.toDouble(), 0.0)
      return ((clamped - SonogramFloorDb) / -SonogramFloorDb).toFloat()
    }

    /**
     * In-place radix-2 Cooley–Tukey, decimation in time.
     *
     * Two halves: reorder the input into bit-reversed positions, then combine pairs, quads, octets
     * and so on up to the whole array. [SonogramWindow] is a power of two, which is what lets the
     * second half be a loop rather than a recursion.
     */
    fun transform(real: DoubleArray, imaginary: DoubleArray) {
      val n = real.size

      var j = 0
      for (i in 1 until n) {
        var bit = n shr 1
        while (j and bit != 0) {
          j = j xor bit
          bit = bit shr 1
        }
        j = j or bit
        if (i < j) {
          val swapReal = real[i]
          real[i] = real[j]
          real[j] = swapReal
          val swapImaginary = imaginary[i]
          imaginary[i] = imaginary[j]
          imaginary[j] = swapImaginary
        }
      }

      var length = 2
      while (length <= n) {
        val angle = -2.0 * PI / length
        val stepReal = cos(angle)
        val stepImaginary = sin(angle)
        var start = 0
        while (start < n) {
          var twiddleReal = 1.0
          var twiddleImaginary = 0.0
          for (k in 0 until length / 2) {
            val evenReal = real[start + k]
            val evenImaginary = imaginary[start + k]
            val oddIndex = start + k + length / 2
            val oddReal = real[oddIndex] * twiddleReal - imaginary[oddIndex] * twiddleImaginary
            val oddImaginary = real[oddIndex] * twiddleImaginary + imaginary[oddIndex] * twiddleReal

            real[start + k] = evenReal + oddReal
            imaginary[start + k] = evenImaginary + oddImaginary
            real[oddIndex] = evenReal - oddReal
            imaginary[oddIndex] = evenImaginary - oddImaginary

            val nextTwiddleReal = twiddleReal * stepReal - twiddleImaginary * stepImaginary
            twiddleImaginary = twiddleReal * stepImaginary + twiddleImaginary * stepReal
            twiddleReal = nextTwiddleReal
          }
          start += length
        }
        length = length shl 1
      }
    }
  }
}
