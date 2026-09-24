/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.pixelandtexel.birdspotter.data.journal

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * What shape the outing took. `LIVE` is the recording flow — a timeline with media and events
 * pinned to it. `MANUAL` is the identify wizard — no capture, no clock, one confirmed bird, written
 * in one breath.
 */
enum class OutingKind {
  LIVE,
  MANUAL,
}

/** What a timeline event row carries. See the per-type column contract in the design doc. */
enum class OutingEventType {
  DETECTION,
  QA,
  WIZARD_ANSWER,
}

/** Which wizard question a `WIZARD_ANSWER` event answers. */
enum class WizardTrait {
  SIZE,
  COLORS,
  BEHAVIOR,
}

/** What a captured file is. Derived visuals (waveform, spectrogram) are never stored. */
enum class OutingMediaType {
  PHOTO,
  AUDIO,
}

/** Which device performed a capture. Also decides whose compass fed that row's `bearingDeg`. */
enum class CaptureSource {
  GLASSES,
  PHONE,
}

/**
 * Where the observer was looking at a moment — the stratum, not the angle.
 *
 * The same five strata the live session's chip reads, and deliberately the same type: what a
 * watcher was shown while aiming is what gets stored beside what they caught. These were three
 * (`CANOPY`/`LEVEL`/`GROUND`) while the stored value and the on-screen one were separate ideas,
 * which cost the two most useful readings a birder has — *overhead*, where a flyover or a raptor
 * is, and *understory*, which is not the ground.
 *
 * Ordered low to high, so `ordinal` is the stratum's height and comparisons read the way the words
 * do. See [com.meta.pixelandtexel.birdspotter.domain.gazeBand] for the thresholds.
 */
enum class GazeContext {
  GROUND,
  UNDERSTORY,
  HORIZON,
  CANOPY,
  OVERHEAD,
}

/**
 * The phone's fix at the start of an outing — one per outing, per open question #16; per-moment
 * location is deliberately not tracked.
 *
 * **Required, not optional.** Identify is gated on location being granted, and a screen that cannot
 * get a fix refuses to save rather than writing an outing that claims to be nowhere. The
 * nullability used to live here and be checked nowhere; now the type carries the rule.
 *
 * Grouped into one value rather than two loose parameters so the call reads the same on both
 * platforms, and so a latitude can never be passed without its longitude.
 */
data class CaptureLocation(
    val latitude: Double,
    val longitude: Double,
)

/**
 * Where a camera was aimed for one *photo* — never the outing, which spans many headings, and never
 * an event or a confirmation, which reach it through the photo they name. Grouped for the same
 * reason as [CaptureLocation].
 *
 * A photo is the only row that *is* a moment of aiming, which is why this rides on [PendingMedia]
 * alone. See the design notes, "What owns a moment".
 */
data class MomentContext(
    val gazeContext: GazeContext? = null,
    /** True north, 0–360, from the same device as the capture's `source`. */
    val bearingDeg: Double? = null,
)

/**
 * One journal entry — the root every child row carries the id of.
 *
 * Written **at save**, never before: an outing assembles in memory as an [OutingDraft] while it is
 * happening and reaches this table only when the user keeps it. A row here is therefore a finished
 * outing by construction — the Journal is a query over these, newest first, with nothing to filter
 * out.
 *
 * See the design notes.
 */
@Entity(
    indices =
        [
            Index(value = ["startedAt"], orders = [Index.Order.DESC]),
        ],
)
data class Outing(
    @PrimaryKey val id: String,
    val kind: OutingKind,
    /** Capture time. May predate [createdAt] when a transfer lags; the Journal sorts on this. */
    val startedAt: Long,
    /** Stamped at stop. Null for `MANUAL`, which has no clock. */
    val durationMs: Long? = null,
    /**
     * The phone's fix, taken once at the start. Not nullable: Identify is gated on location, and a
     * flow that cannot get a fix declines to save rather than logging a bird nowhere.
     */
    val latitude: Double,
    val longitude: Double,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * A file that actually landed in the app's media directory, pinned to the outing's timeline by
 * [offsetMs]. A saved outing may legitimately have zero of these — a walk that heard nothing still
 * keeps. Audio arrives as *segments* — the source can change or vanish under a session that never
 * stopped — which is why one outing holds many audio rows.
 */
@Entity(
    foreignKeys =
        [
            ForeignKey(
                entity = Outing::class,
                parentColumns = ["id"],
                childColumns = ["outingId"],
                onDelete = ForeignKey.CASCADE,
            ),
        ],
    indices = [Index(value = ["outingId"])],
)
data class OutingMedia(
    @PrimaryKey val id: String,
    val outingId: String,
    val type: OutingMediaType,
    val source: CaptureSource,
    /** ms from the outing's `startedAt`. Null where there is no clock — `MANUAL` children. */
    val offsetMs: Long? = null,
    /**
     * Relative to the media root — never absolute. See
     * [com.meta.pixelandtexel.birdspotter.data.media.MediaFileStore].
     */
    @ColumnInfo(name = "filePath") val filePath: String,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
    /** The moment's pitch and heading — photos only; an audio segment has no single moment. */
    val gazeContext: GazeContext? = null,
    val bearingDeg: Double? = null,
    val createdAt: Long,
)

/**
 * A non-file timeline pin. One deliberately wide table rather than three narrow ones, because its
 * one real read is the whole timeline ordered by `offsetMs`; columns are per-[type], null
 * elsewhere:
 *
 * - `DETECTION` — [speciesId], [confidence], and one of [mediaId] / [offsetMs]
 * - `QA` — [question], [answer]; one row per exchange
 * - `WIZARD_ANSWER` — [trait], [value]; three rows per wizard outing
 */
@Entity(
    foreignKeys =
        [
            ForeignKey(
                entity = Outing::class,
                parentColumns = ["id"],
                childColumns = ["outingId"],
                onDelete = ForeignKey.CASCADE,
            ),
            ForeignKey(
                entity = OutingMedia::class,
                parentColumns = ["id"],
                childColumns = ["mediaId"],
                onDelete = ForeignKey.SET_NULL,
            ),
        ],
    indices = [Index(value = ["outingId"]), Index(value = ["mediaId"])],
)
data class OutingEvent(
    @PrimaryKey val id: String,
    val outingId: String,
    /**
     * The capture this event was produced from — a photo, for a detection made by looking at one.
     * Null for everything else, including a *sound* detection: the classifier's window is a range
     * on the clock that can straddle a handover, so there is no single file it came from.
     *
     * Set, this row's moment lives on that photo and [offsetMs] stays null.
     */
    val mediaId: String? = null,
    val type: OutingEventType,
    /**
     * ms from the outing's `startedAt`. Null where there is no clock (`MANUAL` children) and null
     * again where [mediaId] owns the moment — the shutter is the honest position for a detection,
     * and inference lands a few hundred ms after it.
     */
    val offsetMs: Long? = null,
    /** Cross-file reference into `catalog.db`, not a FK. `DETECTION` only. */
    val speciesId: String? = null,
    /** 0–1, and welded to [speciesId] — the label this number actually scored. */
    val confidence: Double? = null,
    val question: String? = null,
    val answer: String? = null,
    val trait: WizardTrait? = null,
    /**
     * `WIZARD_ANSWER` payload, in the catalog enums' own SCREAMING_SNAKE spellings: `SIZE` is the
     * sparrow-to-goose digit ("4"), `COLORS` up to three comma-joined tokens ("BLACK,RED"),
     * `BEHAVIOR` one token.
     */
    val value: String? = null,
    val createdAt: Long,
)

/**
 * A confirmed bird within an outing — the life-list unit. Confirmation is the act of naming a
 * species, so [speciesId] is non-null here; an outing with nothing confirmed simply has no rows.
 *
 * **Who was confirmed, and what evidence backs it — nothing else.** When the bird was seen, how
 * confident the detector was and where the camera pointed all belong to the rows that recorded
 * them, and are read through [sourceEventId] rather than copied here. See
 * [OutingWithChildren.confidenceOf] for the one that can otherwise go wrong.
 *
 * [speciesId] points at `Species.id` in `catalog.db` and deliberately carries no foreign key —
 * SQLite cannot enforce one across files. Treat an unresolvable id as an unidentified sighting,
 * never an error.
 */
@Entity(
    foreignKeys =
        [
            ForeignKey(
                entity = Outing::class,
                parentColumns = ["id"],
                childColumns = ["outingId"],
                onDelete = ForeignKey.CASCADE,
            ),
            ForeignKey(
                entity = OutingEvent::class,
                parentColumns = ["id"],
                childColumns = ["sourceEventId"],
                onDelete = ForeignKey.SET_NULL,
            ),
        ],
    indices =
        [
            Index(value = ["outingId"]),
            Index(value = ["speciesId"]),
            Index(value = ["sourceEventId"]),
        ],
)
data class Sighting(
    @PrimaryKey val id: String,
    val outingId: String,
    /**
     * The `DETECTION` this confirmation accepted. Null for a wizard entry, which reached its bird
     * by answering questions and has no detection to name.
     */
    val sourceEventId: String? = null,
    val speciesId: String,
    val createdAt: Long,
)

/**
 * An outing while it is still happening — the in-memory shape the screens build up and hand to
 * [com.meta.pixelandtexel.birdspotter.domain.JournalRepository.saveOuting] in one piece.
 *
 * **This is what a draft is now.** There is no half-written outing in the database and no status
 * column saying so: an outing that was never saved is a value that was never passed, and it goes
 * away with the screen that held it. The wizard keeps one in its ui state across three questions; a
 * live session keeps one for the length of the walk. Timestamps and file paths are the repository's
 * to assign, and so is the outing's own id — but each child mints its id on construction, which is
 * what lets a detection point at the photo it came from while both are still values in memory.
 */
data class OutingDraft(
    val kind: OutingKind,
    /** Capture time — when the outing began, not when it was saved. */
    val startedAt: Long,
    /** The stop-clock reading for a `LIVE` outing. Null for `MANUAL`, which has no clock. */
    val durationMs: Long? = null,
    /** No default — an outing without a fix is one the caller should not have built. */
    val location: CaptureLocation,
    val notes: String? = null,
    val media: List<PendingMedia> = emptyList(),
    val events: List<PendingEvent> = emptyList(),
    val sightings: List<PendingSighting> = emptyList(),
    /**
     * The session's finished strip, as [SonogramBuffer.encoded] wrote it — null for a wizard entry,
     * which never heard anything, and null for a live one whose recording is empty.
     *
     * **Not a [PendingMedia], deliberately.** The media table holds what was *captured*, and this
     * was derived from it; giving it a row would mean a row that can point at a missing file and a
     * second thing to keep in step with the audio. It rides here instead and lands as a sidecar in
     * the outing's own group, which the existing delete sweep already carries away.
     *
     * Saving it at all is a departure from "Derived visuals are not stored" — see that section of
     * the design notes, which now records why the greyscale is the exception the rendered image is
     * not.
     *
     * It is a [ByteArray] in a `data class`, so the generated `equals` compares it by identity —
     * harmless only because nothing compares drafts.
     */
    val sonogram: ByteArray? = null,
)

/**
 * Bytes captured during an outing, waiting for the outing to be saved.
 *
 * Deliberately **not** a `data class`: it carries a [ByteArray], whose `equals` is identity, so a
 * generated `equals` would be quietly wrong. Nothing compares these.
 */
class PendingMedia(
    val type: OutingMediaType,
    val source: CaptureSource,
    val bytes: ByteArray,
    /**
     * Always explicit: a capture knows its own format, and a defaulted "jpg" would mislabel HEIF.
     */
    val fileExtension: String,
    val offsetMs: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
    /** Photos only — an audio segment spans many moments and has no single one. */
    val moment: MomentContext = MomentContext(),
    /** Minted here, written through by the repository, and what [PendingEvent.seen] points at. */
    val id: String = newDraftId(),
)

/**
 * A timeline pin waiting for its outing. Built through the four factories rather than the
 * constructor, so a caller cannot assemble a `DETECTION` carrying a wizard trait — the per-type
 * column contract is enforced where the row is made, not by convention.
 *
 * A detection is **seen or heard, never both**, which is why there are two of it: [seen] takes the
 * photo and leaves `offsetMs` null, [heard] takes the clock reading and leaves `mediaId` null.
 * Neither can be assembled into the other's shape.
 */
class PendingEvent
private constructor(
    val type: OutingEventType,
    val mediaId: String? = null,
    val offsetMs: Long? = null,
    val speciesId: String? = null,
    val confidence: Double? = null,
    val question: String? = null,
    val answer: String? = null,
    val trait: WizardTrait? = null,
    val value: String? = null,
    /** Minted here, written through by the repository, and what [PendingSighting] confirms. */
    val id: String = newDraftId(),
) {
  companion object {
    /**
     * A candidate found by looking at a photo. The photo carries the moment — when the shutter
     * went, and where the camera was aimed — so this row carries neither.
     */
    fun seen(
        speciesId: String,
        confidence: Double,
        inPhoto: PendingMedia,
    ): PendingEvent = PendingEvent(
        type = OutingEventType.DETECTION,
        mediaId = inPhoto.id,
        speciesId = speciesId,
        confidence = confidence,
    )

    /**
     * A candidate found by listening. [offsetMs] is the only anchor it has: the classifier's window
     * is a range on the clock and can straddle a handover, so no single audio segment is the one it
     * came from.
     */
    fun heard(speciesId: String, confidence: Double, offsetMs: Long?): PendingEvent = PendingEvent(
        type = OutingEventType.DETECTION,
        offsetMs = offsetMs,
        speciesId = speciesId,
        confidence = confidence,
    )

    /** One Q&A exchange during the outing. Multi-turn is multiple events. */
    fun exchange(question: String, answer: String, offsetMs: Long?): PendingEvent = PendingEvent(
        type = OutingEventType.QA,
        offsetMs = offsetMs,
        question = question,
        answer = answer,
    )

    /**
     * One wizard answer, as provenance for the ID. [value] uses the catalog enums' own
     * SCREAMING_SNAKE spellings — see [OutingEvent.value].
     */
    fun wizardAnswer(trait: WizardTrait, value: String): PendingEvent =
        PendingEvent(type = OutingEventType.WIZARD_ANSWER, trait = trait, value = value)
  }
}

/**
 * A bird the user confirmed during an outing — the life-list unit, waiting to be saved with it.
 * Confirmation names a species, so [speciesId] is required; an outing with nothing confirmed simply
 * carries none of these, and still saves.
 *
 * [confirming] is the detection the watcher accepted, and null where they reached the bird another
 * way — the wizard has no detection to name. Note that confirming a detection does not mean
 * agreeing with it: a watcher looking at the same photo may name a different bird, and [speciesId]
 * is theirs either way.
 */
data class PendingSighting(
    val speciesId: String,
    val confirming: PendingEvent? = null,
    val id: String = newDraftId(),
)

/**
 * The id a draft child is born with. Row ids and draft ids are one space on purpose: a second one
 * would need a fixing-up pass after the insert, and the links exist precisely so that a screen can
 * wire evidence together before anything is a row.
 */
private fun newDraftId(): String = UUID.randomUUID().toString()
