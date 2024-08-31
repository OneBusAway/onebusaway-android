package org.onebusaway.android.ui.survey.dao

import androidx.room.*
import org.onebusaway.android.ui.survey.entity.Study

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
