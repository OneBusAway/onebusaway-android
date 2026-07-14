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

package org.onebusaway.android.database.survey.dao

import androidx.room.*
import org.onebusaway.android.database.survey.entity.Study

/**
 * DAO interface for managing database operations related to Study entities in the survey module.
 */
@Dao
interface StudiesDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStudy(study: Study): Long

    @Update
    suspend fun updateStudy(study: Study)

    @Delete
    suspend fun deleteStudy(study: Study)

    @Query("SELECT * FROM studies WHERE study_id = :studyId")
    suspend fun getStudyById(studyId: Int): Study?

    @Query("SELECT * FROM studies")
    suspend fun getAllStudies(): List<Study>
}
