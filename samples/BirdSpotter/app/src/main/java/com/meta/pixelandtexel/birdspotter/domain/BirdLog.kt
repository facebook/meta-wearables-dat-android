/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.domain

/**
 * The app's diagnostic log — the one call any code makes to say what just happened.
 *
 * ```kotlin
 * BirdLog.info(LogCategory.GLASSES) { "session started on $name" }
 * BirdLog.warning(LogCategory.AUDIO) { "glasses mic unavailable — falling back to the phone" }
 * BirdLog.error(LogCategory.JOURNAL, error) { "could not save the outing" }
 * ```
 *
 * **A global, deliberately, and the only one in the app.** Everything else here is injected through
 * [com.meta.pixelandtexel.birdspotter.AppContainer] — but a logger threaded through constructors is
 * a logger that does not get called from the awkward places, and the awkward places are where the
 * demo breaks. Every logging library that people actually reach for makes the same trade.
 *
 * **Not a wrapper around `Log`.** Lines fan out to whatever sinks are installed, which is what puts
 * the same line in logcat *and* in a file the demo team can read on the phone at the venue with no
 * laptop in sight. [install] is called once from the composition root; before it lands, lines go
 * nowhere but are still cheap — see below.
 *
 * **Callable from anywhere, on any thread.** An `object` with `@Volatile` state, so a DAT callback
 * on some SDK thread and a `ViewModel` on the main one use the same three words. Messages are
 * lambdas, so a line filtered out by [minimumLevel] never builds its string — the reason a `DEBUG`
 * line in a hot path costs a comparison rather than an interpolation.
 */
object BirdLog {

  // region Writing

  /** The step-by-step. Free when the floor is above it. */
  inline fun debug(category: LogCategory, message: () -> String) =
      emit(LogLevel.DEBUG, category, message)

  /** The beats worth reading back. */
  inline fun info(category: LogCategory, message: () -> String) =
      emit(LogLevel.INFO, category, message)

  /** The long way round, taken and survived. */
  inline fun warning(category: LogCategory, message: () -> String) =
      emit(LogLevel.WARNING, category, message)

  /** Something the user can see is broken. */
  inline fun error(category: LogCategory, message: () -> String) =
      emit(LogLevel.ERROR, category, message)

  /**
   * An error, with the thing that went wrong appended.
   *
   * An overload worth having: `"$message — $error"` written by hand at forty call sites is forty
   * chances to format it differently, and a log whose error lines do not look alike is one nobody
   * can grep.
   */
  inline fun error(category: LogCategory, cause: Any?, message: () -> String) =
      emit(LogLevel.ERROR, category) { "${message()} — $cause" }

  /**
   * The one place a line is made. `inline` so [message] is not even allocated when the level is
   * below the floor — public because an inline function's body has to be, not because anything
   * outside should call it.
   */
  inline fun emit(level: LogLevel, category: LogCategory, message: () -> String) {
    // Read once into locals: `sinks` is replaced wholesale rather than mutated, so a
    // concurrent install can never be seen half-applied, and a sink is never called
    // while anything is held.
    val sinks = installedSinks
    if (level < minimumLevel || sinks.isEmpty()) return
    val entry = LogEntry(System.currentTimeMillis(), level, category, message())
    sinks.forEach { it.write(entry) }
  }

  // endregion

  // region Installing

  /**
   * Points the log at its sinks. Called once, from the composition root.
   *
   * Replaces rather than appends, so a test can install a fake and be sure nothing else is
   * listening.
   */
  fun install(sinks: List<LogSink>) {
    installedSinks = sinks.toList()
  }

  /**
   * Adds one more sink to whatever is already installed — how the file sink joins the logcat one
   * once the store has opened.
   */
  fun add(sink: LogSink) {
    installedSinks = installedSinks + sink
  }

  /** Drops every sink. For tests, and for the Diagnostics screen turning file logging off. */
  fun removeAllSinks() {
    installedSinks = emptyList()
  }

  /**
   * The sinks, swapped as a whole immutable list rather than mutated in place: a reader on some SDK
   * thread then either sees the old list or the new one, never a list being appended to underneath
   * it, and [emit] needs no lock at all.
   *
   * Public only because [emit] is `inline`. Nothing else should touch it.
   */
  @Volatile
  var installedSinks: List<LogSink> = emptyList()
    private set

  /**
   * The floor. Lines below it are dropped before their message is built.
   *
   * [LogLevel.DEBUG] by default: this app's whole reason for having a diagnostic log is a demo that
   * went sideways in a room with no debugger in it, and a default that hides the step-by-step is a
   * default that hides the answer. The Diagnostics screen raises it for anyone who finds the noise
   * unhelpful.
   */
  @Volatile var minimumLevel: LogLevel = LogLevel.DEBUG

  // endregion
}
