/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.diagnostics

import com.meta.pixelandtexel.birdspotter.domain.LogEntry
import com.meta.pixelandtexel.birdspotter.domain.LogSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Writes lines into the rolling [DiagnosticsLogStore], off whatever thread logged them.
 *
 * **A channel with one consumer, and it is the whole design.** [LogSink] promises not to block its
 * caller, and the caller here is routinely a DAT callback or an audio thread — code that must not
 * go anywhere near a disk. [Channel.trySend] returns immediately, and the single consumer coroutine
 * on [Dispatchers.IO] is what makes the file's order the order things actually happened in. Several
 * consumers would interleave lines and quietly destroy the only property a log has.
 *
 * It is also what lets [DiagnosticsLogStore.append] be a single-writer method: this is the only
 * thing that calls it.
 *
 * The line is rendered on the calling thread, deliberately — [LogEntry.fileLine] is string work,
 * not I/O, and rendering it here means the channel holds a `String` rather than an entry the
 * consumer would have to format later, out of order with its neighbours.
 *
 * **A full buffer drops the newest line rather than suspending.** [Capacity] is a thousand lines of
 * backlog; anything that outruns it is a runaway loop, and stalling the app to finish writing about
 * one is the worst of both outcomes. The drop is silent for the reason
 * [DiagnosticsLogStore.append]'s failures are.
 */
class FileLogSink(
    private val store: DiagnosticsLogStore,
    /**
     * The consumer's scope. Its own [SupervisorJob] rather than an injected one: this outlives
     * every screen and belongs to the process, which is what the composition root that builds it
     * is.
     */
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : LogSink {

  private val lines = Channel<String>(
      capacity = Capacity,
      onBufferOverflow = BufferOverflow.DROP_LATEST,
  )

  init {
    scope.launch {
      for (line in lines) store.append(line)
    }
  }

  override fun write(entry: LogEntry) {
    lines.trySend(entry.fileLine)
  }

  private companion object {
    const val Capacity = 1_000
  }
}
