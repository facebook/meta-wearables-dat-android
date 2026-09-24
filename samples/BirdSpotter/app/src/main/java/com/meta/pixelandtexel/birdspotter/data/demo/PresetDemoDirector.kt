/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.demo

import com.meta.pixelandtexel.birdspotter.domain.BirdCatalogRepository
import com.meta.pixelandtexel.birdspotter.domain.DemoDirector
import com.meta.pixelandtexel.birdspotter.domain.DemoPhotoResponse
import com.meta.pixelandtexel.birdspotter.domain.DemoPreset
import com.meta.pixelandtexel.birdspotter.domain.DemoQuestion
import com.meta.pixelandtexel.birdspotter.domain.DemoResult
import com.meta.pixelandtexel.birdspotter.domain.SessionFinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * The [DemoDirector] the app ships: it reads the armed preset out of [DemoSettingsStore] and plays
 * it.
 *
 * Which preset is armed is read at collection time, so re-arming between takes needs no restart —
 * the next session simply plays the new script. Within a session nothing re-reads it: a preset
 * changing under a live run is not a case worth designing for, because the operator recovering from
 * anything is "close the session, start a new one".
 *
 * Species ids resolve against the catalog at play time, because a [SessionFinding.Bird] carries a
 * common name and the preset deliberately does not — the catalog is the one source of bird names. A
 * row whose id the catalog cannot resolve is dropped rather than surfaced half-named; the import
 * path is where unknown ids get *refused* (see the QR stretch goal).
 */
class PresetDemoDirector(
    private val store: DemoSettingsStore,
    private val catalog: BirdCatalogRepository,
) : DemoDirector {

  override val armed: DemoPreset?
    get() = store.armedPreset()

  /**
   * The ambient section, on the clock: each call waits its gap, then lands as a bird finding. Cold,
   * like every [SessionDetector][com.meta.pixelandtexel.birdspotter.domain.SessionDetector] —
   * collecting starts the clock, cancelling abandons it, and a second collection is a fresh session
   * from the top.
   *
   * With nothing armed this completes without emitting: the session goes on listening, deliberately
   * silent.
   */
  override fun findingStream(): Flow<SessionFinding> = flow {
    val preset = armed ?: return@flow
    for (call in preset.ambientCalls) {
      delay(call.afterMillis.toLong())
      finding(call.result, call.spokenLine)?.let { emit(it) }
    }
    // Falling off the end leaves the session listening with nothing more scripted to
    // say — the honest end of a script, not a failure, and not a loop.
  }

  override fun responseToPhotoAt(index: Int): DemoPhotoResponse? {
    val preset = armed ?: return null
    return preset.photoResponses.getOrNull(index) ?: DemoPhotoResponse.pastTheEnd
  }

  /**
   * The question a spoken sentence is asking, or null for a clean miss.
   *
   * **Matched word by word, in order, rather than as a run of characters.** A prompt is a phrase
   * somebody typed into the editor and a transcript is a sentence somebody actually said, and the
   * two are never going to be the same string: "green with a yellow belly" against *"Green **bird**
   * with a yellow belly"* misses on containment for the sake of one word nobody thought to author.
   * That is not a hypothetical — it is the first question ever asked of a real pair of glasses, and
   * it went unanswered.
   *
   * So a prompt matches when **all of its words appear in the transcript, in the order it wrote
   * them**, with anything at all allowed in between. The filler the wearer puts in — *bird*, *sort
   * of*, *kind of a* — no longer costs the operator a prompt variant each.
   *
   * **Order is what keeps it from being a bag of words.** Without it, "belly yellow green" would
   * answer a question about a green bird with a yellow belly, and so would any sentence unlucky
   * enough to contain the same four words scattered through it. With it, the phrase has to have
   * been *said*, in the shape it was authored.
   *
   * The looseness is deliberate and it is not free: a run listens for its whole length, so a longer
   * sentence has more chances to contain a short prompt's words in order. **Short prompts are the
   * risk, not long ones** — a two-word prompt will eventually match something said to the room.
   * Author phrases, not keywords.
   */
  override fun answerTo(transcript: String): DemoQuestion? {
    val heard = normalized(transcript).words()
    if (heard.isEmpty()) return null
    val preset = armed ?: return null
    return preset.questions.firstOrNull { question ->
      question.prompts.any { heard.saidInOrder(normalized(it).words()) }
    }
  }

  /**
   * What an ambient result puts on the timeline.
   *
   * [DemoResult.Species] is a bird with a confidence, whatever that confidence is — what a low
   * number is worth is the screen's judgement, not this one's. [DemoResult.NoIdentification] is,
   * correctly, nothing landing. [DemoResult.Ambiguous] lands as the question it is — "Green Jay or
   * Blue Jay?" — for a spoken answer to settle; a candidate the catalog cannot name drops the whole
   * row, the same silence an unresolvable species keeps.
   *
   * [line] is the row's own wording where it authored one, and it travels with the bird rather than
   * being said here: this class scripts *what* is identified, and where a line comes out is the
   * session's rule to keep.
   */
  private suspend fun finding(result: DemoResult, line: String?): SessionFinding? =
      when (result) {
        is DemoResult.Species -> bird(result.speciesId, result.confidence, line)
        is DemoResult.Ambiguous -> question(result.candidateIds)
        DemoResult.NoIdentification -> null
      }

  private suspend fun bird(
      speciesId: String,
      confidence: Double,
      line: String?,
  ): SessionFinding? {
    val found = runCatching { catalog.findById(speciesId) }.getOrNull() ?: return null
    return SessionFinding.Bird(
        speciesId = speciesId,
        commonName = found.species.commonName,
        confidence = confidence.toFloat(),
        spokenLine = line,
    )
  }

  private suspend fun question(candidateIds: List<String>): SessionFinding? {
    if (candidateIds.isEmpty()) return null
    val names = candidateIds.map { id ->
      runCatching { catalog.findById(id) }.getOrNull()?.species?.commonName ?: return null
    }
    return SessionFinding.Answer(names.joinToString(" or ") + "?")
  }

  companion object {

    /**
     * The words as ears hear them: lowercased, punctuation dropped, whitespace collapsed — so "It's
     * green, with a yellow belly!" reads as the prompt "it's green with a yellow belly" does.
     *
     * **The apostrophe is what this is really for.** A recogniser writes *it's*, an operator types
     * *its*, and neither is wrong; dropping the punctuation makes them the same word rather than
     * making somebody choose. Everything past that is [saidInOrder]'s to forgive.
     */
    fun normalized(text: String): String =
        text.lowercase().replace(Regex("[^a-z0-9 ]"), "").replace(Regex(" +"), " ").trim()

    /** A normalised line as the words it is made of. */
    private fun String.words(): List<String> = if (isEmpty()) emptyList() else split(" ")

    /**
     * Whether this sentence said all of [prompt]'s words, in [prompt]'s order — see [answerTo].
     *
     * One pass, keeping a place in the prompt: every word of the sentence either advances it or is
     * filler. An empty prompt matches nothing rather than everything — an operator who left the
     * field blank authored no way to ask, not a way to ask with silence.
     */
    private fun List<String>.saidInOrder(prompt: List<String>): Boolean {
      if (prompt.isEmpty()) return false
      var next = 0
      for (word in this) {
        if (word == prompt[next]) next++
        if (next == prompt.size) return true
      }
      return false
    }
  }
}
