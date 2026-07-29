/*
 * Copyright The OneBusAway Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.onebusaway.android.database.survey.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity class representing a Survey, linked to a Study via a foreign key, with fields for ID, study ID, name, and state.
 */
// The study_id foreign key is covered by an index so parent-table writes don't trigger a full scan of
// surveys (#1739). Created in MIGRATION_3_4.
@Entity(
    tableName = "surveys",
    foreignKeys = [
        ForeignKey(
            entity = Study::class,
            parentColumns = ["study_id"],
            childColumns = ["study_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("study_id")]
)
data class Survey(
    @PrimaryKey val survey_id: Int,
    val study_id: Int,
    val name: String,
    val state: Int
)
