package org.onebusaway.android.database

import androidx.room.Database
import androidx.room.RoomDatabase
import org.onebusaway.android.ui.survey.dao.StudiesDao
import org.onebusaway.android.ui.survey.dao.SurveysDao
import org.onebusaway.android.ui.survey.entity.Study
import org.onebusaway.android.ui.survey.entity.Survey

@Database(entities = [Study::class, Survey::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun studiesDao(): StudiesDao
    abstract fun surveysDao(): SurveysDao
}
