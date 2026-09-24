/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.domain

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * What any section of a demo preset lands — a tagged union on the wire, keyed by `kind`.
 *
 * Three outcomes, because three is what a demo can show: the app named a bird, the app could not
 * choose between two, or the app declined to answer. *Low* confidence is not a fourth — it is
 * [Species] with the slider down, and the screen decides what a number that low is worth. Transport
 * failures are not here either: a photo that never arrived is a Layer-1 problem and not something
 * the Director scripts.
 *
 * The wire format is fixed byte for byte (`{"kind": "species",...}`), which is what lets one
 * shipped JSON file feed every build. The `kind` tag is read here by [JsonClassDiscriminator].
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("kind")
sealed interface DemoResult {

  /**
   * The card, the photo, the confidence — the ninety-percent case, and the low-confidence one too:
   * the [confidence] is the whole of the difference.
   */
  @Serializable
  @SerialName("species")
  data class Species(val speciesId: String, val confidence: Double) : DemoResult

  /** Two candidates, unresolved — "Green Jay or Blue Jay?", settled by an STT answer. */
  @Serializable
  @SerialName("ambiguous")
  data class Ambiguous(val candidateIds: List<String>) : DemoResult

  /** Nothing lands and the app stays calm — the no-false-positive scenario. */
  @Serializable @SerialName("noIdentification") data object NoIdentification : DemoResult
}

/**
 * STT input — a question the watcher asks out loud, and the answer to give back.
 *
 * [speciesId] surfaces a real card with the bird's photo; it deliberately carries **no
 * confidence**, because the app did not guess — the watcher described the bird and the app agreed.
 * A number there would be inventing precision.
 */
@Serializable
data class DemoQuestion(
    val id: String,
    /** Spoken wordings that should match this row — several, so nobody has to be word-perfect. */
    val prompts: List<String>,
    /** The one line the app says or shows. */
    val answer: String,
    /** A catalog species to surface alongside the answer, or null for a words-only reply. */
    val speciesId: String? = null,
    /** Time to compose, in ms. */
    val delayMillis: Int,
)

/**
 * Photo — the Nth capture of a session gets the Nth of these; past the end of the list, every
 * capture gets [DemoPhotoResponse.pastTheEnd].
 */
@Serializable
data class DemoPhotoResponse(
    val id: String,
    val result: DemoResult,
    /** What appears on screen. */
    val caption: String,
    /** What the glasses say — only when glasses are connected; the phone stays silent. */
    val spokenLine: String? = null,
    /** The response-delay slider, in ms. */
    val delayMillis: Int,
) {

  companion object {

    /**
     * What every capture past the end of a preset's list gets, however many are taken.
     *
     * Fixed, not authored: running off the end of a script is the demo going somewhere the operator
     * did not plan for, and the honest answer there is that the app did not identify anything.
     * Letting it be configured invites a preset whose fourth photo confidently names a bird nobody
     * pointed the camera at.
     *
     * It stays silent on the glasses for the same reason.
     */
    val pastTheEnd: DemoPhotoResponse = DemoPhotoResponse(
        id = "photo-past-the-end",
        result = DemoResult.NoIdentification,
        caption = "Not enough to go on — try getting closer.",
        spokenLine = null,
        delayMillis = 2_000,
    )
  }
}

/**
 * Ambient input — a bird call landing on the clock.
 *
 * [afterMillis] is a **gap from the previous row**, not an offset from the start — the first row's
 * gap is measured from the microphone opening. Gaps are what a person editing a demo thinks in, and
 * inserting a row does not renumber every row after it. That is the same reasoning the hard-coded
 * script this replaced was written with — authored now.
 */
@Serializable
data class DemoAmbientCall(
    val id: String,
    val afterMillis: Int,
    val result: DemoResult,
    /**
     * Words to say instead of the sentence this row would compose for itself, or null to let it
     * compose one — see [heardAloud].
     *
     * **Composed is the default because a list on a clock is not a place to write the same opening
     * over and over.** But a row is sometimes the one beat of a demo that needs its own words — a
     * call the script wants introduced rather than announced — and rewriting the sentence beats
     * adding a bird to the catalog to get a different one. It reaches the wearer under the same
     * rule as every other line: only where there are glasses to say it into.
     *
     * It belongs to a row that names a bird. A row that lands no identification says nothing out
     * loud, and a line authored for one would be words that never play.
     */
    val spokenLine: String? = null,
)

/**
 * A named, saved script for the Demo Director. One is armed at a time — or none, which is legal and
 * means the app never identifies anything.
 *
 * Three lists, one per way an answer gets triggered: [questions] are matched on the watcher's
 * words, [photoResponses] are ordered by capture index, [ambientCalls] fire on the clock. That the
 * trigger is the *section* — not a per-row setting — is the design's whole shape.
 *
 * A preset never picks a platform: device source, environment, compass and gaze are all
 * deliberately absent. The same preset runs identically on phone-only, mock device and real
 * glasses.
 */
@Serializable
data class DemoPreset(
    /** Wire-format version. Stored presets outlive the code that wrote them. */
    val version: Int = 1,
    val id: String,
    val name: String,
    val questions: List<DemoQuestion> = emptyList(),
    val photoResponses: List<DemoPhotoResponse> = emptyList(),
    val ambientCalls: List<DemoAmbientCall> = emptyList(),
    /** The line for a spoken question that matches nothing. */
    val unmatchedQuestion: String,
) {

  companion object {

    /**
     * The tolerances the wire format promises: unknown fields are ignored rather than fatal (a
     * preset authored by a newer build must not take an older one down on launch), and nulls are
     * omitted on encode.
     */
    @OptIn(ExperimentalSerializationApi::class)
    val wire: Json = Json {
      ignoreUnknownKeys = true
      explicitNulls = false
    }

    /** One preset off the wire — the shape `assets/presets/full-flow.json` ships in. */
    fun decode(jsonText: String): DemoPreset = wire.decodeFromString(jsonText)
  }
}
