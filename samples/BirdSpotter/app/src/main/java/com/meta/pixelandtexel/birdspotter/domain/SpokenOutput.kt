/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.domain

/**
 * The app's own voice — one authored line, said where the wearer will hear it.
 *
 * **Nothing in the toolkit speaks.** Audio out of a pair of glasses is plain Bluetooth from the
 * phone, which is why this seam names no device: the platform's own synthesiser plays into whatever
 * route the phone is already holding, and the glasses are the far end of it. That is the media
 * profile, in full bandwidth, whichever microphone the session is listening through: the glasses'
 * own microphone arrives on the camera stream rather than on a Bluetooth audio link, so listening
 * costs the voice nothing.
 *
 * **The phone never speaks on its own, and that rule lives under this interface rather than in its
 * callers.** A phone speaker announcing "American Robin, 87%" in the field would scatter the very
 * birds being watched — real birding practice rather than politeness: playback to draw out
 * territorial birds is discouraged generally and banned outright in many reserves. So a line is
 * always handed over and sometimes lands nowhere: with no Bluetooth output route there is nowhere
 * to say it, and the words are dropped rather than played out loud. Asking first would put the same
 * rule in every caller, where it would go stale one caller at a time.
 */
interface SpokenOutput {

  /**
   * Whether the app's own voice is on the route right now — true from the moment a line is handed
   * over until its last word, and false when there was nowhere to say it.
   *
   * **The session reads this and stops listening while it holds.** A spoken line comes back down
   * the microphone: the glasses' speakers sit beside the glasses' microphones, and a phone speaker
   * sits a few inches from a phone microphone. A session that kept drawing through its own
   * announcement would put the app's voice on the spectrogram and record it into the outing, and a
   * classifier would go one worse — the app saying "green jay" and then detecting a green jay is a
   * self-inflicted false positive in the one scenario that exists to prove there are none.
   */
  val isSpeaking: Boolean

  /**
   * Says one line aloud, returning when the last word is out — or at once, when there was nowhere
   * to say it.
   *
   * Lines **queue rather than interrupt**: two identifications in quick succession are both worth
   * hearing, and a bird cut off mid-name is worse than a bird named a second late.
   */
  suspend fun speak(words: String)

  /**
   * Cuts the current line short and drops whatever was queued behind it. What the stop reaches for:
   * a session that has ended has nothing left to say.
   */
  fun silence()
}

/**
 * How sure the app sounds about a bird it heard.
 *
 * **The confidence is the only thing that changes the sentence.** A detection already carries a
 * number, the timeline already prints it, and a voice that read it out — *"Green Jay, eighty-seven
 * percent"* — would be a screen being recited rather than somebody talking. What a person does with
 * a number that is not quite certain is hedge, so that is what the app does with it: the percentage
 * stays on the phone, where it can be looked at, and the ear gets the confidence as grammar.
 *
 * Two openings and no third. A middle band would need a middle wording, and the honest set of
 * things to say about a bird you heard is *I heard one* or *I think I heard one*.
 */
enum class SpokenCertainty(val opening: String) {

  /** At or above [SureAloudConfidence] — "I just heard a Green Jay." */
  SURE("I just heard"),

  /** Below it — "I think I heard a Green Jay." */
  HEDGED("I think I heard"),
  ;

  companion object {

    /** Which opening a confidence earns. */
    fun of(confidence: Float): SpokenCertainty =
        if (confidence >= SureAloudConfidence) SURE else HEDGED
  }
}

/**
 * The confidence at which the app stops hedging out loud, as a fraction.
 *
 * **Well above the floor a detection has to clear to exist at all**, and deliberately so: those are
 * two different questions. The detector's threshold asks *is this worth mentioning*; this one asks
 * *do I sound sure when I mention it*, and the whole band between them is what "I think" exists to
 * cover. Setting them to the same number would leave nothing for the hedge to describe.
 */
const val SureAloudConfidence = 0.85f

/**
 * The line the app says out loud when the microphone hears a bird.
 *
 * **Composed by default, where a photo's line is authored**, and the difference is not an
 * inconsistency. A photo row is one scripted beat that an operator writes a sentence for; ambient
 * calls are a list that runs on a clock, and asking somebody to hand-write a sentence per row —
 * with the same three words at the front of every one — would be a form that mostly gets filled in
 * wrong. What varies between two heard birds is the name and how sure the app is, and both of those
 * are already on the row.
 *
 * The Director shows the result rather than making anyone imagine it: this sentence stands in the
 * editor's spoken-line field until somebody types over it, and the preset page carries the opening
 * as a chip. A row that carries its own line says that instead — the default is what a row falls
 * back to, not the only thing it can say.
 */
fun heardAloud(commonName: String, confidence: Float): String =
    "${SpokenCertainty.of(confidence).opening} ${indefiniteArticle(commonName)} $commonName."

/**
 * "a" or "an", for a bird's name.
 *
 * **Spelling decides it, with one exception, and the exception is the point.** The rule is really
 * about *sound* — it is "an Osprey" and "an American Robin" because those open on a vowel — and the
 * two names in the catalog that break the spelling rule both open on a "y": *European Starling* and
 * *Eurasian Collared-Dove* are "a", the way "a European" is. Every other vowel-initial name the
 * catalog holds takes "an", so this is checked against the whole set rather than reasoned about in
 * the abstract.
 *
 * A name that is neither — a new catalog with an *Upland Sandpiper* in it, say — would be worth a
 * second look here. There is no way to get this right from spelling alone in general, and a
 * pronunciation dictionary is a great deal of machinery for a two-letter word.
 */
fun indefiniteArticle(name: String): String {
  val opening = name.trimStart()
  val first = opening.firstOrNull()?.lowercaseChar() ?: return "a"
  if (first !in "aeiou") return "a"
  return if (opening.startsWith("Eu", ignoreCase = true)) "a" else "an"
}
