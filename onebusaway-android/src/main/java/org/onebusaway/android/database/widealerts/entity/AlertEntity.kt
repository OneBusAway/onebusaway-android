package org.onebusaway.android.database.widealerts.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Represents an alert entity in the database. */
@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey val id: String
)