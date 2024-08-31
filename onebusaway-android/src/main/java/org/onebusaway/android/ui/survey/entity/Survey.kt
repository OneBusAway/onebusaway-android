package org.onebusaway.android.ui.survey.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Entity class representing a Survey, linked to a Study via a foreign key, with fields for ID, study ID, name, and state.
 */
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
