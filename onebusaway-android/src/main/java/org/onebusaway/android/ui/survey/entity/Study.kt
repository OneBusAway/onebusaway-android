package org.onebusaway.android.ui.survey.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "studies")
data class Study(
    @PrimaryKey val study_id: Int,
    val name: String,
    val description: String,
    val is_subscribed: Boolean
)
