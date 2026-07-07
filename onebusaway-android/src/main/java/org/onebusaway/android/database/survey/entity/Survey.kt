package org.onebusaway.android.database.survey.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.RoomWarnings

/**
 * Entity class representing a Survey, linked to a Study via a foreign key, with fields for ID, study ID, name, and state.
 */
// Adding indices = [Index("study_id")] is the right fix, but it's a schema change (DB version bump +
// migration + regenerated schema JSON), deferred to https://github.com/OneBusAway/onebusaway-android/issues/1739.
// The surveys table is tiny, so the missing child index has no practical impact today.
@SuppressWarnings(RoomWarnings.MISSING_INDEX_ON_FOREIGN_KEY_CHILD)
@Entity(
    tableName = "surveys", foreignKeys = [ForeignKey(
        entity = Study::class,
        parentColumns = ["study_id"],
        childColumns = ["study_id"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class Survey(
    @PrimaryKey val survey_id: Int, val study_id: Int, val name: String, val state:Int
)
