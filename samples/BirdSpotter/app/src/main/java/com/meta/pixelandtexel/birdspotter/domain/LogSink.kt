/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.domain

/**
 * Somewhere a diagnostic line ends up. Two ship:
 * [com.meta.pixelandtexel.birdspotter.data.diagnostics.ConsoleLogSink] and
 * [com.meta.pixelandtexel.birdspotter.data.diagnostics.FileLogSink].
 *
 * **[write] must not block and must not throw.** It is called from whatever thread was running when
 * something happened — a DAT callback, an audio thread, the main thread mid-composition — and a
 * logger that can stall or fail its caller is a logger that changes the behaviour it was installed
 * to observe. A sink that cannot do its job drops the line; `FileLogSink` hands the work to its own
 * consumer and returns immediately.
 */
fun interface LogSink {
  fun write(entry: LogEntry)
}
