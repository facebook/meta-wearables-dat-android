/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.diagnostics

import com.meta.pixelandtexel.birdspotter.domain.BirdLog
import com.meta.pixelandtexel.birdspotter.domain.LogCategory
import com.meta.pixelandtexel.birdspotter.domain.LogEntry
import com.meta.pixelandtexel.birdspotter.domain.LogLevel
import com.meta.pixelandtexel.birdspotter.domain.LogSink
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The diagnostic log: the line format both ends of it agree on, the rolling files, and the floor
 * that decides what is written at all.
 *
 * Scenario names are fixed by the testing-parity rule.
 */
class DiagnosticsLogTest {

  @get:Rule val folder = TemporaryFolder()

  @After
  fun tearDown() {
    BirdLog.removeAllSinks()
    BirdLog.minimumLevel = LogLevel.DEBUG
  }

  // ── The line format ────────────────────────────────────────────────────

  @Test
  fun fileLine_roundTripsThroughParse() {
    val entry = LogEntry(
        1_753_142_405_210L,
        LogLevel.WARNING,
        LogCategory.GLASSES,
        "capture — nothing arrived after 15000 ms",
    )

    val parsed = LogEntry.parse(entry.fileLine)

    assertEquals(entry, parsed)
  }

  @Test
  fun fileLine_collapsesAMultiLineMessageToOneLine() {
    val entry = LogEntry(0L, LogLevel.INFO, LogCategory.APP, "first\n   second\t\tthird\n")

    // The whole reason parsing can be one regular expression: a multi-line SDK error must
    // not be able to make every line after it unreadable.
    assertEquals("first second third", entry.message)
    assertFalse(entry.fileLine.contains("\n"))
  }

  @Test
  fun parse_refusesALineThatIsNotOurs() {
    assertNull(LogEntry.parse("just some text"))
    assertNull(LogEntry.parse(""))
    // A level this build does not know fails rather than defaulting to one it does.
    assertNull(LogEntry.parse("2026-07-29T09:14:22.913-05:00 [TRACE] [glasses] hello"))
    // Likewise a category.
    assertNull(LogEntry.parse("2026-07-29T09:14:22.913-05:00 [INFO ] [weather] hello"))
  }

  // ── Levels ─────────────────────────────────────────────────────────────

  @Test
  fun levels_areOrderedBySeverity() {
    assertTrue(LogLevel.DEBUG < LogLevel.INFO)
    assertTrue(LogLevel.INFO < LogLevel.WARNING)
    assertTrue(LogLevel.WARNING < LogLevel.ERROR)
  }

  // ── The rolling store ──────────────────────────────────────────────────

  @Test
  fun append_rollsOntoANewFileAtTheSizeCap() {
    val store = temporaryStore()
    store.beginNewRun()

    // Comfortably past the cap, in lines big enough that the count stays small.
    val line = "x".repeat(4_096)
    repeat(80) { store.append(line) }

    val files = store.files()
    assertTrue(files.size > 1)
    // The cap is a ceiling the file stays under, not one it steps over.
    assertTrue(files.all { it.sizeBytes <= DiagnosticsLogStore.MaxFileBytes })
  }

  @Test
  fun beginNewRun_prunesToTheFileLimit() {
    val store = temporaryStore()

    repeat(DiagnosticsLogStore.MaxFiles + 5) {
      store.beginNewRun()
      store.append("a line, so the file is not empty")
    }

    assertEquals(DiagnosticsLogStore.MaxFiles, store.files().size)
  }

  @Test
  fun files_areNewestFirstAndNameTheCurrentOne() {
    val store = temporaryStore()
    store.beginNewRun()
    store.append("older run")
    store.beginNewRun()
    store.append("newer run")

    val files = store.files()
    assertEquals(2, files.size)
    assertTrue(files.first().isCurrent)
    assertFalse(files.last().isCurrent)
    assertTrue(files[0].startedAtMillis >= files[1].startedAtMillis)
  }

  @Test
  fun entries_readTheRunBackInOrder() {
    val store = temporaryStore()
    store.beginNewRun()
    store.append(LogEntry(1_000L, LogLevel.INFO, LogCategory.GLASSES, "one").fileLine)
    store.append(LogEntry(2_000L, LogLevel.ERROR, LogCategory.AUDIO, "two").fileLine)

    val entries = store.entries(store.files().first().name)

    // Oldest first — the file's own order. Reversing for the screen is the view model's.
    assertEquals(2, entries.size)
    assertEquals("one", entries[0].message)
    assertEquals(LogCategory.AUDIO, entries[1].category)
  }

  @Test
  fun entries_keepALineItCannotParse() {
    val store = temporaryStore()
    store.beginNewRun()
    store.append("something a future build wrote")

    val entries = store.entries(store.files().first().name)

    // Kept rather than dropped: a viewer that eats what it does not understand is one you
    // cannot trust to be showing you everything.
    assertEquals(1, entries.size)
    assertEquals("something a future build wrote", entries[0].message)
  }

  @Test
  fun deleteAll_emptiesTheDirectoryAndKeepsWriting() {
    val store = temporaryStore()
    store.beginNewRun()
    store.append("before")

    store.deleteAll()
    store.append("after")

    val files = store.files()
    assertEquals(1, files.size)
    val text = store.text(files.first().name)
    assertTrue(text.contains("after"))
    assertFalse(text.contains("before"))
  }

  @Test
  fun fileName_roundTripsItsStartTime() {
    val millis = 1_753_142_405_210L
    val name = DiagnosticsLogStore.fileName(millis)

    assertEquals(millis, DiagnosticsLogStore.millisFromFileName(name))
    assertNull(DiagnosticsLogStore.millisFromFileName("not-ours.txt"))
  }

  // ── The facade ─────────────────────────────────────────────────────────

  @Test
  fun birdLog_dropsLinesBelowTheFloor() {
    val sink = RecordingLogSink()
    BirdLog.install(listOf(sink))

    BirdLog.minimumLevel = LogLevel.WARNING
    BirdLog.debug(LogCategory.APP) { "quiet" }
    BirdLog.info(LogCategory.APP) { "also quiet" }
    BirdLog.warning(LogCategory.APP) { "loud" }
    BirdLog.error(LogCategory.APP) { "louder" }

    assertEquals(listOf("loud", "louder"), sink.messages())
  }

  @Test
  fun birdLog_doesNotBuildAMessageItWillDrop() {
    val sink = RecordingLogSink()
    BirdLog.install(listOf(sink))
    BirdLog.minimumLevel = LogLevel.ERROR

    // The reason messages are lambdas: a DEBUG line in a hot path costs a comparison
    // rather than an interpolation.
    var built = false
    BirdLog.debug(LogCategory.APP) {
      built = true
      "expensive"
    }

    assertFalse(built)
    assertTrue(sink.messages().isEmpty())
  }

  // ── Helpers ────────────────────────────────────────────────────────────

  /** A store in its own throwaway directory, so tests never touch the real one. */
  private fun temporaryStore() = DiagnosticsLogStore(folder.newFolder())
}

/** Remembers what it was handed. */
private class RecordingLogSink : LogSink {
  private val written = mutableListOf<LogEntry>()

  fun messages(): List<String> = synchronized(written) { written.map { it.message } }

  override fun write(entry: LogEntry) {
    synchronized(written) { written.add(entry) }
  }
}
