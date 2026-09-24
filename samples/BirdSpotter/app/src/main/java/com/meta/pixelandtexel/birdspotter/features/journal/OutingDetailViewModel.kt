/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.features.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.meta.pixelandtexel.birdspotter.data.audio.WavCodec
import com.meta.pixelandtexel.birdspotter.data.journal.OutingEventType
import com.meta.pixelandtexel.birdspotter.data.journal.OutingMedia
import com.meta.pixelandtexel.birdspotter.data.journal.OutingWithChildren
import com.meta.pixelandtexel.birdspotter.data.media.MediaFileStore
import com.meta.pixelandtexel.birdspotter.domain.AudioChunk
import com.meta.pixelandtexel.birdspotter.domain.BirdCatalogRepository
import com.meta.pixelandtexel.birdspotter.domain.CaptureSampleRate
import com.meta.pixelandtexel.birdspotter.domain.JournalRepository
import com.meta.pixelandtexel.birdspotter.domain.OutingAudioPlayer
import com.meta.pixelandtexel.birdspotter.domain.OutingPlayback
import com.meta.pixelandtexel.birdspotter.domain.PlaybackSegment
import com.meta.pixelandtexel.birdspotter.domain.SonogramAnalyzer
import com.meta.pixelandtexel.birdspotter.domain.SonogramBuffer
import com.meta.pixelandtexel.birdspotter.domain.SonogramColumnsPerSecond
import com.meta.pixelandtexel.birdspotter.domain.TimelineTickMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * One moment of a saved outing's timeline, ready to render — the live log's row shapes, rebuilt
 * from the rows the journal kept.
 *
 * Three kinds rather than the event table's three types: `WIZARD_ANSWER` events are provenance and
 * stay in the field notes, and a photo nothing pointed at earns a row of its own — the watcher
 * pressed the shutter, and the timeline records what happened.
 */
sealed interface TimelineRow {
  val id: String

  /** Where this sits on the outing's clock — and where a tap seeks to. Null when clockless. */
  val offsetMs: Long?

  /** A detection — what the app offered, confirmed into the life list or not. */
  data class Detection(
      override val id: String,
      override val offsetMs: Long?,
      /** The resolved catalog name, or null for an id the catalog no longer answers. */
      val commonName: String?,
      val confidence: Double?,
      /** True when a sighting names this event — the confirmation, worn as ink. */
      val isConfirmed: Boolean,
      /** The photo the detection was made from, when it was made from one. */
      val photo: OutingMedia?,
  ) : TimelineRow

  /** One Q&A exchange — the watcher asked, the app answered. */
  data class Exchange(
      override val id: String,
      override val offsetMs: Long?,
      val question: String,
      val answer: String,
  ) : TimelineRow

  /** A photo no detection claimed. Still a moment; still on the clock. */
  data class Photo(
      override val id: String,
      override val offsetMs: Long?,
      val media: OutingMedia,
  ) : TimelineRow
}

/**
 * The recording, ready to play: the whole outing's sonogram, and where the playhead is on it.
 * Published beside the page state rather than inside it, the way the bird page keeps its clip
 * transport beside the bird — the entry does not change thirty times a second.
 */
data class OutingPlaybackUiState(
    /** The full session's strip, built once from the saved segments. Gaps stay dark. */
    val sonogram: SonogramBuffer,
    val totalMs: Long,
    val positionMs: Long = 0,
    val isPlaying: Boolean = false,
)

/**
 * What the journal-entry detail screen renders.
 *
 * [NotFound] is not an error: an outing can be deleted from under an open address, or a stale link
 * can name one that never existed.
 */
sealed interface OutingDetailUiState {
  data object Loading : OutingDetailUiState

  data object NotFound : OutingDetailUiState

  data class Loaded(
      val entry: JournalEntry,
      /** The outing's moments in clock order — empty for a wizard entry. */
      val timeline: List<TimelineRow>,
  ) : OutingDetailUiState
}

/**
 * The journal entry page's state holder.
 *
 * Takes the outing id rather than the row, so the page is reachable by address alone —
 * `Route.OutingDetail(outingId)` — the same way `BirdDetailViewModel` takes a slug. Reads the
 * outing from the Journal store and resolves its confirmed birds against the catalog, producing the
 * same [JournalEntry] the list is built from.
 *
 * A `LIVE` entry also gets its recording back: the audio segments are decoded off the main thread
 * into one [OutingPlayback] — the edit decision list, gaps and all — and the same samples are run
 * through the same [SonogramAnalyzer] the session drew with, so the strip here is the strip the
 * watcher saw, recomputed rather than stored (the design notes, "Derived visuals are not stored").
 * Playback position is sampled on a ticker while playing, the way the bird page samples its clip.
 */
class OutingDetailViewModel(
    private val outingId: String,
    private val journal: JournalRepository,
    private val birdCatalog: BirdCatalogRepository,
    private val mediaFileStore: MediaFileStore,
    private val player: OutingAudioPlayer,
) : ViewModel() {

  private val _uiState = MutableStateFlow<OutingDetailUiState>(OutingDetailUiState.Loading)
  val uiState: StateFlow<OutingDetailUiState> = _uiState.asStateFlow()

  private val _playback = MutableStateFlow<OutingPlaybackUiState?>(null)

  /** Null until the recording is decoded — and forever, for an outing that has none. */
  val playback: StateFlow<OutingPlaybackUiState?> = _playback.asStateFlow()

  /** Samples [OutingAudioPlayer.positionMs] at the strip's cadence while playing. */
  private var tickerJob: Job? = null

  init {
    load()
  }

  private fun load() {
    viewModelScope.launch {
      val withChildren = runCatching { journal.findById(outingId) }.getOrNull()
      if (withChildren == null) {
        _uiState.value = OutingDetailUiState.NotFound
        return@launch
      }
      val birds =
          JournalEntry.storyOrder(withChildren).map { sighting ->
            ConfirmedBird(
                sighting = sighting,
                species = runCatching { birdCatalog.findById(sighting.speciesId) }.getOrNull(),
            )
          }
      _uiState.value =
          OutingDetailUiState.Loaded(
              entry = JournalEntry(withChildren, birds),
              timeline = timeline(withChildren),
          )
      prepareRecording(withChildren)
    }
  }

  /**
   * The outing's moments, oldest first — the order the walk happened in, which is the order a
   * reader replays it. Stable within a stamp, so two moments the same second keep their written
   * order.
   */
  private suspend fun timeline(withChildren: OutingWithChildren): List<TimelineRow> {
    val confirmedEvents = withChildren.sightings.mapNotNull { it.sourceEventId }.toSet()
    val claimedPhotos = withChildren.events.mapNotNull { it.mediaId }.toSet()

    val rows = mutableListOf<TimelineRow>()
    for (event in withChildren.events) when (event.type) {
      OutingEventType.DETECTION ->
          rows +=
              TimelineRow.Detection(
                  id = event.id,
                  offsetMs = withChildren.offsetOf(event),
                  commonName =
                      event.speciesId?.let { id ->
                        runCatching { birdCatalog.findById(id) }.getOrNull()?.species?.commonName
                      },
                  confidence = event.confidence,
                  isConfirmed = event.id in confirmedEvents,
                  photo = withChildren.mediaFor(event),
              )

      OutingEventType.QA -> {
        val question = event.question ?: continue
        val answer = event.answer ?: continue
        rows +=
            TimelineRow.Exchange(
                id = event.id,
                offsetMs = withChildren.offsetOf(event),
                question = question,
                answer = answer,
            )
      }

      // Provenance, not a moment — the field notes already read the wizard's answers.
      OutingEventType.WIZARD_ANSWER -> Unit
    }
    for (photo in withChildren.photos) {
      if (photo.id in claimedPhotos) continue
      rows += TimelineRow.Photo(id = photo.id, offsetMs = photo.offsetMs, media = photo)
    }
    return rows.sortedBy { it.offsetMs ?: Long.MAX_VALUE }
  }

  /**
   * Decodes the saved segments and hands the page its recording — off the main thread, because a
   * walk's worth of WAV and FFT belongs nowhere near a frame.
   *
   * A segment whose file is gone drops out silently: a row pointing at a missing file is the
   * recoverable case the delete ordering was designed around, and the rest of the recording is
   * still worth playing.
   *
   * **The strip is read back rather than measured, when Save left one.** Every one of its columns
   * was computed already, as the audio arrived during the session; the sidecar is that work kept
   * instead of thrown away. The analyzer still runs for an outing saved before the sidecar existed,
   * or one whose sidecar no longer reads — see [storedSonogram].
   */
  private fun prepareRecording(withChildren: OutingWithChildren) {
    val audioRows = withChildren.audio
    if (audioRows.isEmpty()) return

    viewModelScope.launch(Dispatchers.Default) {
      val segments = audioRows.mapNotNull { row ->
        runCatching {
          PlaybackSegment(
              offsetMs = row.offsetMs ?: 0L,
              samples =
                  WavCodec.decode(
                      mediaFileStore.resolve(row.filePath).readBytes(),
                  ),
          )
        }
            .getOrNull()
      }
      if (segments.isEmpty()) return@launch

      val playback = OutingPlayback(
          segments = segments,
          totalMs = withChildren.outing.durationMs ?: 0L,
      )
      player.load(playback)
      player.onFinish = {
        // Played out: transport returns to the top, ready to play again.
        player.seek(0)
        _playback.update { it?.copy(isPlaying = false, positionMs = 0) }
      }
      _playback.value =
          OutingPlaybackUiState(
              sonogram = storedSonogram() ?: sonogramOf(segments),
              totalMs = playback.totalMs,
          )
    }
  }

  /**
   * The strip as Save left it, or null for a walk with none to read.
   *
   * **Null is the ordinary answer, not an error**, and every way of reaching it means the same
   * thing to the caller: an outing saved before the sidecar existed, a file a crash truncated, a
   * version this build no longer knows. All of them fall through to [sonogramOf], which is what the
   * page did before any of this — see [SonogramBuffer.decoded].
   *
   * `oldest > 0` joins them: it means the live ring had already overwritten the start of the walk
   * before Save read it — a session longer than [SonogramCapacity]. The journal wants the whole
   * outing, so a strip missing its opening is recomputed rather than drawn short.
   */
  private fun storedSonogram(): SonogramBuffer? = runCatching {
    mediaFileStore.resolve(MediaFileStore.sonogramPath(outingId)).readBytes()
  }
      .getOrNull()
      ?.let(SonogramBuffer::decoded)
      ?.takeIf { it.oldest == 0 }

  /**
   * The whole outing's strip, from the same analyzer the session drew with. Sized to the outing
   * rather than the live ring's ten minutes — the journal wants all of it, and a byte per bin keeps
   * even a long walk under a few megabytes.
   */
  private fun sonogramOf(segments: List<PlaybackSegment>): SonogramBuffer {
    val analyzer = SonogramAnalyzer()
    val lastEnd = segments.maxOf { it.offsetMs + it.samples.size * 1000L / CaptureSampleRate }
    val columns = (lastEnd / 1000.0 * SonogramColumnsPerSecond).toInt() + 1
    val buffer = SonogramBuffer(capacity = columns)

    for (segment in segments.sortedBy { it.offsetMs }) {
      // Each segment starts on its own clock reading; the distance from the last
      // one's end stays dark, which is what a gap in the audio looks like.
      analyzer.reset()
      buffer.advance(to = (segment.offsetMs / 1000.0 * SonogramColumnsPerSecond).toInt())
      analyzer.analyze(AudioChunk(segment.samples)).forEach { buffer.append(it) }
    }
    return buffer
  }

  // ── Transport ──────────────────────────────────────────────────────────

  /** Play from here — or from the top, when the playhead is at the end — or hold. */
  fun togglePlayback() {
    val state = _playback.value ?: return
    if (state.isPlaying) {
      player.pause()
      _playback.update { it?.copy(isPlaying = false, positionMs = player.positionMs) }
      tickerJob?.cancel()
    } else {
      if (state.positionMs >= state.totalMs) {
        player.seek(0)
        _playback.update { it?.copy(positionMs = 0) }
      }
      resume()
    }
  }

  /**
   * The playhead under a moving finger. The engine goes quiet for the length of the scrub — seeking
   * stops a streamed transport (see [OutingAudioPlayer.seek]) — but the transport's *intent* is
   * left alone, so a recording that was playing is still playing as far as the page is concerned,
   * and [seek] picks it back up when the finger lets go.
   */
  fun scrub(toMs: Long) {
    move(toMs)
  }

  /**
   * Settles the playhead: the strip's tap, the end of a scrub, and a tapped row's landing. A
   * recording that was playing carries straight on from the new position — moving the playhead is
   * not asking the recording to stop.
   */
  fun seek(toMs: Long) {
    val wasPlaying = _playback.value?.isPlaying ?: false
    move(toMs)
    if (wasPlaying) resume()
  }

  /** A tap on a timeline row: the playhead goes to its moment. Clockless rows stay put. */
  fun seekTo(row: TimelineRow) {
    row.offsetMs?.let { seek(it) }
  }

  /**
   * What a scrub and a seek share: the playhead moves, and the ticker stops — position is the
   * finger's to say until the transport is rolling again.
   */
  private fun move(toMs: Long) {
    val state = _playback.value ?: return
    val clamped = toMs.coerceIn(0L, state.totalMs)
    player.seek(clamped)
    tickerJob?.cancel()
    _playback.update { it?.copy(positionMs = clamped) }
  }

  /** Rolls from wherever the playhead is. */
  private fun resume() {
    val state = _playback.value ?: return
    if (state.positionMs >= state.totalMs) {
      // Scrubbed to the very end: there is nothing left to play, so the transport reads
      // stopped rather than showing a pause button over silence. The play button is
      // what returns it to the top.
      _playback.update { it?.copy(isPlaying = false) }
      return
    }
    player.play()
    _playback.update { it?.copy(isPlaying = true) }
    startTicker()
  }

  private fun startTicker() {
    tickerJob?.cancel()
    tickerJob = viewModelScope.launch {
      while (isActive && player.isPlaying) {
        _playback.update { it?.copy(positionMs = player.positionMs) }
        delay(TimelineTickMillis)
      }
    }
  }

  /**
   * Deletes the outing — its files first, then the rows, per [JournalRepository] — and runs
   * [onDeleted] on success so the screen can pop.
   */
  fun delete(onDeleted: () -> Unit) {
    viewModelScope.launch {
      runCatching { journal.delete(outingId) }.onSuccess { onDeleted() }
    }
  }

  override fun onCleared() {
    player.release()
  }

  companion object {
    fun factory(
        outingId: String,
        journal: JournalRepository,
        birdCatalog: BirdCatalogRepository,
        mediaFileStore: MediaFileStore,
        player: OutingAudioPlayer,
    ): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
          override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            @Suppress("UNCHECKED_CAST")
            return OutingDetailViewModel(
                outingId,
                journal,
                birdCatalog,
                mediaFileStore,
                player,
            )
                as T
          }
        }
  }
}
