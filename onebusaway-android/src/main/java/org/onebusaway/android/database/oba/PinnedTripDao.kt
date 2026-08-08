/*
 * Copyright (C) 2026 Open Transit Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package org.onebusaway.android.database.oba

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * The one trip the rider parked so they could go and look at something else (#2053) — the query they
 * asked and the plan it produced, kept whole so resuming redraws the trip with no network at all.
 *
 * Room rather than a preferences slot (the [org.onebusaway.android.tracking.TrackedRouteStore]
 * pattern) because of size alone: an itinerary carries every leg's encoded geometry and turn-by-turn
 * steps, so [itinerariesJson] runs to tens of kilobytes. Preferences DataStore rewrites its whole
 * file on every unrelated write and keeps its slots in a cache for the app's life; a row read only
 * when the card actually draws belongs on disk.
 *
 * @param pinId always [SINGLE_PIN_ID]. There is exactly one pinned trip — pinning replaces — so the
 *        key is a sentinel rather than an identity, which makes "exactly one" structural instead of
 *        a rule the writers have to remember.
 * @param formatVersion the [org.onebusaway.android.ui.tripplan.pinned.PINNED_TRIP_FORMAT_VERSION]
 *        this row was written by; see there for what it protects.
 * @param queryJson the encoded [org.onebusaway.android.ui.tripplan.pinned.PinnedTripQuery] — the
 *        request the rider made, including the "depart now" anchor the request object itself doesn't
 *        carry.
 * @param itinerariesJson every option the plan returned, encoded by
 *        [org.onebusaway.android.directions.model.toJson]. All of them, not just the chosen one: a
 *        picker redrawn with a single card would state that the planner found one option, and the
 *        only way back to the alternatives would be the network the snapshot exists to avoid.
 * @param selectedIndex which of those options the rider pinned. A column rather than a field inside
 *        [itinerariesJson] because it is the one value a reader validates *against the other blob's
 *        length*, and keeping it visible makes that guard obvious.
 * @param pinnedAtMs when the pin was taken, on the device wall clock. Recorded, but deliberately
 *        **never compared against a clock**: a pin has no expiry (#2053 question (a) is answered by
 *        there being one slot, not by a timer), and inventing an age bound for it would be exactly
 *        the kind of unjustified threshold this codebase refuses. It is here for logging and for a
 *        future "pinned on Tuesday" affordance.
 */
@Entity(tableName = "pinned_trips")
data class PinnedTripRecord(
    @PrimaryKey @ColumnInfo(name = "pin_id") val pinId: String,
    @ColumnInfo(name = "format_version") val formatVersion: Int,
    @ColumnInfo(name = "query_json") val queryJson: String,
    @ColumnInfo(name = "itineraries_json") val itinerariesJson: String,
    @ColumnInfo(name = "selected_index") val selectedIndex: Int,
    @ColumnInfo(name = "pinned_at_ms") val pinnedAtMs: Long
)

/** The primary key every pinned-trip row is written under; see [PinnedTripRecord.pinId]. */
const val SINGLE_PIN_ID = "pinned_trip"

@Dao
interface PinnedTripDao {

    /**
     * The pinned trip as it changes, or null when nothing is pinned.
     *
     * Emits the whole row rather than a "is something pinned" boolean (the shape
     * [NavigationSessionDao.observeHasActiveSession] takes) because the resume card draws the trip's
     * destination and summary from the payload — presence alone would only mean a second read.
     */
    @Query("SELECT * FROM pinned_trips LIMIT 1")
    fun observePinned(): Flow<PinnedTripRecord?>

    @Query("SELECT * FROM pinned_trips LIMIT 1")
    suspend fun pinned(): PinnedTripRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: PinnedTripRecord)

    @Query("DELETE FROM pinned_trips")
    suspend fun clear()

    /** Makes [record] the pinned trip, whatever was pinned before. One row in, one row out. */
    @Transaction
    suspend fun replace(record: PinnedTripRecord) {
        clear()
        upsert(record)
    }
}
