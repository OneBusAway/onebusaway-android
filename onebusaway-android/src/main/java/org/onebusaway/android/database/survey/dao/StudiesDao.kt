package org.onebusaway.android.database.survey.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
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
